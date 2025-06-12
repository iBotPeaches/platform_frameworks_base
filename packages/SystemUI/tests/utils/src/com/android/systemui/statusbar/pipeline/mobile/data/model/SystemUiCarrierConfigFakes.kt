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

package com.android.systemui.statusbar.pipeline.mobile.data.model

import android.os.PersistableBundle
import android.telephony.CarrierConfigManager

/**
 * In order to keep us from having to update every place that might want to create a config, make
 * sure to add new keys here
 */
fun testCarrierConfig() =
    PersistableBundle().also {
        it.putBoolean(CarrierConfigManager.KEY_INFLATE_SIGNAL_STRENGTH_BOOL, false)
        it.putBoolean(CarrierConfigManager.KEY_SHOW_OPERATOR_NAME_IN_STATUSBAR_BOOL, false)
        it.putBoolean(CarrierConfigManager.KEY_SHOW_5G_SLICE_ICON_BOOL, true)
    }

/** Override the default config with the given (key, value) pair */
fun testCarrierConfigWithOverride(key: String, override: Boolean): PersistableBundle =
    testCarrierConfig().also { it.putBoolean(key, override) }

/** Override any number of configs from the default */
fun testCarrierConfigWithOverrides(vararg overrides: Pair<String, Boolean>) =
    testCarrierConfig().also { config ->
        overrides.forEach { (key, value) -> config.putBoolean(key, value) }
    }
