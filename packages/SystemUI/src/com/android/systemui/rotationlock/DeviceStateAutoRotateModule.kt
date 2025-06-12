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

package com.android.systemui.rotationlock

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.policy.DeviceStateRotationLockSettingController
import com.android.window.flags.Flags
import dagger.Module
import dagger.Provides
import java.util.Optional
import javax.inject.Provider
import javax.inject.Qualifier

@Module
class DeviceStateAutoRotateModule {
    /** Qualifier for dependencies to be bound with [DeviceStateAutoRotateModule]. */
    @Qualifier
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    annotation class BoundsDeviceStateAutoRotateModule

    /**
     * Provides an instance of [DeviceStateRotationLockSettingController].
     *
     * @param controllerProvider The provider for [DeviceStateRotationLockSettingController].
     * @return An [Optional] containing the [DeviceStateRotationLockSettingController] instance if
     *   the `Flags.enableDeviceStateAutoRotateSettingRefactor()` flag is disabled, or an empty
     *   [Optional] otherwise.
     */
    @Provides
    @BoundsDeviceStateAutoRotateModule
    @SysUISingleton
    fun provideDeviceStateRotationLockSettingController(
        controllerProvider: Provider<DeviceStateRotationLockSettingController>
    ): Optional<DeviceStateRotationLockSettingController> =
        if (Flags.enableDeviceStateAutoRotateSettingRefactor()) {
            Optional.empty()
        } else {
            Optional.of(controllerProvider.get())
        }
}
