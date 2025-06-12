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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.ActivatedKairosFixture
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.statusbar.connectivity.MobileIconCarrierIdOverridesFake
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.airplaneModeInteractor
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorKairosImpl
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.mobileIconsInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.shared.ConnectivityConstants
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class LocationBasedMobileIconViewModelKairosTest : SysuiTestCase() {

    private val Kosmos.commonImpl: MobileIconViewModelKairosCommon by ActivatedKairosFixture {
        MobileIconViewModelKairos(
            SUB_1_ID,
            interactor,
            airplaneModeInteractor,
            constants,
            featureFlagsClassic,
        )
    }

    private val Kosmos.homeIcon: HomeMobileIconViewModelKairos by
        Kosmos.Fixture { HomeMobileIconViewModelKairos(commonImpl, mock()) }

    private val Kosmos.qsIcon: QsMobileIconViewModelKairos by
        Kosmos.Fixture { QsMobileIconViewModelKairos(commonImpl) }

    private val Kosmos.keyguardIcon: KeyguardMobileIconViewModelKairos by
        Kosmos.Fixture { KeyguardMobileIconViewModelKairos(commonImpl) }

    private val Kosmos.iconsInteractor: MobileIconsInteractorKairos
        get() = mobileIconsInteractorKairos

    private val Kosmos.interactor: MobileIconInteractorKairos by
        Kosmos.Fixture {
            MobileIconInteractorKairosImpl(
                iconsInteractor.activeDataConnectionHasDataEnabled,
                iconsInteractor.alwaysShowDataRatIcon,
                iconsInteractor.alwaysUseCdmaLevel,
                iconsInteractor.isSingleCarrier,
                iconsInteractor.mobileIsDefault,
                iconsInteractor.defaultMobileIconMapping,
                iconsInteractor.defaultMobileIconGroup,
                iconsInteractor.isDefaultConnectionFailed,
                iconsInteractor.isForceHidden,
                repository,
                context,
                MobileIconCarrierIdOverridesFake(),
            )
        }

    private val Kosmos.repository: FakeMobileConnectionRepositoryKairos by
        Kosmos.Fixture {
            FakeMobileConnectionRepositoryKairos(SUB_1_ID, kairos, tableLogBuffer).apply {
                isInService.setValue(true)
                cdmaLevel.setValue(1)
                primaryLevel.setValue(1)
                isEmergencyOnly.setValue(false)
                numberOfLevels.setValue(4)
                resolvedNetworkType.setValue(
                    ResolvedNetworkType.DefaultNetworkType(lookupKey = "3G")
                )
                dataConnectionState.setValue(DataConnectionState.Connected)
            }
        }

    private val Kosmos.constants: ConnectivityConstants by Kosmos.Fixture { mock() }
    private val Kosmos.tableLogBuffer by
        Kosmos.Fixture { logcatTableLogBuffer(this, "LocationBasedMobileIconViewModelTest") }

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            mobileConnectionsRepositoryKairos =
                fakeMobileConnectionsRepositoryKairos.apply {
                    setActiveMobileDataSubscriptionId(SUB_1_ID)
                    subscriptions.setValue(
                        listOf(
                            SubscriptionModel(
                                SUB_1_ID,
                                carrierName = "carrierName",
                                profileClass = 0,
                            )
                        )
                    )
                }
            connectivityRepository.fake.apply { setMobileConnected() }
            featureFlagsClassic.fake.apply {
                set(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS, true)
            }
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    @Test
    fun locationBasedViewModelsReceiveSameIconIdWhenCommonImplUpdates() = runTest {
        repository.dataEnabled.setValue(true)
        repository.isInService.setValue(true)

        val latestHome by homeIcon.icon.collectLastValue()
        val latestQs by qsIcon.icon.collectLastValue()
        val latestKeyguard by keyguardIcon.icon.collectLastValue()

        var expected = defaultSignal(level = 1)

        assertThat(latestHome).isEqualTo(expected)
        assertThat(latestQs).isEqualTo(expected)
        assertThat(latestKeyguard).isEqualTo(expected)

        repository.setAllLevels(2)
        expected = defaultSignal(level = 2)

        assertThat(latestHome).isEqualTo(expected)
        assertThat(latestQs).isEqualTo(expected)
        assertThat(latestKeyguard).isEqualTo(expected)
    }

    companion object {
        private const val SUB_1_ID = 1
        private const val NUM_LEVELS = 4

        /** Convenience constructor for these tests */
        fun defaultSignal(level: Int = 1): SignalIconModel {
            return SignalIconModel.Cellular(
                level,
                NUM_LEVELS,
                showExclamationMark = false,
                carrierNetworkChange = false,
            )
        }
    }
}
