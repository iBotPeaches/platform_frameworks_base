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
package com.android.systemui.lowlightclock

import android.content.res.Resources
import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.internal.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.shared.condition.Condition
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Condition for monitoring if the screensaver setting is enabled. */
class ScreenSaverEnabledCondition
@Inject
constructor(
    @Background scope: CoroutineScope,
    @Main resources: Resources,
    private val secureSettings: SecureSettings,
) : Condition(scope) {
    private val screenSaverEnabledByDefaultConfig =
        resources.getBoolean(R.bool.config_dreamsEnabledByDefault)

    private val screenSaverSettingObserver: ContentObserver =
        object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                updateScreenSaverEnabledSetting()
            }
        }

    public override suspend fun start() {
        secureSettings.registerContentObserverForUserSync(
            Settings.Secure.SCREENSAVER_ENABLED,
            screenSaverSettingObserver,
            UserHandle.USER_CURRENT,
        )
        updateScreenSaverEnabledSetting()
    }

    override fun stop() {
        secureSettings.unregisterContentObserverSync(screenSaverSettingObserver)
    }

    override val startStrategy: Int
        get() = START_EAGERLY

    private fun updateScreenSaverEnabledSetting() {
        val enabled =
            secureSettings.getIntForUser(
                Settings.Secure.SCREENSAVER_ENABLED,
                if (screenSaverEnabledByDefaultConfig) 1 else 0,
                UserHandle.USER_CURRENT,
            ) != 0
        if (!enabled) {
            Log.i(TAG, "Disabling low-light clock because screen saver has been disabled")
        }
        updateCondition(enabled)
    }

    companion object {
        private val TAG: String = ScreenSaverEnabledCondition::class.java.simpleName
    }
}
