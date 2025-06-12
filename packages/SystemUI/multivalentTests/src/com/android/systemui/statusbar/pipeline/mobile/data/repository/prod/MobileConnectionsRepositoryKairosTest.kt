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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.applicationContext
import android.content.testableContext
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.connectivityManager
import android.net.vcn.VcnTransportInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.ParcelUuid
import android.telephony.CarrierConfigManager
import android.telephony.ServiceState
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import android.telephony.TelephonyCallback.ActiveDataSubscriptionIdListener
import android.telephony.TelephonyCallback.EmergencyCallbackModeListener
import android.telephony.TelephonyManager
import android.telephony.telephonyManager
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.telephony.PhoneConstants
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.keyguard.keyguardUpdateMonitor
import com.android.settingslib.R
import com.android.settingslib.mobile.MobileMappings
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.broadcast.broadcastDispatcherContext
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.combine
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.map
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.statusbar.connectivity.WifiPickerTrackerFactory
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepositoryKairosImpl
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileTelephonyHelpers.getTelephonyCallbackForType
import com.android.systemui.statusbar.pipeline.mobile.data.repository.subscriptionManager
import com.android.systemui.statusbar.pipeline.mobile.data.repository.subscriptionManagerProxy
import com.android.systemui.statusbar.pipeline.mobile.util.fake
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlots
import com.android.systemui.statusbar.pipeline.shared.data.repository.ConnectivityRepositoryImpl
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.wifi.data.repository.prod.WifiRepositoryImpl
import com.android.systemui.statusbar.pipeline.wifi.data.repository.wifiRepository
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import com.android.wifitrackerlib.MergedCarrierEntry
import com.android.wifitrackerlib.WifiEntry
import com.android.wifitrackerlib.WifiPickerTracker
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

@OptIn(ExperimentalKairosApi::class)
@SmallTest
// This is required because our [SubscriptionManager.OnSubscriptionsChangedListener] uses a looper
// to run the callback and this makes the looper place nicely with TestScope etc.
@TestableLooper.RunWithLooper
@RunWith(AndroidJUnit4::class)
class MobileConnectionsRepositoryKairosTest : SysuiTestCase() {

    private val Kosmos.wifiManager: WifiManager by Fixture { mock {} }
    private val Kosmos.wifiPickerTrackerFactory: WifiPickerTrackerFactory by Fixture {
        mock {
            on { create(any(), any(), wifiPickerTrackerCallback.capture(), any()) } doReturn
                wifiPickerTracker
        }
    }
    private val Kosmos.wifiPickerTracker: WifiPickerTracker by Fixture { mock {} }
    private val Kosmos.wifiTableLogBuffer by Fixture { logcatTableLogBuffer(this, "wifiTableLog") }

    private val mainExecutor = FakeExecutor(FakeSystemClock())
    private val wifiLogBuffer = LogBuffer("wifi", maxSize = 100, logcatEchoTracker = mock())
    private val wifiPickerTrackerCallback =
        argumentCaptor<WifiPickerTracker.WifiPickerTrackerCallback>()
    private val vcnTransportInfo = VcnTransportInfo.Builder().build()

    private val Kosmos.underTest
        get() = mobileConnectionsRepositoryKairosImpl

