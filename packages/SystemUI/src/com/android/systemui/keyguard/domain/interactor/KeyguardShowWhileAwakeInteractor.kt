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
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import com.android.systemui.util.kotlin.sample
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** The reason we're showing lockscreen while awake, used for logging. */
enum class ShowWhileAwakeReason(private val logReason: String) {
    FOLDED_WITH_SWIPE_UP_TO_CONTINUE(
        "Folded with continue using apps on fold set to 'swipe up to continue'."
    ),
    LOCKDOWN("Lockdown initiated."),
    KEYGUARD_REENABLED(
        "Keyguard was re-enabled. We weren't unlocked when it was disabled, " +
            "so we're returning to the lockscreen."
    ),
    KEYGUARD_TIMEOUT_WHILE_SCREEN_ON(
        "Timed out while the screen was kept on, or WM#lockNow() was called."
    ),
    SWITCHED_TO_SECURE_USER_WHILE_GOING_AWAY(
        "User switch to secure user occurred during keyguardGoingAway sequence, so we're locking."
    );

    override fun toString(): String {
        return logReason
    }
}

/**
 * Logic around cases where the we show the lockscreen while still awake (transition from GONE ->
 * LOCKSCREEN), vs. the more common cases of a power button press or screen timeout, which result in
 * the device going to sleep.
 *
 * This does not necessarily mean we lock the device (which does not happen if showing the
 * lockscreen is triggered by [KeyguardService.showDismissibleKeyguard]). We'll show keyguard here
 * and authentication logic will decide if that keyguard is dismissible or not.
 *
 * This is possible in the following situations:
 * - The user initiates lockdown from the power menu.
 * - Theft detection, etc. has requested lockdown.
 * - The keyguard was disabled while visible, and has now been re-enabled, so it's re-showing.
 * - Someone called WM#lockNow().
 * - The screen timed out, but an activity with FLAG_ALLOW_LOCK_WHILE_SCREEN_ON is on top.
 * - A foldable is folded with "Continue using apps on fold" set to "Swipe up to continue" (if set
 *   to "never", then lockscreen will be shown when the device goes to sleep, which is not tnohe
 *   concern of this interactor).
 */
@SysUISingleton
class KeyguardShowWhileAwakeInteractor
@Inject
constructor(
    @Background val backgroundScope: CoroutineScope,
    biometricSettingsRepository: BiometricSettingsRepository,
    keyguardEnabledInteractor: KeyguardEnabledInteractor,
    keyguardServiceShowLockscreenInteractor: KeyguardServiceShowLockscreenInteractor,
) {

    /** Emits whenever the current user is in lockdown mode. */
    private val inLockdown: Flow<ShowWhileAwakeReason> =
        biometricSettingsRepository.isCurrentUserInLockdown
            .distinctUntilChanged()
            .filter { inLockdown -> inLockdown }
            .map { ShowWhileAwakeReason.LOCKDOWN }

    /**
     * Emits whenever the keyguard is re-enabled, and we need to return to lockscreen due to the
     * device being locked when the keyguard was originally disabled.
     */
    private val keyguardReenabled: Flow<ShowWhileAwakeReason> =
        keyguardEnabledInteractor.isKeyguardEnabled
            .filter { enabled -> enabled }
            .sample(keyguardEnabledInteractor.showKeyguardWhenReenabled)
            .filter { reshow -> reshow }
            .map { ShowWhileAwakeReason.KEYGUARD_REENABLED }

    /**
     * Emits whenever a user switch to a secure user occurs during keyguard going away.
     *
     * This is an event flow, hence the SharedFlow.
     */
    @SuppressLint("SharedFlowCreation")
    val switchedToSecureUserDuringGoingAway: MutableSharedFlow<ShowWhileAwakeReason> =
        MutableSharedFlow()

    /** Emits whenever we should show lockscreen while the screen is on, for any reason. */
    val showWhileAwakeEvents: Flow<ShowWhileAwakeReason> =
        merge(
            // We're in lockdown, and the keyguard is enabled. If the keyguard is disabled, the
            // lockdown button is hidden in the UI, but it's still possible to trigger lockdown in
            // tests.
            inLockdown
                .filter { keyguardEnabledInteractor.isKeyguardEnabledAndNotSuppressed() }
                .map { ShowWhileAwakeReason.LOCKDOWN },
            // The keyguard was re-enabled, and it was showing when it was originally disabled.
            // Tests currently expect that if the keyguard is re-enabled, it will show even if it's
            // suppressed, so we don't check for isKeyguardEnabledAndNotSuppressed() on this flow.
            keyguardReenabled.map { ShowWhileAwakeReason.KEYGUARD_REENABLED },
            // KeyguardService says we need to show now, and the lockscreen is enabled.
            keyguardServiceShowLockscreenInteractor.showNowEvents.filter {
                keyguardEnabledInteractor.isKeyguardEnabledAndNotSuppressed()
            },
            switchedToSecureUserDuringGoingAway,
        )

    /** A user switch to a secure user occurred while we were going away. We need to re-lock. */
    fun onSwitchedToSecureUserWhileKeyguardGoingAway() {
        backgroundScope.launch {
            switchedToSecureUserDuringGoingAway.emit(
                ShowWhileAwakeReason.SWITCHED_TO_SECURE_USER_WHILE_GOING_AWAY
            )
        }
    }
}
