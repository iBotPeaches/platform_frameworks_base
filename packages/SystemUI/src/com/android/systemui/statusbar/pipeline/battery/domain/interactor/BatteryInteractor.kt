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

package com.android.systemui.statusbar.pipeline.battery.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.pipeline.battery.data.repository.BatteryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@SysUISingleton
class BatteryInteractor @Inject constructor(repo: BatteryRepository) {
    /** The current level in the range of [0-100] */
    val level = repo.level.filterNotNull()

    /** Whether the battery has been fully charged */
    val isFull = level.map { isBatteryFull(it) }

    /**
     * For the sake of battery views, consider it to be "charging" if plugged in. This allows users
     * to easily confirm that the device is properly plugged in, even if its' technically not
     * charging due to issues with the source.
     */
    val isCharging = repo.isPluggedIn

    /**
     * The critical level (see [CRITICAL_LEVEL]) defines the level below which we might want to
     * display an error UI. E.g., show the battery as red.
     */
    val isCritical = level.map { it <= CRITICAL_LEVEL }

    /** @see [BatteryRepository.isStateUnknown] for docs. The battery cannot be detected */
    val isStateUnknown = repo.isStateUnknown

    /** @see [BatteryRepository.isBatteryDefenderEnabled] */
    val isBatteryDefenderEnabled = repo.isBatteryDefenderEnabled

    /** @see [BatteryRepository.isPowerSaveEnabled] */
    val powerSave = repo.isPowerSaveEnabled

    /** @see [BatteryRepository.isShowBatteryPercentSettingEnabled] */
    val isBatteryPercentSettingEnabled = repo.isShowBatteryPercentSettingEnabled

    /**
     * The battery attribution (@see [BatteryAttributionModel]) describes the attribution that best
     * represents the current battery charging state. If charging, the attribution is
     * [BatteryAttributionModel.Charging], etc.
     *
     * This flow can be used to canonically describe the battery state charging state.
     */
    val batteryAttributionType =
        combine(isCharging, powerSave, isBatteryDefenderEnabled) { charging, powerSave, defend ->
            if (powerSave) {
                BatteryAttributionModel.PowerSave
            } else if (defend) {
                BatteryAttributionModel.Defend
            } else if (charging) {
                BatteryAttributionModel.Charging
            } else {
                null
            }
        }

    /** @see [BatteryRepository.batteryTimeRemainingEstimate] */
    val batteryTimeRemainingEstimate = repo.batteryTimeRemainingEstimate

    companion object {
        /** Level below which we consider to be critically low */
        private const val CRITICAL_LEVEL = 20

        fun isBatteryFull(level: Int) = level >= 100
    }
}

/** The charging state, and therefore attribution for the battery */
enum class BatteryAttributionModel {
    Defend,
    PowerSave,
    Charging,
}
