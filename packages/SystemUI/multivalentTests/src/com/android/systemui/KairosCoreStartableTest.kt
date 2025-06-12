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

package com.android.systemui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kairos.toColdConflatedFlow
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.junit.runner.RunWith

@OptIn(ExperimentalKairosApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class KairosCoreStartableTest : SysuiTestCase() {

    @Test
    fun kairosNetwork_usedBeforeStarted() =
        testKosmos().useUnconfinedTestDispatcher().runKairosTest {
            lateinit var activatable: TestActivatable
            val underTest = KairosCoreStartable(applicationCoroutineScope) { setOf(activatable) }
            activatable = TestActivatable(underTest)

            // collect from the cold flow before starting the CoreStartable
            var collectCount = 0
            testScope.backgroundScope.launch { activatable.coldFlow.collect { collectCount++ } }

            // start the CoreStartable
            underTest.start()

            // verify emissions are received
            activatable.emitEvent()

            assertThat(collectCount).isEqualTo(1)
        }

    private class TestActivatable(network: KairosNetwork) : KairosBuilder by kairosBuilder() {
        private val emitter = MutableSharedFlow<Unit>()
        private val events = buildEvents { emitter.toEvents() }

        val coldFlow = events.toColdConflatedFlow(network)

        suspend fun emitEvent() = emitter.emit(Unit)
    }
}
