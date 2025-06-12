/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.systemui.dreams.conditions

import android.app.DreamManager
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shared.condition.Condition
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** [DreamCondition] provides a signal when a dream begins and ends. */
class DreamCondition
@Inject
constructor(
    @Application scope: CoroutineScope,
    private val _dreamManager: DreamManager,
    private val _updateMonitor: KeyguardUpdateMonitor,
) : Condition(scope) {
    private val _updateCallback: KeyguardUpdateMonitorCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onDreamingStateChanged(dreaming: Boolean) {
                updateCondition(dreaming)
            }
        }

    override suspend fun start() {
        _updateMonitor.registerCallback(_updateCallback)
        updateCondition(_dreamManager.isDreaming)
    }

    override fun stop() {
        _updateMonitor.removeCallback(_updateCallback)
    }

    override val startStrategy: Int
        get() = START_EAGERLY
}
