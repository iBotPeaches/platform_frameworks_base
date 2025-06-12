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

package com.android.systemui.reardisplay

import android.content.Context
import android.hardware.devicestate.DeviceStateManager
import android.hardware.devicestate.feature.flags.Flags
import android.os.Handler
import android.view.accessibility.AccessibilityManager
import androidx.annotation.VisibleForTesting
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.domain.interactor.RearDisplayStateInteractor
import com.android.systemui.statusbar.phone.SystemUIDialog
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Provides a {@link com.android.systemui.statusbar.phone.SystemUIDialog} to be shown on the inner
 * display when the device enters Rear Display Mode, containing an UI affordance to let the user
 * know that the main content has moved to the outer display, as well as an UI affordance to cancel
 * the Rear Display Mode.
 */
@SysUISingleton
class RearDisplayCoreStartable
@Inject
internal constructor(
    private val context: Context,
    private val deviceStateManager: DeviceStateManager,
    private val rearDisplayStateInteractor: RearDisplayStateInteractor,
    private val rearDisplayInnerDialogDelegateFactory: RearDisplayInnerDialogDelegate.Factory,
    @Application private val scope: CoroutineScope,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val accessibilityManager: AccessibilityManager,
    @Background private val handler: Handler,
) : CoreStartable, AutoCloseable {

    companion object {
        private const val TAG: String = "RearDisplayCoreStartable"
    }

    @VisibleForTesting var stateChangeListener: Job? = null
    private val keyguardVisible = MutableStateFlow(false)
    private val keyguardVisibleFlow = keyguardVisible.asStateFlow()

    @VisibleForTesting
    val keyguardCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onKeyguardVisibilityChanged(visible: Boolean) {
                keyguardVisible.value = visible
            }
        }

    override fun close() {
        stateChangeListener?.cancel()
    }

    override fun start() {
        if (Flags.deviceStateRdmV2()) {
            var dialog: SystemUIDialog? = null
            var touchExplorationEnabled = AtomicBoolean(false)

            accessibilityManager.addTouchExplorationStateChangeListener(
                { enabled -> touchExplorationEnabled.set(enabled) },
                handler,
            )

            keyguardUpdateMonitor.registerCallback(keyguardCallback)

            stateChangeListener =
                scope.launch {
                    combine(rearDisplayStateInteractor.state, keyguardVisibleFlow) {
                            rearDisplayState,
                            keyguardVisible ->
                            Pair(rearDisplayState, keyguardVisible)
                        }
                        .collectLatest { (rearDisplayState, keyguardVisible) ->
                            when (rearDisplayState) {
                                is RearDisplayStateInteractor.State.Enabled -> {
                                    if (!keyguardVisible) {
                                        val rearDisplayContext =
                                            context.createDisplayContext(
                                                rearDisplayState.innerDisplay
                                            )
                                        val delegate =
                                            rearDisplayInnerDialogDelegateFactory.create(
                                                rearDisplayContext,
                                                deviceStateManager::cancelStateRequest,
                                                touchExplorationEnabled.get(),
                                            )
                                        dialog = delegate.createDialog().apply { show() }
                                    }
                                }

                                is RearDisplayStateInteractor.State.Disabled -> {
                                    dialog?.dismiss()
                                    dialog = null
                                }
                            }
                        }
                }
        }
    }
}
