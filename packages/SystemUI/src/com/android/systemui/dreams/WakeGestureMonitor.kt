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

package com.android.systemui.dreams

import android.hardware.Sensor
import android.hardware.display.AmbientDisplayConfiguration
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.kotlin.observeTriggerSensor
import com.android.systemui.util.sensors.AsyncSensorManager
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@SysUISingleton
class WakeGestureMonitor
@Inject
constructor(
    private val ambientDisplayConfiguration: AmbientDisplayConfiguration,
    private val asyncSensorManager: AsyncSensorManager,
    @Background bgContext: CoroutineContext,
    private val secureSettings: SecureSettings,
    selectedUserInteractor: SelectedUserInteractor,
) {

    private val pickupSensor by lazy {
        asyncSensorManager.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE)
    }

    private val pickupGestureEnabled: Flow<Boolean> =
        selectedUserInteractor.selectedUser.flatMapLatestConflated { userId ->
            isPickupEnabledForUser(userId)
        }

    private fun isPickupEnabledForUser(userId: Int): Flow<Boolean> =
        secureSettings
            .observerFlow(userId, Settings.Secure.DOZE_PICK_UP_GESTURE)
            .emitOnStart()
            .map { ambientDisplayConfiguration.pickupGestureEnabled(userId) }

    val wakeUpDetected: Flow<Unit> =
        pickupGestureEnabled
            .flatMapLatestConflated { enabled ->
                if (enabled && pickupSensor != null) {
                    asyncSensorManager.observeTriggerSensor(pickupSensor!!)
                } else {
                    emptyFlow()
                }
            }
            .flowOn(bgContext)
}
