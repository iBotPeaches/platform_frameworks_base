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

package com.android.systemui.statusbar.pipeline.mobile.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.mobile.MobileIconCarrierIdOverrides
import com.android.settingslib.mobile.MobileIconCarrierIdOverridesImpl
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.ActivatedKairosFixture
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.MutableState
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.map
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType.CarrierMergedNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType.DefaultNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType.OverrideNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileMappingsProxy
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.FakeMobileIconsInteractor.Companion.FIVE_G_OVERRIDE
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.FakeMobileIconsInteractor.Companion.FOUR_G
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.FakeMobileIconsInteractor.Companion.THREE_G
import com.android.systemui.statusbar.pipeline.mobile.domain.model.NetworkTypeIconModel
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileIconInteractorKairosTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            featureFlagsClassic.fake.apply { setDefault(FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS) }
        }

    private val Kosmos.tableLogBuffer by Fixture {
        logcatTableLogBuffer(this, "MobileIconInteractorKairosTest")
    }

    private var Kosmos.overrides: MobileIconCarrierIdOverrides by Fixture {
        MobileIconCarrierIdOverridesImpl()
    }

    private val Kosmos.defaultSubscriptionHasDataEnabled by Fixture { MutableState(kairos, true) }

    private val Kosmos.alwaysShowDataRatIcon by Fixture { MutableState(kairos, false) }

    private val Kosmos.alwaysUseCdmaLevel by Fixture { MutableState(kairos, false) }

    private val Kosmos.isSingleCarrier by Fixture { MutableState(kairos, true) }

    private val Kosmos.mobileIsDefault by Fixture { MutableState(kairos, false) }

    private val Kosmos.defaultMobileIconMapping by Fixture {
        MutableState(kairos, fakeMobileIconsInteractor.TEST_MAPPING)
    }

    private val Kosmos.defaultMobileIconGroup by Fixture { MutableState(kairos, TelephonyIcons.G) }

    private val Kosmos.isDefaultConnectionFailed by Fixture { MutableState(kairos, false) }

    private val Kosmos.isForceHidden by Fixture { MutableState(kairos, false) }

    private val Kosmos.underTest by ActivatedKairosFixture {
        MobileIconInteractorKairosImpl(
            defaultSubscriptionHasDataEnabled,
            alwaysShowDataRatIcon,
            alwaysUseCdmaLevel,
            isSingleCarrier,
            mobileIsDefault,
            defaultMobileIconMapping,
            defaultMobileIconGroup,
            isDefaultConnectionFailed,
            isForceHidden,
            connectionRepository = connectionRepo,
            context = context,
            carrierIdOverrides = overrides,
        )
    }

    private val Kosmos.connectionRepo by Fixture {
        FakeMobileConnectionRepositoryKairos(SUB_1_ID, kairos, tableLogBuffer).apply {
            dataEnabled.setValue(true)
            isInService.setValue(true)
        }
    }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun gsm_usesGsmLevel() = runTest {
        connectionRepo.isGsm.setValue(true)
        connectionRepo.primaryLevel.setValue(GSM_LEVEL)
        connectionRepo.cdmaLevel.setValue(CDMA_LEVEL)

        val latest by underTest.signalLevelIcon.collectLastValue()

        assertThat(latest?.level).isEqualTo(GSM_LEVEL)
    }

    @Test
    fun gsm_alwaysShowCdmaTrue_stillUsesGsmLevel() = runTest {
        connectionRepo.isGsm.setValue(true)
        connectionRepo.primaryLevel.setValue(GSM_LEVEL)
        connectionRepo.cdmaLevel.setValue(CDMA_LEVEL)
        //            mobileIconsInteractor.alwaysUseCdmaLevel.setValue(true)
        alwaysUseCdmaLevel.setValue(true)

        val latest by underTest.signalLevelIcon.collectLastValue()

        assertThat(latest?.level).isEqualTo(GSM_LEVEL)
    }

    @Test
    fun notGsm_level_default_unknown() = runTest {
        connectionRepo.isGsm.setValue(false)

        val latest by underTest.signalLevelIcon.collectLastValue()

        assertThat(latest?.level).isEqualTo(CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN)
    }

    @Test
    fun notGsm_alwaysShowCdmaTrue_usesCdmaLevel() = runTest {
        connectionRepo.isGsm.setValue(false)
        connectionRepo.primaryLevel.setValue(GSM_LEVEL)
        connectionRepo.cdmaLevel.setValue(CDMA_LEVEL)
        //            mobileIconsInteractor.alwaysUseCdmaLevel.setValue(true)
        alwaysUseCdmaLevel.setValue(true)

        val latest by underTest.signalLevelIcon.collectLastValue()

        assertThat(latest?.level).isEqualTo(CDMA_LEVEL)
    }

    @Test
    fun notGsm_alwaysShowCdmaFalse_usesPrimaryLevel() = runTest {
        connectionRepo.isGsm.setValue(false)
        connectionRepo.primaryLevel.setValue(GSM_LEVEL)
        connectionRepo.cdmaLevel.setValue(CDMA_LEVEL)
        //            mobileIconsInteractor.alwaysUseCdmaLevel.setValue(false)
        alwaysUseCdmaLevel.setValue(false)

        val latest by underTest.signalLevelIcon.collectLastValue()

        assertThat(latest?.level).isEqualTo(GSM_LEVEL)
    }

    @Test
    fun numberOfLevels_comesFromRepo_whenApplicable() = runTest {
        val latest by
            underTest.signalLevelIcon
                .map { (it as? SignalIconModel.Cellular)?.numberOfLevels }
                .collectLastValue()

        connectionRepo.numberOfLevels.setValue(5)
        assertThat(latest).isEqualTo(5)

        connectionRepo.numberOfLevels.setValue(4)
        assertThat(latest).isEqualTo(4)
    }

    @Test
    fun inflateSignalStrength_arbitrarilyAddsOneToTheReportedLevel() = runTest {
        connectionRepo.inflateSignalStrength.setValue(false)
        val latest by underTest.signalLevelIcon.collectLastValue()

        connectionRepo.primaryLevel.setValue(4)
        assertThat(latest!!.level).isEqualTo(4)

        connectionRepo.inflateSignalStrength.setValue(true)
        connectionRepo.primaryLevel.setValue(4)

        // when INFLATE_SIGNAL_STRENGTH is true, we add 1 to the reported signal level
        assertThat(latest!!.level).isEqualTo(5)
    }

    @Test
    fun networkSlice_configOn_hasPrioritizedCaps_showsSlice() = runTest {
        connectionRepo.allowNetworkSliceIndicator.setValue(true)
        val latest by underTest.showSliceAttribution.collectLastValue()

        connectionRepo.hasPrioritizedNetworkCapabilities.setValue(true)

        assertThat(latest).isTrue()
    }

    @Test
    fun networkSlice_configOn_noPrioritizedCaps_noSlice() = runTest {
        connectionRepo.allowNetworkSliceIndicator.setValue(true)
        val latest by underTest.showSliceAttribution.collectLastValue()

        connectionRepo.hasPrioritizedNetworkCapabilities.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun networkSlice_configOff_hasPrioritizedCaps_noSlice() = runTest {
        connectionRepo.allowNetworkSliceIndicator.setValue(false)
        val latest by underTest.showSliceAttribution.collectLastValue()

        connectionRepo.hasPrioritizedNetworkCapabilities.setValue(true)

        assertThat(latest).isFalse()
    }

    @Test
    fun networkSlice_configOff_noPrioritizedCaps_noSlice() = runTest {
        connectionRepo.allowNetworkSliceIndicator.setValue(false)
        val latest by underTest.showSliceAttribution.collectLastValue()

        connectionRepo.hasPrioritizedNetworkCapabilities.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun iconGroup_three_g() = runTest {
        connectionRepo.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileMappingsProxy.toIconKey(THREE_G))
        )

        val latest by underTest.networkTypeIconGroup.collectLastValue()

        assertThat(latest).isEqualTo(NetworkTypeIconModel.DefaultIcon(TelephonyIcons.THREE_G))
    }

    @Test
    fun iconGroup_updates_on_change() = runTest {
        connectionRepo.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileMappingsProxy.toIconKey(THREE_G))
        )

        val latest by underTest.networkTypeIconGroup.collectLastValue()

        connectionRepo.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileMappingsProxy.toIconKey(FOUR_G))
        )

        assertThat(latest).isEqualTo(NetworkTypeIconModel.DefaultIcon(TelephonyIcons.FOUR_G))
    }

    @Test
    fun iconGroup_5g_override_type() = runTest {
        connectionRepo.resolvedNetworkType.setValue(
            OverrideNetworkType(mobileMappingsProxy.toIconKeyOverride(FIVE_G_OVERRIDE))
        )

        val latest by underTest.networkTypeIconGroup.collectLastValue()

        assertThat(latest).isEqualTo(NetworkTypeIconModel.DefaultIcon(TelephonyIcons.NR_5G))
    }

    @Test
    fun iconGroup_default_if_no_lookup() = runTest {
        connectionRepo.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileMappingsProxy.toIconKey(NETWORK_TYPE_UNKNOWN))
        )

        val latest by underTest.networkTypeIconGroup.collectLastValue()

        assertThat(latest)
            .isEqualTo(NetworkTypeIconModel.DefaultIcon(FakeMobileIconsInteractor.DEFAULT_ICON))
    }

    @Test
    fun iconGroup_carrierMerged_usesOverride() = runTest {
        connectionRepo.resolvedNetworkType.setValue(CarrierMergedNetworkType)

        val latest by underTest.networkTypeIconGroup.collectLastValue()

        assertThat(latest)
            .isEqualTo(NetworkTypeIconModel.DefaultIcon(CarrierMergedNetworkType.iconGroupOverride))
    }

    @Test
    fun overrideIcon_usesCarrierIdOverride() = runTest {
        overrides =
            mock<MobileIconCarrierIdOverrides> {
                on { carrierIdEntryExists(anyInt()) } doReturn true
                on { getOverrideFor(anyInt(), anyString(), any()) } doReturn 1234
            }

        connectionRepo.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileMappingsProxy.toIconKey(THREE_G))
        )

        val latest by underTest.networkTypeIconGroup.collectLastValue()

        assertThat(latest)
            .isEqualTo(NetworkTypeIconModel.OverriddenIcon(TelephonyIcons.THREE_G, 1234))
    }

    @Test
    fun alwaysShowDataRatIcon_matchesParent() = runTest {
        val latest by underTest.alwaysShowDataRatIcon.collectLastValue()

        //            mobileIconsInteractor.alwaysShowDataRatIcon.setValue(true)
        alwaysShowDataRatIcon.setValue(true)

        assertThat(latest).isTrue()

        //            mobileIconsInteractor.alwaysShowDataRatIcon.setValue(false)
        alwaysShowDataRatIcon.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun dataState_connected() = runTest {
        val latest by underTest.isDataConnected.collectLastValue()

        connectionRepo.dataConnectionState.setValue(DataConnectionState.Connected)

        assertThat(latest).isTrue()
    }

    @Test
    fun dataState_notConnected() = runTest {
        val latest by underTest.isDataConnected.collectLastValue()

        connectionRepo.dataConnectionState.setValue(DataConnectionState.Disconnected)

        assertThat(latest).isFalse()
    }

    @Test
    fun isInService_usesRepositoryValue() = runTest {
        val latest by underTest.isInService.collectLastValue()

        connectionRepo.isInService.setValue(true)

        assertThat(latest).isTrue()

        connectionRepo.isInService.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun roaming_isGsm_usesConnectionModel() = runTest {
        val latest by underTest.isRoaming.collectLastValue()

        connectionRepo.cdmaRoaming.setValue(true)
        connectionRepo.isGsm.setValue(true)
        connectionRepo.isRoaming.setValue(false)

        assertThat(latest).isFalse()

        connectionRepo.isRoaming.setValue(true)

        assertThat(latest).isTrue()
    }

    @Test
    fun roaming_isCdma_usesCdmaRoamingBit() = runTest {
        val latest by underTest.isRoaming.collectLastValue()

        connectionRepo.cdmaRoaming.setValue(false)
        connectionRepo.isGsm.setValue(false)
        connectionRepo.isRoaming.setValue(true)

        assertThat(latest).isFalse()

        connectionRepo.cdmaRoaming.setValue(true)
        connectionRepo.isGsm.setValue(false)
        connectionRepo.isRoaming.setValue(false)

        assertThat(latest).isTrue()
    }

    @Test
    fun roaming_falseWhileCarrierNetworkChangeActive() = runTest {
        val latest by underTest.isRoaming.collectLastValue()

        connectionRepo.cdmaRoaming.setValue(true)
        connectionRepo.isGsm.setValue(false)
        connectionRepo.isRoaming.setValue(true)
        connectionRepo.carrierNetworkChangeActive.setValue(true)

        assertThat(latest).isFalse()

        connectionRepo.cdmaRoaming.setValue(true)
        connectionRepo.isGsm.setValue(true)

        assertThat(latest).isFalse()
    }

    @Test
    fun networkName_usesOperatorAlphaShortWhenNonNullAndRepoIsDefault() = runTest {
        val latest by underTest.networkName.collectLastValue()

        val testOperatorName = "operatorAlphaShort"

        // Default network name, operator name is non-null, uses the operator name
        connectionRepo.networkName.setValue(DEFAULT_NAME_MODEL)
        connectionRepo.operatorAlphaShort.setValue(testOperatorName)

        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived(testOperatorName))

        // Default network name, operator name is null, uses the default
        connectionRepo.operatorAlphaShort.setValue(null)

        assertThat(latest).isEqualTo(DEFAULT_NAME_MODEL)

        // Derived network name, operator name non-null, uses the derived name
        connectionRepo.networkName.setValue(DERIVED_NAME_MODEL)
        connectionRepo.operatorAlphaShort.setValue(testOperatorName)

        assertThat(latest).isEqualTo(DERIVED_NAME_MODEL)
    }

    @Test
    fun networkNameForSubId_usesOperatorAlphaShortWhenNonNullAndRepoIsDefault() = runTest {
        val latest by underTest.carrierName.collectLastValue()

        val testOperatorName = "operatorAlphaShort"

        // Default network name, operator name is non-null, uses the operator name
        connectionRepo.carrierName.setValue(DEFAULT_NAME_MODEL)
        connectionRepo.operatorAlphaShort.setValue(testOperatorName)

        assertThat(latest).isEqualTo(testOperatorName)

        // Default network name, operator name is null, uses the default
        connectionRepo.operatorAlphaShort.setValue(null)

        assertThat(latest).isEqualTo(DEFAULT_NAME)

        // Derived network name, operator name non-null, uses the derived name
        connectionRepo.carrierName.setValue(NetworkNameModel.SubscriptionDerived(DERIVED_NAME))
        connectionRepo.operatorAlphaShort.setValue(testOperatorName)

        assertThat(latest).isEqualTo(DERIVED_NAME)
    }

    @Test
    fun isSingleCarrier_matchesParent() = runTest {
        val latest by underTest.isSingleCarrier.collectLastValue()

        //            mobileIconsInteractor.isSingleCarrier.setValue(true)
        isSingleCarrier.setValue(true)
        assertThat(latest).isTrue()

        //            mobileIconsInteractor.isSingleCarrier.setValue(false)
        isSingleCarrier.setValue(false)
        assertThat(latest).isFalse()
    }

    @Test
    fun isForceHidden_matchesParent() = runTest {
        val latest by underTest.isForceHidden.collectLastValue()

        //            mobileIconsInteractor.isForceHidden.setValue(true)
        isForceHidden.setValue(true)
        assertThat(latest).isTrue()

        //            mobileIconsInteractor.isForceHidden.setValue(false)
        isForceHidden.setValue(false)
        assertThat(latest).isFalse()
    }

    @Test
    fun isAllowedDuringAirplaneMode_matchesRepo() = runTest {
        val latest by underTest.isAllowedDuringAirplaneMode.collectLastValue()

        connectionRepo.isAllowedDuringAirplaneMode.setValue(true)
        assertThat(latest).isTrue()

        connectionRepo.isAllowedDuringAirplaneMode.setValue(false)
        assertThat(latest).isFalse()
    }

    @Test
    fun cellBasedIconId_correctLevel_notCutout() = runTest {
        connectionRepo.isNonTerrestrial.setValue(false)
        connectionRepo.isInService.setValue(true)
        connectionRepo.primaryLevel.setValue(1)
        connectionRepo.dataEnabled.setValue(true)
        connectionRepo.isNonTerrestrial.setValue(false)

        val latest by
            underTest.signalLevelIcon.map { it as? SignalIconModel.Cellular }.collectLastValue()

        assertThat(latest?.level).isEqualTo(1)

        // TODO: need to provision MobileIconsInteractorKairos#isDefaultConnectionFailed +
        // defaultSubscriptionHasDataEnabled?
        assertThat(latest?.showExclamationMark).isEqualTo(false)
    }

    @Test
    fun icon_usesLevelFromInteractor() = runTest {
        connectionRepo.isNonTerrestrial.setValue(false)
        connectionRepo.isInService.setValue(true)

        val latest by underTest.signalLevelIcon.collectLastValue()

        connectionRepo.primaryLevel.setValue(3)
        assertThat(latest!!.level).isEqualTo(3)

        connectionRepo.primaryLevel.setValue(1)
        assertThat(latest!!.level).isEqualTo(1)
    }

    @Test
    fun cellBasedIcon_usesNumberOfLevelsFromInteractor() = runTest {
        connectionRepo.isNonTerrestrial.setValue(false)

        val latest by
            underTest.signalLevelIcon.map { it as? SignalIconModel.Cellular }.collectLastValue()

        connectionRepo.numberOfLevels.setValue(5)
        assertThat(latest!!.numberOfLevels).isEqualTo(5)

        connectionRepo.numberOfLevels.setValue(2)
        assertThat(latest!!.numberOfLevels).isEqualTo(2)
    }

    @Test
    fun cellBasedIcon_defaultDataDisabled_showExclamationTrue() = runTest {
        connectionRepo.isNonTerrestrial.setValue(false)
        connectionRepo.dataEnabled.setValue(false)
        defaultSubscriptionHasDataEnabled.setValue(false)

        val latest by underTest.signalLevelIcon.collectLastValue()

        assertThat((latest!! as SignalIconModel.Cellular).showExclamationMark).isTrue()
    }

    @Test
    fun cellBasedIcon_defaultConnectionFailed_showExclamationTrue() = runTest {
        connectionRepo.isNonTerrestrial.setValue(false)
        //            mobileIconsInteractor.isDefaultConnectionFailed.setValue(true)
        isDefaultConnectionFailed.setValue(true)

        val latest by underTest.signalLevelIcon.collectLastValue()

        assertThat((latest!! as SignalIconModel.Cellular).showExclamationMark).isTrue()
    }

    @Test
    fun cellBasedIcon_enabledAndNotFailed_showExclamationFalse() = runTest {
        connectionRepo.isNonTerrestrial.setValue(false)
        connectionRepo.isInService.setValue(true)
        connectionRepo.dataEnabled.setValue(true)
        //            mobileIconsInteractor.isDefaultConnectionFailed.setValue(false)
        isDefaultConnectionFailed.setValue(false)

        val latest by underTest.signalLevelIcon.collectLastValue()

        assertThat((latest!! as SignalIconModel.Cellular).showExclamationMark).isFalse()
    }

    @Test
    fun cellBasedIcon_usesEmptyState_whenNotInService() = runTest {
        val latest by
            underTest.signalLevelIcon.map { it as SignalIconModel.Cellular }.collectLastValue()

        connectionRepo.isNonTerrestrial.setValue(false)
        connectionRepo.isInService.setValue(false)

        assertThat(latest?.level).isEqualTo(0)
        assertThat(latest?.showExclamationMark).isTrue()

        // Changing the level doesn't overwrite the disabled state
        connectionRepo.primaryLevel.setValue(2)
        assertThat(latest?.level).isEqualTo(0)
        assertThat(latest?.showExclamationMark).isTrue()

        // Once back in service, the regular icon appears
        connectionRepo.isInService.setValue(true)
        assertThat(latest?.level).isEqualTo(2)
        assertThat(latest?.showExclamationMark).isFalse()
    }

    @Test
    fun cellBasedIcon_usesCarrierNetworkState_whenInCarrierNetworkChangeMode() = runTest {
        val latest by
            underTest.signalLevelIcon.map { it as SignalIconModel.Cellular }.collectLastValue()

        connectionRepo.isNonTerrestrial.setValue(false)
        connectionRepo.isInService.setValue(true)
        connectionRepo.carrierNetworkChangeActive.setValue(true)
        connectionRepo.primaryLevel.setValue(1)
        connectionRepo.cdmaLevel.setValue(1)

        assertThat(latest!!.level).isEqualTo(1)
        assertThat(latest!!.carrierNetworkChange).isTrue()

        // SignalIconModel respects the current level
        connectionRepo.primaryLevel.setValue(2)

        assertThat(latest!!.level).isEqualTo(2)
        assertThat(latest!!.carrierNetworkChange).isTrue()
    }

    @Test
    fun satBasedIcon_isUsedWhenNonTerrestrial() = runTest {
        val latest by underTest.signalLevelIcon.collectLastValue()

        // Start off using cellular
        assertThat(latest).isInstanceOf(SignalIconModel.Cellular::class.java)

        connectionRepo.isNonTerrestrial.setValue(true)

        assertThat(latest).isInstanceOf(SignalIconModel.Satellite::class.java)
    }

    @DisableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    // See b/346904529 for more context
    fun satBasedIcon_doesNotInflateSignalStrength_flagOff() = runTest {
        val latest by underTest.signalLevelIcon.collectLastValue()

        // GIVEN a satellite connection
        connectionRepo.isNonTerrestrial.setValue(true)
        // GIVEN this carrier has set INFLATE_SIGNAL_STRENGTH
        connectionRepo.inflateSignalStrength.setValue(true)

        connectionRepo.primaryLevel.setValue(4)
        assertThat(latest!!.level).isEqualTo(4)

        connectionRepo.inflateSignalStrength.setValue(true)
        connectionRepo.primaryLevel.setValue(4)

        // Icon level is unaffected
        assertThat(latest!!.level).isEqualTo(4)
    }

    @EnableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    // See b/346904529 for more context
    fun satBasedIcon_doesNotInflateSignalStrength_flagOn() = runTest {
        val latest by underTest.signalLevelIcon.collectLastValue()

        // GIVEN a satellite connection
        connectionRepo.isNonTerrestrial.setValue(true)
        // GIVEN this carrier has set INFLATE_SIGNAL_STRENGTH
        connectionRepo.inflateSignalStrength.setValue(true)

        connectionRepo.satelliteLevel.setValue(4)
        assertThat(latest!!.level).isEqualTo(4)

        connectionRepo.inflateSignalStrength.setValue(true)
        connectionRepo.primaryLevel.setValue(4)

        // Icon level is unaffected
        assertThat(latest!!.level).isEqualTo(4)
    }

    @DisableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    fun satBasedIcon_usesPrimaryLevel_flagOff() = runTest {
        val latest by underTest.signalLevelIcon.collectLastValue()

        // GIVEN a satellite connection
        connectionRepo.isNonTerrestrial.setValue(true)

        // GIVEN primary level is set
        connectionRepo.primaryLevel.setValue(4)
        connectionRepo.satelliteLevel.setValue(0)

        // THEN icon uses the primary level because the flag is off
        assertThat(latest!!.level).isEqualTo(4)
    }

    @EnableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    fun satBasedIcon_usesSatelliteLevel_flagOn() = runTest {
        val latest by underTest.signalLevelIcon.collectLastValue()

        // GIVEN a satellite connection
        connectionRepo.isNonTerrestrial.setValue(true)

        // GIVEN satellite level is set
        connectionRepo.satelliteLevel.setValue(4)
        connectionRepo.primaryLevel.setValue(0)

        // THEN icon uses the satellite level because the flag is on
        assertThat(latest!!.level).isEqualTo(4)
    }

    /**
     * Context (b/377518113), this test will not be needed after FLAG_CARRIER_ROAMING_NB_IOT_NTN is
     * rolled out. The new API should report 0 automatically if not in service.
     */
    @DisableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    fun satBasedIcon_reportsLevelZeroWhenOutOfService() = runTest {
        val latest by underTest.signalLevelIcon.collectLastValue()

        // GIVEN a satellite connection
        connectionRepo.isNonTerrestrial.setValue(true)
        // GIVEN this carrier has set INFLATE_SIGNAL_STRENGTH
        connectionRepo.inflateSignalStrength.setValue(true)

        connectionRepo.primaryLevel.setValue(4)
        assertThat(latest!!.level).isEqualTo(4)

        connectionRepo.isInService.setValue(false)
        connectionRepo.primaryLevel.setValue(4)

        // THEN level reports 0, by policy
        assertThat(latest!!.level).isEqualTo(0)
    }

    companion object {
        private const val GSM_LEVEL = 1
        private const val CDMA_LEVEL = 2

        private const val SUB_1_ID = 1

        private const val DEFAULT_NAME = "test default name"
        private val DEFAULT_NAME_MODEL = NetworkNameModel.Default(DEFAULT_NAME)
        private const val DERIVED_NAME = "test derived name"
        private val DERIVED_NAME_MODEL = NetworkNameModel.IntentDerived(DERIVED_NAME)
    }
}
