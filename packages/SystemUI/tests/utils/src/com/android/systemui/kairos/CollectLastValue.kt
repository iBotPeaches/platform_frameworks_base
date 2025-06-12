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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.systemui.kairos

import com.android.systemui.coroutines.collectLastValue
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * Collect [state] in a new [Job] and return a getter for the collection of values collected.
 *
 * ```
 * fun myTest() = runTest {
 *   // ...
 *   val values by collectValues(underTest.flow)
 *   assertThat(values).isEqualTo(listOf(expected1, expected2, ...))
 * }
 * ```
 */
@ExperimentalKairosApi
fun <T> TestScope.collectLastValue(state: State<T>, kairosNetwork: KairosNetwork): KairosValue<T?> {
    var value: T? = null
    backgroundScope.launch { kairosNetwork.activateSpec { state.observe { value = it } } }
    return KairosValueImpl {
        runCurrent()
        value
    }
}

/**
 * Collect [flow] in a new [Job] and return a getter for the collection of values collected.
 *
 * ```
 * fun myTest() = runTest {
 *   // ...
 *   val values by collectValues(underTest.flow)
 *   assertThat(values).isEqualTo(listOf(expected1, expected2, ...))
 * }
 * ```
 */
@ExperimentalKairosApi
fun <T> TestScope.collectLastValue(flow: Events<T>, kairosNetwork: KairosNetwork): KairosValue<T?> {
    var value: T? = null
    backgroundScope.launch { kairosNetwork.activateSpec { flow.observe { value = it } } }
    return KairosValueImpl {
        runCurrent()
        value
    }
}

/**
 * Collect [flow] in a new [Job] and return a getter for the collection of values collected.
 *
 * ```
 * fun myTest() = runTest {
 *   // ...
 *   val values by collectValues(underTest.flow)
 *   assertThat(values).isEqualTo(listOf(expected1, expected2, ...))
 * }
 * ```
 */
@ExperimentalKairosApi
fun <T> TestScope.collectValues(
    flow: Events<T>,
    kairosNetwork: KairosNetwork,
): KairosValue<List<T>> {
    val values = mutableListOf<T>()
    backgroundScope.launch { kairosNetwork.activateSpec { flow.observe { values.add(it) } } }
    return KairosValueImpl {
        runCurrent()
        values.toList()
    }
}

/** @see collectLastValue */
interface KairosValue<T> : ReadOnlyProperty<Any?, T> {
    val value: T
}

private class KairosValueImpl<T>(private val block: () -> T) : KairosValue<T> {
    override val value: T
        get() = block()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}
