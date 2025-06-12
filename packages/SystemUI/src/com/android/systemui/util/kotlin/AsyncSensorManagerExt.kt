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

package com.android.systemui.util.kotlin

import android.hardware.Sensor
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import com.android.systemui.util.sensors.AsyncSensorManager
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow

/**
 * Helper for continuously observing a trigger sensor, which automatically unregisters itself after
 * it executes once. We therefore re-register ourselves after each emission.
 */
fun AsyncSensorManager.observeTriggerSensor(sensor: Sensor): Flow<Unit> = conflatedCallbackFlow {
    val isRegistered = AtomicBoolean(false)
    fun registerCallbackInternal(callback: TriggerEventListener) {
        if (isRegistered.compareAndSet(false, true)) {
            requestTriggerSensor(callback, sensor)
        }
    }

    val callback =
        object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent) {
                trySend(Unit)
                if (isRegistered.getAndSet(false)) {
                    registerCallbackInternal(this)
                }
            }
        }

    registerCallbackInternal(callback)

    awaitClose {
        if (isRegistered.getAndSet(false)) {
            cancelTriggerSensor(callback, sensor)
        }
    }
}
