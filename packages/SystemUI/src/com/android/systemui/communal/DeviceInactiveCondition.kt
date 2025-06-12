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
package com.android.systemui.communal

import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel.Companion.isDozeOff
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.shared.condition.Condition
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.util.kotlin.JavaAdapter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Condition which estimates device inactivity in order to avoid launching a full-screen activity
 * while the user is actively using the device.
 */
class DeviceInactiveCondition
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    @Background backgroundScope: CoroutineScope,
    private val keyguardStateController: KeyguardStateController,
    private val wakefulnessLifecycle: WakefulnessLifecycle,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val keyguardInteractor: KeyguardInteractor,
    private val javaAdapter: JavaAdapter,
) : Condition(backgroundScope) {
    private var anyDozeListenerJob: Job? = null
    private var anyDoze = false
    private val keyguardStateCallback: KeyguardStateController.Callback =
        object : KeyguardStateController.Callback {
            override fun onKeyguardShowingChanged() {
                updateState()
            }
        }
    private val wakefulnessObserver: WakefulnessLifecycle.Observer =
        object : WakefulnessLifecycle.Observer {
            override fun onStartedGoingToSleep() {
                updateState()
            }
        }
    private val keyguardUpdateCallback: KeyguardUpdateMonitorCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onDreamingStateChanged(dreaming: Boolean) {
                updateState()
            }
        }

    override suspend fun start() {
        updateState()
        keyguardStateController.addCallback(keyguardStateCallback)

        // Keyguard update monitor callbacks must be registered on the main thread
        applicationScope.launch { keyguardUpdateMonitor.registerCallback(keyguardUpdateCallback) }
        wakefulnessLifecycle.addObserver(wakefulnessObserver)
        anyDozeListenerJob =
            javaAdapter.alwaysCollectFlow(keyguardInteractor.dozeTransitionModel) {
                dozeModel: DozeTransitionModel ->
                anyDoze = !isDozeOff(dozeModel.to)
                updateState()
            }
    }

    override fun stop() {
        keyguardStateController.removeCallback(keyguardStateCallback)
        keyguardUpdateMonitor.removeCallback(keyguardUpdateCallback)
        wakefulnessLifecycle.removeObserver(wakefulnessObserver)
        anyDozeListenerJob?.cancel(null)
    }

    override val startStrategy: Int
        get() = START_EAGERLY

    private fun updateState() {
        val asleep = wakefulnessLifecycle.wakefulness == WakefulnessLifecycle.WAKEFULNESS_ASLEEP
        // Doze/AoD is also a dream, but we should never override it with low light as to the user
        // it's totally unrelated.
        updateCondition(
            !anyDoze &&
                (asleep || keyguardStateController.isShowing || keyguardUpdateMonitor.isDreaming)
        )
    }
}
