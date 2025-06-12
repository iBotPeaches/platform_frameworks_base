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

package com.android.systemui.statusbar.policy

import android.os.Bundle
import com.android.systemui.animation.Expandable
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.statusbar.policy.BatteryController.EstimateFetchCompletion
import java.io.PrintWriter
import java.lang.ref.WeakReference

class FakeBatteryControllerImpl : BatteryController {
    var listeners = mutableSetOf<BatteryStateChangeCallback>()
        private set

    var _level = 50
        set(value) {
            if (field != value) {
                field = value
                listeners.forEach { it.onBatteryLevelChanged(field, _isPluggedIn, _isPluggedIn) }
            }
        }

    var _isPowerSave = false
        set(value) {
            if (field != value) {
                field = value
                listeners.forEach { it.onPowerSaveChanged(field) }
            }
        }

    var _isPluggedIn = false
        set(value) {
            if (field != value) {
                field = value
                listeners.forEach { it.onBatteryLevelChanged(_level, field, field) }
            }
        }

    var _isStateUnknown = false
        set(value) {
            if (field != value) {
                field = value
                listeners.forEach { it.onBatteryUnknownStateChanged(field) }
            }
        }

    var _isDefender = false
        set(value) {
            if (field != value) {
                field = value
                listeners.forEach { it.onIsBatteryDefenderChanged(field) }
            }
        }

    var _isWirelessCharging = false
        set(value) {
            if (field != value) {
                field = value
                listeners.forEach { it.onWirelessChargingChanged(field) }
            }
        }

    var _isAodPowerSave = false

    var _isReverseSupported = false
    var _isReverseOn = false
    var _isExtremeBatterySaverOn = false
    var _isChargingSourceDock = false

    var _estimatedTimeRemainingString: String? = null

    override fun dump(pw: PrintWriter?, args: Array<out String>?) {
        // nop
    }

    override fun dispatchDemoCommand(command: String?, args: Bundle?) {
        // nop
    }

    override fun addCallback(listener: BatteryStateChangeCallback) {
        listeners += listener

        listener.onBatteryLevelChanged(_level, _isPluggedIn, _isPluggedIn)
        listener.onPowerSaveChanged(_isPowerSave)
        listener.onBatteryUnknownStateChanged(_isStateUnknown)
        listener.onWirelessChargingChanged(_isWirelessCharging)
        listener.onIsBatteryDefenderChanged(_isDefender)
    }

    override fun removeCallback(listener: BatteryStateChangeCallback) {
        listeners -= listener
    }

    override fun setPowerSaveMode(powerSave: Boolean) {
        setPowerSaveMode(powerSave, null)
    }

    override fun setPowerSaveMode(powerSave: Boolean, expandable: Expandable?) {
        _isPowerSave = powerSave
    }

    override fun getLastPowerSaverStartExpandable(): WeakReference<Expandable>? {
        return null
    }

    override fun clearLastPowerSaverStartExpandable() {
        // nop
    }

    override fun isPluggedIn() = _isPluggedIn

    override fun isPluggedInWireless(): Boolean {
        return false
    }

    override fun isPowerSave() = _isPowerSave

    override fun isAodPowerSave() = _isAodPowerSave

    override fun init() {
        // nop
    }

    override fun isWirelessCharging(): Boolean {
        return false
    }

    override fun isReverseSupported() = _isReverseSupported

    override fun isReverseOn() = _isReverseOn

    override fun setReverseState(isReverse: Boolean) {
        _isReverseOn = isReverse
    }

    override fun isExtremeSaverOn() = _isExtremeBatterySaverOn

    override fun isChargingSourceDock() = _isChargingSourceDock

    // Just pretend that it's cached and returns instantly
    override fun getEstimatedTimeRemainingString(completion: EstimateFetchCompletion?) {
        completion?.onBatteryRemainingEstimateRetrieved(_estimatedTimeRemainingString)
    }
}
