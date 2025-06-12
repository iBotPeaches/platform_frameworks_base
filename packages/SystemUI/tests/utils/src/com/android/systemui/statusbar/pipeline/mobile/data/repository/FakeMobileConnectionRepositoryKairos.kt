/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.mobile.data.repository

import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.MutableState
import com.android.systemui.kairos.State
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepository.Companion.DEFAULT_NETWORK_NAME
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepository.Companion.DEFAULT_NUM_LEVELS
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel

@ExperimentalKairosApi
class FakeMobileConnectionRepositoryKairos(
    override val subId: Int,
    kairos: KairosNetwork,
    override val tableLogBuffer: TableLogBuffer,
) : MobileConnectionRepositoryKairos {
    override val carrierId = MutableState(kairos, 0)
    override val inflateSignalStrength = MutableState(kairos, false)
    override val allowNetworkSliceIndicator = MutableState(kairos, true)
    override val isEmergencyOnly = MutableState(kairos, false)
    override val isRoaming = MutableState(kairos, false)
    override val operatorAlphaShort = MutableState<String?>(kairos, null)
    override val isInService = MutableState(kairos, false)
    override val isNonTerrestrial = MutableState(kairos, false)
    override val isGsm = MutableState(kairos, false)
    override val cdmaLevel = MutableState(kairos, 0)
    override val primaryLevel = MutableState(kairos, 0)
    override val satelliteLevel = MutableState(kairos, 0)
    override val dataConnectionState = MutableState(kairos, DataConnectionState.Disconnected)
    override val dataActivityDirection =
        MutableState(kairos, DataActivityModel(hasActivityIn = false, hasActivityOut = false))
    override val carrierNetworkChangeActive = MutableState(kairos, false)
    override val resolvedNetworkType =
        MutableState<ResolvedNetworkType>(kairos, ResolvedNetworkType.UnknownNetworkType)
    override val numberOfLevels = MutableState(kairos, DEFAULT_NUM_LEVELS)
    override val dataEnabled = MutableState(kairos, true)
    override val cdmaRoaming = MutableState(kairos, false)
    override val networkName =
        MutableState<NetworkNameModel>(kairos, NetworkNameModel.Default(DEFAULT_NETWORK_NAME))
    override val carrierName =
        MutableState<NetworkNameModel>(kairos, NetworkNameModel.Default(DEFAULT_NETWORK_NAME))
    override val isAllowedDuringAirplaneMode = MutableState(kairos, false)
    override val hasPrioritizedNetworkCapabilities = MutableState(kairos, false)
    override val isInEcmMode: State<Boolean> = MutableState(kairos, false)

    /**
     * Set [primaryLevel] and [cdmaLevel]. Convenient when you don't care about the connection type
     */
    fun setAllLevels(level: Int) {
        cdmaLevel.setValue(level)
        primaryLevel.setValue(level)
    }

    /** Set the correct [resolvedNetworkType] for the given group via its lookup key */
    fun setNetworkTypeKey(key: String) {
        resolvedNetworkType.setValue(ResolvedNetworkType.DefaultNetworkType(key))
    }

    /**
     * Set both [isRoaming] and [cdmaRoaming] properties, in the event that you don't care about the
     * connection type
     */
    fun setAllRoaming(roaming: Boolean) {
        isRoaming.setValue(roaming)
        cdmaRoaming.setValue(roaming)
    }
}
