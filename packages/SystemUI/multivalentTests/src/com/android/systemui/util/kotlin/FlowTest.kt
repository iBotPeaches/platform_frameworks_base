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

package com.android.systemui.util.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class FlowTest : SysuiTestCase() {
    val kosmos = testKosmos()
    val testScope = kosmos.testScope

    @Test
    fun combine6() {
        testScope.runTest {
            val result by collectLastValue(combine(f0, f1, f2, f3, f4, f5, ::listOf6))
            assertItemsEqualIndices(result)
        }
    }

    @Test
    fun combine7() {
        testScope.runTest {
            val result by collectLastValue(combine(f0, f1, f2, f3, f4, f5, f6, ::listOf7))
            assertItemsEqualIndices(result)
        }
    }

    @Test
    fun combine8() {
        testScope.runTest {
            val result by collectLastValue(combine(f0, f1, f2, f3, f4, f5, f6, f7, ::listOf8))
            assertItemsEqualIndices(result)
        }
    }

    @Test
    fun combine9() {
        testScope.runTest {
            val result by collectLastValue(combine(f0, f1, f2, f3, f4, f5, f6, f7, f8, ::listOf9))
            assertItemsEqualIndices(result)
        }
    }

    private fun assertItemsEqualIndices(list: List<Int>?) {
        assertThat(list).isNotNull()
        list ?: return

        for (index in list.indices) {
            assertThat(list[index]).isEqualTo(index)
        }
    }

    private val f0: Flow<Int> = flowOf(0)
    private val f1: Flow<Int> = flowOf(1)
    private val f2: Flow<Int> = flowOf(2)
    private val f3: Flow<Int> = flowOf(3)
    private val f4: Flow<Int> = flowOf(4)
    private val f5: Flow<Int> = flowOf(5)
    private val f6: Flow<Int> = flowOf(6)
    private val f7: Flow<Int> = flowOf(7)
    private val f8: Flow<Int> = flowOf(8)
}

private fun <T> listOf6(a0: T, a1: T, a2: T, a3: T, a4: T, a5: T): List<T> =
    listOf(a0, a1, a2, a3, a4, a5)

private fun <T> listOf7(a0: T, a1: T, a2: T, a3: T, a4: T, a5: T, a6: T): List<T> =
    listOf(a0, a1, a2, a3, a4, a5, a6)

private fun <T> listOf8(a0: T, a1: T, a2: T, a3: T, a4: T, a5: T, a6: T, a7: T): List<T> =
    listOf(a0, a1, a2, a3, a4, a5, a6, a7)

private fun <T> listOf9(a0: T, a1: T, a2: T, a3: T, a4: T, a5: T, a6: T, a7: T, a8: T): List<T> =
    listOf(a0, a1, a2, a3, a4, a5, a6, a7, a8)
