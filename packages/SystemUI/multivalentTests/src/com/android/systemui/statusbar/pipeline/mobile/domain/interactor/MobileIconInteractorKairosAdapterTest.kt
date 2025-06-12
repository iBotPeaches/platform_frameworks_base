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
import com.android.settingslib.mobile.MobileIconCarrierIdOverrides
import com.android.systemui.activated
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.State
import com.android.systemui.kairos.asIncremental
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.combine
import com.android.systemui.kairos.launchKairosNetwork
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorKairosAdapterTest.Companion.wrapRepo
import com.android.systemui.util.mockito.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.runner.RunWith

@OptIn(ExperimentalKairosApi::class, ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileIconInteractorKairosAdapterTest : MobileIconInteractorTestBase() {

    var job: Job? = null
    val kairosNetwork = testScope.backgroundScope.launchKairosNetwork()

    override fun createInteractor(overrides: MobileIconCarrierIdOverrides): MobileIconInteractor {
        lateinit var result: MobileIconInteractor
        job?.cancel()
        job =
            testScope.backgroundScope.launch {
                kairosNetwork.activateSpec {
                    val wrapped = wrap(mobileIconsInteractor)
                    result =
                        MobileIconInteractorKairosAdapter(
                            kairosImpl =
                                activated {
                                    MobileIconInteractorKairosImpl(
                                        defaultSubscriptionHasDataEnabled =
                                            wrapped.activeDataConnectionHasDataEnabled,
                                        alwaysShowDataRatIcon = wrapped.alwaysShowDataRatIcon,
                                        alwaysUseCdmaLevel = wrapped.alwaysUseCdmaLevel,
                                        isSingleCarrier = wrapped.isSingleCarrier,
                                        mobileIsDefault = wrapped.mobileIsDefault,
                                        defaultMobileIconMapping = wrapped.defaultMobileIconMapping,
                                        defaultMobileIconGroup = wrapped.defaultMobileIconGroup,
                                        isDefaultConnectionFailed =
                                            wrapped.isDefaultConnectionFailed,
                                        isForceHidden = wrapped.isForceHidden,
                                        connectionRepository = wrapRepo(connectionRepository),
                                        context = context,
                                        carrierIdOverrides = overrides,
                                    )
                                }
                        )
                    Unit
                }
            }
        testScope.runCurrent() // ensure the lateinit is set
        return result
    }

    /** Allows us to wrap a (likely fake) MobileIconsInteractor into a Kairos version. */
    private fun BuildScope.wrap(interactor: MobileIconsInteractor): MobileIconsInteractorKairos {
        val filteredSubscriptions = interactor.filteredSubscriptions.toState(emptyList())
        val icons = interactor.icons.toState()
        return InteractorWrapper(
            mobileIsDefault = interactor.mobileIsDefault.toState(),
            filteredSubscriptions = filteredSubscriptions,
            icons =
                combine(filteredSubscriptions, icons) { subs, icons ->
                        subs.zip(icons).associate { (subModel, icon) ->
                            subModel.subscriptionId to buildSpec { wrap(icon) }
                        }
                    }
                    .asIncremental()
                    .applyLatestSpecForKey(),
            isStackable = interactor.isStackable.toState(false),
            activeDataConnectionHasDataEnabled =
                interactor.activeDataConnectionHasDataEnabled.toState(),
            activeDataIconInteractor =
                interactor.activeDataIconInteractor.toState().mapLatestBuild {
                    it?.let { wrap(it) }
                },
            alwaysShowDataRatIcon = interactor.alwaysShowDataRatIcon.toState(),
            alwaysUseCdmaLevel = interactor.alwaysUseCdmaLevel.toState(),
            isSingleCarrier = interactor.isSingleCarrier.toState(),
            defaultMobileIconMapping = interactor.defaultMobileIconMapping.toState(),
            defaultMobileIconGroup = interactor.defaultMobileIconGroup.toState(),
            isDefaultConnectionFailed = interactor.isDefaultConnectionFailed.toState(),
            isUserSetUp = interactor.isUserSetUp.toState(),
            isForceHidden = interactor.isForceHidden.toState(false),
            isDeviceInEmergencyCallsOnlyMode =
                interactor.isDeviceInEmergencyCallsOnlyMode.toState(false),
        )
    }

    private fun BuildScope.wrap(interactor: MobileIconInteractor): MobileIconInteractorKairos =
        // unused in tests
        mock()

    private class InteractorWrapper(
        override val mobileIsDefault: State<Boolean>,
        override val filteredSubscriptions: State<List<SubscriptionModel>>,
        override val icons: Incremental<Int, MobileIconInteractorKairos>,
        override val isStackable: State<Boolean>,
        override val activeDataConnectionHasDataEnabled: State<Boolean>,
        override val activeDataIconInteractor: State<MobileIconInteractorKairos?>,
        override val alwaysShowDataRatIcon: State<Boolean>,
        override val alwaysUseCdmaLevel: State<Boolean>,
        override val isSingleCarrier: State<Boolean>,
        override val defaultMobileIconMapping: State<Map<String, SignalIcon.MobileIconGroup>>,
        override val defaultMobileIconGroup: State<SignalIcon.MobileIconGroup>,
        override val isDefaultConnectionFailed: State<Boolean>,
        override val isUserSetUp: State<Boolean>,
        override val isForceHidden: State<Boolean>,
        override val isDeviceInEmergencyCallsOnlyMode: State<Boolean>,
    ) : MobileIconsInteractorKairos
}
