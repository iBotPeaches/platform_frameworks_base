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

import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.demoModeController
import com.android.systemui.demomode.DemoMode
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.DemoMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.validMobileEvent
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * The switcher acts as a dispatcher to either the `prod` or `demo` versions of the repository
 * interface it's switching on. These tests just need to verify that the entire interface properly
 * switches over when the value of `demoMode` changes
 */
@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileRepositorySwitcherKairosTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            demoModeController.stub {
                // Never start in demo mode
                on { isInDemoMode } doReturn false
            }
            wifiDataSource.stub { on { wifiEvents } doReturn MutableStateFlow(null) }
        }

    private val Kosmos.underTest
        get() = mobileRepositorySwitcherKairos

    private val Kosmos.realRepo
        get() = mobileConnectionsRepositoryKairosImpl

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun activeRepoMatchesDemoModeSetting() = runTest {
        demoModeController.stub { on { isInDemoMode } doReturn false }

        val latest by underTest.activeRepo.collectLastValue()

        assertThat(latest).isEqualTo(realRepo)

        startDemoMode()

        assertThat(latest).isInstanceOf(DemoMobileConnectionsRepositoryKairos::class.java)

        finishDemoMode()

        assertThat(latest).isEqualTo(realRepo)
    }

    @Test
    fun subscriptionListUpdatesWhenDemoModeChanges() = runTest {
        demoModeController.stub { on { isInDemoMode } doReturn false }

        subscriptionManager.stub {
            on { completeActiveSubscriptionInfoList } doReturn listOf(SUB_1, SUB_2)
        }

        val latest by underTest.subscriptions.collectLastValue()

        // The real subscriptions has 2 subs
        getSubscriptionCallback().onSubscriptionsChanged()

        assertThat(latest).isEqualTo(listOf(MODEL_1, MODEL_2))

        // Demo mode turns on, and we should see only the demo subscriptions
        startDemoMode()
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(validMobileEvent(subId = 3))

        // Demo mobile connections repository makes arbitrarily-formed subscription info
        // objects, so just validate the data we care about
        assertThat(latest).hasSize(1)
        assertThat(latest!!.first().subscriptionId).isEqualTo(3)

        finishDemoMode()

        assertThat(latest).isEqualTo(listOf(MODEL_1, MODEL_2))
    }

    private fun KairosTestScope.startDemoMode() {
        demoModeController.stub { on { isInDemoMode } doReturn true }
        getDemoModeCallback().onDemoModeStarted()
    }

    private fun KairosTestScope.finishDemoMode() {
        demoModeController.stub { on { isInDemoMode } doReturn false }
        getDemoModeCallback().onDemoModeFinished()
    }

    private fun KairosTestScope.getSubscriptionCallback():
        SubscriptionManager.OnSubscriptionsChangedListener =
        argumentCaptor<SubscriptionManager.OnSubscriptionsChangedListener>()
            .apply {
                verify(subscriptionManager).addOnSubscriptionsChangedListener(any(), capture())
            }
            .lastValue

    private fun KairosTestScope.getDemoModeCallback(): DemoMode =
        argumentCaptor<DemoMode>()
            .apply { verify(demoModeController).addCallback(capture()) }
            .lastValue

    companion object {
        private const val SUB_1_ID = 1
        private const val SUB_1_NAME = "Carrier $SUB_1_ID"
        private val SUB_1: SubscriptionInfo = mock {
            on { subscriptionId } doReturn SUB_1_ID
            on { carrierName } doReturn SUB_1_NAME
            on { profileClass } doReturn PROFILE_CLASS_UNSET
        }
        private val MODEL_1 =
            SubscriptionModel(
                subscriptionId = SUB_1_ID,
                carrierName = SUB_1_NAME,
                profileClass = PROFILE_CLASS_UNSET,
            )

        private const val SUB_2_ID = 2
        private const val SUB_2_NAME = "Carrier $SUB_2_ID"
        private val SUB_2: SubscriptionInfo = mock {
            on { subscriptionId } doReturn SUB_2_ID
            on { carrierName } doReturn SUB_2_NAME
            on { profileClass } doReturn PROFILE_CLASS_UNSET
        }
        private val MODEL_2 =
            SubscriptionModel(
                subscriptionId = SUB_2_ID,
                carrierName = SUB_2_NAME,
                profileClass = PROFILE_CLASS_UNSET,
            )
    }
}
