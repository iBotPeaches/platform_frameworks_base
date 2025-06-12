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

package com.android.systemui.statusbar.pipeline.battery.data.repository

import android.content.testableContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.shared.settings.data.repository.systemSettingsRepository
import com.android.systemui.statusbar.policy.batteryController

/** Use [Kosmos.batteryController.fake] to make the repo have the state you want */
val Kosmos.batteryRepository by
    Kosmos.Fixture {
        BatteryRepository(
            testableContext,
            testScope.backgroundScope,
            testDispatcher,
            batteryController,
            systemSettingsRepository,
        )
    }
