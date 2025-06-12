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

import android.telephony.Annotation
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.DATA_ACTIVITY_NONE
import androidx.test.filters.SmallTest
import com.android.settingslib.SignalIcon
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.SysuiTestCase
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.map
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.model.FakeNetworkEventModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demoMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demoModeMobileConnectionDataSourceKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.wifiDataSource
import com.android.systemui.statusbar.pipeline.shared.data.model.toMobileDataActivityModel
import com.android.systemui.statusbar.pipeline.wifi.data.repository.demo.model.FakeWifiEventModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/**
 * Parameterized test for all of the common values of [FakeNetworkEventModel]. This test simply
 * verifies that passing the given model to [DemoMobileConnectionsRepositoryKairos] results in the
 * correct flows emitting from the given connection.
 */
@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
internal class DemoMobileConnectionKairosParameterizedTest(private val testCase: TestCase) :
    SysuiTestCase() {

    private val Kosmos.fakeWifiEventFlow by Fixture { MutableStateFlow<FakeWifiEventModel?>(null) }

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            wifiDataSource.stub { on { wifiEvents } doReturn fakeWifiEventFlow }
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun demoNetworkData() = runTest {
        val underTest by
            demoMobileConnectionsRepositoryKairos.mobileConnectionsBySubId
                .map { it[subId] }
                .collectLastValue()
        val networkModel =
            FakeNetworkEventModel.Mobile(
                level = testCase.level,
                dataType = testCase.dataType,
                subId = testCase.subId,
                carrierId = testCase.carrierId,
                inflateStrength = testCase.inflateStrength,
                activity = testCase.activity,
                carrierNetworkChange = testCase.carrierNetworkChange,
                roaming = testCase.roaming,
                name = "demo name",
                slice = testCase.slice,
            )
        demoModeMobileConnectionDataSourceKairos.fake.mobileEvents.emit(networkModel)
        assertConnection(underTest!!, networkModel)
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

                    // TODO(b/261029387): check these once we start handling them
                    assertThat(conn.isEmergencyOnly.sample()).isFalse()
                    assertThat(conn.isGsm.sample()).isFalse()
                    assertThat(conn.dataConnectionState.sample())
                        .isEqualTo(DataConnectionState.Connected)
                }
            }
            // MobileDisabled isn't combinatorial in nature, and is tested in
            // DemoMobileConnectionsRepositoryTest.kt
            else -> {}
        }
    }

    /** Matches [FakeNetworkEventModel] */
    internal data class TestCase(
        val level: Int,
        val dataType: SignalIcon.MobileIconGroup,
        val subId: Int,
        val carrierId: Int,
        val inflateStrength: Boolean,
        @Annotation.DataActivityType val activity: Int,
        val carrierNetworkChange: Boolean,
        val roaming: Boolean,
        val name: String,
        val slice: Boolean,
        val ntn: Boolean,
    ) {
        override fun toString(): String {
            return "INPUT(level=$level, " +
                "dataType=${dataType.name}, " +
                "subId=$subId, " +
                "carrierId=$carrierId, " +
                "inflateStrength=$inflateStrength, " +
                "activity=$activity, " +
                "carrierNetworkChange=$carrierNetworkChange, " +
                "roaming=$roaming, " +
                "name=$name," +
                "slice=$slice" +
                "ntn=$ntn)"
        }

        // Convenience for iterating test data and creating new cases
        fun modifiedBy(
            level: Int? = null,
            dataType: SignalIcon.MobileIconGroup? = null,
            subId: Int? = null,
            carrierId: Int? = null,
            inflateStrength: Boolean? = null,
            @Annotation.DataActivityType activity: Int? = null,
            carrierNetworkChange: Boolean? = null,
            roaming: Boolean? = null,
            name: String? = null,
            slice: Boolean? = null,
            ntn: Boolean? = null,
        ): TestCase =
            TestCase(
                level = level ?: this.level,
                dataType = dataType ?: this.dataType,
                subId = subId ?: this.subId,
                carrierId = carrierId ?: this.carrierId,
                inflateStrength = inflateStrength ?: this.inflateStrength,
                activity = activity ?: this.activity,
                carrierNetworkChange = carrierNetworkChange ?: this.carrierNetworkChange,
                roaming = roaming ?: this.roaming,
                name = name ?: this.name,
                slice = slice ?: this.slice,
                ntn = ntn ?: this.ntn,
            )
    }

    companion object {
        private val subId = 1

        private val booleanList = listOf(true, false)
        private val levels = listOf(0, 1, 2, 3)
        private val dataTypes =
            listOf(
                TelephonyIcons.THREE_G,
                TelephonyIcons.LTE,
                TelephonyIcons.FOUR_G,
                TelephonyIcons.NR_5G,
                TelephonyIcons.NR_5G_PLUS,
            )
        private val carrierIds = listOf(1, 10, 100)
        private val inflateStrength = booleanList
        private val activity =
            listOf(
                TelephonyManager.DATA_ACTIVITY_NONE,
                TelephonyManager.DATA_ACTIVITY_IN,
                TelephonyManager.DATA_ACTIVITY_OUT,
                TelephonyManager.DATA_ACTIVITY_INOUT,
            )
        private val carrierNetworkChange = booleanList
        // false first so the base case doesn't have roaming set (more common)
        private val roaming = listOf(false, true)
        private val names = listOf("name 1", "name 2")
        private val slice = listOf(false, true)
        private val ntn = listOf(false, true)

        @Parameters(name = "{0}") @JvmStatic fun data() = testData()

        /**
         * Generate some test data. For the sake of convenience, we'll parameterize only non-null
         * network event data. So given the lists of test data:
         * ```
         *    list1 = [1, 2, 3]
         *    list2 = [false, true]
         *    list3 = [a, b, c]
         * ```
         *
         * We'll generate test cases for:
         *
         * Test (1, false, a) Test (2, false, a) Test (3, false, a) Test (1, true, a) Test (1,
         * false, b) Test (1, false, c)
         *
         * NOTE: this is not a combinatorial product of all of the possible sets of parameters.
         * Since this test is built to exercise demo mode, the general approach is to define a
         * fully-formed "base case", and from there to make sure to use every valid parameter once,
         * by defining the rest of the test cases against the base case. Specific use-cases can be
         * added to the non-parameterized test, or manually below the generated test cases.
         */
        private fun testData(): List<TestCase> {
            val testSet = mutableSetOf<TestCase>()

            val baseCase =
                TestCase(
                    levels.first(),
                    dataTypes.first(),
                    subId,
                    carrierIds.first(),
                    inflateStrength.first(),
                    activity.first(),
                    carrierNetworkChange.first(),
                    roaming.first(),
                    names.first(),
                    slice.first(),
                    ntn.first(),
                )

            val tail =
                sequenceOf(
                        levels.map { baseCase.modifiedBy(level = it) },
                        dataTypes.map { baseCase.modifiedBy(dataType = it) },
                        carrierIds.map { baseCase.modifiedBy(carrierId = it) },
                        inflateStrength.map { baseCase.modifiedBy(inflateStrength = it) },
                        activity.map { baseCase.modifiedBy(activity = it) },
                        carrierNetworkChange.map { baseCase.modifiedBy(carrierNetworkChange = it) },
                        roaming.map { baseCase.modifiedBy(roaming = it) },
                        names.map { baseCase.modifiedBy(name = it) },
                        slice.map { baseCase.modifiedBy(slice = it) },
                        ntn.map { baseCase.modifiedBy(ntn = it) },
                    )
                    .flatten()

            testSet.add(baseCase)
            tail.toCollection(testSet)

            return testSet.toList()
        }
    }
}
