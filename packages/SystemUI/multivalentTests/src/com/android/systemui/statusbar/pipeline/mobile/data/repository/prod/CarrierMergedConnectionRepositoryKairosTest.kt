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

package com.android.systemui.statusbar.pipeline.mobile.data.repository.prod

import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kairos.ActivatedKairosFixture
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kairos.stateOf
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import com.android.systemui.statusbar.pipeline.wifi.data.repository.fakeWifiRepository
import com.android.systemui.statusbar.pipeline.wifi.data.repository.wifiRepository
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class CarrierMergedConnectionRepositoryKairosTest : SysuiTestCase() {

    private val Kosmos.underTest by ActivatedKairosFixture {
        CarrierMergedConnectionRepositoryKairos(
            subId = SUB_ID,
            tableLogBuffer = logcatTableLogBuffer(this),
            telephonyManager = telephonyManager,
            wifiRepository = wifiRepository,
            isInEcmMode = stateOf(false),
        )
    }

    private val Kosmos.telephonyManager: TelephonyManager by Fixture {
        mock {
            on { subscriptionId } doReturn SUB_ID
            on { simOperatorName } doReturn ""
        }
    }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        testKosmos().run {
            useUnconfinedTestDispatcher()
            runKairosTest { block() }
        }

    @Test
    fun inactiveWifi_isDefault() = runTest {
        val latestConnState by underTest.dataConnectionState.collectLastValue()
        val latestNetType by underTest.resolvedNetworkType.collectLastValue()

        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.Inactive())

        assertThat(latestConnState).isEqualTo(DataConnectionState.Disconnected)
        assertThat(latestNetType).isNotEqualTo(ResolvedNetworkType.CarrierMergedNetworkType)
    }

    @Test
    fun activeWifi_isDefault() = runTest {
        val latestConnState by underTest.dataConnectionState.collectLastValue()
        val latestNetType by underTest.resolvedNetworkType.collectLastValue()

        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.Active.of(level = 1))

        assertThat(latestConnState).isEqualTo(DataConnectionState.Disconnected)
        assertThat(latestNetType).isNotEqualTo(ResolvedNetworkType.CarrierMergedNetworkType)
    }

    @Test
    fun carrierMergedWifi_isValidAndFieldsComeFromWifiNetwork() = runTest {
        val latest by underTest.primaryLevel.collectLastValue()

        fakeWifiRepository.setIsWifiEnabled(true)
        fakeWifiRepository.setIsWifiDefault(true)

        fakeWifiRepository.setWifiNetwork(
            WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
        )

        assertThat(latest).isEqualTo(3)
    }

    @Test
    fun activity_comesFromWifiActivity() = runTest {
        val latest by underTest.dataActivityDirection.collectLastValue()

        fakeWifiRepository.setIsWifiEnabled(true)
        fakeWifiRepository.setIsWifiDefault(true)
        fakeWifiRepository.setWifiNetwork(
            WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
        )
        fakeWifiRepository.setWifiActivity(
            DataActivityModel(hasActivityIn = true, hasActivityOut = false)
        )

        assertThat(latest!!.hasActivityIn).isTrue()
        assertThat(latest!!.hasActivityOut).isFalse()

        fakeWifiRepository.setWifiActivity(
            DataActivityModel(hasActivityIn = false, hasActivityOut = true)
        )

        assertThat(latest!!.hasActivityIn).isFalse()
        assertThat(latest!!.hasActivityOut).isTrue()
    }

    @Test
    fun carrierMergedWifi_wrongSubId_isDefault() = runTest {
        val latestLevel by underTest.primaryLevel.collectLastValue()
        val latestType by underTest.resolvedNetworkType.collectLastValue()

        fakeWifiRepository.setWifiNetwork(
            WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID + 10, level = 3)
        )

        assertThat(latestLevel).isNotEqualTo(3)
        assertThat(latestType).isNotEqualTo(ResolvedNetworkType.CarrierMergedNetworkType)
    }

    // This scenario likely isn't possible, but write a test for it anyway
    @Test
    fun carrierMergedButNotEnabled_isDefault() = runTest {
        val latest by underTest.primaryLevel.collectLastValue()

        fakeWifiRepository.setWifiNetwork(
            WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
        )
        fakeWifiRepository.setIsWifiEnabled(false)

        assertThat(latest).isNotEqualTo(3)
    }

    // This scenario likely isn't possible, but write a test for it anyway
    @Test
    fun carrierMergedButWifiNotDefault_isDefault() = runTest {
        val latest by underTest.primaryLevel.collectLastValue()

        fakeWifiRepository.setWifiNetwork(
            WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
        )
        fakeWifiRepository.setIsWifiDefault(false)

        assertThat(latest).isNotEqualTo(3)
    }

    @Test
    fun numberOfLevels_comesFromCarrierMerged() = runTest {
        val latest by underTest.numberOfLevels.collectLastValue()

        fakeWifiRepository.setWifiNetwork(
            WifiNetworkModel.CarrierMerged.of(
                subscriptionId = SUB_ID,
                level = 1,
                numberOfLevels = 6,
            )
        )

        assertThat(latest).isEqualTo(6)
    }

    @Test
    fun dataEnabled_matchesWifiEnabled() = runTest {
        val latest by underTest.dataEnabled.collectLastValue()

        fakeWifiRepository.setIsWifiEnabled(true)
        assertThat(latest).isTrue()

        fakeWifiRepository.setIsWifiEnabled(false)
        assertThat(latest).isFalse()
    }

    @Test
    fun cdmaRoaming_alwaysFalse() = runTest {
        val latest by underTest.cdmaRoaming.collectLastValue()
        assertThat(latest).isFalse()
    }

    @Test
    fun networkName_usesSimOperatorNameAsInitial() = runTest {
        telephonyManager.stub { on { simOperatorName } doReturn "Test SIM name" }

        val latest by underTest.networkName.collectLastValue()

        assertThat(latest).isEqualTo(NetworkNameModel.SimDerived("Test SIM name"))
    }

    @Test
    fun networkName_updatesOnNetworkUpdate() = runTest {
        fakeWifiRepository.setIsWifiEnabled(true)
        fakeWifiRepository.setIsWifiDefault(true)

        telephonyManager.stub { on { simOperatorName } doReturn "Test SIM name" }

        val latest by underTest.networkName.collectLastValue()

        assertThat(latest).isEqualTo(NetworkNameModel.SimDerived("Test SIM name"))

        telephonyManager.stub { on { simOperatorName } doReturn "New SIM name" }
        fakeWifiRepository.setWifiNetwork(
            WifiNetworkModel.CarrierMerged.of(subscriptionId = SUB_ID, level = 3)
        )

        assertThat(latest).isEqualTo(NetworkNameModel.SimDerived("New SIM name"))
    }

    @Test
    fun isAllowedDuringAirplaneMode_alwaysTrue() = runTest {
        val latest by underTest.isAllowedDuringAirplaneMode.collectLastValue()

        assertThat(latest).isTrue()
    }

    private companion object {
        const val SUB_ID = 123
    }
}
