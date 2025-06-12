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

import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType.DefaultNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType.UnknownNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepositoryKairos
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileIconsViewModelKairosTest : SysuiTestCase() {

    private val Kosmos.underTest
        get() = mobileIconsViewModelKairos

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            featureFlagsClassic.fake.apply {
                setDefault(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS)
            }
            mobileConnectionsRepositoryKairos =
                fakeMobileConnectionsRepositoryKairos.apply {
                    val subList = listOf(SUB_1, SUB_2)
                    setActiveMobileDataSubscriptionId(SUB_1.subscriptionId)
                    subscriptions.setValue(subList)
                }
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    private fun KairosTestScope.setSubscriptions(
        subList: List<SubscriptionModel>,
        activeSubId: Int = subList.getOrNull(0)?.subscriptionId ?: INVALID_SUBSCRIPTION_ID,
    ) {
        println("setSubscriptions: mobileConnectionsRepositoryKairos.fake.subscriptions")
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(subList)
        println(
            "setSubscriptions: mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId"
        )
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(activeSubId)
    }

    @Test
    fun subscriptionIdsFlow_matchesInteractor() = runTest {
        val latest by underTest.subscriptionIds.collectLastValue()
        setSubscriptions(
            listOf(
                SubscriptionModel(
                    subscriptionId = 1,
                    isOpportunistic = false,
                    carrierName = "Carrier 1",
                    profileClass = PROFILE_CLASS_UNSET,
                )
            )
        )
        assertThat(latest).isEqualTo(listOf(1))

        setSubscriptions(
            listOf(
                SubscriptionModel(
                    subscriptionId = 2,
                    isOpportunistic = false,
                    carrierName = "Carrier 2",
                    profileClass = PROFILE_CLASS_UNSET,
                ),
                SubscriptionModel(
                    subscriptionId = 5,
                    isOpportunistic = true,
                    carrierName = "Carrier 5",
                    profileClass = PROFILE_CLASS_UNSET,
                ),
                SubscriptionModel(
                    subscriptionId = 7,
                    isOpportunistic = true,
                    carrierName = "Carrier 7",
                    profileClass = PROFILE_CLASS_UNSET,
                ),
            )
        )
        assertThat(latest).isEqualTo(listOf(2, 5, 7))

        setSubscriptions(emptyList())
        assertThat(latest).isEmpty()
    }

    @Test
    fun firstMobileSubShowingNetworkTypeIcon_noSubs_false() = runTest {
        val latest by underTest.firstMobileSubShowingNetworkTypeIcon.collectLastValue()

        setSubscriptions(emptyList())

        assertThat(latest).isEqualTo(false)
    }

    @Test
    fun firstMobileSubShowingNetworkTypeIcon_oneSub_notShowingRat_false() = runTest {
        val latest by underTest.firstMobileSubShowingNetworkTypeIcon.collectLastValue()

        setSubscriptions(listOf(SUB_1))

        // The unknown icon group doesn't show a RAT
        mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
            .sample()[SUB_1.subscriptionId]
            ?.resolvedNetworkType
            ?.setValue(UnknownNetworkType)

        assertThat(latest).isFalse()
    }

    @Test
    fun firstMobileSubShowingNetworkTypeIcon_oneSub_showingRat_true() = runTest {
        val latest by underTest.firstMobileSubShowingNetworkTypeIcon.collectLastValue()
        setSubscriptions(listOf(SUB_1))

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)

        // The 3G icon group will show a RAT
        val repo =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_1.subscriptionId]!!

        repo.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        )
        repo.dataConnectionState.setValue(DataConnectionState.Connected)

        assertThat(latest).isEqualTo(true)
    }

    @Test
    fun firstMobileSubShowingNetworkTypeIcon_updatesAsSubUpdates() = runTest {
        val latest by underTest.firstMobileSubShowingNetworkTypeIcon.collectLastValue()
        setSubscriptions(listOf(SUB_1))

        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)

        val repo =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_1.subscriptionId]!!

        repo.dataConnectionState.setValue(DataConnectionState.Connected)

        repo.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        )
        assertThat(latest).isEqualTo(true)

        mobileConnectionsRepositoryKairos.fake.defaultMobileIconGroup.setValue(
            TelephonyIcons.UNKNOWN
        )

        repo.resolvedNetworkType.setValue(UnknownNetworkType)
        assertThat(latest).isEqualTo(false)

        repo.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileConnectionsRepositoryKairos.fake.LTE_KEY)
        )
        assertThat(latest).isEqualTo(true)
    }

    @Test
    fun firstMobileSubShowingNetworkTypeIcon_multipleSubs_lastSubNotShowingRat_false() = runTest {
        val latest by underTest.firstMobileSubShowingNetworkTypeIcon.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.defaultMobileIconGroup.setValue(
            TelephonyIcons.UNKNOWN
        )
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)

        val repo1 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_1.subscriptionId]!!

        repo1.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        )

        val repo2 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_2.subscriptionId]!!

        repo2.resolvedNetworkType.setValue(UnknownNetworkType)

        assertThat(latest).isFalse()
    }

    @Test
    fun firstMobileSubShowingNetworkTypeIcon_multipleSubs_lastSubShowingRat_true() = runTest {
        val latest by underTest.firstMobileSubShowingNetworkTypeIcon.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.defaultMobileIconGroup.setValue(
            TelephonyIcons.UNKNOWN
        )
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)

        val repo1 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_1.subscriptionId]!!

        repo1.dataConnectionState.setValue(DataConnectionState.Connected)
        repo1.resolvedNetworkType.setValue(UnknownNetworkType)

        val repo2 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_2.subscriptionId]!!

        repo2.dataConnectionState.setValue(DataConnectionState.Connected)
        repo2.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        )

        assertThat(latest).isEqualTo(true)
    }

    @Test
    fun firstMobileSubShowingNetworkTypeIcon_subListUpdates_valAlsoUpdates() = runTest {
        val latest by underTest.firstMobileSubShowingNetworkTypeIcon.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.defaultMobileIconGroup.setValue(
            TelephonyIcons.UNKNOWN
        )
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)

        val repo1 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_1.subscriptionId]!!

        repo1.dataConnectionState.setValue(DataConnectionState.Connected)
        repo1.resolvedNetworkType.setValue(UnknownNetworkType)

        val repo2 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_2.subscriptionId]!!

        repo2.dataConnectionState.setValue(DataConnectionState.Connected)
        repo2.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        )

        assertThat(latest).isEqualTo(true)

        // WHEN the sub list gets new subscriptions where the last subscription is not showing
        // the network type icon
        setSubscriptions(listOf(SUB_1, SUB_2, SUB_3))

        val repo3 =
            mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId
                .sample()[SUB_3.subscriptionId]!!

        repo3.dataConnectionState.setValue(DataConnectionState.Connected)
        repo3.resolvedNetworkType.setValue(UnknownNetworkType)

        // THEN the flow updates
        assertThat(latest).isEqualTo(false)
    }

    @Test
    fun firstMobileSubShowingNetworkTypeIcon_subListReorders_valAlsoUpdates() = runTest {
        val latest by underTest.firstMobileSubShowingNetworkTypeIcon.collectLastValue()

        mobileConnectionsRepositoryKairos.fake.defaultMobileIconGroup.setValue(
            TelephonyIcons.UNKNOWN
        )
        mobileConnectionsRepositoryKairos.fake.mobileIsDefault.setValue(true)

        setSubscriptions(listOf(SUB_1, SUB_2))
        // Immediately switch the order so that we've created both interactors
        setSubscriptions(listOf(SUB_2, SUB_1))

        val repos = mobileConnectionsRepositoryKairos.fake.mobileConnectionsBySubId.sample()
        val repo1 = repos[SUB_1.subscriptionId]!!
        repo1.dataConnectionState.setValue(DataConnectionState.Connected)

        val repo2 = repos[SUB_2.subscriptionId]!!
        repo2.dataConnectionState.setValue(DataConnectionState.Connected)

        setSubscriptions(listOf(SUB_1, SUB_2))
        repo1.resolvedNetworkType.setValue(UnknownNetworkType)
        repo2.resolvedNetworkType.setValue(
            DefaultNetworkType(mobileConnectionsRepositoryKairos.fake.GSM_KEY)
        )

        assertThat(latest).isEqualTo(true)

        // WHEN sub1 becomes last and sub1 has no network type icon
        setSubscriptions(listOf(SUB_2, SUB_1))

        // THEN the flow updates
        assertThat(latest).isEqualTo(false)

        // WHEN sub2 becomes last and sub2 has a network type icon
        setSubscriptions(listOf(SUB_1, SUB_2))

        // THEN the flow updates
        assertThat(latest).isEqualTo(true)
    }

    companion object {
        private val SUB_1 =
            SubscriptionModel(
                subscriptionId = 1,
                isOpportunistic = false,
                carrierName = "Carrier 1",
                profileClass = PROFILE_CLASS_UNSET,
            )
        private val SUB_2 =
            SubscriptionModel(
                subscriptionId = 2,
                isOpportunistic = false,
                carrierName = "Carrier 2",
                profileClass = PROFILE_CLASS_UNSET,
            )
        private val SUB_3 =
            SubscriptionModel(
                subscriptionId = 3,
                isOpportunistic = false,
                carrierName = "Carrier 3",
                profileClass = PROFILE_CLASS_UNSET,
            )
    }
}
