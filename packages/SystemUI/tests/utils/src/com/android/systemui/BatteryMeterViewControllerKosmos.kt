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

package com.android.systemui.battery

import android.os.Handler
import android.test.mock.MockContentResolver
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.settings.userTracker
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.tuner.tunerService
import org.mockito.kotlin.mock

val Kosmos.batteryMeterViewControllerFactory: BatteryMeterViewController.Factory by
Kosmos.Fixture {
    BatteryMeterViewController.Factory(
        userTracker,
        configurationController,
        tunerService,
        mock<Handler>(),
        MockContentResolver(),
        featureFlagsClassic.fake,
        batteryController
    )
}