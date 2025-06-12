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

import android.os.ParcelUuid
import android.platform.test.annotations.EnableFlags
import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import android.telephony.SubscriptionManager.PROFILE_CLASS_PROVISIONING
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.mobile.MobileMappings
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlot
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.testKosmos
import com.android.systemui.util.carrierConfigTracker
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileIconsInteractorKairosTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            mobileConnectionsRepositoryKairos =
                fakeMobileConnectionsRepositoryKairos.apply {
                    setActiveMobileDataSubscriptionId(SUB_1_ID)
                    subscriptions.setValue(listOf(SUB_1, SUB_2, SUB_3_OPP, SUB_4_OPP))
                }
            featureFlagsClassic.fake.set(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS, true)
        }

    private val Kosmos.underTest
        get() = mobileIconsInteractorKairos

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun filteredSubscriptions_default() = runTest {
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(emptyList())
        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(emptyList<SubscriptionModel>())
    }

    // Based on the logic from the old pipeline, we'll never filter subs when there are more than 2
    @Test
    fun filteredSubscriptions_moreThanTwo_doesNotFilter() = runTest {
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(
            listOf(SUB_1, SUB_3_OPP, SUB_4_OPP)
        )
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_4_ID)

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(SUB_1, SUB_3_OPP, SUB_4_OPP))
    }

    @Test
    fun filteredSubscriptions_nonOpportunistic_updatesWithMultipleSubs() = runTest {
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1, SUB_2))

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(SUB_1, SUB_2))
    }

    @Test
    fun filteredSubscriptions_opportunistic_differentGroups_doesNotFilter() = runTest {
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_3_OPP, SUB_4_OPP))
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_3_ID)

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(SUB_3_OPP, SUB_4_OPP))
    }

    @Test
    fun filteredSubscriptions_opportunistic_nonGrouped_doesNotFilter() = runTest {
        val (sub1, sub2) =
            createSubscriptionPair(
                subscriptionIds = Pair(SUB_1_ID, SUB_2_ID),
                opportunistic = Pair(true, true),
                grouped = false,
            )
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1, sub2))
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_1_ID)

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(sub1, sub2))
    }

    @Test
    fun filteredSubscriptions_opportunistic_grouped_configFalse_showsActive_3() = runTest {
        val (sub3, sub4) =
            createSubscriptionPair(
                subscriptionIds = Pair(SUB_3_ID, SUB_4_ID),
                opportunistic = Pair(true, true),
                grouped = true,
            )
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub3, sub4))
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_3_ID)
        whenever(carrierConfigTracker.alwaysShowPrimarySignalBarInOpportunisticNetworkDefault)
            .thenReturn(false)

        val latest by underTest.filteredSubscriptions.collectLastValue()

        // Filtered subscriptions should show the active one when the config is false
        assertThat(latest).isEqualTo(listOf(sub3))
    }

    @Test
    fun filteredSubscriptions_opportunistic_grouped_configFalse_showsActive_4() = runTest {
        val (sub3, sub4) =
            createSubscriptionPair(
                subscriptionIds = Pair(SUB_3_ID, SUB_4_ID),
                opportunistic = Pair(true, true),
                grouped = true,
            )
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub3, sub4))
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_4_ID)
        whenever(carrierConfigTracker.alwaysShowPrimarySignalBarInOpportunisticNetworkDefault)
            .thenReturn(false)

        val latest by underTest.filteredSubscriptions.collectLastValue()

        // Filtered subscriptions should show the active one when the config is false
        assertThat(latest).isEqualTo(listOf(sub4))
    }

    @Test
    fun filteredSubscriptions_oneOpportunistic_grouped_configTrue_showsPrimary_active_1() =
        runTest {
            val (sub1, sub3) =
                createSubscriptionPair(
                    subscriptionIds = Pair(SUB_1_ID, SUB_3_ID),
                    opportunistic = Pair(false, true),
                    grouped = true,
                )
            mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1, sub3))
            mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_1_ID)
            whenever(carrierConfigTracker.alwaysShowPrimarySignalBarInOpportunisticNetworkDefault)
                .thenReturn(true)

            val latest by underTest.filteredSubscriptions.collectLastValue()

            // Filtered subscriptions should show the primary (non-opportunistic) if the config is
            // true
            assertThat(latest).isEqualTo(listOf(sub1))
        }

    @Test
    fun filteredSubscriptions_oneOpportunistic_grouped_configTrue_showsPrimary_nonActive_1() =
        runTest {
            val (sub1, sub3) =
                createSubscriptionPair(
                    subscriptionIds = Pair(SUB_1_ID, SUB_3_ID),
                    opportunistic = Pair(false, true),
                    grouped = true,
                )
            mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1, sub3))
            mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_3_ID)
            whenever(carrierConfigTracker.alwaysShowPrimarySignalBarInOpportunisticNetworkDefault)
                .thenReturn(true)

            val latest by underTest.filteredSubscriptions.collectLastValue()

            // Filtered subscriptions should show the primary (non-opportunistic) if the config is
            // true
            assertThat(latest).isEqualTo(listOf(sub1))
        }

    @Test
    fun filteredSubscriptions_vcnSubId_agreesWithActiveSubId_usesActiveAkaVcnSub() = runTest {
        val (sub1, sub3) =
            createSubscriptionPair(
                subscriptionIds = Pair(SUB_1_ID, SUB_3_ID),
                opportunistic = Pair(true, true),
                grouped = true,
            )
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1, sub3))
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_3_ID)
        kosmos.connectivityRepository.fake.vcnSubId.value = SUB_3_ID
        whenever(carrierConfigTracker.alwaysShowPrimarySignalBarInOpportunisticNetworkDefault)
            .thenReturn(false)

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(sub3))
    }

    @Test
    fun filteredSubscriptions_vcnSubId_disagreesWithActiveSubId_usesVcnSub() = runTest {
        val (sub1, sub3) =
            createSubscriptionPair(
                subscriptionIds = Pair(SUB_1_ID, SUB_3_ID),
                opportunistic = Pair(true, true),
                grouped = true,
            )
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1, sub3))
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_3_ID)
        kosmos.connectivityRepository.fake.vcnSubId.value = SUB_1_ID
        whenever(carrierConfigTracker.alwaysShowPrimarySignalBarInOpportunisticNetworkDefault)
            .thenReturn(false)

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(sub1))
    }

    @Test
    fun filteredSubscriptions_doesNotFilterProvisioningWhenFlagIsFalse() = runTest {
        // GIVEN the flag is false
        featureFlagsClassic.fake.set(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS, false)

        // GIVEN 1 sub that is in PROFILE_CLASS_PROVISIONING
        val sub1 =
            SubscriptionModel(
                subscriptionId = SUB_1_ID,
                isOpportunistic = false,
                carrierName = "Carrier 1",
                profileClass = PROFILE_CLASS_PROVISIONING,
            )

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1))

        // WHEN filtering is applied
        val latest by underTest.filteredSubscriptions.collectLastValue()

        // THEN the provisioning sub is still present (unfiltered)
        assertThat(latest).isEqualTo(listOf(sub1))
    }

    @Test
    fun filteredSubscriptions_filtersOutProvisioningSubs() = runTest {
        val sub1 =
            SubscriptionModel(
                subscriptionId = SUB_1_ID,
                isOpportunistic = false,
                carrierName = "Carrier 1",
                profileClass = PROFILE_CLASS_UNSET,
            )
        val sub2 =
            SubscriptionModel(
                subscriptionId = SUB_2_ID,
                isOpportunistic = false,
                carrierName = "Carrier 2",
                profileClass = PROFILE_CLASS_PROVISIONING,
            )

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1, sub2))

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(sub1))
    }

    /** Note: I'm not sure if this will ever be the case, but we can test it at least */
    @Test
    fun filteredSubscriptions_filtersOutProvisioningSubsBeforeOpportunistic() = runTest {
        // This is a contrived test case, where the active subId is the one that would
        // also be filtered by opportunistic filtering.

        // GIVEN grouped, opportunistic subscriptions
        val groupUuid = ParcelUuid(UUID.randomUUID())
        val sub1 =
            SubscriptionModel(
                subscriptionId = 1,
                isOpportunistic = true,
                groupUuid = groupUuid,
                carrierName = "Carrier 1",
                profileClass = PROFILE_CLASS_PROVISIONING,
            )

        val sub2 =
            SubscriptionModel(
                subscriptionId = 2,
                isOpportunistic = true,
                groupUuid = groupUuid,
                carrierName = "Carrier 2",
                profileClass = PROFILE_CLASS_UNSET,
            )

        // GIVEN active subId is 1
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1, sub2))
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(1)

        // THEN filtering of provisioning subs takes place first, and we result in sub2

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(sub2))
    }

    @Test
    fun filteredSubscriptions_groupedPairAndNonProvisioned_groupedFilteringStillHappens() =
        runTest {
            // Grouped filtering only happens when the list of subs is length 2. In this case
            // we'll show that filtering of provisioning subs happens before, and thus grouped
            // filtering happens even though the unfiltered list is length 3
            val (sub1, sub3) =
                createSubscriptionPair(
                    subscriptionIds = Pair(SUB_1_ID, SUB_3_ID),
                    opportunistic = Pair(true, true),
                    grouped = true,
                )

            val sub2 =
                SubscriptionModel(
                    subscriptionId = 2,
                    isOpportunistic = true,
                    groupUuid = null,
                    carrierName = "Carrier 2",
                    profileClass = PROFILE_CLASS_PROVISIONING,
                )

            mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(sub1, sub2, sub3))
            mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(1)

            val latest by underTest.filteredSubscriptions.collectLastValue()

            assertThat(latest).isEqualTo(listOf(sub1))
        }

    @Test
    fun filteredSubscriptions_subNotExclusivelyNonTerrestrial_hasSub() = runTest {
        val notExclusivelyNonTerrestrialSub =
            SubscriptionModel(
                isExclusivelyNonTerrestrial = false,
                subscriptionId = 5,
                carrierName = "Carrier 5",
                profileClass = PROFILE_CLASS_UNSET,
            )

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(
            listOf(notExclusivelyNonTerrestrialSub)
        )

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(notExclusivelyNonTerrestrialSub))
    }

    @Test
    fun filteredSubscriptions_subExclusivelyNonTerrestrial_doesNotHaveSub() = runTest {
        val exclusivelyNonTerrestrialSub =
            SubscriptionModel(
                isExclusivelyNonTerrestrial = true,
                subscriptionId = 5,
                carrierName = "Carrier 5",
                profileClass = PROFILE_CLASS_UNSET,
            )

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(
            listOf(exclusivelyNonTerrestrialSub)
        )

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEmpty()
    }

    @Test
    fun filteredSubscription_mixOfExclusivelyNonTerrestrialAndOther_hasOtherSubsOnly() = runTest {
        val exclusivelyNonTerrestrialSub =
            SubscriptionModel(
                isExclusivelyNonTerrestrial = true,
                subscriptionId = 5,
                carrierName = "Carrier 5",
                profileClass = PROFILE_CLASS_UNSET,
            )
        val otherSub1 =
            SubscriptionModel(
                isExclusivelyNonTerrestrial = false,
                subscriptionId = 1,
                carrierName = "Carrier 1",
                profileClass = PROFILE_CLASS_UNSET,
            )
        val otherSub2 =
            SubscriptionModel(
                isExclusivelyNonTerrestrial = false,
                subscriptionId = 2,
                carrierName = "Carrier 2",
                profileClass = PROFILE_CLASS_UNSET,
            )

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(
            listOf(otherSub1, exclusivelyNonTerrestrialSub, otherSub2)
        )

        val latest by underTest.filteredSubscriptions.collectLastValue()

        assertThat(latest).isEqualTo(listOf(otherSub1, otherSub2))
    }

    @Test
    fun filteredSubscriptions_exclusivelyNonTerrestrialSub_andOpportunistic_bothFiltersHappen() =
        runTest {
            // Exclusively non-terrestrial sub
            val exclusivelyNonTerrestrialSub =
                SubscriptionModel(
                    isExclusivelyNonTerrestrial = true,
                    subscriptionId = 5,
                    carrierName = "Carrier 5",
                    profileClass = PROFILE_CLASS_UNSET,
                )

            // Opportunistic subs
            val (sub3, sub4) =
                createSubscriptionPair(
                    subscriptionIds = Pair(SUB_3_ID, SUB_4_ID),
                    opportunistic = Pair(true, true),
                    grouped = true,
                )

            // WHEN both an exclusively non-terrestrial sub and opportunistic sub pair is included
            mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(
                listOf(sub3, sub4, exclusivelyNonTerrestrialSub)
            )
            mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(SUB_3_ID)

            val latest by underTest.filteredSubscriptions.collectLastValue()

            // THEN both the only-non-terrestrial sub and the non-active sub are filtered out,
            // leaving only sub3.
            assertThat(latest).isEqualTo(listOf(sub3))
        }

    @Test
    fun activeDataConnection_turnedOn() = runTest {
        val connection1 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId.sample()[SUB_1_ID]!!

        connection1.dataEnabled.setValue(true)

        val latest by underTest.activeDataConnectionHasDataEnabled.collectLastValue()

        assertThat(latest).isTrue()
    }

    @Test
    fun activeDataConnection_turnedOff() = runTest {
        val connection1 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId.sample()[SUB_1_ID]!!

        connection1.dataEnabled.setValue(true)
        val latest by underTest.activeDataConnectionHasDataEnabled.collectLastValue()

        connection1.dataEnabled.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun activeDataConnection_invalidSubId() = runTest {
        val latest by underTest.activeDataConnectionHasDataEnabled.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(
            INVALID_SUBSCRIPTION_ID
        )

        // An invalid active subId should tell us that data is off
        assertThat(latest).isFalse()
    }

    @Test
    fun failedConnection_default_validated_notFailed() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(true)

        assertThat(latest).isFalse()
    }

    @Test
    fun failedConnection_notDefault_notValidated_notFailed() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(false)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun failedConnection_default_notValidated_failed() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)

        assertThat(latest).isTrue()
    }

    @Test
    fun failedConnection_carrierMergedDefault_notValidated_failed() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.hasCarrierMergedConnection.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)

        assertThat(latest).isTrue()
    }

    /** Regression test for b/275076959. */
    @Test
    fun failedConnection_dataSwitchInSameGroup_notFailed() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(true)
        runCurrent()

        // WHEN there's a data change in the same subscription group
        mobileConnectionsRepositoryKairos.fake.activeSubChangedInGroupEvent.emit(Unit)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)
        runCurrent()

        // THEN the default connection is *not* marked as failed because of forced validation
        assertThat(latest).isFalse()
    }

    @Test
    fun failedConnection_dataSwitchNotInSameGroup_isFailed() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(true)
        runCurrent()

        // WHEN the connection is invalidated without a activeSubChangedInGroupEvent
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)

        // THEN the connection is immediately marked as failed
        assertThat(latest).isTrue()
    }

    @Test
    fun alwaysShowDataRatIcon_configHasTrue() = runTest {
        val latest by underTest.alwaysShowDataRatIcon.collectLastValue()

        val config = MobileMappings.Config()
        config.alwaysShowDataRatIcon = true
        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(config)

        assertThat(latest).isTrue()
    }

    @Test
    fun alwaysShowDataRatIcon_configHasFalse() = runTest {
        val latest by underTest.alwaysShowDataRatIcon.collectLastValue()

        val config = MobileMappings.Config()
        config.alwaysShowDataRatIcon = false
        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(config)

        assertThat(latest).isFalse()
    }

    @Test
    fun alwaysUseCdmaLevel_configHasTrue() = runTest {
        val latest by underTest.alwaysUseCdmaLevel.collectLastValue()

        val config = MobileMappings.Config()
        config.alwaysShowCdmaRssi = true
        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(config)

        assertThat(latest).isTrue()
    }

    @Test
    fun alwaysUseCdmaLevel_configHasFalse() = runTest {
        val latest by underTest.alwaysUseCdmaLevel.collectLastValue()

        val config = MobileMappings.Config()
        config.alwaysShowCdmaRssi = false
        mobileConnectionsRepositoryKairos.fake.defaultDataSubRatConfig.setValue(config)

        assertThat(latest).isFalse()
    }

    @Test
    fun isSingleCarrier_zeroSubscriptions_false() = runTest {
        val latest by underTest.isSingleCarrier.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(emptyList())

        assertThat(latest).isFalse()
    }

    @Test
    fun isSingleCarrier_oneSubscription_true() = runTest {
        val latest by underTest.isSingleCarrier.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1))

        assertThat(latest).isTrue()
    }

    @Test
    fun isSingleCarrier_twoSubscriptions_false() = runTest {
        val latest by underTest.isSingleCarrier.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1, SUB_2))

        assertThat(latest).isFalse()
    }

    @Test
    fun isSingleCarrier_updates() = runTest {
        val latest by underTest.isSingleCarrier.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1))
        assertThat(latest).isTrue()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1, SUB_2))
        assertThat(latest).isFalse()
    }

    @Test
    fun mobileIsDefault_mobileFalseAndCarrierMergedFalse_false() = runTest {
        val latest by underTest.mobileIsDefault.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(false)
        mobileConnectionsRepositoryKairos.fake.hasCarrierMergedConnection.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    fun mobileIsDefault_mobileTrueAndCarrierMergedFalse_true() = runTest {
        val latest by underTest.mobileIsDefault.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.hasCarrierMergedConnection.setValue(false)

        assertThat(latest).isTrue()
    }

    /** Regression test for b/272586234. */
    @Test
    fun mobileIsDefault_mobileFalseAndCarrierMergedTrue_true() = runTest {
        val latest by underTest.mobileIsDefault.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(false)
        mobileConnectionsRepositoryKairos.fake.hasCarrierMergedConnection.setValue(true)

        assertThat(latest).isTrue()
    }

    @Test
    fun mobileIsDefault_updatesWhenRepoUpdates() = runTest {
        val latest by underTest.mobileIsDefault.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        assertThat(latest).isTrue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(false)
        assertThat(latest).isFalse()

        mobileConnectionsRepositoryKairos.fake.hasCarrierMergedConnection.setValue(true)
        assertThat(latest).isTrue()
    }

    // The data switch tests are mostly testing the [forcingCellularValidation] flow, but that flow
    // is private and can only be tested by looking at [isDefaultConnectionFailed].

    @Test
    fun dataSwitch_inSameGroup_validatedMatchesPreviousValue_expiresAfter2s() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(true)
        runCurrent()

        // Trigger a data change in the same subscription group that's not yet validated
        mobileConnectionsRepositoryKairos.fake.activeSubChangedInGroupEvent.emit(Unit)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)
        runCurrent()

        // After 1s, the force validation bit is still present, so the connection is not marked
        // as failed
        testScope.advanceTimeBy(1000)
        assertThat(latest).isFalse()

        // After 2s, the force validation expires so the connection updates to failed
        testScope.advanceTimeBy(1001)
        assertThat(latest).isTrue()
    }

    @Test
    fun dataSwitch_inSameGroup_notValidated_immediatelyMarkedAsFailed() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)
        runCurrent()

        mobileConnectionsRepositoryKairos.fake.activeSubChangedInGroupEvent.emit(Unit)

        assertThat(latest).isTrue()
    }

    @Test
    fun dataSwitch_loseValidation_thenSwitchHappens_clearsForcedBit() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()

        // GIVEN the network starts validated
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(true)
        runCurrent()

        // WHEN a data change happens in the same group
        mobileConnectionsRepositoryKairos.fake.activeSubChangedInGroupEvent.emit(Unit)

        // WHEN the validation bit is lost
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)
        runCurrent()

        // WHEN another data change happens in the same group
        mobileConnectionsRepositoryKairos.fake.activeSubChangedInGroupEvent.emit(Unit)

        // THEN the forced validation bit is still used...
        assertThat(latest).isFalse()

        testScope.advanceTimeBy(1000)
        assertThat(latest).isFalse()

        // ... but expires after 2s
        testScope.advanceTimeBy(1001)
        assertThat(latest).isTrue()
    }

    @Test
    fun dataSwitch_whileAlreadyForcingValidation_resetsClock() = runTest {
        val latest by underTest.isDefaultConnectionFailed.collectLastValue()
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(true)
        runCurrent()

        mobileConnectionsRepositoryKairos.fake.activeSubChangedInGroupEvent.emit(Unit)

        testScope.advanceTimeBy(1000)

        // WHEN another change in same group event happens
        mobileConnectionsRepositoryKairos.fake.activeSubChangedInGroupEvent.emit(Unit)
        mobileConnectionsRepositoryKairos.fake.defaultConnectionIsValidated.setValue(false)
        runCurrent()

        // THEN the forced validation remains for exactly 2 more seconds from now

        // 1.500s from second event
        testScope.advanceTimeBy(1500)
        assertThat(latest).isFalse()

        // 2.001s from the second event
        testScope.advanceTimeBy(501)
        assertThat(latest).isTrue()
    }

    @Test
    fun isForceHidden_repoHasMobileHidden_true() = runTest {
        val latest by underTest.isForceHidden.collectLastValue()

        kosmos.connectivityRepository.fake.setForceHiddenIcons(setOf(ConnectivitySlot.MOBILE))

        assertThat(latest).isTrue()
    }

    @Test
    fun isForceHidden_repoDoesNotHaveMobileHidden_false() = runTest {
        val latest by underTest.isForceHidden.collectLastValue()

        kosmos.connectivityRepository.fake.setForceHiddenIcons(setOf(ConnectivitySlot.WIFI))

        assertThat(latest).isFalse()
    }

    @Test
    fun deviceBasedEmergencyMode_emergencyCallsOnly_followsDeviceServiceStateFromRepo() = runTest {
        val latest by underTest.isDeviceInEmergencyCallsOnlyMode.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.isDeviceEmergencyCallCapable.setValue(true)

        assertThat(latest).isTrue()

        mobileConnectionsRepositoryKairos.fake.isDeviceEmergencyCallCapable.setValue(false)

        assertThat(latest).isFalse()
    }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun isStackable_tracksNumberOfSubscriptions() = runTest {
        val latest by underTest.isStackable.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1))
        assertThat(latest).isFalse()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1, SUB_2))
        assertThat(latest).isTrue()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(
            listOf(SUB_1, SUB_2, SUB_3_OPP)
        )
        assertThat(latest).isFalse()
    }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun isStackable_checksForTerrestrialConnections() = runTest {
        val latest by underTest.isStackable.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1, SUB_2))
        setNumberOfLevelsForSubId(SUB_1_ID, 5)
        setNumberOfLevelsForSubId(SUB_2_ID, 5)
        assertThat(latest).isTrue()

        mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
            .sample()[SUB_1_ID]!!
            .isNonTerrestrial
            .setValue(true)

        assertThat(latest).isFalse()
    }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun isStackable_checksForNumberOfBars() = runTest {
        val latest by underTest.isStackable.collectLastValue()

        // Number of levels is the same for both
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(listOf(SUB_1, SUB_2))
        setNumberOfLevelsForSubId(SUB_1_ID, 5)
        setNumberOfLevelsForSubId(SUB_2_ID, 5)

        assertThat(latest).isTrue()

        // Change the number of levels to be different than SUB_2
        setNumberOfLevelsForSubId(SUB_1_ID, 6)

        assertThat(latest).isFalse()
    }

    private suspend fun KairosTestScope.setNumberOfLevelsForSubId(subId: Int, numberOfLevels: Int) {
        mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
            .sample()[subId]!!
            .numberOfLevels
            .setValue(numberOfLevels)
    }

    /**
     * Convenience method for creating a pair of subscriptions to test the filteredSubscriptions
     * flow.
     */
    private fun createSubscriptionPair(
        subscriptionIds: Pair<Int, Int>,
        opportunistic: Pair<Boolean, Boolean> = Pair(false, false),
        grouped: Boolean = false,
    ): Pair<SubscriptionModel, SubscriptionModel> {
        val groupUuid = if (grouped) ParcelUuid(UUID.randomUUID()) else null
        val sub1 =
            SubscriptionModel(
                subscriptionId = subscriptionIds.first,
                isOpportunistic = opportunistic.first,
                groupUuid = groupUuid,
                carrierName = "Carrier ${subscriptionIds.first}",
                profileClass = PROFILE_CLASS_UNSET,
            )

        val sub2 =
            SubscriptionModel(
                subscriptionId = subscriptionIds.second,
                isOpportunistic = opportunistic.second,
                groupUuid = groupUuid,
                carrierName = "Carrier ${opportunistic.second}",
                profileClass = PROFILE_CLASS_UNSET,
            )

        return Pair(sub1, sub2)
    }

    companion object {

        private const val SUB_1_ID = 1
        private val SUB_1 =
            SubscriptionModel(
                subscriptionId = SUB_1_ID,
                carrierName = "Carrier $SUB_1_ID",
                profileClass = PROFILE_CLASS_UNSET,
            )

        private const val SUB_2_ID = 2
        private val SUB_2 =
            SubscriptionModel(
                subscriptionId = SUB_2_ID,
                carrierName = "Carrier $SUB_2_ID",
                profileClass = PROFILE_CLASS_UNSET,
            )

        private const val SUB_3_ID = 3
        private val SUB_3_OPP =
            SubscriptionModel(
                subscriptionId = SUB_3_ID,
                isOpportunistic = true,
                groupUuid = ParcelUuid(UUID.randomUUID()),
                carrierName = "Carrier $SUB_3_ID",
                profileClass = PROFILE_CLASS_UNSET,
            )

        private const val SUB_4_ID = 4
        private val SUB_4_OPP =
            SubscriptionModel(
                subscriptionId = SUB_4_ID,
                isOpportunistic = true,
                groupUuid = ParcelUuid(UUID.randomUUID()),
                carrierName = "Carrier $SUB_4_ID",
                profileClass = PROFILE_CLASS_UNSET,
            )
    }
}
