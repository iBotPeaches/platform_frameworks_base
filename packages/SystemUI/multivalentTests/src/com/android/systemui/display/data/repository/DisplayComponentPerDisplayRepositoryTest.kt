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

package com.android.systemui.display.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class DisplayComponentPerDisplayRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope

    private val underTest =
        DisplayComponentInstanceProvider(kosmos.fakeSysuiDisplayComponentFactory)

    @Test
    fun createInstance_activeByDefault() =
        testScope.runTest {
            val scopeForDisplay = underTest.createInstance(displayId = 1)!!.displayCoroutineScope

            assertThat(scopeForDisplay.isActive).isTrue()
        }

    @Test
    fun destroyInstance_afterDisplayRemoved_scopeIsCancelled() =
        testScope.runTest {
            val component = underTest.createInstance(displayId = 1)
            val scope = component!!.displayCoroutineScope

            underTest.destroyInstance(component)

            assertThat(scope.isActive).isFalse()
        }
}
