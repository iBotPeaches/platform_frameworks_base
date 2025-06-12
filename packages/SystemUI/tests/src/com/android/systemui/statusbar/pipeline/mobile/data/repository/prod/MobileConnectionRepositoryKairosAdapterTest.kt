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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.activated
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.launchKairosNetwork
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.runner.RunWith
import org.mockito.Mockito

@OptIn(ExperimentalKairosApi::class, ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileConnectionRepositoryKairosAdapterTest : MobileConnectionRepositoryTest() {

    var job: Job? = null
    val kairosNetwork = testScope.backgroundScope.launchKairosNetwork()

    override fun recreateRepo(): MobileConnectionRepository {
        lateinit var adapter: MobileConnectionRepositoryKairosAdapter
        job?.cancel()
        Mockito.clearInvocations(telephonyManager)
        job =
            testScope.backgroundScope.launch {
                kairosNetwork.activateSpec {
                    val repo = activated {
                        MobileConnectionRepositoryKairosImpl(
                            SUB_1_ID,
                            context,
                            subscriptionModel.toState(),
                            DEFAULT_NAME_MODEL,
                            SEP,
                            connectivityManager,
                            telephonyManager,
                            systemUiCarrierConfig,
                            fakeBroadcastDispatcher,
                            mobileMappings,
                            testDispatcher,
                            logger,
                            tableLogger,
                            flags,
                        )
                    }
                    adapter = MobileConnectionRepositoryKairosAdapter(repo, systemUiCarrierConfig)
                    Unit
                }
            }
        testScope.runCurrent() // ensure the lateinit is set
        return adapter
    }
}
