/*
 * Copyright (C) 2025 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.SignalIcon
import com.android.settingslib.mobile.MobileMappings
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.KairosBuilder
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.State
import com.android.systemui.kairos.activateKairosActivatable
import com.android.systemui.kairos.asIncremental
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.kairos
import com.android.systemui.kairos.map
import com.android.systemui.kairos.mapValues
import com.android.systemui.kairos.stateOf
import com.android.systemui.kairosBuilder
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.tableLogBufferFactory
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.ResolvedNetworkType
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileMappingsProxy
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.policy.data.repository.FakeUserSetupRepository
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileIconsInteractorKairosAdapterTest : MobileIconsInteractorTestBase() {
    override fun Kosmos.createInteractor(): MobileIconsInteractor {
        val userSetupRepo = FakeUserSetupRepository()
        val repoK =
            MobileConnectionsRepoWrapper(connectionsRepository).also {
                activateKairosActivatable(it)
            }
        val kairosInteractor =
            MobileIconsInteractorKairosImpl(
                    mobileConnectionsRepo = repoK,
                    carrierConfigTracker = carrierConfigTracker,
                    tableLogger = mock(),
                    connectivityRepository = connectivityRepository,
                    userSetupRepo = userSetupRepo,
                    context = context,
                    featureFlagsClassic = featureFlagsClassic,
                )
                .also { activateKairosActivatable(it) }
        return MobileIconsInteractorKairosAdapter(
                kairosInteractor = kairosInteractor,
                repo = connectionsRepository,
                repoK = repoK,
                kairosNetwork = kairos,
                scope = applicationCoroutineScope,
                context = context,
                mobileMappingsProxy = mobileMappingsProxy,
                userSetupRepo = userSetupRepo,
                logFactory = tableLogBufferFactory,
            )
            .also {
                activateKairosActivatable(it)
                runCurrent()
            }
    }

    /** Allows us to wrap a (likely fake) MobileConnectionsRepository into a Kairos version. */
    private class MobileConnectionsRepoWrapper(val unwrapped: MobileConnectionsRepository) :
        MobileConnectionsRepositoryKairos, KairosBuilder by kairosBuilder() {

        override val mobileConnectionsBySubId: Incremental<Int, MobileConnectionRepositoryKairos> =
            buildIncremental {
                unwrapped.subscriptions
                    .toState()
                    .map { it.associate { it.subscriptionId to Unit } }
                    .asIncremental()
                    .mapValues { (subId, _) ->
                        buildSpec { wrapRepo(unwrapped.getRepoForSubId(subId)) }
                    }
                    .applyLatestSpecForKey()
            }
        override val subscriptions: State<Collection<SubscriptionModel>> = buildState {
            unwrapped.subscriptions.toState()
        }
        override val activeMobileDataSubscriptionId: State<Int?> = buildState {
            unwrapped.activeMobileDataSubscriptionId.toState()
        }
        override val activeMobileDataRepository: State<MobileConnectionRepositoryKairos?> =
            buildState {
                unwrapped.activeMobileDataRepository.toState().mapLatestBuild {
                    it?.let { wrapRepo(it) }
                }
            }
        override val activeSubChangedInGroupEvent: Events<Unit> = buildEvents {
            unwrapped.activeSubChangedInGroupEvent.toEvents()
        }
        override val defaultDataSubId: State<Int?> = buildState {
            unwrapped.defaultDataSubId.toState()
        }
        override val mobileIsDefault: State<Boolean> = buildState {
            unwrapped.mobileIsDefault.toState()
        }
        override val hasCarrierMergedConnection: State<Boolean> = buildState {
            unwrapped.hasCarrierMergedConnection.toState(false)
        }
        override val defaultConnectionIsValidated: State<Boolean> = buildState {
            unwrapped.defaultConnectionIsValidated.toState()
        }
        override val defaultDataSubRatConfig: State<MobileMappings.Config> = buildState {
            unwrapped.defaultDataSubRatConfig.toState()
        }
        override val defaultMobileIconMapping: State<Map<String, SignalIcon.MobileIconGroup>> =
            buildState {
                unwrapped.defaultMobileIconMapping.toState(emptyMap())
            }
        override val defaultMobileIconGroup: State<SignalIcon.MobileIconGroup> = buildState {
            unwrapped.defaultMobileIconGroup.toState(TelephonyIcons.THREE_G)
        }
        override val isDeviceEmergencyCallCapable: State<Boolean> = buildState {
            unwrapped.isDeviceEmergencyCallCapable.toState()
        }
        override val isAnySimSecure: State<Boolean> = buildState {
            unwrapped.isDeviceEmergencyCallCapable.toState()
        }
        override val isInEcmMode: State<Boolean> = stateOf(false)
    }

    private class MobileConnectionRepoWrapper(
        override val subId: Int,
        override val carrierId: State<Int>,
        override val inflateSignalStrength: State<Boolean>,
        override val allowNetworkSliceIndicator: State<Boolean>,
        override val tableLogBuffer: TableLogBuffer,
        override val isEmergencyOnly: State<Boolean>,
        override val isRoaming: State<Boolean>,
        override val operatorAlphaShort: State<String?>,
        override val isInService: State<Boolean>,
        override val isNonTerrestrial: State<Boolean>,
        override val isGsm: State<Boolean>,
        override val cdmaLevel: State<Int>,
        override val primaryLevel: State<Int>,
        override val satelliteLevel: State<Int>,
        override val dataConnectionState: State<DataConnectionState>,
        override val dataActivityDirection: State<DataActivityModel>,
        override val carrierNetworkChangeActive: State<Boolean>,
        override val resolvedNetworkType: State<ResolvedNetworkType>,
        override val numberOfLevels: State<Int>,
        override val dataEnabled: State<Boolean>,
        override val cdmaRoaming: State<Boolean>,
        override val networkName: State<NetworkNameModel>,
        override val carrierName: State<NetworkNameModel>,
        override val isAllowedDuringAirplaneMode: State<Boolean>,
        override val hasPrioritizedNetworkCapabilities: State<Boolean>,
        override val isInEcmMode: State<Boolean>,
    ) : MobileConnectionRepositoryKairos

    companion object {
        /** Allows us to wrap a (likely fake) MobileConnectionRepository into a Kairos version. */
        fun BuildScope.wrapRepo(
            conn: MobileConnectionRepository
        ): MobileConnectionRepositoryKairos =
            with(conn) {
                MobileConnectionRepoWrapper(
                    subId = subId,
                    carrierId = carrierId.toState(),
                    inflateSignalStrength = inflateSignalStrength.toState(),
                    allowNetworkSliceIndicator = allowNetworkSliceIndicator.toState(),
                    tableLogBuffer = tableLogBuffer,
                    isEmergencyOnly = isEmergencyOnly.toState(),
                    isRoaming = isRoaming.toState(),
                    operatorAlphaShort = operatorAlphaShort.toState(),
                    isInService = isInService.toState(),
                    isNonTerrestrial = isNonTerrestrial.toState(),
                    isGsm = isGsm.toState(),
                    cdmaLevel = cdmaLevel.toState(),
                    primaryLevel = primaryLevel.toState(),
                    satelliteLevel = satelliteLevel.toState(),
                    dataConnectionState = dataConnectionState.toState(),
                    dataActivityDirection = dataActivityDirection.toState(),
                    carrierNetworkChangeActive = carrierNetworkChangeActive.toState(),
                    resolvedNetworkType = resolvedNetworkType.toState(),
                    numberOfLevels = numberOfLevels.toState(),
                    dataEnabled = dataEnabled.toState(),
                    cdmaRoaming = cdmaRoaming.toState(),
                    networkName = networkName.toState(),
                    carrierName = carrierName.toState(),
                    isAllowedDuringAirplaneMode = isAllowedDuringAirplaneMode.toState(),
                    hasPrioritizedNetworkCapabilities = hasPrioritizedNetworkCapabilities.toState(),
                    isInEcmMode = stateOf(false),
                )
            }
    }
}
