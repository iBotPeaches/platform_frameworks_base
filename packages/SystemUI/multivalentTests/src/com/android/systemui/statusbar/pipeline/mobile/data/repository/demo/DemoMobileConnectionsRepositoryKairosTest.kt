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

package com.android.systemui.statusbar.pipeline.mobile.data.repository.demo

import android.telephony.TelephonyManager.DATA_ACTIVITY_INOUT
import android.telephony.TelephonyManager.DATA_ACTIVITY_NONE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.map
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.model.FakeNetworkEventModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.model.FakeNetworkEventModel.MobileDisabled
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demoMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demoModeMobileConnectionDataSourceKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.wifiDataSource
import com.android.systemui.statusbar.pipeline.shared.data.model.toMobileDataActivityModel
import com.android.systemui.statusbar.pipeline.wifi.data.repository.demo.model.FakeWifiEventModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class DemoMobileConnectionsRepositoryKairosTest : SysuiTestCase() {

    private val Kosmos.fakeWifiEventFlow by
        Kosmos.Fixture { MutableStateFlow<FakeWifiEventModel?>(null) }

    private val Kosmos.underTest
        get() = demoMobileConnectionsRepositoryKairos

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            wifiDataSource.stub { on { wifiEvents } doReturn fakeWifiEventFlow }
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun isDefault_defaultsToTrue() = runTest {
        underTest
        val isDefault = kairos.transact { underTest.mobileIsDefault.sample() }
        assertThat(isDefault).isTrue()
    }

    @Test
    fun validated_defaultsToTrue() = runTest {
        underTest
        val isValidated = kairos.transact { underTest.defaultConnectionIsValidated.sample() }
        assertThat(isValidated).isTrue()
    }

    @Test
    fun networkEvent_createNewSubscription() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        assertThat(latest).isEmpty()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(validMobileEvent(subId = 1))

        assertThat(latest).hasSize(1)
        assertThat(latest!!.first().subscriptionId).isEqualTo(1)
    }

    @Test
    fun wifiCarrierMergedEvent_createNewSubscription() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        assertThat(latest).isEmpty()

        fakeWifiEventFlow.value = validCarrierMergedEvent(subId = 5)

        assertThat(latest).hasSize(1)
        assertThat(latest!!.first().subscriptionId).isEqualTo(5)
    }

    @Test
    fun networkEvent_reusesSubscriptionWhenSameId() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        assertThat(latest).isEmpty()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 1, level = 1)
        )

        assertThat(latest).hasSize(1)
        assertThat(latest!!.first().subscriptionId).isEqualTo(1)

        // Second network event comes in with the same subId, does not create a new subscription
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 1, level = 2)
        )

        assertThat(latest).hasSize(1)
        assertThat(latest!!.first().subscriptionId).isEqualTo(1)
    }

    @Test
    fun wifiCarrierMergedEvent_reusesSubscriptionWhenSameId() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        assertThat(latest).isEmpty()

        fakeWifiEventFlow.value = validCarrierMergedEvent(subId = 5, level = 1)

        assertThat(latest).hasSize(1)
        assertThat(latest!!.first().subscriptionId).isEqualTo(5)

        // Second network event comes in with the same subId, does not create a new subscription
        fakeWifiEventFlow.value = validCarrierMergedEvent(subId = 5, level = 2)

        assertThat(latest).hasSize(1)
        assertThat(latest!!.first().subscriptionId).isEqualTo(5)
    }

    @Test
    fun multipleSubscriptions() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(validMobileEvent(subId = 1))
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(validMobileEvent(subId = 2))

        assertThat(latest).hasSize(2)
    }

    @Test
    fun mobileSubscriptionAndCarrierMergedSubscription() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(validMobileEvent(subId = 1))
        fakeWifiEventFlow.value = validCarrierMergedEvent(subId = 5)

        assertThat(latest).hasSize(2)
    }

    @Test
    fun multipleMobileSubscriptionsAndCarrierMergedSubscription() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(validMobileEvent(subId = 1))
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(validMobileEvent(subId = 2))
        fakeWifiEventFlow.value = validCarrierMergedEvent(subId = 3)

        assertThat(latest).hasSize(3)
    }

    @Test
    fun mobileDisabledEvent_disablesConnection_subIdSpecified_singleConn() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 1, level = 1)
        )

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(MobileDisabled(subId = 1))

        assertThat(latest).hasSize(0)
    }

    @Test
    fun mobileDisabledEvent_disablesConnection_subIdNotSpecified_singleConn() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 1, level = 1)
        )

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            MobileDisabled(subId = null)
        )

        assertThat(latest).hasSize(0)
    }

    @Test
    fun mobileDisabledEvent_disablesConnection_subIdSpecified_multipleConn() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 1, level = 1)
        )

        assertThat(latest).hasSize(1)

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 2, level = 1)
        )

        assertThat(latest).hasSize(2)

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(MobileDisabled(subId = 2))

        assertThat(latest).hasSize(1)
    }

    @Test
    fun mobileDisabledEvent_subIdNotSpecified_multipleConn_ignoresCommand() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 1, level = 1)
        )
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 2, level = 1)
        )
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            MobileDisabled(subId = null)
        )

        assertThat(latest).hasSize(2)
    }

    @Test
    fun wifiNetworkUpdatesToDisabled_carrierMergedConnectionRemoved() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        fakeWifiEventFlow.value = validCarrierMergedEvent(subId = 1)

        assertThat(latest).hasSize(1)

        fakeWifiEventFlow.value = FakeWifiEventModel.WifiDisabled

        assertThat(latest).isEmpty()
    }

    @Test
    fun wifiNetworkUpdatesToActive_carrierMergedConnectionRemoved() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        fakeWifiEventFlow.value = validCarrierMergedEvent(subId = 1)

        assertThat(latest).hasSize(1)

        fakeWifiEventFlow.value =
            FakeWifiEventModel.Wifi(level = 1, activity = 0, ssid = null, validated = true)

        assertThat(latest).isEmpty()
    }

    @Test
    fun mobileSubUpdatesToCarrierMerged_onlyOneConnection() = runTest {
        val latestSubsList by underTest.subscriptions.collectLastValue()
        val connections by underTest.mobileConnectionsBySubId.map { it.values }.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(
            validMobileEvent(subId = 3, level = 2)
        )
        assertThat(latestSubsList).hasSize(1)

        val carrierMergedEvent = validCarrierMergedEvent(subId = 3, level = 1)
        fakeWifiEventFlow.value = carrierMergedEvent
        assertThat(latestSubsList).hasSize(1)
        val connection = connections!!.find { it.subId == 3 }!!
        assertCarrierMergedConnection(connection, carrierMergedEvent)
    }

    @Test
    fun mobileSubUpdatesToCarrierMergedThenBack_hasOldMobileData() = runTest {
        val latestSubsList by underTest.subscriptions.collectLastValue()
        val connections by underTest.mobileConnectionsBySubId.map { it.values }.collectLastValue()

        val mobileEvent = validMobileEvent(subId = 3, level = 2)
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(mobileEvent)
        assertThat(latestSubsList).hasSize(1)

        val carrierMergedEvent = validCarrierMergedEvent(subId = 3, level = 1)
        fakeWifiEventFlow.value = carrierMergedEvent
        assertThat(latestSubsList).hasSize(1)
        var connection = connections!!.find { it.subId == 3 }!!
        assertCarrierMergedConnection(connection, carrierMergedEvent)

        // WHEN the carrier merged is removed
        fakeWifiEventFlow.value =
            FakeWifiEventModel.Wifi(level = 4, activity = 0, ssid = null, validated = true)

        assertThat(latestSubsList).hasSize(1)
        assertThat(connections).hasSize(1)

        // THEN the subId=3 connection goes back to the mobile information
        connection = connections!!.find { it.subId == 3 }!!
        assertConnection(connection, mobileEvent)
    }

    @Test
    fun demoConnection_singleSubscription() = runTest {
        var currentEvent: FakeNetworkEventModel = validMobileEvent(subId = 1)
        val connections by underTest.mobileConnectionsBySubId.map { it.values }.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(currentEvent)

        assertThat(connections).hasSize(1)
        val connection1 = connections!!.first()

        assertConnection(connection1, currentEvent)

        // Exercise the whole api

        currentEvent = validMobileEvent(subId = 1, level = 2)
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(currentEvent)
        assertConnection(connection1, currentEvent)
    }

    @Test
    fun demoConnection_twoConnections_updateSecond_noAffectOnFirst() = runTest {
        var currentEvent1 = validMobileEvent(subId = 1)
        var connection1: DemoMobileConnectionRepositoryKairos? = null
        var currentEvent2 = validMobileEvent(subId = 2)
        var connection2: DemoMobileConnectionRepositoryKairos? = null
        val connections by underTest.mobileConnectionsBySubId.map { it.values }.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(currentEvent1)
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(currentEvent2)
        assertThat(connections).hasSize(2)
        connections!!.forEach {
            when (it.subId) {
                1 -> connection1 = it
                2 -> connection2 = it
                else -> Assert.fail("Unexpected subscription")
            }
        }

        assertConnection(connection1!!, currentEvent1)
        assertConnection(connection2!!, currentEvent2)

        // WHEN the event changes for connection 2, it updates, and connection 1 stays the same
        currentEvent2 = validMobileEvent(subId = 2, activity = DATA_ACTIVITY_INOUT)
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(currentEvent2)
        assertConnection(connection1!!, currentEvent1)
        assertConnection(connection2!!, currentEvent2)

        // and vice versa
        currentEvent1 = validMobileEvent(subId = 1, inflateStrength = true)
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(currentEvent1)
        assertConnection(connection1!!, currentEvent1)
        assertConnection(connection2!!, currentEvent2)
    }

    @Test
    fun demoConnection_twoConnections_updateCarrierMerged_noAffectOnFirst() = runTest {
        var currentEvent1 = validMobileEvent(subId = 1)
        var connection1: DemoMobileConnectionRepositoryKairos? = null
        var currentEvent2 = validCarrierMergedEvent(subId = 2)
        var connection2: DemoMobileConnectionRepositoryKairos? = null
        val connections by underTest.mobileConnectionsBySubId.map { it.values }.collectLastValue()

        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(currentEvent1)
        fakeWifiEventFlow.value = currentEvent2
        assertThat(connections).hasSize(2)
        connections!!.forEach {
            when (it.subId) {
                1 -> connection1 = it
                2 -> connection2 = it
                else -> Assert.fail("Unexpected subscription")
            }
        }

        assertConnection(connection1!!, currentEvent1)
        assertCarrierMergedConnection(connection2!!, currentEvent2)

        // WHEN the event changes for connection 2, it updates, and connection 1 stays the same
        currentEvent2 = validCarrierMergedEvent(subId = 2, level = 4)
        fakeWifiEventFlow.value = currentEvent2
        assertConnection(connection1!!, currentEvent1)
        assertCarrierMergedConnection(connection2!!, currentEvent2)

        // and vice versa
        currentEvent1 = validMobileEvent(subId = 1, inflateStrength = true)
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(currentEvent1)
        assertConnection(connection1!!, currentEvent1)
        assertCarrierMergedConnection(connection2!!, currentEvent2)
    }

    @Test
    fun demoIsNotInEcmState() = runTest {
        underTest
        assertThat(kairos.transact { underTest.isInEcmMode.sample() }).isFalse()
    }

    private suspend fun KairosTestScope.assertConnection(
        conn: DemoMobileConnectionRepositoryKairos,
        model: FakeNetworkEventModel,
    ) {
        when (model) {
            is FakeNetworkEventModel.Mobile -> {
                kairos.transact {
                    assertThat(conn.subId).isEqualTo(model.subId)
                    assertThat(conn.cdmaLevel.sample()).isEqualTo(model.level)
                    assertThat(conn.primaryLevel.sample()).isEqualTo(model.level)
                    assertThat(conn.dataActivityDirection.sample())
                        .isEqualTo(
                            (model.activity ?: DATA_ACTIVITY_NONE).toMobileDataActivityModel()
                        )
                    assertThat(conn.carrierNetworkChangeActive.sample())
                        .isEqualTo(model.carrierNetworkChange)
                    assertThat(conn.isRoaming.sample()).isEqualTo(model.roaming)
                    assertThat(conn.networkName.sample())
                        .isEqualTo(NetworkNameModel.IntentDerived(model.name))
                    assertThat(conn.carrierName.sample())
                        .isEqualTo(
                            NetworkNameModel.SubscriptionDerived("${model.name} ${model.subId}")
                        )
                    assertThat(conn.hasPrioritizedNetworkCapabilities.sample())
                        .isEqualTo(model.slice)
                    assertThat(conn.isNonTerrestrial.sample()).isEqualTo(model.ntn)

                    // TODO(b/261029387) check these once we start handling them
                    assertThat(conn.isEmergencyOnly.sample()).isFalse()
                    assertThat(conn.isGsm.sample()).isFalse()
                    assertThat(conn.dataConnectionState.sample())
                        .isEqualTo(DataConnectionState.Connected)
                }
            }
            else -> {}
        }
    }

    private suspend fun KairosTestScope.assertCarrierMergedConnection(
        conn: DemoMobileConnectionRepositoryKairos,
        model: FakeWifiEventModel.CarrierMerged,
    ) {
        kairos.transact {
            assertThat(conn.subId).isEqualTo(model.subscriptionId)
            assertThat(conn.cdmaLevel.sample()).isEqualTo(model.level)
            assertThat(conn.primaryLevel.sample()).isEqualTo(model.level)
            assertThat(conn.carrierNetworkChangeActive.sample()).isEqualTo(false)
            assertThat(conn.isRoaming.sample()).isEqualTo(false)
            assertThat(conn.isEmergencyOnly.sample()).isFalse()
            assertThat(conn.isGsm.sample()).isFalse()
            assertThat(conn.dataConnectionState.sample()).isEqualTo(DataConnectionState.Connected)
            assertThat(conn.hasPrioritizedNetworkCapabilities.sample()).isFalse()
        }
    }
}
