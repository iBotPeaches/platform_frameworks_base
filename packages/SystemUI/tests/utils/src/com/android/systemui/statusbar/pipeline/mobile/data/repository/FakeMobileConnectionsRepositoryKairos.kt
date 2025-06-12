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

import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.android.settingslib.SignalIcon
import com.android.settingslib.mobile.MobileMappings
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.KairosBuilder
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.MutableEvents
import com.android.systemui.kairos.MutableState
import com.android.systemui.kairos.State
import com.android.systemui.kairos.asIncremental
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.combine
import com.android.systemui.kairos.map
import com.android.systemui.kairos.mapValues
import com.android.systemui.kairosBuilder
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.util.FakeMobileMappingsProxy
import com.android.systemui.statusbar.pipeline.mobile.util.MobileMappingsProxy

// TODO(b/261632894): remove this in favor of the real impl or DemoMobileConnectionsRepositoryKairos
@ExperimentalKairosApi
class FakeMobileConnectionsRepositoryKairos(
    kairos: KairosNetwork,
    val tableLogBuffer: TableLogBuffer,
    mobileMappings: MobileMappingsProxy = FakeMobileMappingsProxy(),
) : MobileConnectionsRepositoryKairos, KairosBuilder by kairosBuilder() {

    val GSM_KEY = mobileMappings.toIconKey(GSM)
    val LTE_KEY = mobileMappings.toIconKey(LTE)
    val UMTS_KEY = mobileMappings.toIconKey(UMTS)
    val LTE_ADVANCED_KEY = mobileMappings.toIconKeyOverride(LTE_ADVANCED_PRO)

    /**
     * To avoid a reliance on [MobileMappings], we'll build a simpler map from network type to
     * mobile icon. See TelephonyManager.NETWORK_TYPES for a list of types and [TelephonyIcons] for
     * the exhaustive set of icons
     */
    val TEST_MAPPING: Map<String, SignalIcon.MobileIconGroup> =
        mapOf(
            GSM_KEY to TelephonyIcons.THREE_G,
            LTE_KEY to TelephonyIcons.LTE,
            UMTS_KEY to TelephonyIcons.FOUR_G,
            LTE_ADVANCED_KEY to TelephonyIcons.NR_5G,
        )

    override val subscriptions = MutableState(kairos, emptyList<SubscriptionModel>())

    override val mobileConnectionsBySubId = buildIncremental {
        subscriptions
            .map { it.associate { sub -> sub.subscriptionId to Unit } }
            .asIncremental()
            .mapValues { (subId, _) ->
                buildSpec {
                    FakeMobileConnectionRepositoryKairos(subId, kairosNetwork, tableLogBuffer)
                }
            }
            .applyLatestSpecForKey()
    }

    private val _activeMobileDataSubscriptionId = MutableState<Int?>(kairos, null)
    override val activeMobileDataSubscriptionId: State<Int?> = _activeMobileDataSubscriptionId

    override val activeMobileDataRepository: State<MobileConnectionRepositoryKairos?> =
        combine(mobileConnectionsBySubId, activeMobileDataSubscriptionId) { conns, activeSub ->
            conns[activeSub]
        }

    override val activeSubChangedInGroupEvent = MutableEvents<Unit>(kairos)

    override val defaultDataSubId = MutableState(kairos, INVALID_SUBSCRIPTION_ID)

    override val mobileIsDefault = MutableState(kairos, false)

    override val hasCarrierMergedConnection = MutableState(kairos, false)

    override val defaultConnectionIsValidated = MutableState(kairos, false)

    override val defaultDataSubRatConfig = MutableState(kairos, MobileMappings.Config())

    override val defaultMobileIconMapping = MutableState(kairos, TEST_MAPPING)

    override val defaultMobileIconGroup = MutableState(kairos, DEFAULT_ICON)

    override val isDeviceEmergencyCallCapable = MutableState(kairos, false)

    override val isAnySimSecure = MutableState(kairos, false)

    override val isInEcmMode: State<Boolean> = MutableState(kairos, false)

    fun setActiveMobileDataSubscriptionId(subId: Int) {
        // Simulate the filtering that the repo does
        if (subId == INVALID_SUBSCRIPTION_ID) {
            _activeMobileDataSubscriptionId.setValue(null)
        } else {
            _activeMobileDataSubscriptionId.setValue(subId)
        }
    }

    companion object {
        val DEFAULT_ICON = TelephonyIcons.G

        // Use [MobileMappings] to define some simple definitions
        const val GSM = TelephonyManager.NETWORK_TYPE_GSM
        const val LTE = TelephonyManager.NETWORK_TYPE_LTE
        const val UMTS = TelephonyManager.NETWORK_TYPE_UMTS
        const val LTE_ADVANCED_PRO = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
    }
}

@ExperimentalKairosApi
val MobileConnectionsRepositoryKairos.fake
    get() = this as FakeMobileConnectionsRepositoryKairos
