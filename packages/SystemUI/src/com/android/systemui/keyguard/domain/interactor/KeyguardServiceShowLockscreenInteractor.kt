/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.keyguard.domain.interactor

import android.annotation.SuppressLint
import android.app.KeyguardManager.LOCK_ON_USER_SWITCH_CALLBACK
import android.os.Bundle
import android.os.IRemoteCallback
import android.os.RemoteException
import android.util.Log
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.data.repository.KeyguardServiceShowLockscreenRepository
import com.android.systemui.keyguard.data.repository.ShowLockscreenCallback
import com.android.systemui.settings.UserTracker
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Logic around requests by [KeyguardService] and friends to show keyguard right now, even though
 * the device is awake and not going to sleep.
 *
 * This can happen if WM#lockNow() is called, if KeyguardService#showDismissibleKeyguard is called
 * because we're folding with "continue using apps on fold" set to "swipe up to continue", or if the
 * screen is forced to stay awake but the lock timeout elapses.
 *
 * This is not the only way for the device to lock while the screen is on. The other cases, which do
 * not directly involve [KeyguardService], are handled in [KeyguardShowWhileAwakeInteractor].
 */
@SysUISingleton
class KeyguardServiceShowLockscreenInteractor
@Inject
constructor(
    @Background val backgroundScope: CoroutineScope,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val repository: KeyguardServiceShowLockscreenRepository,
    private val userTracker: UserTracker,
    private val wmLockscreenVisibilityInteractor: Lazy<WindowManagerLockscreenVisibilityInteractor>,
    private val keyguardEnabledInteractor: KeyguardEnabledInteractor,
) : CoreStartable {

    override fun start() {
        backgroundScope.launch {
            // Whenever we tell ATMS that lockscreen is visible, notify any showLockscreenCallbacks.
            // This is not the only place we notify the lockNowCallbacks - there are cases where we
            // decide not to show the lockscreen despite being asked to, and we need to notify the
            // callback in those cases as well.
            wmLockscreenVisibilityInteractor.get().lockscreenVisibility.collect { visible ->
                if (visible) {
                    notifyShowLockscreenCallbacks()
                }
            }
        }
    }

    /**
     * Emits whenever [KeyguardService] receives a call that indicates we should show the lockscreen
     * right now, even though the device is awake and not going to sleep.
     *
     * WARNING: This is only one of multiple reasons the keyguard might need to show while not going
     * to sleep. Unless you're dealing with keyguard internals that specifically need to know that
     * we're locking due to a call to doKeyguardTimeout or showDismissibleKeyguard, use
     * [KeyguardShowWhileAwakeInteractor.showWhileAwakeEvents].
     *
     * This is fundamentally an event flow, hence the SharedFlow.
     */
    @SuppressLint("SharedFlowCreation")
    val showNowEvents: MutableSharedFlow<ShowWhileAwakeReason> = MutableSharedFlow()

    /**
     * Called by [KeyguardService] when it receives a doKeyguardTimeout() call. This indicates that
     * the device locked while the screen was on.
     *
     * We'll show keyguard, and if provided, save the lock on user switch callback, to notify it
     * later when we successfully show.
     */
    fun onKeyguardServiceDoKeyguardTimeout(options: Bundle? = null) {
        backgroundScope.launch {
            if (options?.getBinder(LOCK_ON_USER_SWITCH_CALLBACK) != null) {
                val userId = userTracker.userId

                // This callback needs to be invoked after we show the lockscreen (or decide not to
                // show it) otherwise System UI will crash in 20 seconds, as a security measure.
                repository.addShowLockscreenCallback(
                    userId,
                    IRemoteCallback.Stub.asInterface(
                        options.getBinder(LOCK_ON_USER_SWITCH_CALLBACK)
                    ),
                )

                Log.d(
                    TAG,
                    "Showing lockscreen now - setting required callback for user $userId. " +
                        "SysUI will crash if this callback is not invoked.",
                )

                // If the keyguard is disabled or suppressed, we'll never actually show the
                // lockscreen. Notify the callback so we don't crash.
                if (!keyguardEnabledInteractor.isKeyguardEnabledAndNotSuppressed()) {
                    Log.d(TAG, "Keyguard is disabled or suppressed, notifying callbacks now.")
                    notifyShowLockscreenCallbacks()
                }
            }

            showNowEvents.emit(ShowWhileAwakeReason.KEYGUARD_TIMEOUT_WHILE_SCREEN_ON)
        }
    }

    /**
     * Called by [KeyguardService] when it receives a showDismissibleKeyguard() call. This indicates
     * that the device was folded with settings configured to show a dismissible keyguard on the
     * outer display.
     */
    fun onKeyguardServiceShowDismissibleKeyguard() {
        backgroundScope.launch {
            showNowEvents.emit(ShowWhileAwakeReason.FOLDED_WITH_SWIPE_UP_TO_CONTINUE)
        }
    }

    /** Notifies the callbacks that we've either locked, or decided not to lock. */
    private fun notifyShowLockscreenCallbacks() {
        var callbacks: MutableList<ShowLockscreenCallback>

        synchronized(repository.showLockscreenCallbacks) {
            callbacks = ArrayList(repository.showLockscreenCallbacks)
            repository.showLockscreenCallbacks.clear()
        }

        callbacks.forEach { callback ->
            if (callback.userId != selectedUserInteractor.getSelectedUserId()) {
                Log.i(TAG, "Not notifying lockNowCallback due to user mismatch")
                return
            }
            Log.i(TAG, "Notifying lockNowCallback")
            try {
                callback.remoteCallback.sendResult(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Could not issue LockNowCallback sendResult", e)
            }
        }
    }

    companion object {
        private const val TAG = "ShowLockscreenInteractor"
    }
}
