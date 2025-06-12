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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager.NetworkCallback
import android.net.connectivityManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WLAN
import android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN
import android.telephony.CarrierConfigManager.KEY_INFLATE_SIGNAL_STRENGTH_BOOL
import android.telephony.CarrierConfigManager.KEY_SHOW_5G_SLICE_ICON_BOOL
import android.telephony.NetworkRegistrationInfo
import android.telephony.NetworkRegistrationInfo.DOMAIN_PS
import android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_DENIED
import android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME
import android.telephony.ServiceState
import android.telephony.ServiceState.STATE_IN_SERVICE
import android.telephony.ServiceState.STATE_OUT_OF_SERVICE
import android.telephony.SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.CarrierRoamingNtnListener
import android.telephony.TelephonyCallback.DataActivityListener
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyCallback.ServiceStateListener
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA
import android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.DATA_ACTIVITY_DORMANT
import android.telephony.TelephonyManager.DATA_ACTIVITY_IN
import android.telephony.TelephonyManager.DATA_ACTIVITY_INOUT
import android.telephony.TelephonyManager.DATA_ACTIVITY_NONE
import android.telephony.TelephonyManager.DATA_ACTIVITY_OUT
import android.telephony.TelephonyManager.DATA_CONNECTED
import android.telephony.TelephonyManager.DATA_CONNECTING
import android.telephony.TelephonyManager.DATA_DISCONNECTED
import android.telephony.TelephonyManager.DATA_DISCONNECTING
import android.telephony.TelephonyManager.DATA_HANDOVER_IN_PROGRESS
import android.telephony.TelephonyManager.DATA_SUSPENDED
import android.telephony.TelephonyManager.DATA_UNKNOWN
import android.telephony.TelephonyManager.ERI_OFF
import android.telephony.TelephonyManager.ERI_ON
import android.telephony.TelephonyManager.EXTRA_CARRIER_ID
import android.telephony.TelephonyManager.EXTRA_DATA_SPN
import android.telephony.TelephonyManager.EXTRA_PLMN
import android.telephony.TelephonyManager.EXTRA_SHOW_PLMN
import android.telephony.TelephonyManager.EXTRA_SHOW_SPN
import android.telephony.TelephonyManager.EXTRA_SPN
import android.telephony.TelephonyManager.EXTRA_SUBSCRIPTION_ID
import android.telephony.TelephonyManager.NETWORK_TYPE_LTE
import android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN
import android.telephony.telephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.mobile.MobileMappings
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags.ROAMING_INDICATOR_VIA_DISPLAY_INFO
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.ActivatedKairosFixture
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.MutableState
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogcatEchoTrackerAlways
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.tableLogBufferFactory
import com.android.systemui.statusbar.pipeline.mobile.data.MobileInputLogger
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType.DefaultNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType.OverrideNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType.UnknownNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.SystemUiCarrierConfig
import com.android.systemui.statusbar.pipeline.mobile.data.model.testCarrierConfig
import com.android.systemui.statusbar.pipeline.mobile.data.model.testCarrierConfigWithOverride
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepository.Companion.DEFAULT_NUM_LEVELS
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileMappingsProxy
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileTelephonyHelpers.signalStrength
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileTelephonyHelpers.telephonyDisplayInfo
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import com.android.systemui.statusbar.pipeline.shared.data.model.toMobileDataActivityModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileConnectionRepositoryKairosTest : SysuiTestCase() {

    private val Kosmos.underTest by ActivatedKairosFixture {
        MobileConnectionRepositoryKairosImpl(
            SUB_1_ID,
            context,
            subscriptionModel,
            DEFAULT_NAME_MODEL,
            SEP,
            connectivityManager,
            telephonyManager,
            systemUiCarrierConfig,
            fakeBroadcastDispatcher,
            mobileMappingsProxy,
            testDispatcher,
            logger,
            tableLogger,
            featureFlagsClassic,
        )
    }

    private val Kosmos.logger: MobileInputLogger by Fixture {
        MobileInputLogger(LogBuffer("test_buffer", 1, LogcatEchoTrackerAlways()))
    }

    private val Kosmos.tableLogger: TableLogBuffer by Fixture {
        tableLogBufferFactory.getOrCreate("test_buffer", 1)
    }

    private val Kosmos.context: Context by Fixture { mock() }

    private val systemUiCarrierConfig = SystemUiCarrierConfig(SUB_1_ID, testCarrierConfig())

    private val Kosmos.subscriptionModel: MutableState<SubscriptionModel?> by Fixture {
        MutableState(
            kairos,
            SubscriptionModel(
                subscriptionId = SUB_1_ID,
                carrierName = DEFAULT_NAME,
                profileClass = PROFILE_CLASS_UNSET,
            ),
        )
    }

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            featureFlagsClassic.fake.set(ROAMING_INDICATOR_VIA_DISPLAY_INFO, true)
            telephonyManager.stub { on { subscriptionId } doReturn SUB_1_ID }
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun emergencyOnly() = runTest {
        val latest by underTest.isEmergencyOnly.collectLastValue()

        val serviceState = ServiceState().apply { isEmergencyOnly = true }

        getTelephonyCallbackForType<ServiceStateListener>().onServiceStateChanged(serviceState)

        assertThat(latest).isEqualTo(true)
    }

    @Test
    fun emergencyOnly_toggles() = runTest {
        val latest by underTest.isEmergencyOnly.collectLastValue()

        val callback = getTelephonyCallbackForType<ServiceStateListener>()
        callback.onServiceStateChanged(ServiceState().apply { isEmergencyOnly = true })

        assertThat(latest).isTrue()

        callback.onServiceStateChanged(ServiceState().apply { isEmergencyOnly = false })

        assertThat(latest).isFalse()
    }

    @Test
    fun cdmaLevelUpdates() = runTest {
        val latest by underTest.cdmaLevel.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.SignalStrengthsListener>()
        var strength = signalStrength(gsmLevel = 1, cdmaLevel = 2, isGsm = true)
        callback.onSignalStrengthsChanged(strength)

        assertThat(latest).isEqualTo(2)

        // gsmLevel updates, no change to cdmaLevel
        strength = signalStrength(gsmLevel = 3, cdmaLevel = 2, isGsm = true)
        callback.onSignalStrengthsChanged(strength)

        assertThat(latest).isEqualTo(2)
    }

    @Test
    fun gsmLevelUpdates() = runTest {
        val latest by underTest.primaryLevel.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.SignalStrengthsListener>()
        var strength = signalStrength(gsmLevel = 1, cdmaLevel = 2, isGsm = true)
        callback.onSignalStrengthsChanged(strength)

        assertThat(latest).isEqualTo(1)

        strength = signalStrength(gsmLevel = 3, cdmaLevel = 2, isGsm = true)
        callback.onSignalStrengthsChanged(strength)

        assertThat(latest).isEqualTo(3)
    }

    @Test
    fun isGsm() = runTest {
        val latest by underTest.isGsm.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.SignalStrengthsListener>()
        var strength = signalStrength(gsmLevel = 1, cdmaLevel = 2, isGsm = true)
        callback.onSignalStrengthsChanged(strength)

        assertThat(latest).isTrue()

        strength = signalStrength(gsmLevel = 1, cdmaLevel = 2, isGsm = false)
        callback.onSignalStrengthsChanged(strength)

        assertThat(latest).isFalse()
    }

    @Test
    fun dataConnectionState_connected() = runTest {
        val latest by underTest.dataConnectionState.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataConnectionStateListener>()
        callback.onDataConnectionStateChanged(DATA_CONNECTED, 200 /* unused */)

        assertThat(latest).isEqualTo(DataConnectionState.Connected)
    }

    @Test
    fun dataConnectionState_connecting() = runTest {
        val latest by underTest.dataConnectionState.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataConnectionStateListener>()
        callback.onDataConnectionStateChanged(DATA_CONNECTING, 200 /* unused */)

        assertThat(latest).isEqualTo(DataConnectionState.Connecting)
    }

    @Test
    fun dataConnectionState_disconnected() = runTest {
        val latest by underTest.dataConnectionState.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataConnectionStateListener>()
        callback.onDataConnectionStateChanged(DATA_DISCONNECTED, 200 /* unused */)

        assertThat(latest).isEqualTo(DataConnectionState.Disconnected)
    }

    @Test
    fun dataConnectionState_disconnecting() = runTest {
        val latest by underTest.dataConnectionState.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataConnectionStateListener>()
        callback.onDataConnectionStateChanged(DATA_DISCONNECTING, 200 /* unused */)

        assertThat(latest).isEqualTo(DataConnectionState.Disconnecting)
    }

    @Test
    fun dataConnectionState_suspended() = runTest {
        val latest by underTest.dataConnectionState.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataConnectionStateListener>()
        callback.onDataConnectionStateChanged(DATA_SUSPENDED, 200 /* unused */)

        assertThat(latest).isEqualTo(DataConnectionState.Suspended)
    }

    @Test
    fun dataConnectionState_handoverInProgress() = runTest {
        val latest by underTest.dataConnectionState.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataConnectionStateListener>()
        callback.onDataConnectionStateChanged(DATA_HANDOVER_IN_PROGRESS, 200 /* unused */)

        assertThat(latest).isEqualTo(DataConnectionState.HandoverInProgress)
    }

    @Test
    fun dataConnectionState_unknown() = runTest {
        val latest by underTest.dataConnectionState.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataConnectionStateListener>()
        callback.onDataConnectionStateChanged(DATA_UNKNOWN, 200 /* unused */)

        assertThat(latest).isEqualTo(DataConnectionState.Unknown)
    }

    @Test
    fun dataConnectionState_invalid() = runTest {
        val latest by underTest.dataConnectionState.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataConnectionStateListener>()
        callback.onDataConnectionStateChanged(45, 200 /* unused */)

        assertThat(latest).isEqualTo(DataConnectionState.Invalid)
    }

    @Test
    fun dataActivity() = runTest {
        val latest by underTest.dataActivityDirection.collectLastValue()

        val callback = getTelephonyCallbackForType<DataActivityListener>()
        callback.onDataActivity(DATA_ACTIVITY_INOUT)

        assertThat(latest).isEqualTo(DATA_ACTIVITY_INOUT.toMobileDataActivityModel())
    }

    @Test
    fun carrierId_initialValueCaptured() = runTest {
        whenever(telephonyManager.simCarrierId).thenReturn(1234)

        val latest by underTest.carrierId.collectLastValue()

        assertThat(latest).isEqualTo(1234)
    }

    @Test
    fun carrierId_updatesOnBroadcast() = runTest {
        whenever(telephonyManager.simCarrierId).thenReturn(1234)

        val latest by underTest.carrierId.collectLastValue()

        fakeBroadcastDispatcher.sendIntentToMatchingReceiversOnly(
            context,
            carrierIdIntent(carrierId = 4321),
        )

        assertThat(latest).isEqualTo(4321)
    }

    @Test
    fun carrierNetworkChange() = runTest {
        val latest by underTest.carrierNetworkChangeActive.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.CarrierNetworkListener>()
        callback.onCarrierNetworkChange(true)

        assertThat(latest).isEqualTo(true)
    }

    @Test
    fun networkType_default() = runTest {
        val latest by underTest.resolvedNetworkType.collectLastValue()

        val expected = UnknownNetworkType

        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_unknown_hasCorrectKey() = runTest {
        val latest by underTest.resolvedNetworkType.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DisplayInfoListener>()
        val ti =
            telephonyDisplayInfo(
                networkType = NETWORK_TYPE_UNKNOWN,
                overrideNetworkType = NETWORK_TYPE_UNKNOWN,
            )

        callback.onDisplayInfoChanged(ti)

        val expected = UnknownNetworkType
        assertThat(latest).isEqualTo(expected)
        assertThat(latest!!.lookupKey).isEqualTo(MobileMappings.toIconKey(NETWORK_TYPE_UNKNOWN))
    }

    @Test
    fun networkType_updatesUsingDefault() = runTest {
        val latest by underTest.resolvedNetworkType.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DisplayInfoListener>()
        val overrideType = OVERRIDE_NETWORK_TYPE_NONE
        val type = NETWORK_TYPE_LTE
        val ti = telephonyDisplayInfo(networkType = type, overrideNetworkType = overrideType)
        callback.onDisplayInfoChanged(ti)

        val expected = DefaultNetworkType(mobileMappingsProxy.toIconKey(type))
        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_updatesUsingOverride() = runTest {
        val latest by underTest.resolvedNetworkType.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DisplayInfoListener>()
        val type = OVERRIDE_NETWORK_TYPE_LTE_CA
        val ti = telephonyDisplayInfo(networkType = type, overrideNetworkType = type)
        callback.onDisplayInfoChanged(ti)

        val expected = OverrideNetworkType(mobileMappingsProxy.toIconKeyOverride(type))
        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun networkType_unknownNetworkWithOverride_usesOverrideKey() = runTest {
        val latest by underTest.resolvedNetworkType.collectLastValue()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DisplayInfoListener>()
        val unknown = NETWORK_TYPE_UNKNOWN
        val type = OVERRIDE_NETWORK_TYPE_LTE_CA
        val ti = telephonyDisplayInfo(unknown, type)
        callback.onDisplayInfoChanged(ti)

        val expected = OverrideNetworkType(mobileMappingsProxy.toIconKeyOverride(type))
        assertThat(latest).isEqualTo(expected)
    }

    @Test
    fun dataEnabled_initial_false() = runTest {
        whenever(telephonyManager.isDataConnectionAllowed).thenReturn(false)

        val latest by underTest.dataEnabled.collectLastValue()

        assertThat(latest).isFalse()
    }

    @Test
    fun isDataEnabled_tracksTelephonyCallback() = runTest {
        val latest by underTest.dataEnabled.collectLastValue()

        whenever(telephonyManager.isDataConnectionAllowed).thenReturn(false)
        assertThat(latest).isFalse()

        val callback = getTelephonyCallbackForType<TelephonyCallback.DataEnabledListener>()

        callback.onDataEnabledChanged(true, 1)
        assertThat(latest).isTrue()

        callback.onDataEnabledChanged(false, 1)
        assertThat(latest).isFalse()
    }

    @Test
    fun numberOfLevels_isDefault() = runTest {
        val latest by underTest.numberOfLevels.collectLastValue()

        assertThat(latest).isEqualTo(DEFAULT_NUM_LEVELS)
    }

    @Test
    fun roaming_cdma_queriesTelephonyManager() = runTest {
        val latest by underTest.cdmaRoaming.collectLastValue()

        val cb = getTelephonyCallbackForType<ServiceStateListener>()

        // CDMA roaming is off, GSM roaming is on
        whenever(telephonyManager.cdmaEnhancedRoamingIndicatorDisplayNumber).thenReturn(ERI_OFF)
        cb.onServiceStateChanged(ServiceState().also { it.roaming = true })

        assertThat(latest).isFalse()

        // CDMA roaming is on, GSM roaming is off
        whenever(telephonyManager.cdmaEnhancedRoamingIndicatorDisplayNumber).thenReturn(ERI_ON)
        cb.onServiceStateChanged(ServiceState().also { it.roaming = false })

        assertThat(latest).isTrue()
    }

    /**
     * [TelephonyManager.getCdmaEnhancedRoamingIndicatorDisplayNumber] returns -1 if the service is
     * not running or if there is an error while retrieving the cdma ERI
     */
    @Test
    fun cdmaRoaming_ignoresNegativeOne() = runTest {
        val latest by underTest.cdmaRoaming.collectLastValue()

        val serviceState = ServiceState()
        serviceState.roaming = false

        val cb = getTelephonyCallbackForType<ServiceStateListener>()

        // CDMA roaming is unavailable (-1), GSM roaming is off
        whenever(telephonyManager.cdmaEnhancedRoamingIndicatorDisplayNumber).thenReturn(-1)
        cb.onServiceStateChanged(serviceState)

        assertThat(latest).isFalse()
    }

    @Test
    fun roaming_gsm_queriesDisplayInfo_viaDisplayInfo() = runTest {
        // GIVEN flag is true
        featureFlagsClassic.fake.set(ROAMING_INDICATOR_VIA_DISPLAY_INFO, true)

        val latest by underTest.isRoaming.collectLastValue()

        val cb = getTelephonyCallbackForType<DisplayInfoListener>()

        // CDMA roaming is off, GSM roaming is off
        whenever(telephonyManager.cdmaEnhancedRoamingIndicatorDisplayNumber).thenReturn(ERI_OFF)
        cb.onDisplayInfoChanged(TelephonyDisplayInfo(NETWORK_TYPE_LTE, NETWORK_TYPE_UNKNOWN, false))

        assertThat(latest).isFalse()

        // CDMA roaming is off, GSM roaming is on
        cb.onDisplayInfoChanged(TelephonyDisplayInfo(NETWORK_TYPE_LTE, NETWORK_TYPE_UNKNOWN, true))

        assertThat(latest).isTrue()
    }

    @Test
    fun roaming_gsm_queriesDisplayInfo_viaServiceState() = runTest {
        // GIVEN flag is false
        featureFlagsClassic.fake.set(ROAMING_INDICATOR_VIA_DISPLAY_INFO, false)

        val latest by underTest.isRoaming.collectLastValue()

        val cb = getTelephonyCallbackForType<ServiceStateListener>()

        // CDMA roaming is off, GSM roaming is off
        whenever(telephonyManager.cdmaEnhancedRoamingIndicatorDisplayNumber).thenReturn(ERI_OFF)
        cb.onServiceStateChanged(ServiceState().also { it.roaming = false })

        assertThat(latest).isFalse()

        // CDMA roaming is off, GSM roaming is on
        cb.onServiceStateChanged(ServiceState().also { it.roaming = true })

        assertThat(latest).isTrue()
    }

    @Test
    fun activity_updatesFromCallback() = runTest {
        val latest by underTest.dataActivityDirection.collectLastValue()

        assertThat(latest)
            .isEqualTo(DataActivityModel(hasActivityIn = false, hasActivityOut = false))

        val cb = getTelephonyCallbackForType<DataActivityListener>()
        cb.onDataActivity(DATA_ACTIVITY_IN)
        assertThat(latest)
            .isEqualTo(DataActivityModel(hasActivityIn = true, hasActivityOut = false))

        cb.onDataActivity(DATA_ACTIVITY_OUT)
        assertThat(latest)
            .isEqualTo(DataActivityModel(hasActivityIn = false, hasActivityOut = true))

        cb.onDataActivity(DATA_ACTIVITY_INOUT)
        assertThat(latest).isEqualTo(DataActivityModel(hasActivityIn = true, hasActivityOut = true))

        cb.onDataActivity(DATA_ACTIVITY_NONE)
        assertThat(latest)
            .isEqualTo(DataActivityModel(hasActivityIn = false, hasActivityOut = false))

        cb.onDataActivity(DATA_ACTIVITY_DORMANT)
        assertThat(latest)
            .isEqualTo(DataActivityModel(hasActivityIn = false, hasActivityOut = false))

        cb.onDataActivity(1234)
        assertThat(latest)
            .isEqualTo(DataActivityModel(hasActivityIn = false, hasActivityOut = false))
    }

    @Test
    fun networkNameForSubId_updates() = runTest {
        val latest by underTest.carrierName.collectLastValue()

        subscriptionModel.setValue(
            SubscriptionModel(
                subscriptionId = SUB_1_ID,
                carrierName = DEFAULT_NAME,
                profileClass = PROFILE_CLASS_UNSET,
            )
        )

        assertThat(latest?.name).isEqualTo(DEFAULT_NAME)

        val updatedName = "Derived Carrier"
        subscriptionModel.setValue(
            SubscriptionModel(
                subscriptionId = SUB_1_ID,
                carrierName = updatedName,
                profileClass = PROFILE_CLASS_UNSET,
            )
        )

        assertThat(latest?.name).isEqualTo(updatedName)
    }

    @Test
    fun networkNameForSubId_defaultWhenSubscriptionModelNull() = runTest {
        val latest by underTest.carrierName.collectLastValue()

        subscriptionModel.setValue(null)

        assertThat(latest?.name).isEqualTo(DEFAULT_NAME)
    }

    @Test
    fun networkName_default() = runTest {
        val latest by underTest.networkName.collectLastValue()

        assertThat(latest).isEqualTo(DEFAULT_NAME_MODEL)
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_usesBroadcastInfo_returnsDerived() = runTest {
        val latest by underTest.networkName.collectLastValue()

        val intent = spnIntent()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        captor.lastValue.onReceive(context, intent)

        // spnIntent() sets all values to true and test strings
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_usesBroadcastInfo_returnsDerived_flagOff() = runTest {
        val latest by underTest.networkName.collectLastValue()

        val intent = spnIntent()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        captor.lastValue.onReceive(context, intent)

        // spnIntent() sets all values to true and test strings
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_broadcastNotForThisSubId_keepsOldValue() = runTest {
        val latest by underTest.networkName.collectLastValue()

        val intent = spnIntent()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        captor.lastValue.onReceive(context, intent)

        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))

        // WHEN an intent with a different subId is sent
        val wrongSubIntent = spnIntent(subId = 101)

        captor.lastValue.onReceive(context, wrongSubIntent)

        // THEN the previous intent's name is still used
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_broadcastNotForThisSubId_keepsOldValue_flagOff() = runTest {
        val latest by underTest.networkName.collectLastValue()

        val intent = spnIntent()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        captor.lastValue.onReceive(context, intent)

        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))

        // WHEN an intent with a different subId is sent
        val wrongSubIntent = spnIntent(subId = 101)

        captor.lastValue.onReceive(context, wrongSubIntent)

        // THEN the previous intent's name is still used
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_broadcastHasNoData_updatesToDefault() = runTest {
        val latest by underTest.networkName.collectLastValue()

        val intent = spnIntent()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        captor.lastValue.onReceive(context, intent)

        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))

        val intentWithoutInfo = spnIntent(showSpn = false, showPlmn = false)

        captor.lastValue.onReceive(context, intentWithoutInfo)

        assertThat(latest).isEqualTo(DEFAULT_NAME_MODEL)
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_broadcastHasNoData_updatesToDefault_flagOff() = runTest {
        val latest by underTest.networkName.collectLastValue()

        val intent = spnIntent()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        captor.lastValue.onReceive(context, intent)

        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))

        val intentWithoutInfo = spnIntent(showSpn = false, showPlmn = false)

        captor.lastValue.onReceive(context, intentWithoutInfo)

        assertThat(latest).isEqualTo(DEFAULT_NAME_MODEL)
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_usingEagerStrategy_retainsNameBetweenSubscribers() = runTest {
        // Use the [StateFlow.value] getter so we can prove that the collection happens
        // even when there is no [Job]

        // Starts out default
        val latest by underTest.networkName.collectLastValue()
        assertThat(latest).isEqualTo(DEFAULT_NAME_MODEL)

        val intent = spnIntent()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        captor.lastValue.onReceive(context, intent)

        // The value is still there despite no active subscribers
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_usingEagerStrategy_retainsNameBetweenSubscribers_flagOff() = runTest {
        // Use the [StateFlow.value] getter so we can prove that the collection happens
        // even when there is no [Job]

        // Starts out default
        val latest by underTest.networkName.collectLastValue()
        assertThat(latest).isEqualTo(DEFAULT_NAME_MODEL)

        val intent = spnIntent()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        captor.lastValue.onReceive(context, intent)

        // The value is still there despite no active subscribers
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_allFieldsSet_prioritizesDataSpnOverSpn() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())

        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = SPN,
                dataSpn = DATA_SPN,
                showPlmn = true,
                plmn = PLMN,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_spnAndPlmn_fallbackToSpnWhenNullDataSpn() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())

        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = SPN,
                dataSpn = null,
                showPlmn = true,
                plmn = PLMN,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$SPN"))
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_allFieldsSet_flagOff() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())

        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = SPN,
                dataSpn = DATA_SPN,
                showPlmn = true,
                plmn = PLMN,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_showPlmn_plmnNotNull_showSpn_spnNull_dataSpnNotNull() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = null,
                dataSpn = DATA_SPN,
                showPlmn = true,
                plmn = PLMN,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_showPlmn_plmnNotNull_showSpn_spnNotNull_dataSpnNull() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = SPN,
                dataSpn = null,
                showPlmn = true,
                plmn = PLMN,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$SPN"))
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_showPlmn_plmnNotNull_showSpn_spnNull_dataSpnNotNull_flagOff() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = null,
                dataSpn = DATA_SPN,
                showPlmn = true,
                plmn = PLMN,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN$SEP$DATA_SPN"))
    }

    @Test
    fun networkName_showPlmn_noShowSPN() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = false,
                spn = SPN,
                dataSpn = DATA_SPN,
                showPlmn = true,
                plmn = PLMN,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$PLMN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_showPlmn_plmnNull_showSpn() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = SPN,
                dataSpn = DATA_SPN,
                showPlmn = true,
                plmn = null,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$DATA_SPN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_showPlmn_plmnNull_showSpn_dataSpnNull() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = SPN,
                dataSpn = null,
                showPlmn = true,
                plmn = null,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$SPN"))
    }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_showPlmn_plmnNull_showSpn_bothSpnNull() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = null,
                dataSpn = null,
                showPlmn = true,
                plmn = null,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(DEFAULT_NAME_MODEL)
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN)
    fun networkName_showPlmn_plmnNull_showSpn_flagOff() = runTest {
        val latest by underTest.networkName.collectLastValue()
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        val intent =
            spnIntent(
                subId = SUB_1_ID,
                showSpn = true,
                spn = SPN,
                dataSpn = DATA_SPN,
                showPlmn = true,
                plmn = null,
            )
        captor.lastValue.onReceive(context, intent)
        assertThat(latest).isEqualTo(NetworkNameModel.IntentDerived("$DATA_SPN"))
    }

    @Test
    fun operatorAlphaShort_tracked() = runTest {
        val latest by underTest.operatorAlphaShort.collectLastValue()

        val shortName = "short name"
        val serviceState = ServiceState()
        serviceState.setOperatorName(
            /* longName */ "long name",
            /* shortName */ shortName,
            /* numeric */ "12345",
        )

        getTelephonyCallbackForType<ServiceStateListener>().onServiceStateChanged(serviceState)

        assertThat(latest).isEqualTo(shortName)
    }

    @Test
    fun isInService_notIwlan() = runTest {
        val latest by underTest.isInService.collectLastValue()

        val nriInService =
            NetworkRegistrationInfo.Builder()
                .setDomain(DOMAIN_PS)
                .setTransportType(TRANSPORT_TYPE_WWAN)
                .setRegistrationState(REGISTRATION_STATE_HOME)
                .build()

        getTelephonyCallbackForType<ServiceStateListener>()
            .onServiceStateChanged(
                ServiceState().also {
                    it.voiceRegState = STATE_IN_SERVICE
                    it.addNetworkRegistrationInfo(nriInService)
                }
            )

        assertThat(latest).isTrue()

        getTelephonyCallbackForType<ServiceStateListener>()
            .onServiceStateChanged(
                ServiceState().also {
                    it.voiceRegState = STATE_OUT_OF_SERVICE
                    it.addNetworkRegistrationInfo(nriInService)
                }
            )
        assertThat(latest).isTrue()

        val nriNotInService =
            NetworkRegistrationInfo.Builder()
                .setDomain(DOMAIN_PS)
                .setTransportType(TRANSPORT_TYPE_WWAN)
                .setRegistrationState(REGISTRATION_STATE_DENIED)
                .build()
        getTelephonyCallbackForType<ServiceStateListener>()
            .onServiceStateChanged(
                ServiceState().also {
                    it.voiceRegState = STATE_OUT_OF_SERVICE
                    it.addNetworkRegistrationInfo(nriNotInService)
                }
            )
        assertThat(latest).isFalse()
    }

    @Test
    fun isInService_isIwlan_voiceOutOfService_dataInService() = runTest {
        val latest by underTest.isInService.collectLastValue()

        val iwlanData =
            NetworkRegistrationInfo.Builder()
                .setDomain(DOMAIN_PS)
                .setTransportType(TRANSPORT_TYPE_WLAN)
                .setRegistrationState(REGISTRATION_STATE_HOME)
                .build()
        val serviceState =
            ServiceState().also {
                it.voiceRegState = STATE_OUT_OF_SERVICE
                it.addNetworkRegistrationInfo(iwlanData)
            }

        getTelephonyCallbackForType<ServiceStateListener>().onServiceStateChanged(serviceState)
        assertThat(latest).isFalse()
    }

    @Test
    fun isNonTerrestrial_updatesFromCallback0() = runTest {
        val latest by underTest.isNonTerrestrial.collectLastValue()

        // Starts out false
        assertThat(latest).isFalse()

        val callback = getTelephonyCallbackForType<CarrierRoamingNtnListener>()

        callback.onCarrierRoamingNtnModeChanged(true)
        assertThat(latest).isTrue()

        callback.onCarrierRoamingNtnModeChanged(false)
        assertThat(latest).isFalse()
    }

    @Test
    fun numberOfLevels_usesCarrierConfig() = runTest {
        val latest by underTest.numberOfLevels.collectLastValue()

        assertThat(latest).isEqualTo(DEFAULT_NUM_LEVELS)

        systemUiCarrierConfig.processNewCarrierConfig(
            testCarrierConfigWithOverride(KEY_INFLATE_SIGNAL_STRENGTH_BOOL, true)
        )

        assertThat(latest).isEqualTo(DEFAULT_NUM_LEVELS + 1)

        systemUiCarrierConfig.processNewCarrierConfig(
            testCarrierConfigWithOverride(KEY_INFLATE_SIGNAL_STRENGTH_BOOL, false)
        )

        assertThat(latest).isEqualTo(DEFAULT_NUM_LEVELS)
    }

    @Test
    fun inflateSignalStrength_usesCarrierConfig() = runTest {
        val latest by underTest.inflateSignalStrength.collectLastValue()

        assertThat(latest).isEqualTo(false)

        systemUiCarrierConfig.processNewCarrierConfig(
            testCarrierConfigWithOverride(KEY_INFLATE_SIGNAL_STRENGTH_BOOL, true)
        )

        assertThat(latest).isEqualTo(true)

        systemUiCarrierConfig.processNewCarrierConfig(
            testCarrierConfigWithOverride(KEY_INFLATE_SIGNAL_STRENGTH_BOOL, false)
        )

        assertThat(latest).isEqualTo(false)
    }

    @Test
    fun allowNetworkSliceIndicator_exposesCarrierConfigValue() = runTest {
        val latest by underTest.allowNetworkSliceIndicator.collectLastValue()

        systemUiCarrierConfig.processNewCarrierConfig(
            testCarrierConfigWithOverride(KEY_SHOW_5G_SLICE_ICON_BOOL, true)
        )

        assertThat(latest).isTrue()

        systemUiCarrierConfig.processNewCarrierConfig(
            testCarrierConfigWithOverride(KEY_SHOW_5G_SLICE_ICON_BOOL, false)
        )

        assertThat(latest).isFalse()
    }

    @Test
    fun isAllowedDuringAirplaneMode_alwaysFalse() = runTest {
        val latest by underTest.isAllowedDuringAirplaneMode.collectLastValue()

        assertThat(latest).isFalse()
    }

    @Test
    fun hasPrioritizedCaps_defaultFalse() = runTest {
        // stand up under-test to kick-off activation
        underTest

        assertThat(kairos.transact { underTest.hasPrioritizedNetworkCapabilities.sample() })
            .isFalse()
    }

    @Test
    fun hasPrioritizedCaps_trueWhenAvailable() = runTest {
        val latest by underTest.hasPrioritizedNetworkCapabilities.collectLastValue()

        val callback: NetworkCallback =
            argumentCaptor<NetworkCallback>()
                .apply { verify(connectivityManager).registerNetworkCallback(any(), capture()) }
                .lastValue

        callback.onAvailable(mock())

        assertThat(latest).isTrue()
    }

    @Test
    fun hasPrioritizedCaps_becomesFalseWhenNetworkLost() = runTest {
        val latest by underTest.hasPrioritizedNetworkCapabilities.collectLastValue()

        val callback: NetworkCallback =
            argumentCaptor<NetworkCallback>()
                .apply { verify(connectivityManager).registerNetworkCallback(any(), capture()) }
                .lastValue

        callback.onAvailable(mock())

        assertThat(latest).isTrue()

        callback.onLost(mock())

        assertThat(latest).isFalse()
    }

    private inline fun <reified T> Kosmos.getTelephonyCallbackForType(): T {
        return MobileTelephonyHelpers.getTelephonyCallbackForType(telephonyManager)
    }

    private fun carrierIdIntent(subId: Int = SUB_1_ID, carrierId: Int): Intent =
        Intent(TelephonyManager.ACTION_SUBSCRIPTION_CARRIER_IDENTITY_CHANGED).apply {
            putExtra(EXTRA_SUBSCRIPTION_ID, subId)
            putExtra(EXTRA_CARRIER_ID, carrierId)
        }

    private fun spnIntent(
        subId: Int = SUB_1_ID,
        showSpn: Boolean = true,
        spn: String? = SPN,
        dataSpn: String? = DATA_SPN,
        showPlmn: Boolean = true,
        plmn: String? = PLMN,
    ): Intent =
        Intent(TelephonyManager.ACTION_SERVICE_PROVIDERS_UPDATED).apply {
            putExtra(EXTRA_SUBSCRIPTION_INDEX, subId)
            putExtra(EXTRA_SHOW_SPN, showSpn)
            putExtra(EXTRA_SPN, spn)
            putExtra(EXTRA_DATA_SPN, dataSpn)
            putExtra(EXTRA_SHOW_PLMN, showPlmn)
            putExtra(EXTRA_PLMN, plmn)
        }

    companion object {
        private const val SUB_1_ID = 1

        private const val DEFAULT_NAME = "Fake Mobile Network"
        private val DEFAULT_NAME_MODEL = NetworkNameModel.Default(DEFAULT_NAME)
        private const val SEP = "-"

        private const val SPN = "testSpn"
        private const val DATA_SPN = "testDataSpn"
        private const val PLMN = "testPlmn"
    }
}
