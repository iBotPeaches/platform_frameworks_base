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

import android.os.PersistableBundle
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.activated
import com.android.systemui.flags.Flags.ROAMING_INDICATOR_VIA_DISPLAY_INFO
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.ActivatedKairosFixture
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.MutableState
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kairos.stateOf
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.SystemUiCarrierConfig
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.FullMobileConnectionRepository.Companion.COL_EMERGENCY
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.FullMobileConnectionRepository.Companion.COL_OPERATOR
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.FullMobileConnectionRepositoryKairos.Companion.COL_PRIMARY_LEVEL
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileTelephonyHelpers.getTelephonyCallbackForType
import com.android.systemui.statusbar.pipeline.wifi.data.repository.fakeWifiRepository
import com.android.systemui.statusbar.pipeline.wifi.data.repository.wifiRepository
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * This repo acts as a dispatcher to either the `typical` or `carrier merged` versions of the
 * repository interface it's switching on. These tests just need to verify that the entire interface
 * properly switches over when the value of `isCarrierMerged` changes.
 */
@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class FullMobileConnectionRepositoryKairosTest : SysuiTestCase() {
    private val Kosmos.fakeMobileRepo by Fixture {
        FakeMobileConnectionRepositoryKairos(SUB_ID, kairos, mobileLogger)
    }

    private val Kosmos.fakeCarrierMergedRepo by Fixture {
        FakeMobileConnectionRepositoryKairos(SUB_ID, kairos, mobileLogger).apply {
            // Mimicks the real carrier merged repository
            isAllowedDuringAirplaneMode.setValue(true)
        }
    }

    private var Kosmos.mobileRepo: MobileConnectionRepositoryKairos by Fixture { fakeMobileRepo }
    private var Kosmos.carrierMergedRepoSpec:
        BuildSpec<MobileConnectionRepositoryKairos> by Fixture {
        buildSpec { fakeCarrierMergedRepo }
    }

    private val Kosmos.mobileLogger by Fixture { logcatTableLogBuffer(this, "TestName") }

    private val Kosmos.underTest by ActivatedKairosFixture {
        FullMobileConnectionRepositoryKairos(
            SUB_ID,
            mobileLogger,
            mobileRepo,
            carrierMergedRepoSpec,
            isCarrierMerged,
        )
    }

    private val Kosmos.subscriptionModel by Fixture {
        MutableState(
            kairos,
            SubscriptionModel(
                subscriptionId = SUB_ID,
                carrierName = DEFAULT_NAME,
                profileClass = PROFILE_CLASS_UNSET,
            ),
        )
    }

    private val Kosmos.isCarrierMerged by Fixture { MutableState(kairos, false) }

    // Use a real config, with no overrides
    private val systemUiCarrierConfig = SystemUiCarrierConfig(SUB_ID, PersistableBundle())

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            fakeFeatureFlagsClassic.set(ROAMING_INDICATOR_VIA_DISPLAY_INFO, true)
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun startingIsCarrierMerged_usesCarrierMergedInitially() = runTest {
        val carrierMergedOperatorName = "Carrier Merged Operator"
        val nonCarrierMergedName = "Non-carrier-merged"

        fakeCarrierMergedRepo.operatorAlphaShort.setValue(carrierMergedOperatorName)
        fakeMobileRepo.operatorAlphaShort.setValue(nonCarrierMergedName)

        isCarrierMerged.setValue(true)

        val activeRepo by underTest.activeRepo.collectLastValue()
        val operatorAlphaShort by underTest.operatorAlphaShort.collectLastValue()

        assertThat(activeRepo).isEqualTo(fakeCarrierMergedRepo)
        assertThat(operatorAlphaShort).isEqualTo(carrierMergedOperatorName)
    }

    @Test
    fun startingNotCarrierMerged_usesTypicalInitially() = runTest {
        val carrierMergedOperatorName = "Carrier Merged Operator"
        val nonCarrierMergedName = "Typical Operator"

        fakeCarrierMergedRepo.operatorAlphaShort.setValue(carrierMergedOperatorName)
        fakeMobileRepo.operatorAlphaShort.setValue(nonCarrierMergedName)
        isCarrierMerged.setValue(false)

        assertThat(underTest.activeRepo.collectLastValue().value).isEqualTo(fakeMobileRepo)
        assertThat(underTest.operatorAlphaShort.collectLastValue().value)
            .isEqualTo(nonCarrierMergedName)
    }

    @Test
    fun activeRepo_matchesIsCarrierMerged() = runTest {
        isCarrierMerged.setValue(false)

        val latest by underTest.activeRepo.collectLastValue()

        isCarrierMerged.setValue(true)

        assertThat(latest).isEqualTo(fakeCarrierMergedRepo)

        isCarrierMerged.setValue(false)

        assertThat(latest).isEqualTo(fakeMobileRepo)

        isCarrierMerged.setValue(true)

        assertThat(latest).isEqualTo(fakeCarrierMergedRepo)
    }

    @Test
    fun connectionInfo_getsUpdatesFromRepo_carrierMerged() = runTest {
        isCarrierMerged.setValue(false)

        val latestName by underTest.operatorAlphaShort.collectLastValue()
        val latestLevel by underTest.primaryLevel.collectLastValue()

        isCarrierMerged.setValue(true)

        val operator1 = "Carrier Merged Operator"
        val level1 = 1
        fakeCarrierMergedRepo.operatorAlphaShort.setValue(operator1)
        fakeCarrierMergedRepo.primaryLevel.setValue(level1)

        assertThat(latestName).isEqualTo(operator1)
        assertThat(latestLevel).isEqualTo(level1)

        val operator2 = "Carrier Merged Operator #2"
        val level2 = 2
        fakeCarrierMergedRepo.operatorAlphaShort.setValue(operator2)
        fakeCarrierMergedRepo.primaryLevel.setValue(level2)

        assertThat(latestName).isEqualTo(operator2)
        assertThat(latestLevel).isEqualTo(level2)

        val operator3 = "Carrier Merged Operator #3"
        val level3 = 3
        fakeCarrierMergedRepo.operatorAlphaShort.setValue(operator3)
        fakeCarrierMergedRepo.primaryLevel.setValue(level3)

        assertThat(latestName).isEqualTo(operator3)
        assertThat(latestLevel).isEqualTo(level3)
    }

    @Test
    fun connectionInfo_getsUpdatesFromRepo_mobile() = runTest {
        isCarrierMerged.setValue(false)

        val latestName by underTest.operatorAlphaShort.collectLastValue()
        val latestLevel by underTest.primaryLevel.collectLastValue()

        isCarrierMerged.setValue(false)

        val operator1 = "Typical Merged Operator"
        val level1 = 1
        fakeMobileRepo.operatorAlphaShort.setValue(operator1)
        fakeMobileRepo.primaryLevel.setValue(level1)

        assertThat(latestName).isEqualTo(operator1)
        assertThat(latestLevel).isEqualTo(level1)

        val operator2 = "Typical Merged Operator #2"
        val level2 = 2
        fakeMobileRepo.operatorAlphaShort.setValue(operator2)
        fakeMobileRepo.primaryLevel.setValue(level2)

        assertThat(latestName).isEqualTo(operator2)
        assertThat(latestLevel).isEqualTo(level2)

        val operator3 = "Typical Merged Operator #3"
        val level3 = 3
        fakeMobileRepo.operatorAlphaShort.setValue(operator3)
        fakeMobileRepo.primaryLevel.setValue(level3)

        assertThat(latestName).isEqualTo(operator3)
        assertThat(latestLevel).isEqualTo(level3)
    }

    @Test
    fun connectionInfo_updatesWhenCarrierMergedUpdates() = runTest {
        isCarrierMerged.setValue(false)

        val latestName by underTest.operatorAlphaShort.collectLastValue()
        val latestLevel by underTest.primaryLevel.collectLastValue()

        val carrierMergedOperator = "Carrier Merged Operator"
        val carrierMergedLevel = 4
        fakeCarrierMergedRepo.operatorAlphaShort.setValue(carrierMergedOperator)
        fakeCarrierMergedRepo.primaryLevel.setValue(carrierMergedLevel)

        val mobileName = "Typical Operator"
        val mobileLevel = 2
        fakeMobileRepo.operatorAlphaShort.setValue(mobileName)
        fakeMobileRepo.primaryLevel.setValue(mobileLevel)

        // Start with the mobile info
        assertThat(latestName).isEqualTo(mobileName)
        assertThat(latestLevel).isEqualTo(mobileLevel)

        // WHEN isCarrierMerged is set to true
        isCarrierMerged.setValue(true)

        // THEN the carrier merged info is used
        assertThat(latestName).isEqualTo(carrierMergedOperator)
        assertThat(latestLevel).isEqualTo(carrierMergedLevel)

        val newCarrierMergedName = "New CM Operator"
        val newCarrierMergedLevel = 0
        fakeCarrierMergedRepo.operatorAlphaShort.setValue(newCarrierMergedName)
        fakeCarrierMergedRepo.primaryLevel.setValue(newCarrierMergedLevel)

        assertThat(latestName).isEqualTo(newCarrierMergedName)
        assertThat(latestLevel).isEqualTo(newCarrierMergedLevel)

        // WHEN isCarrierMerged is set to false
        isCarrierMerged.setValue(false)

        // THEN the typical info is used
        assertThat(latestName).isEqualTo(mobileName)
        assertThat(latestLevel).isEqualTo(mobileLevel)

        val newMobileName = "New MobileOperator"
        val newMobileLevel = 3
        fakeMobileRepo.operatorAlphaShort.setValue(newMobileName)
        fakeMobileRepo.primaryLevel.setValue(newMobileLevel)

        assertThat(latestName).isEqualTo(newMobileName)
        assertThat(latestLevel).isEqualTo(newMobileLevel)
    }

    @Test
    fun isAllowedDuringAirplaneMode_updatesWhenCarrierMergedUpdates() = runTest {
        isCarrierMerged.setValue(false)

        val latest by underTest.isAllowedDuringAirplaneMode.collectLastValue()

        assertThat(latest).isFalse()

        isCarrierMerged.setValue(true)

        assertThat(latest).isTrue()

        isCarrierMerged.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun connectionInfo_logging_notCarrierMerged_getsUpdates() = runTest {
        // SETUP: Use real repositories to verify the diffing still works. (See b/267501739.)
        val telephonyManager: TelephonyManager = mock {
            on { simOperatorName } doReturn ""
            on { subscriptionId } doReturn SUB_ID
        }
        fakeWifiRepository.setIsWifiEnabled(true)
        fakeWifiRepository.setIsWifiDefault(true)
        mobileRepo = createRealMobileRepo(telephonyManager)
        carrierMergedRepoSpec = realCarrierMergedRepo(telephonyManager)

        isCarrierMerged.setValue(false)

        // Stand-up activated repository
        underTest

        // WHEN we set up some mobile connection info
        val serviceState = ServiceState()
        serviceState.setOperatorName("longName", "OpTypical", "1")
        serviceState.isEmergencyOnly = true
        getTelephonyCallbackForType<TelephonyCallback.ServiceStateListener>(telephonyManager)
            .onServiceStateChanged(serviceState)

        // THEN it's logged to the buffer
        assertThat(dumpBuffer()).contains("$COL_OPERATOR${BUFFER_SEPARATOR}OpTypical")
        assertThat(dumpBuffer()).contains("$COL_EMERGENCY${BUFFER_SEPARATOR}true")

        // WHEN we update mobile connection info
        val serviceState2 = ServiceState()
        serviceState2.setOperatorName("longName", "OpDiff", "1")
        serviceState2.isEmergencyOnly = false
        getTelephonyCallbackForType<TelephonyCallback.ServiceStateListener>(telephonyManager)
            .onServiceStateChanged(serviceState2)

        // THEN the updates are logged
        assertThat(dumpBuffer()).contains("$COL_OPERATOR${BUFFER_SEPARATOR}OpDiff")
        assertThat(dumpBuffer()).contains("$COL_EMERGENCY${BUFFER_SEPARATOR}false")
    }

    @Test
    fun connectionInfo_logging_carrierMerged_getsUpdates() = runTest {
        // SETUP: Use real repositories to verify the diffing still works. (See b/267501739.)
        val telephonyManager: TelephonyManager = mock {
            on { simOperatorName } doReturn ""
            on { subscriptionId } doReturn SUB_ID
        }
        fakeWifiRepository.setIsWifiEnabled(true)
        fakeWifiRepository.setIsWifiDefault(true)
        mobileRepo = createRealMobileRepo(telephonyManager)
        carrierMergedRepoSpec = realCarrierMergedRepo(telephonyManager)

        isCarrierMerged.setValue(true)

        // Stand-up activated repository
        underTest

        // WHEN we set up carrier merged info
        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.CarrierMerged.of(SUB_ID, level = 3))

        // THEN the carrier merged info is logged
        assertThat(dumpBuffer()).contains("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}3")

        // WHEN we update the info
        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.CarrierMerged.of(SUB_ID, level = 1))

        // THEN the updates are logged
        assertThat(dumpBuffer()).contains("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}1")
    }

    @Test
    fun connectionInfo_logging_updatesWhenCarrierMergedUpdates() = runTest {
        // SETUP: Use real repositories to verify the diffing still works. (See b/267501739.)
        val telephonyManager: TelephonyManager = mock {
            on { simOperatorName } doReturn ""
            on { subscriptionId } doReturn SUB_ID
        }
        fakeWifiRepository.setIsWifiEnabled(true)
        fakeWifiRepository.setIsWifiDefault(true)
        mobileRepo = createRealMobileRepo(telephonyManager)
        carrierMergedRepoSpec = realCarrierMergedRepo(telephonyManager)

        isCarrierMerged.setValue(false)

        // Stand-up activated repository
        underTest

        // WHEN we set up some mobile connection info
        val cb =
            getTelephonyCallbackForType<TelephonyCallback.SignalStrengthsListener>(telephonyManager)
        cb.onSignalStrengthsChanged(mock(stubOnly = true) { on { level } doReturn 1 })

        // THEN it's logged to the buffer
        assertThat(dumpBuffer()).contains("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}1")

        // WHEN isCarrierMerged is set to true
        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.CarrierMerged.of(SUB_ID, level = 3))
        isCarrierMerged.setValue(true)

        // THEN the carrier merged info is logged
        assertThat(dumpBuffer()).contains("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}3")

        // WHEN the carrier merge network is updated
        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.CarrierMerged.of(SUB_ID, level = 4))

        // THEN the new level is logged
        assertThat(dumpBuffer()).contains("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}4")

        // WHEN isCarrierMerged is set to false
        isCarrierMerged.setValue(false)

        // THEN the typical info is logged
        // Note: Since our first logs also had the typical info, we need to search the log
        // contents for after our carrier merged level log.
        val fullBuffer = dumpBuffer()
        val carrierMergedContentIndex = fullBuffer.indexOf("${BUFFER_SEPARATOR}4")
        val bufferAfterCarrierMerged = fullBuffer.substring(carrierMergedContentIndex)
        assertThat(bufferAfterCarrierMerged).contains("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}1")

        // WHEN the normal network is updated
        cb.onSignalStrengthsChanged(mock(stubOnly = true) { on { level } doReturn 0 })

        // THEN the new level is logged
        assertThat(dumpBuffer()).contains("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}0")
    }

    @Test
    fun connectionInfo_logging_doesNotLogUpdatesForNotActiveRepo() = runTest {
        // SETUP: Use real repositories to verify the diffing still works. (See b/267501739.)
        val telephonyManager: TelephonyManager = mock {
            on { simOperatorName } doReturn ""
            on { subscriptionId } doReturn SUB_ID
        }
        fakeWifiRepository.setIsWifiEnabled(true)
        fakeWifiRepository.setIsWifiDefault(true)
        mobileRepo = createRealMobileRepo(telephonyManager)
        carrierMergedRepoSpec = realCarrierMergedRepo(telephonyManager)

        // WHEN isCarrierMerged = false
        isCarrierMerged.setValue(false)

        // Stand-up activated repository
        underTest

        fun setSignalLevel(newLevel: Int) {
            val signalStrength =
                mock<SignalStrength>(stubOnly = true) { on { level } doReturn newLevel }
            argumentCaptor<TelephonyCallback>()
                .apply { verify(telephonyManager).registerTelephonyCallback(any(), capture()) }
                .allValues
                .asSequence()
                .filterIsInstance<TelephonyCallback.SignalStrengthsListener>()
                .forEach { it.onSignalStrengthsChanged(signalStrength) }
        }

        // WHEN we set up some mobile connection info
        setSignalLevel(1)

        // THEN updates to the carrier merged level aren't logged
        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.CarrierMerged.of(SUB_ID, level = 4))
        assertThat(dumpBuffer()).doesNotContain("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}4")

        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.CarrierMerged.of(SUB_ID, level = 3))
        assertThat(dumpBuffer()).doesNotContain("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}3")

        // WHEN isCarrierMerged is set to true
        isCarrierMerged.setValue(true)

        // THEN updates to the normal level aren't logged
        setSignalLevel(5)
        assertThat(dumpBuffer()).doesNotContain("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}5")

        setSignalLevel(6)
        assertThat(dumpBuffer()).doesNotContain("$COL_PRIMARY_LEVEL${BUFFER_SEPARATOR}6")
    }

    private fun KairosTestScope.createRealMobileRepo(
        telephonyManager: TelephonyManager
    ): MobileConnectionRepositoryKairosImpl =
        MobileConnectionRepositoryKairosImpl(
                subId = SUB_ID,
                context = context,
                subscriptionModel = subscriptionModel,
                defaultNetworkName = DEFAULT_NAME_MODEL,
                networkNameSeparator = SEP,
                connectivityManager = mock(stubOnly = true),
                telephonyManager = telephonyManager,
                systemUiCarrierConfig = systemUiCarrierConfig,
                broadcastDispatcher = fakeBroadcastDispatcher,
                mobileMappingsProxy = mock(stubOnly = true),
                bgDispatcher = testDispatcher,
                logger = mock(stubOnly = true),
                tableLogBuffer = mobileLogger,
                flags = featureFlagsClassic,
            )
            .activated()

    private fun Kosmos.realCarrierMergedRepo(
        telephonyManager: TelephonyManager
    ): BuildSpec<CarrierMergedConnectionRepositoryKairos> = buildSpec {
        activated {
            CarrierMergedConnectionRepositoryKairos(
                subId = SUB_ID,
                tableLogBuffer = mobileLogger,
                telephonyManager = telephonyManager,
                wifiRepository = wifiRepository,
                isInEcmMode = stateOf(false),
            )
        }
    }

    private fun Kosmos.dumpBuffer(): String {
        val outputWriter = StringWriter()
        mobileLogger.dump(PrintWriter(outputWriter), arrayOf())
        return outputWriter.toString()
    }

    private companion object {
        const val SUB_ID = 42
        private const val DEFAULT_NAME = "default name"
        private val DEFAULT_NAME_MODEL = NetworkNameModel.Default(DEFAULT_NAME)
        private const val SEP = "-"
        private const val BUFFER_SEPARATOR = "|"
    }
}
