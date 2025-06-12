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

package com.android.systemui.kairos

import com.android.systemui.KairosActivatable
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@ExperimentalKairosApi
val Kosmos.kairos: KairosNetwork by Fixture { applicationCoroutineScope.launchKairosNetwork() }

@ExperimentalKairosApi
fun Kosmos.activateKairosActivatable(activatable: KairosActivatable) {
    applicationCoroutineScope.launch { kairos.activateSpec { activatable.run { activate() } } }
}

@ExperimentalKairosApi
fun <T : KairosActivatable> ActivatedKairosFixture(block: Kosmos.() -> T) = Fixture {
    block().also { activateKairosActivatable(it) }
}

@ExperimentalKairosApi
fun Kosmos.runKairosTest(timeout: Duration = 5.seconds, block: suspend KairosTestScope.() -> Unit) =
    testScope.runTest(timeout) { KairosTestScopeImpl(this@runKairosTest, this, kairos).block() }

@ExperimentalKairosApi
interface KairosTestScope : Kosmos {
    fun <T> State<T>.collectLastValue(): KairosValue<T?>

    suspend fun <T> State<T>.sample(): T

    fun <T : KairosActivatable> T.activated(): T
}

@ExperimentalKairosApi
private class KairosTestScopeImpl(
    kosmos: Kosmos,
    val testScope: TestScope,
    val kairos: KairosNetwork,
) : KairosTestScope, Kosmos by kosmos {
    override fun <T> State<T>.collectLastValue(): KairosValue<T?> =
        testScope.collectLastValue(this@collectLastValue, kairos)

    override suspend fun <T> State<T>.sample(): T = kairos.transact { sample() }

    override fun <T : KairosActivatable> T.activated(): T =
        this.also { activateKairosActivatable(it) }
}
