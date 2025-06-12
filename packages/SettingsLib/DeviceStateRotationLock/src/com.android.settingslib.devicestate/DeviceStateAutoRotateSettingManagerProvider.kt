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

package com.android.settingslib.devicestate

import android.content.Context
import android.os.Handler
import com.android.window.flags.Flags
import java.util.concurrent.Executor

/**
 * Provides appropriate instance of [DeviceStateAutoRotateSettingManager], based on the value of
 * [Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR].
 */
object DeviceStateAutoRotateSettingManagerProvider {
    /**
     * Provides an instance of [DeviceStateAutoRotateSettingManager], based on the value of
     * [Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR]. It is supposed to be used
     * by apps that supports dagger.
     */
    @JvmStatic
    fun createInstance(
        context: Context,
        backgroundExecutor: Executor,
        secureSettings: SecureSettings,
        mainHandler: Handler,
        posturesHelper: PosturesHelper,
    ): DeviceStateAutoRotateSettingManager =
        if (Flags.enableDeviceStateAutoRotateSettingRefactor()) {
            DeviceStateAutoRotateSettingManagerImpl(
                context,
                backgroundExecutor,
                secureSettings,
                mainHandler,
                posturesHelper,
            )
        } else {
            DeviceStateRotationLockSettingsManager(context, secureSettings)
        }
}