    private val kosmos =
        testKosmos().apply {
            fakeFeatureFlagsClassic.set(Flags.ROAMING_INDICATOR_VIA_DISPLAY_INFO, true)
            broadcastDispatcherContext = testableContext
            connectivityRepository =
                ConnectivityRepositoryImpl(
                    connectivityManager,
                    ConnectivitySlots(applicationContext),
                    applicationContext,
                    mock(),
                    mock(),
                    applicationCoroutineScope,
                    mock(),
                )
            wifiRepository =
                WifiRepositoryImpl(
                    applicationContext,
                    userRepository,
                    applicationCoroutineScope,
                    mainExecutor,
                    testDispatcher,
                    wifiPickerTrackerFactory,
                    wifiManager,
                    wifiLogBuffer,
                    wifiTableLogBuffer,
                )
            subscriptionManager.stub {
                // For convenience, set up the subscription info callbacks
                on { getActiveSubscriptionInfo(anyInt()) } doAnswer
                    { invocation ->
                        when (invocation.getArgument(0) as Int) {
                            1 -> SUB_1
                            2 -> SUB_2
                            3 -> SUB_3
                            4 -> SUB_4
                            else -> null
                        }
                    }
            }
            telephonyManager.stub {
                on { simOperatorName } doReturn ""
                // Set up so the individual connection repositories
                on { createForSubscriptionId(anyInt()) } doAnswer
                    { invocation ->
                        telephonyManager.stub {
                            on { subscriptionId } doReturn invocation.getArgument(0)
                        }
                    }
            }
            testScope.runCurrent()
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun testSubscriptions_initiallyEmpty() = runTest {
        assertThat(underTest.subscriptions.collectLastValue().value)
            .isEqualTo(listOf<SubscriptionModel>())
    }

    @Test
    fun testSubscriptions_listUpdates() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1, SUB_2)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(latest).isEqualTo(listOf(MODEL_1, MODEL_2))
    }

    @Test
    fun testSubscriptions_removingSub_updatesList() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        // WHEN 2 networks show up
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1, SUB_2)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        // WHEN one network is removed
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_2)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        // THEN the subscriptions list represents the newest change
        assertThat(latest).isEqualTo(listOf(MODEL_2))
    }

    @Test
    fun subscriptions_subIsOnlyNtn_modelHasExclusivelyNtnTrue() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        val onlyNtnSub =
            mock<SubscriptionInfo> {
                on { isOnlyNonTerrestrialNetwork } doReturn true
                on { subscriptionId } doReturn 45
                on { groupUuid } doReturn GROUP_1
                on { carrierName } doReturn "NTN only"
                on { profileClass } doReturn PROFILE_CLASS_UNSET
            }

        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(onlyNtnSub)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(latest).hasSize(1)
        assertThat(latest!![0].isExclusivelyNonTerrestrial).isTrue()
    }

    @Test
    fun subscriptions_subIsNotOnlyNtn_modelHasExclusivelyNtnFalse() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        val notOnlyNtnSub =
            mock<SubscriptionInfo> {
                on { isOnlyNonTerrestrialNetwork } doReturn false
                on { subscriptionId } doReturn 45
                on { groupUuid } doReturn GROUP_1
                on { carrierName } doReturn "NTN only"
                on { profileClass } doReturn PROFILE_CLASS_UNSET
            }

        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(notOnlyNtnSub)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(latest).hasSize(1)
        assertThat(latest!![0].isExclusivelyNonTerrestrial).isFalse()
    }

    @Test
    fun testSubscriptions_carrierMergedOnly_listHasCarrierMerged() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        setWifiState(isCarrierMerged = true)
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_CM)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(latest).isEqualTo(listOf(MODEL_CM))
    }

    @Test
    fun testSubscriptions_carrierMergedAndOther_listHasBothWithCarrierMergedLast() = runTest {
        val latest by underTest.subscriptions.collectLastValue()

        setWifiState(isCarrierMerged = true)
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1, SUB_2, SUB_CM)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(latest).isEqualTo(listOf(MODEL_1, MODEL_2, MODEL_CM))
    }

    @Test
    fun testActiveDataSubscriptionId_initialValueIsNull() = runTest {
        assertThat(underTest.activeMobileDataSubscriptionId.collectLastValue().value)
            .isEqualTo(null)
    }

    @Test
    fun testActiveDataSubscriptionId_updates() = runTest {
        val active by underTest.activeMobileDataSubscriptionId.collectLastValue()
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_2_ID)

        assertThat(active).isEqualTo(SUB_2_ID)
    }

    @Test
    fun activeSubId_nullIfInvalidSubIdIsReceived() = runTest {
        val latest by underTest.activeMobileDataSubscriptionId.collectLastValue()
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_2_ID)

        assertThat(latest).isNotNull()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(INVALID_SUBSCRIPTION_ID)

        assertThat(latest).isNull()
    }

    @Test
    fun activeRepo_initiallyNull() = runTest {
        assertThat(underTest.activeMobileDataRepository.collectLastValue().value).isNull()
    }

    @Test
    fun activeRepo_updatesWithActiveDataId() = runTest {
        val latest by underTest.activeMobileDataRepository.collectLastValue()
        testScope.runCurrent()

        // GIVEN the subscription list is then updated which includes the active data sub id
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_2)
        }
        getSubscriptionCallback().onSubscriptionsChanged()
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_2_ID)

        assertThat(latest?.subId).isEqualTo(SUB_2_ID)
    }

    @Test
    fun activeRepo_nullIfActiveDataSubIdBecomesInvalid() = runTest {
        val latest by underTest.activeMobileDataRepository.collectLastValue()
        testScope.runCurrent()

        // GIVEN the subscription list is then updated which includes the active data sub id
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_2)
        }
        getSubscriptionCallback().onSubscriptionsChanged()
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_2_ID)
        testScope.runCurrent()

        assertThat(latest).isNotNull()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(INVALID_SUBSCRIPTION_ID)
        testScope.runCurrent()

        assertThat(latest).isNull()
    }

    @Test
    /** Regression test for b/268146648. */
    fun activeSubIdIsSetBeforeSubscriptionsAreUpdated_doesNotThrow() = runTest {
        val activeRepo by underTest.activeMobileDataRepository.collectLastValue()
        val subscriptions by underTest.subscriptions.collectLastValue()
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_2_ID)
        testScope.runCurrent()

        assertThat(subscriptions).isEmpty()
        assertThat(activeRepo).isNull()
    }

    @Test
    fun getRepoForSubId_activeDataSubIdIsRequestedBeforeSubscriptionsUpdate() = runTest {
        underTest

        var latestActiveRepo: MobileConnectionRepositoryKairos? = null
        testScope.backgroundScope.launch {
            kairos.activateSpec {
                underTest.activeMobileDataSubscriptionId
                    .combine(underTest.mobileConnectionsBySubId) { id, conns ->
                        id?.let { conns[id] }
                    }
                    .observe {
                        if (it != null) {
                            latestActiveRepo = it
                        }
                    }
            }
        }

        val latestSubscriptions by underTest.subscriptions.collectLastValue()
        testScope.runCurrent()

        // Active data subscription id is sent, but no subscription change has been posted yet
        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_2_ID)
        testScope.runCurrent()

        // Subscriptions list is empty
        assertThat(latestSubscriptions).isEmpty()

        // getRepoForSubId does not throw
        assertThat(latestActiveRepo).isNull()
    }

    @Test
    fun activeDataSentBeforeSubscriptionList_subscriptionReusesActiveDataRepo() = runTest {
        val activeRepo by underTest.activeMobileDataRepository.collectLastValue()
        testScope.runCurrent()

        // GIVEN active repo is updated before the subscription list updates
        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_2_ID)
        testScope.runCurrent()

        assertThat(activeRepo).isNull()

        // GIVEN the subscription list is then updated which includes the active data sub id
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_2)
        }
        getSubscriptionCallback().onSubscriptionsChanged()
        testScope.runCurrent()

        // WHEN requesting a connection repository for the subscription
        val newRepo =
            kairos.transact { underTest.mobileConnectionsBySubId.map { it[SUB_2_ID] }.sample() }

        // THEN the newly request repo has been cached and reused
        assertThat(activeRepo).isSameInstanceAs(newRepo)
    }

    @Test
    fun testConnectionRepository_validSubId_isCached() = runTest {
        underTest

        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        val repo1 by underTest.mobileConnectionsBySubId.map { it[SUB_1_ID] }.collectLastValue()
        val repo2 by underTest.mobileConnectionsBySubId.map { it[SUB_1_ID] }.collectLastValue()

        assertThat(repo1).isNotNull()
        assertThat(repo1).isSameInstanceAs(repo2)
    }

    @Test
    fun testConnectionRepository_carrierMergedSubId_isCached() = runTest {
        underTest

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, WIFI_NETWORK_CAPS_CM)
        setWifiState(isCarrierMerged = true)
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_CM)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        val repo1 by underTest.mobileConnectionsBySubId.map { it[SUB_CM_ID] }.collectLastValue()
        val repo2 by underTest.mobileConnectionsBySubId.map { it[SUB_CM_ID] }.collectLastValue()

        assertThat(repo1).isNotNull()
        assertThat(repo1).isSameInstanceAs(repo2)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Test
    fun testDeviceEmergencyCallState_eagerlyChecksState() = runTest {
        val latest by underTest.isDeviceEmergencyCallCapable.collectLastValue()

        // Value starts out false
        assertThat(latest).isFalse()
        telephonyManager.stub { on { activeModemCount } doReturn 1 }
        whenever(telephonyManager.getServiceStateForSlot(any())).thenAnswer { _ ->
            ServiceState().apply { isEmergencyOnly = true }
        }

        // WHEN an appropriate intent gets sent out
        val intent = serviceStateIntent(subId = -1)
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(applicationContext, intent)
        testScope.runCurrent()

        // THEN the repo's state is updated despite no listeners
        assertThat(latest).isEqualTo(true)
    }

    @Test
    fun testDeviceEmergencyCallState_aggregatesAcrossSlots_oneTrue() = runTest {
        val latest by underTest.isDeviceEmergencyCallCapable.collectLastValue()

        // GIVEN there are multiple slots
        telephonyManager.stub { on { activeModemCount } doReturn 4 }
        // GIVEN only one of them reports ECM
        whenever(telephonyManager.getServiceStateForSlot(any())).thenAnswer { invocation ->
            when (invocation.getArgument(0) as Int) {
                0 -> ServiceState().apply { isEmergencyOnly = false }
                1 -> ServiceState().apply { isEmergencyOnly = false }
                2 -> ServiceState().apply { isEmergencyOnly = true }
                3 -> ServiceState().apply { isEmergencyOnly = false }
                else -> null
            }
        }

        // GIVEN a broadcast goes out for the appropriate subID
        val intent = serviceStateIntent(subId = -1)
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(applicationContext, intent)
        testScope.runCurrent()

        // THEN the device is in ECM, because one of the service states is
        assertThat(latest).isTrue()
    }

    @Test
    fun testDeviceEmergencyCallState_aggregatesAcrossSlots_allFalse() = runTest {
        val latest by underTest.isDeviceEmergencyCallCapable.collectLastValue()

        // GIVEN there are multiple slots
        telephonyManager.stub { on { activeModemCount } doReturn 4 }
        // GIVEN only one of them reports ECM
        whenever(telephonyManager.getServiceStateForSlot(any())).thenAnswer { invocation ->
            when (invocation.getArgument(0) as Int) {
                0 -> ServiceState().apply { isEmergencyOnly = false }
                1 -> ServiceState().apply { isEmergencyOnly = false }
                2 -> ServiceState().apply { isEmergencyOnly = false }
                3 -> ServiceState().apply { isEmergencyOnly = false }
                else -> null
            }
        }

        // GIVEN a broadcast goes out for the appropriate subID
        val intent = serviceStateIntent(subId = -1)
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(applicationContext, intent)
        testScope.runCurrent()

        // THEN the device is in ECM, because one of the service states is
        assertThat(latest).isFalse()
    }

    @Test
    fun testConnectionCache_clearsInvalidSubscriptions() = runTest {
        underTest

        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1, SUB_2)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        val repoCache by underTest.mobileConnectionsBySubId.collectLastValue()

        assertThat(repoCache?.keys).containsExactly(SUB_1_ID, SUB_2_ID)

        // SUB_2 disappears
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(repoCache?.keys).containsExactly(SUB_1_ID)
    }

    @Test
    fun testConnectionCache_clearsInvalidSubscriptions_includingCarrierMerged() = runTest {
        underTest

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, WIFI_NETWORK_CAPS_CM)
        setWifiState(isCarrierMerged = true)
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1, SUB_2, SUB_CM)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        val repoCache by underTest.mobileConnectionsBySubId.collectLastValue()

        assertThat(repoCache?.keys).containsExactly(SUB_1_ID, SUB_2_ID, SUB_CM_ID)

        // SUB_2 and SUB_CM disappear
        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(repoCache?.keys).containsExactly(SUB_1_ID)
    }

    /** Regression test for b/261706421 */
    @Test
    fun testConnectionsCache_clearMultipleSubscriptionsAtOnce_doesNotThrow() = runTest {
        underTest

        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1, SUB_2)
        }
        getSubscriptionCallback().onSubscriptionsChanged()

        val repoCache by underTest.mobileConnectionsBySubId.collectLastValue()

        assertThat(repoCache?.keys).containsExactly(SUB_1_ID, SUB_2_ID)

        // All subscriptions disappear
        subscriptionManager.stub { on { completeActiveSubscriptionInfoList } doReturn listOf() }
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(repoCache).isEmpty()
    }

    @Test
    fun testDefaultDataSubId_updatesOnBroadcast() = runTest {
        val latest by underTest.defaultDataSubId.collectLastValue()

        assertThat(latest).isEqualTo(null)

        val intent2 =
            Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
                .putExtra(PhoneConstants.SUBSCRIPTION_KEY, SUB_2_ID)
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(applicationContext, intent2)

        assertThat(latest).isEqualTo(SUB_2_ID)

        val intent1 =
            Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
                .putExtra(PhoneConstants.SUBSCRIPTION_KEY, SUB_1_ID)
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(applicationContext, intent1)

        assertThat(latest).isEqualTo(SUB_1_ID)
    }

    @Test
    fun defaultDataSubId_fetchesInitialValueOnStart() = runTest {
        subscriptionManagerProxy.fake.defaultDataSubId = 2
        val latest by underTest.defaultDataSubId.collectLastValue()

        assertThat(latest).isEqualTo(2)
    }

    @Test
    fun mobileIsDefault_startsAsFalse() = runTest {
        assertThat(underTest.mobileIsDefault.collectLastValue().value).isFalse()
    }

    @Test
    fun mobileIsDefault_capsHaveCellular_isDefault() = runTest {
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
            }

        val latest by underTest.mobileIsDefault.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

        assertThat(latest).isTrue()
    }

    @Test
    fun mobileIsDefault_capsDoNotHaveCellular_isNotDefault() = runTest {
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn false
            }

        val latest by underTest.mobileIsDefault.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

        assertThat(latest).isFalse()
    }

    @Test
    fun mobileIsDefault_carrierMergedViaMobile_isDefault() = runTest {
        val carrierMergedInfo = mock<WifiInfo> { on { isCarrierMerged } doReturn true }
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
                on { transportInfo } doReturn carrierMergedInfo
            }

        val latest by underTest.mobileIsDefault.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

        assertThat(latest).isTrue()
    }

    @Test
    fun mobileIsDefault_wifiDefault_mobileNotDefault() = runTest {
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_WIFI) } doReturn true
            }

        val latest by underTest.mobileIsDefault.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

        assertThat(latest).isFalse()
    }

    @Test
    fun mobileIsDefault_ethernetDefault_mobileNotDefault() = runTest {
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_ETHERNET) } doReturn true
            }

        val latest by underTest.mobileIsDefault.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

        assertThat(latest).isFalse()
    }

    /** Regression test for b/272586234. */
    @Test
    fun hasCarrierMergedConnection_carrierMergedViaWifi_isTrue() = runTest {
        val carrierMergedInfo =
            mock<WifiInfo> {
                on { isCarrierMerged } doReturn true
                on { isPrimary } doReturn true
            }
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_WIFI) } doReturn true
                on { transportInfo } doReturn carrierMergedInfo
            }

        val latest by underTest.hasCarrierMergedConnection.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)
        setWifiState(isCarrierMerged = true)

        assertThat(latest).isTrue()
    }

    @Test
    fun hasCarrierMergedConnection_carrierMergedViaMobile_isTrue() = runTest {
        val carrierMergedInfo =
            mock<WifiInfo> {
                on { isCarrierMerged } doReturn true
                on { isPrimary } doReturn true
            }
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
                on { transportInfo } doReturn carrierMergedInfo
            }

        val latest by underTest.hasCarrierMergedConnection.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)
        setWifiState(isCarrierMerged = true)

        assertThat(latest).isTrue()
    }

    private fun KairosTestScope.newWifiNetwork(wifiInfo: WifiInfo): Network {
        val network = mock<Network>()
        val capabilities =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_WIFI) } doReturn true
                on { transportInfo } doReturn wifiInfo
            }
        connectivityManager.stub { on { getNetworkCapabilities(network) } doReturn capabilities }
        return network
    }

    /** Regression test for b/272586234. */
    @Test
    fun hasCarrierMergedConnection_carrierMergedViaWifiWithVcnTransport_isTrue() = runTest {
        val carrierMergedInfo =
            mock<WifiInfo> {
                on { isCarrierMerged } doReturn true
                on { isPrimary } doReturn true
            }
        val underlyingWifi = newWifiNetwork(carrierMergedInfo)
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_WIFI) } doReturn true
                on { transportInfo } doReturn vcnTransportInfo
                on { underlyingNetworks } doReturn listOf(underlyingWifi)
            }

        val latest by underTest.hasCarrierMergedConnection.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)
        setWifiState(isCarrierMerged = true)

        assertThat(latest).isTrue()
    }

    @Test
    fun hasCarrierMergedConnection_carrierMergedViaMobileWithVcnTransport_isTrue() = runTest {
        val carrierMergedInfo =
            mock<WifiInfo> {
                on { isCarrierMerged } doReturn true
                on { isPrimary } doReturn true
            }
        val underlyingWifi = newWifiNetwork(carrierMergedInfo)
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
                on { transportInfo } doReturn vcnTransportInfo
                on { underlyingNetworks } doReturn listOf(underlyingWifi)
            }

        val latest by underTest.hasCarrierMergedConnection.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)
        setWifiState(isCarrierMerged = true)

        assertThat(latest).isTrue()
    }

    @Test
    fun hasCarrierMergedConnection_isCarrierMergedViaUnderlyingWifi_isTrue() = runTest {
        val latest by underTest.hasCarrierMergedConnection.collectLastValue()

        val underlyingNetwork = mock<Network>()
        val carrierMergedInfo =
            mock<WifiInfo> {
                on { isCarrierMerged } doReturn true
                on { isPrimary } doReturn true
            }
        val underlyingWifiCapabilities =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_WIFI) } doReturn true
                on { transportInfo } doReturn carrierMergedInfo
            }
        connectivityManager.stub {
            on { getNetworkCapabilities(underlyingNetwork) } doReturn underlyingWifiCapabilities
        }

        // WHEN the main capabilities have an underlying carrier merged network via WIFI
        // transport and WifiInfo
        val mainCapabilities =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
                on { transportInfo } doReturn null
                on { underlyingNetworks } doReturn listOf(underlyingNetwork)
            }

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, mainCapabilities)
        setWifiState(isCarrierMerged = true)

        // THEN there's a carrier merged connection
        assertThat(latest).isTrue()
    }

    @Test
    fun hasCarrierMergedConnection_isCarrierMergedViaUnderlyingCellular_isTrue() = runTest {
        val latest by underTest.hasCarrierMergedConnection.collectLastValue()

        val underlyingCarrierMergedNetwork = mock<Network>()
        val carrierMergedInfo =
            mock<WifiInfo> {
                on { isCarrierMerged } doReturn true
                on { isPrimary } doReturn true
            }

        // The Wifi network that is under the VCN network
        val physicalWifiNetwork = newWifiNetwork(carrierMergedInfo)

        val underlyingCapabilities =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
                on { transportInfo } doReturn vcnTransportInfo
                on { underlyingNetworks } doReturn listOf(physicalWifiNetwork)
            }
        connectivityManager.stub {
            on { getNetworkCapabilities(underlyingCarrierMergedNetwork) } doReturn
                underlyingCapabilities
        }

        // WHEN the main capabilities have an underlying carrier merged network via CELLULAR
        // transport and VcnTransportInfo
        val mainCapabilities =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
                on { transportInfo } doReturn null
                on { underlyingNetworks } doReturn listOf(underlyingCarrierMergedNetwork)
            }

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, mainCapabilities)
        setWifiState(isCarrierMerged = true)

        // THEN there's a carrier merged connection
        assertThat(latest).isTrue()
    }

    /** Regression test for b/272586234. */
    @Test
    fun hasCarrierMergedConnection_defaultIsWifiNotCarrierMerged_wifiRepoIsCarrierMerged_isTrue() =
        runTest {
            val latest by underTest.hasCarrierMergedConnection.collectLastValue()

            // WHEN the default callback is TRANSPORT_WIFI but not carrier merged
            val carrierMergedInfo = mock<WifiInfo> { on { isCarrierMerged } doReturn false }
            val caps =
                Mockito.mock(NetworkCapabilities::class.java).stub {
                    on { hasTransport(TRANSPORT_WIFI) } doReturn true
                    on { transportInfo } doReturn carrierMergedInfo
                }
            getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

            // BUT the wifi repo has gotten updates that it *is* carrier merged
            setWifiState(isCarrierMerged = true)

            // THEN hasCarrierMergedConnection is true
            assertThat(latest).isTrue()
        }

    /** Regression test for b/278618530. */
    @Test
    fun hasCarrierMergedConnection_defaultIsCellular_wifiRepoIsCarrierMerged_isFalse() = runTest {
        val latest by underTest.hasCarrierMergedConnection.collectLastValue()

        // WHEN the default callback is TRANSPORT_CELLULAR and not carrier merged
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
                on { transportInfo } doReturn null
            }
        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

        // BUT the wifi repo has gotten updates that it *is* carrier merged
        setWifiState(isCarrierMerged = true)

        // THEN hasCarrierMergedConnection is **false** (The default network being CELLULAR
        // takes precedence over the wifi network being carrier merged.)
        assertThat(latest).isFalse()
    }

    /** Regression test for b/278618530. */
    @Test
    fun hasCarrierMergedConnection_defaultCellular_wifiIsCarrierMerged_airplaneMode_isTrue() =
        runTest {
            val latest by underTest.hasCarrierMergedConnection.collectLastValue()

            // WHEN the default callback is TRANSPORT_CELLULAR and not carrier merged
            val caps =
                Mockito.mock(NetworkCapabilities::class.java).stub {
                    on { hasTransport(TRANSPORT_CELLULAR) } doReturn true
                    on { transportInfo } doReturn null
                }
            getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

            // BUT the wifi repo has gotten updates that it *is* carrier merged
            setWifiState(isCarrierMerged = true)
            // AND we're in airplane mode
            airplaneModeRepository.setIsAirplaneMode(true)

            // THEN hasCarrierMergedConnection is true.
            assertThat(latest).isTrue()
        }

    @Test
    fun defaultConnectionIsValidated_startsAsFalse() = runTest {
        assertThat(underTest.defaultConnectionIsValidated.collectLastValue().value).isFalse()
    }

    @Test
    fun defaultConnectionIsValidated_capsHaveValidated_isValidated() = runTest {
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasCapability(NET_CAPABILITY_VALIDATED) } doReturn true
            }

        val latest by underTest.defaultConnectionIsValidated.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

        assertThat(latest).isTrue()
    }

    @Test
    fun defaultConnectionIsValidated_capsHaveNotValidated_isNotValidated() = runTest {
        val caps =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasCapability(NET_CAPABILITY_VALIDATED) } doReturn false
            }

        val latest by underTest.defaultConnectionIsValidated.collectLastValue()

        getDefaultNetworkCallback().onCapabilitiesChanged(NETWORK, caps)

        assertThat(latest).isFalse()
    }

    @Test
    fun config_initiallyFromContext() = runTest {
        overrideResource(R.bool.config_showMin3G, true)
        val configFromContext = MobileMappings.Config.readConfig(applicationContext)
        assertThat(configFromContext.showAtLeast3G).isTrue()

        val latest by underTest.defaultDataSubRatConfig.collectLastValue()

        assertTrue(latest!!.areEqual(configFromContext))
        assertTrue(latest!!.showAtLeast3G)
    }

    @Test
    fun config_subIdChangeEvent_updated() = runTest {
        val latest by underTest.defaultDataSubRatConfig.collectLastValue()

        assertThat(latest!!.showAtLeast3G).isFalse()

        overrideResource(R.bool.config_showMin3G, true)
        val configFromContext = MobileMappings.Config.readConfig(applicationContext)
        assertThat(configFromContext.showAtLeast3G).isTrue()

        // WHEN the change event is fired
        val intent =
            Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
                .putExtra(PhoneConstants.SUBSCRIPTION_KEY, SUB_1_ID)
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(applicationContext, intent)

        // THEN the config is updated
        assertThat(latest?.areEqual(configFromContext)).isEqualTo(true)
        assertThat(latest?.showAtLeast3G).isEqualTo(true)
    }

    @Test
    fun config_carrierConfigChangeEvent_updated() = runTest {
        val latest by underTest.defaultDataSubRatConfig.collectLastValue()

        assertThat(latest!!.showAtLeast3G).isFalse()

        overrideResource(R.bool.config_showMin3G, true)
        val configFromContext = MobileMappings.Config.readConfig(applicationContext)
        assertThat(configFromContext.showAtLeast3G).isTrue()

        // WHEN the change event is fired
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(
            applicationContext,
            Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED),
        )

        // THEN the config is updated
        assertThat(latest?.areEqual(configFromContext)).isEqualTo(true)
        assertThat(latest?.showAtLeast3G).isEqualTo(true)
    }

    @Test
    fun carrierConfig_initialValueIsFetched() = runTest {
        underTest
        testScope.runCurrent()

        // Value starts out false
        assertThat(underTest.defaultDataSubRatConfig.sample().showAtLeast3G).isFalse()

        overrideResource(R.bool.config_showMin3G, true)
        val configFromContext = MobileMappings.Config.readConfig(applicationContext)
        assertThat(configFromContext.showAtLeast3G).isTrue()

        assertThat(broadcastDispatcher.numReceiversRegistered).isAtLeast(1)

        // WHEN the change event is fired
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(
            applicationContext,
            Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED),
        )
        testScope.runCurrent()

        // WHEN collection starts AFTER the broadcast is sent out
        val latest by underTest.defaultDataSubRatConfig.collectLastValue()

        // THEN the config has the updated value
        assertWithMessage("showAtLeast3G is false").that(latest!!.showAtLeast3G).isTrue()
        assertWithMessage("not equal").that(latest!!.areEqual(configFromContext)).isTrue()
    }

    @Test
    fun activeDataChange_inSameGroup_emitsUnit() = runTest {
        var eventCount = 0
        underTest
        testScope.backgroundScope.launch {
            kairos.activateSpec { underTest.activeSubChangedInGroupEvent.observe { eventCount++ } }
        }
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_3_ID_GROUPED)
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_4_ID_GROUPED)
        testScope.runCurrent()

        assertThat(eventCount).isEqualTo(1)
    }

    @Test
    fun activeDataChange_notInSameGroup_doesNotEmit() = runTest {
        var eventCount = 0
        underTest
        testScope.backgroundScope.launch {
            kairos.activateSpec { underTest.activeSubChangedInGroupEvent.observe { eventCount++ } }
        }
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_3_ID_GROUPED)
        testScope.runCurrent()

        getTelephonyCallbackForType<ActiveDataSubscriptionIdListener>(telephonyManager)
            .onActiveDataSubscriptionIdChanged(SUB_1_ID)
        testScope.runCurrent()

        assertThat(eventCount).isEqualTo(0)
    }

    @Test
    fun anySimSecure_propagatesStateFromKeyguardUpdateMonitor() = runTest {
        val latest by underTest.isAnySimSecure.collectLastValue()
        assertThat(latest).isFalse()

        val updateMonitorCallback = argumentCaptor<KeyguardUpdateMonitorCallback>()
        verify(keyguardUpdateMonitor).registerCallback(updateMonitorCallback.capture())

        keyguardUpdateMonitor.stub { on { isSimPinSecure } doReturn true }
        updateMonitorCallback.lastValue.onSimStateChanged(0, 0, 0)

        assertThat(latest).isTrue()

        keyguardUpdateMonitor.stub { on { isSimPinSecure } doReturn false }
        updateMonitorCallback.lastValue.onSimStateChanged(0, 0, 0)

        assertThat(latest).isFalse()
    }

    @Test
    fun getIsAnySimSecure_delegatesCallToKeyguardUpdateMonitor() = runTest {
        val anySimSecure by underTest.isAnySimSecure.collectLastValue()

        assertThat(anySimSecure).isFalse()

        keyguardUpdateMonitor.stub { on { isSimPinSecure } doReturn true }
        argumentCaptor<KeyguardUpdateMonitorCallback>()
            .apply { verify(keyguardUpdateMonitor).registerCallback(capture()) }
            .lastValue
            .onSimStateChanged(0, 0, 0)

        assertThat(anySimSecure).isTrue()
    }

    @Test
    fun noSubscriptionsInEcmMode_notInEcmMode() = runTest {
        val latest by underTest.isInEcmMode.collectLastValue()
        testScope.runCurrent()

        assertThat(latest).isFalse()
    }

    @Test
    fun someSubscriptionsInEcmMode_inEcmMode() = runTest {
        val latest by underTest.isInEcmMode.collectLastValue()
        testScope.runCurrent()

        getTelephonyCallbackForType<EmergencyCallbackModeListener>(telephonyManager)
            .onCallbackModeStarted(0, mock(), 0)

        assertThat(latest).isTrue()
    }

    private fun KairosTestScope.getDefaultNetworkCallback(): ConnectivityManager.NetworkCallback {
        testScope.runCurrent()
        val callbackCaptor = argumentCaptor<ConnectivityManager.NetworkCallback>()
        verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
        return callbackCaptor.lastValue
    }

    private fun KairosTestScope.setWifiState(isCarrierMerged: Boolean) {
        if (isCarrierMerged) {
            val mergedEntry =
                mock<MergedCarrierEntry> {
                    on { isPrimaryNetwork } doReturn true
                    on { isDefaultNetwork } doReturn true
                    on { subscriptionId } doReturn SUB_CM_ID
                }
            wifiPickerTracker.stub {
                on { mergedCarrierEntry } doReturn mergedEntry
                on { connectedWifiEntry } doReturn null
            }
        } else {
            val wifiEntry =
                mock<WifiEntry> {
                    on { isPrimaryNetwork } doReturn true
                    on { isDefaultNetwork } doReturn true
                }
            wifiPickerTracker.stub {
                on { connectedWifiEntry } doReturn wifiEntry
                on { mergedCarrierEntry } doReturn null
            }
        }
        wifiPickerTrackerCallback.allValues.forEach { it.onWifiEntriesChanged() }
    }

    private fun KairosTestScope.getSubscriptionCallback(): OnSubscriptionsChangedListener {
        testScope.runCurrent()
        return argumentCaptor<OnSubscriptionsChangedListener>()
            .apply {
                verify(subscriptionManager).addOnSubscriptionsChangedListener(any(), capture())
            }
            .lastValue
    }

    companion object {
        // Subscription 1
        private const val SUB_1_ID = 1
        private const val SUB_1_NAME = "Carrier $SUB_1_ID"
        private val GROUP_1 = ParcelUuid(UUID.randomUUID())
        private val SUB_1 =
            mock<SubscriptionInfo> {
                on { subscriptionId } doReturn SUB_1_ID
                on { groupUuid } doReturn GROUP_1
                on { carrierName } doReturn SUB_1_NAME
                on { profileClass } doReturn PROFILE_CLASS_UNSET
            }
        private val MODEL_1 =
            SubscriptionModel(
                subscriptionId = SUB_1_ID,
                groupUuid = GROUP_1,
                carrierName = SUB_1_NAME,
                profileClass = PROFILE_CLASS_UNSET,
            )

        // Subscription 2
        private const val SUB_2_ID = 2
        private const val SUB_2_NAME = "Carrier $SUB_2_ID"
        private val GROUP_2 = ParcelUuid(UUID.randomUUID())
        private val SUB_2 =
            mock<SubscriptionInfo> {
                on { subscriptionId } doReturn SUB_2_ID
                on { groupUuid } doReturn GROUP_2
                on { carrierName } doReturn SUB_2_NAME
                on { profileClass } doReturn PROFILE_CLASS_UNSET
            }
        private val MODEL_2 =
            SubscriptionModel(
                subscriptionId = SUB_2_ID,
                groupUuid = GROUP_2,
                carrierName = SUB_2_NAME,
                profileClass = PROFILE_CLASS_UNSET,
            )

        // Subs 3 and 4 are considered to be in the same group ------------------------------------
        private val GROUP_ID_3_4 = ParcelUuid(UUID.randomUUID())

        // Subscription 3
        private const val SUB_3_ID_GROUPED = 3
        private val SUB_3 =
            mock<SubscriptionInfo> {
                on { subscriptionId } doReturn SUB_3_ID_GROUPED
                on { groupUuid } doReturn GROUP_ID_3_4
                on { profileClass } doReturn PROFILE_CLASS_UNSET
            }

        // Subscription 4
        private const val SUB_4_ID_GROUPED = 4
        private val SUB_4 =
            mock<SubscriptionInfo> {
                on { subscriptionId } doReturn SUB_4_ID_GROUPED
                on { groupUuid } doReturn GROUP_ID_3_4
                on { profileClass } doReturn PROFILE_CLASS_UNSET
            }

        // Subs 3 and 4 are considered to be in the same group ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

        private const val NET_ID = 123
        private val NETWORK = mock<Network> { on { getNetId() } doReturn NET_ID }

        // Carrier merged subscription
        private const val SUB_CM_ID = 5
        private const val SUB_CM_NAME = "Carrier $SUB_CM_ID"
        private val SUB_CM =
            mock<SubscriptionInfo> {
                on { subscriptionId } doReturn SUB_CM_ID
                on { carrierName } doReturn SUB_CM_NAME
                on { profileClass } doReturn PROFILE_CLASS_UNSET
            }
        private val MODEL_CM =
            SubscriptionModel(
                subscriptionId = SUB_CM_ID,
                carrierName = SUB_CM_NAME,
                profileClass = PROFILE_CLASS_UNSET,
            )

        private val WIFI_INFO_CM =
            mock<WifiInfo> {
                on { isPrimary } doReturn true
                on { isCarrierMerged } doReturn true
                on { subscriptionId } doReturn SUB_CM_ID
            }
        private val WIFI_NETWORK_CAPS_CM =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_WIFI) } doReturn true
                on { transportInfo } doReturn WIFI_INFO_CM
                on { hasCapability(NET_CAPABILITY_VALIDATED) } doReturn true
            }

        private val WIFI_INFO_ACTIVE =
            mock<WifiInfo> {
                on { isPrimary } doReturn true
                on { isCarrierMerged } doReturn false
            }

        private val WIFI_NETWORK_CAPS_ACTIVE =
            Mockito.mock(NetworkCapabilities::class.java).stub {
                on { hasTransport(TRANSPORT_WIFI) } doReturn true
                on { transportInfo } doReturn WIFI_INFO_ACTIVE
                on { hasCapability(NET_CAPABILITY_VALIDATED) } doReturn true
            }

        /**
         * To properly mimic telephony manager, create a service state, and then turn it into an
         * intent
         */
        private fun serviceStateIntent(subId: Int): Intent {
            return Intent(Intent.ACTION_SERVICE_STATE).apply {
                putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, subId)
            }
        }
    }
}
