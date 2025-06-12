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

package com.android.systemui.statusbar.pipeline.mobile.data.repository.prod

import android.testing.TestableLooper
import androidx.test.filters.SmallTest
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.launchKairosNetwork
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeCarrierConfigRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionsRepositoryKairosAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.mockito.Mockito
import org.mockito.kotlin.mock

@OptIn(ExperimentalKairosApi::class, ExperimentalCoroutinesApi::class)
@SmallTest
// This is required because our [SubscriptionManager.OnSubscriptionsChangedListener] uses a looper
// to run the callback and this makes the looper place nicely with TestScope etc.
@TestableLooper.RunWithLooper
class MobileConnectionsRepositoryKairosAdapterTest :
    MobileConnectionsRepositoryTest<MobileConnectionsRepositoryKairosAdapter>() {

    var job: Job? = null
    val kairosNetwork = testScope.backgroundScope.launchKairosNetwork()

    override fun recreateRepo(): MobileConnectionsRepositoryKairosAdapter {
        val carrierConfigRepo = FakeCarrierConfigRepository()
        lateinit var connectionsRepo: MobileConnectionsRepositoryKairosImpl
        connectionsRepo =
            MobileConnectionsRepositoryKairosImpl(
                connectivityRepository = connectivityRepository,
                subscriptionManager = subscriptionManager,
                subscriptionManagerProxy = subscriptionManagerProxy,
                telephonyManager = telephonyManager,
                logger = logger,
                tableLogger = summaryLogger,
                mobileMappingsProxy = mobileMappings,
                broadcastDispatcher = fakeBroadcastDispatcher,
                context = context,
                bgDispatcher = testDispatcher,
                mainDispatcher = testDispatcher,
                airplaneModeRepository = airplaneModeRepository,
                wifiRepository = wifiRepository,
                keyguardUpdateMonitor = updateMonitor,
                dumpManager = mock(),
                mobileRepoFactory = {
                    MobileConnectionRepositoryKairosFactoryImpl(
                        context = context,
                        connectionsRepo = connectionsRepo,
                        logFactory = logBufferFactory,
                        carrierConfigRepo = carrierConfigRepo,
                        telephonyManager = telephonyManager,
                        mobileRepoFactory = {
                            subId,
                            mobileLogger,
                            subscriptionModel,
                            defaultNetworkName,
                            networkNameSeparator,
                            systemUiCarrierConfig,
                            telephonyManager ->
                            MobileConnectionRepositoryKairosImpl(
                                subId = subId,
                                context = context,
                                subscriptionModel = subscriptionModel,
                                defaultNetworkName = defaultNetworkName,
                                networkNameSeparator = networkNameSeparator,
                                connectivityManager = connectivityManager,
                                telephonyManager = telephonyManager,
                                systemUiCarrierConfig = systemUiCarrierConfig,
                                broadcastDispatcher = fakeBroadcastDispatcher,
                                mobileMappingsProxy = mobileMappings,
                                bgDispatcher = testDispatcher,
                                logger = logger,
                                tableLogBuffer = mobileLogger,
                                flags = flags,
                            )
                        },
                        mergedRepoFactory =
                            CarrierMergedConnectionRepositoryKairos.Factory(
                                telephonyManager,
                                wifiRepository,
                            ),
                    )
                },
            )

        val adapter =
            MobileConnectionsRepositoryKairosAdapter(
                kairosRepo = connectionsRepo,
                kairosNetwork = kairosNetwork,
                scope = testScope.backgroundScope,
                connectivityRepository = connectivityRepository,
                context = context,
                carrierConfigRepo = carrierConfigRepo,
            )

        job?.cancel()
        Mockito.clearInvocations(telephonyManager)
        job =
            testScope.backgroundScope.launch {
                kairosNetwork.activateSpec {
                    connectionsRepo.run { activate() }
                    adapter.run { activate() }
                }
            }
        testScope.runCurrent() // ensure everything is activated
        return adapter
    }
}
