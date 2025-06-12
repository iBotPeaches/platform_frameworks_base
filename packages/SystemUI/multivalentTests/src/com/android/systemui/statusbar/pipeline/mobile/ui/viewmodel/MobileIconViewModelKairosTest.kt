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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.mobile.MobileMappings
import com.android.settingslib.mobile.TelephonyIcons.G
import com.android.settingslib.mobile.TelephonyIcons.THREE_G
import com.android.settingslib.mobile.TelephonyIcons.UNKNOWN
import com.android.systemui.Flags.FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.ActivatedKairosFixture
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.map
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.res.R
import com.android.systemui.statusbar.connectivity.MobileIconCarrierIdOverridesFake
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.airplane.data.repository.fake
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.airplaneModeInteractor
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepository.Companion.DEFAULT_NETWORK_NAME
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorKairosImpl
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.mobileIconsInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.model.MobileContentDescription
import com.android.systemui.statusbar.pipeline.shared.ConnectivityConstants
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlot
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileIconViewModelKairosTest : SysuiTestCase() {

    private val Kosmos.underTest: MobileIconViewModelKairos by ActivatedKairosFixture {
        MobileIconViewModelKairos(
            SUB_1_ID,
            interactor,
            airplaneModeInteractor,
            constants,
            featureFlagsClassic,
        )
    }
    private val Kosmos.interactor: MobileIconInteractorKairos by ActivatedKairosFixture {
        MobileIconInteractorKairosImpl(
            mobileIconsInteractorKairos.activeDataConnectionHasDataEnabled,
            mobileIconsInteractorKairos.alwaysShowDataRatIcon,
            mobileIconsInteractorKairos.alwaysUseCdmaLevel,
            mobileIconsInteractorKairos.isSingleCarrier,
            mobileIconsInteractorKairos.mobileIsDefault,
            mobileIconsInteractorKairos.defaultMobileIconMapping,
            mobileIconsInteractorKairos.defaultMobileIconGroup,
            mobileIconsInteractorKairos.isDefaultConnectionFailed,
            mobileIconsInteractorKairos.isForceHidden,
            repository,
            context,
            MobileIconCarrierIdOverridesFake(),
        )
    }
    private val Kosmos.repository: FakeMobileConnectionRepositoryKairos by
        Kosmos.Fixture {
            FakeMobileConnectionRepositoryKairos(SUB_1_ID, kairos, tableLogBuffer)
                .also {
                    mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(
                        SUB_1_ID
                    )
                    mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(
                        listOf(
                            SubscriptionModel(
                                SUB_1_ID,
                                carrierName = "carrierName",
                                profileClass = 0,
                            )
                        )
                    )
                }
                .apply {
                    isInService.setValue(true)
                    dataConnectionState.setValue(DataConnectionState.Connected)
                    dataEnabled.setValue(true)
                    setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
                }
        }
    private val Kosmos.constants: ConnectivityConstants by
        Kosmos.Fixture { mock { on { hasDataCapabilities } doReturn true } }
    private val Kosmos.tableLogBuffer by
        Kosmos.Fixture { logcatTableLogBuffer(this, "MobileIconViewModelKairosTest") }

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            mobileConnectionsRepositoryKairos =
                fakeMobileConnectionsRepositoryKairos.apply { mobileIsDefault.setValue(true) }
            featureFlagsClassic.fake.apply {
                set(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS, true)
            }
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun isVisible_notDataCapable_alwaysFalse() = runTest {
        // Create a new view model here so the constants are properly read
        constants.stub { on { hasDataCapabilities } doReturn false }

        val latest by underTest.isVisible.collectLastValue()

        assertThat(latest).isFalse()
    }

    @Test
    fun isVisible_notAirplane_notForceHidden_true() = runTest {
        val latest by underTest.isVisible.collectLastValue()

        airplaneModeRepository.fake.setIsAirplaneMode(false)

        assertThat(latest).isTrue()
    }

    @Test
    fun isVisible_airplaneAndNotAllowed_false() = runTest {
        val latest by underTest.isVisible.collectLastValue()

        airplaneModeRepository.fake.setIsAirplaneMode(true)
        repository.isAllowedDuringAirplaneMode.setValue(false)
        connectivityRepository.fake.setForceHiddenIcons(setOf())

        assertThat(latest).isEqualTo(false)
    }

    /** Regression test for b/291993542. */
    @Test
    fun isVisible_airplaneButAllowed_true() = runTest {
        val latest by underTest.isVisible.collectLastValue()

        airplaneModeRepository.fake.setIsAirplaneMode(true)
        repository.isAllowedDuringAirplaneMode.setValue(true)
        connectivityRepository.fake.setForceHiddenIcons(setOf())

        assertThat(latest).isTrue()
    }

    @Test
    fun isVisible_forceHidden_false() = runTest {
        val latest by underTest.isVisible.collectLastValue()

        airplaneModeRepository.fake.setIsAirplaneMode(false)
        connectivityRepository.fake.setForceHiddenIcons(setOf(ConnectivitySlot.MOBILE))

        assertThat(latest).isFalse()
    }

    @Test
    fun isVisible_respondsToUpdates() = runTest {
        val latest by underTest.isVisible.collectLastValue()

        airplaneModeRepository.fake.setIsAirplaneMode(false)
        connectivityRepository.fake.setForceHiddenIcons(setOf())

        assertThat(latest).isEqualTo(true)

        airplaneModeRepository.fake.setIsAirplaneMode(true)
        assertThat(latest).isEqualTo(false)

        repository.isAllowedDuringAirplaneMode.setValue(true)
        assertThat(latest).isEqualTo(true)

        connectivityRepository.fake.setForceHiddenIcons(setOf(ConnectivitySlot.MOBILE))
        assertThat(latest).isEqualTo(false)
    }

    @Test
    fun isVisible_satellite_respectsAirplaneMode() = runTest {
        val latest by underTest.isVisible.collectLastValue()

        repository.isNonTerrestrial.setValue(true)
        airplaneModeInteractor.setIsAirplaneMode(false)

        assertThat(latest).isTrue()

        airplaneModeInteractor.setIsAirplaneMode(true)

        assertThat(latest).isFalse()
    }

    @Test
    fun contentDescription_notInService_usesNoPhone() = runTest {
        val latest by underTest.contentDescription.collectLastValue()

        repository.isInService.setValue(false)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))
    }

    @Test
    fun contentDescription_includesNetworkName() = runTest {
        val latest by underTest.contentDescription.collectLastValue()

        repository.isInService.setValue(true)
        repository.networkName.setValue(NetworkNameModel.SubscriptionDerived("Test Network Name"))
        repository.numberOfLevels.setValue(5)
        repository.setAllLevels(3)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular("Test Network Name", THREE_BARS))
    }

    @Test
    fun contentDescription_inService_usesLevel() = runTest {
        val latest by underTest.contentDescription.collectLastValue()

        repository.setAllLevels(2)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, TWO_BARS))

        repository.setAllLevels(0)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))
    }

    @Test
    fun contentDescription_nonInflated_invalidLevelUsesNoSignalText() = runTest {
        val latest by underTest.contentDescription.collectLastValue()

        repository.inflateSignalStrength.setValue(false)
        repository.setAllLevels(-1)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))

        repository.setAllLevels(100)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))
    }

    @Test
    fun contentDescription_nonInflated_levelStrings() = runTest {
        val latest by underTest.contentDescription.collectLastValue()

        repository.inflateSignalStrength.setValue(false)
        repository.setAllLevels(0)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))

        repository.setAllLevels(1)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, ONE_BAR))

        repository.setAllLevels(2)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, TWO_BARS))

        repository.setAllLevels(3)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, THREE_BARS))

        repository.setAllLevels(4)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, FULL_BARS))
    }

    @Test
    fun contentDescription_inflated_invalidLevelUsesNoSignalText() = runTest {
        val latest by underTest.contentDescription.collectLastValue()

        repository.inflateSignalStrength.setValue(true)
        repository.numberOfLevels.setValue(6)
        repository.setAllLevels(-2)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))

        repository.setAllLevels(100)

        assertThat(latest as MobileContentDescription.Cellular)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))
    }

    @Test
    fun contentDescription_inflated_levelStrings() = runTest {
        val latest by underTest.contentDescription.collectLastValue()

        repository.inflateSignalStrength.setValue(true)
        repository.numberOfLevels.setValue(6)

        // Note that the _repo_ level is 1 lower than the reported level through the interactor

        repository.setAllLevels(0)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, ONE_BAR))

        repository.setAllLevels(1)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, TWO_BARS))

        repository.setAllLevels(2)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, THREE_BARS))

        repository.setAllLevels(3)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, FOUR_BARS))

        repository.setAllLevels(4)

        assertThat(latest)
            .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, FULL_BARS))
    }

    @Test
    fun contentDescription_nonInflated_testABunchOfLevelsForNull() = runTest {
        val latest by underTest.contentDescription.collectLastValue()

        repository.inflateSignalStrength.setValue(false)
        repository.numberOfLevels.setValue(5)

        // -1 and 5 are out of the bounds for non-inflated content descriptions
        for (i in -1..5) {
            repository.setAllLevels(i)
            when (i) {
                -1,
                5 ->
                    assertWithMessage("Level $i is expected to be null")
                        .that((latest as MobileContentDescription.Cellular).levelDescriptionRes)
                        .isEqualTo(NO_SIGNAL)
                else ->
                    assertWithMessage("Level $i is expected not to be null")
                        .that(latest)
                        .isNotNull()
            }
        }
    }

    @Test
    fun contentDescription_inflated_testABunchOfLevelsForNull() = runTest {
        val latest by underTest.contentDescription.collectLastValue()
        repository.inflateSignalStrength.setValue(true)
        repository.numberOfLevels.setValue(6)
        // -1 and 6 are out of the bounds for inflated content descriptions
        // Note that the interactor adds 1 to the reported level, hence the -2 to 5 range
        for (i in -2..5) {
            repository.setAllLevels(i)
            when (i) {
                -2,
                5 ->
                    assertWithMessage("Level $i is expected to be null")
                        .that((latest as MobileContentDescription.Cellular).levelDescriptionRes)
                        .isEqualTo(NO_SIGNAL)
                else ->
                    assertWithMessage("Level $i is not expected to be null")
                        .that(latest)
                        .isNotNull()
            }
        }
    }

    @Test
    fun networkType_dataEnabled_groupIsRepresented() = runTest {
        val expected =
            Icon.Resource(
                THREE_G.dataType,
                ContentDescription.Resource(THREE_G.dataContentDescription),
            )
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)

        val latest by underTest.networkTypeIcon.collectLastValue()

        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_null_whenDisabled() = runTest {
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        repository.dataEnabled.setValue(false)
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        val latest by underTest.networkTypeIcon.collectLastValue()

        assertThat(latest).isNull()
    }

    @Test
    fun networkType_null_whenCarrierNetworkChangeActive() = runTest {
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        repository.carrierNetworkChangeActive.setValue(true)
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        val latest by underTest.networkTypeIcon.collectLastValue()

        assertThat(latest).isNull()
    }

    @Test
    fun networkTypeIcon_notNull_whenEnabled() = runTest {
        val expected =
            Icon.Resource(
                THREE_G.dataType,
                ContentDescription.Resource(THREE_G.dataContentDescription),
            )
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        repository.dataEnabled.setValue(true)
        repository.dataConnectionState.setValue(DataConnectionState.Connected)
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        val latest by underTest.networkTypeIcon.collectLastValue()

        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_nullWhenDataDisconnects() = runTest {
        val initial =
            Icon.Resource(
                THREE_G.dataType,
                ContentDescription.Resource(THREE_G.dataContentDescription),
            )

        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        val latest by underTest.networkTypeIcon.collectLastValue()

        assertThat(latest).isEqualTo(initial)

        repository.dataConnectionState.setValue(DataConnectionState.Disconnected)

        assertThat(latest).isNull()
    }

    @Test
    fun networkType_null_changeToDisabled() = runTest {
        val expected =
            Icon.Resource(
                THREE_G.dataType,
                ContentDescription.Resource(THREE_G.dataContentDescription),
            )
        repository.dataEnabled.setValue(true)
        val latest by underTest.networkTypeIcon.collectLastValue()

        assertThat(latest).isEqualTo(expected)

        repository.dataEnabled.setValue(false)

        assertThat(latest).isNull()
    }

    @Test
    fun networkType_alwaysShow_shownEvenWhenDisabled() = runTest {
        repository.dataEnabled.setValue(false)

        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(
            MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }
        )

        val latest by underTest.networkTypeIcon.collectLastValue()

        val expected =
            Icon.Resource(
                THREE_G.dataType,
                ContentDescription.Resource(THREE_G.dataContentDescription),
            )
        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_alwaysShow_shownEvenWhenDisconnected() = runTest {
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        repository.dataConnectionState.setValue(DataConnectionState.Disconnected)

        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(
            MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }
        )

        val latest by underTest.networkTypeIcon.collectLastValue()

        val expected =
            Icon.Resource(
                THREE_G.dataType,
                ContentDescription.Resource(THREE_G.dataContentDescription),
            )
        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_alwaysShow_shownEvenWhenFailedConnection() = runTest {
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(
            MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }
        )

        val latest by underTest.networkTypeIcon.collectLastValue()

        val expected =
            Icon.Resource(
                THREE_G.dataType,
                ContentDescription.Resource(THREE_G.dataContentDescription),
            )
        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_alwaysShow_usesDefaultIconWhenInvalid() = runTest {
        // The UNKNOWN icon group doesn't have a valid data type icon ID, and the logic from the
        // old pipeline was to use the default icon group if the map doesn't exist
        repository.setNetworkTypeKey(UNKNOWN.name)
        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(
            MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }
        )

        val latest by underTest.networkTypeIcon.collectLastValue()

        val expected =
            Icon.Resource(
                kairos.transact {
                    mobileConnectionsRepositoryKairos.fake.defaultMobileIconGroup.sample().dataType
                },
                ContentDescription.Resource(G.dataContentDescription),
            )

        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_alwaysShow_shownWhenNotDefault() = runTest {
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(false)
        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(
            MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }
        )

        val latest by underTest.networkTypeIcon.collectLastValue()

        val expected =
            Icon.Resource(
                THREE_G.dataType,
                ContentDescription.Resource(THREE_G.dataContentDescription),
            )
        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_notShownWhenNotDefault() = runTest {
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        repository.dataConnectionState.setValue(DataConnectionState.Connected)
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(false)

        val latest by underTest.networkTypeIcon.collectLastValue()

        assertThat(latest).isNull()
    }

    @Test
    fun roaming() = runTest {
        repository.setAllRoaming(true)

        val latest by underTest.roaming.collectLastValue()

        assertThat(latest).isTrue()

        repository.setAllRoaming(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun dataActivity_nullWhenConfigIsOff() = runTest {
        constants.stub { on { shouldShowActivityConfig } doReturn false }

        val inVisible by underTest.activityInVisible.collectLastValue()

        val outVisible by underTest.activityInVisible.collectLastValue()

        val containerVisible by underTest.activityInVisible.collectLastValue()

        repository.dataActivityDirection.setValue(
            DataActivityModel(hasActivityIn = true, hasActivityOut = true)
        )

        assertThat(inVisible).isFalse()
        assertThat(outVisible).isFalse()
        assertThat(containerVisible).isFalse()
    }

    @Test
    @DisableFlags(FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS)
    fun dataActivity_configOn_testIndicators_staticFlagOff() = runTest {
        constants.stub { on { shouldShowActivityConfig } doReturn true }

        val inVisible by underTest.activityInVisible.collectLastValue()

        val outVisible by underTest.activityOutVisible.collectLastValue()

        val containerVisible by underTest.activityContainerVisible.collectLastValue()

        repository.dataActivityDirection.setValue(
            DataActivityModel(hasActivityIn = true, hasActivityOut = false)
        )

        yield()

        assertThat(inVisible).isTrue()
        assertThat(outVisible).isFalse()
        assertThat(containerVisible).isTrue()

        repository.dataActivityDirection.setValue(
            DataActivityModel(hasActivityIn = false, hasActivityOut = true)
        )

        assertThat(inVisible).isFalse()
        assertThat(outVisible).isTrue()
        assertThat(containerVisible).isTrue()

        repository.dataActivityDirection.setValue(
            DataActivityModel(hasActivityIn = false, hasActivityOut = false)
        )

        assertThat(inVisible).isFalse()
        assertThat(outVisible).isFalse()
        assertThat(containerVisible).isFalse()
    }

    @Test
    @EnableFlags(FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS)
    fun dataActivity_configOn_testIndicators_staticFlagOn() = runTest {
        constants.stub { on { shouldShowActivityConfig } doReturn true }

        val inVisible by underTest.activityInVisible.collectLastValue()

        val outVisible by underTest.activityOutVisible.collectLastValue()

        val containerVisible by underTest.activityContainerVisible.collectLastValue()

        repository.dataActivityDirection.setValue(
            DataActivityModel(hasActivityIn = true, hasActivityOut = false)
        )

        yield()

        assertThat(inVisible).isTrue()
        assertThat(outVisible).isFalse()
        assertThat(containerVisible).isTrue()

        repository.dataActivityDirection.setValue(
            DataActivityModel(hasActivityIn = false, hasActivityOut = true)
        )

        assertThat(inVisible).isFalse()
        assertThat(outVisible).isTrue()
        assertThat(containerVisible).isTrue()

        repository.dataActivityDirection.setValue(
            DataActivityModel(hasActivityIn = false, hasActivityOut = false)
        )

        assertThat(inVisible).isFalse()
        assertThat(outVisible).isFalse()
        assertThat(containerVisible).isTrue()
    }

    @Test
    fun netTypeBackground_nullWhenNoPrioritizedCapabilities() = runTest {
        val latest by underTest.networkTypeBackground.collectLastValue()

        repository.hasPrioritizedNetworkCapabilities.setValue(false)

        assertThat(latest).isNull()
    }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun netTypeBackground_sliceUiEnabled_notNullWhenPrioritizedCapabilities_newIcons() = runTest {
        val latest by underTest.networkTypeBackground.collectLastValue()

        repository.hasPrioritizedNetworkCapabilities.setValue(true)

        assertThat(latest)
            .isEqualTo(Icon.Resource(R.drawable.mobile_network_type_background_updated, null))
    }

    @Test
    @DisableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun netTypeBackground_sliceUiDisabled_notNullWhenPrioritizedCapabilities_oldIcons() = runTest {
        val latest by underTest.networkTypeBackground.collectLastValue()

        repository.allowNetworkSliceIndicator.setValue(true)
        repository.hasPrioritizedNetworkCapabilities.setValue(true)

        assertThat(latest).isEqualTo(Icon.Resource(R.drawable.mobile_network_type_background, null))
    }

    @Test
    fun nonTerrestrial_defaultProperties() = runTest {
        repository.isNonTerrestrial.setValue(true)

        val roaming by underTest.roaming.collectLastValue()
        val networkTypeIcon by underTest.networkTypeIcon.collectLastValue()
        val networkTypeBackground by underTest.networkTypeBackground.collectLastValue()
        val activityInVisible by underTest.activityInVisible.collectLastValue()
        val activityOutVisible by underTest.activityOutVisible.collectLastValue()
        val activityContainerVisible by underTest.activityContainerVisible.collectLastValue()

        assertThat(roaming).isFalse()
        assertThat(networkTypeIcon).isNull()
        assertThat(networkTypeBackground).isNull()
        assertThat(activityInVisible).isFalse()
        assertThat(activityOutVisible).isFalse()
        assertThat(activityContainerVisible).isFalse()
    }

    @Test
    fun nonTerrestrial_ignoresDefaultProperties() = runTest {
        repository.isNonTerrestrial.setValue(true)

        val roaming by underTest.roaming.collectLastValue()
        val networkTypeIcon by underTest.networkTypeIcon.collectLastValue()
        val networkTypeBackground by underTest.networkTypeBackground.collectLastValue()
        val activityInVisible by underTest.activityInVisible.collectLastValue()
        val activityOutVisible by underTest.activityOutVisible.collectLastValue()
        val activityContainerVisible by underTest.activityContainerVisible.collectLastValue()

        repository.setAllRoaming(true)
        repository.setNetworkTypeKey(mobileConnectionsRepositoryKairos.fake.LTE_KEY)
        // sets the background on cellular
        repository.hasPrioritizedNetworkCapabilities.setValue(true)
        repository.dataActivityDirection.setValue(
            DataActivityModel(hasActivityIn = true, hasActivityOut = true)
        )

        assertThat(roaming).isFalse()
        assertThat(networkTypeIcon).isNull()
        assertThat(networkTypeBackground).isNull()
        assertThat(activityInVisible).isFalse()
        assertThat(activityOutVisible).isFalse()
        assertThat(activityContainerVisible).isFalse()
    }

    @DisableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    fun nonTerrestrial_usesSatelliteIcon_flagOff() = runTest {
        repository.isNonTerrestrial.setValue(true)
        repository.setAllLevels(0)
        repository.satelliteLevel.setValue(0)

        val latest by underTest.icon.map { it as SignalIconModel.Satellite }.collectLastValue()

        // Level 0 -> no connection
        assertThat(latest).isNotNull()
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_0)

        // 1-2 -> 1 bar
        repository.setAllLevels(1)
        repository.satelliteLevel.setValue(1)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_1)

        repository.setAllLevels(2)
        repository.satelliteLevel.setValue(2)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_1)

        // 3-4 -> 2 bars
        repository.setAllLevels(3)
        repository.satelliteLevel.setValue(3)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_2)

        repository.setAllLevels(4)
        repository.satelliteLevel.setValue(4)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_2)
    }

    @EnableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    fun nonTerrestrial_usesSatelliteIcon_flagOn() = runTest {
        repository.isNonTerrestrial.setValue(true)
        repository.satelliteLevel.setValue(0)

        val latest by underTest.icon.map { it as SignalIconModel.Satellite }.collectLastValue()

        // Level 0 -> no connection
        assertThat(latest).isNotNull()
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_0)

        // 1-2 -> 1 bar
        repository.satelliteLevel.setValue(1)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_1)

        repository.satelliteLevel.setValue(2)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_1)

        // 3-4 -> 2 bars
        repository.satelliteLevel.setValue(3)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_2)

        repository.satelliteLevel.setValue(4)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_2)
    }

    @DisableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    fun satelliteIcon_ignoresInflateSignalStrength_flagOff() = runTest {
        // Note that this is the exact same test as above, but with inflateSignalStrength set to
        // true we note that the level is unaffected by inflation
        repository.inflateSignalStrength.setValue(true)
        repository.isNonTerrestrial.setValue(true)
        repository.setAllLevels(0)
        repository.satelliteLevel.setValue(0)

        val latest by underTest.icon.map { it as SignalIconModel.Satellite }.collectLastValue()

        // Level 0 -> no connection
        assertThat(latest).isNotNull()
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_0)

        // 1-2 -> 1 bar
        repository.setAllLevels(1)
        repository.satelliteLevel.setValue(1)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_1)

        repository.setAllLevels(2)
        repository.satelliteLevel.setValue(2)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_1)

        // 3-4 -> 2 bars
        repository.setAllLevels(3)
        repository.satelliteLevel.setValue(3)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_2)

        repository.setAllLevels(4)
        repository.satelliteLevel.setValue(4)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_2)
    }

    @EnableFlags(com.android.internal.telephony.flags.Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    @Test
    fun satelliteIcon_ignoresInflateSignalStrength_flagOn() = runTest {
        // Note that this is the exact same test as above, but with inflateSignalStrength set to
        // true we note that the level is unaffected by inflation
        repository.inflateSignalStrength.setValue(true)
        repository.isNonTerrestrial.setValue(true)
        repository.satelliteLevel.setValue(0)

        val latest by underTest.icon.map { it as SignalIconModel.Satellite }.collectLastValue()

        // Level 0 -> no connection
        assertThat(latest).isNotNull()
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_0)

        // 1-2 -> 1 bar
        repository.satelliteLevel.setValue(1)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_1)

        repository.satelliteLevel.setValue(2)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_1)

        // 3-4 -> 2 bars
        repository.satelliteLevel.setValue(3)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_2)

        repository.satelliteLevel.setValue(4)
        assertThat(latest!!.icon.res).isEqualTo(R.drawable.ic_satellite_connected_2)
    }

    companion object {
        private const val SUB_1_ID = 1

        // For convenience, just define these as constants
        private val NO_SIGNAL = R.string.accessibility_no_signal
        private val ONE_BAR = R.string.accessibility_one_bar
        private val TWO_BARS = R.string.accessibility_two_bars
        private val THREE_BARS = R.string.accessibility_three_bars
        private val FOUR_BARS = R.string.accessibility_four_bars
        private val FULL_BARS = R.string.accessibility_signal_full
    }
}
