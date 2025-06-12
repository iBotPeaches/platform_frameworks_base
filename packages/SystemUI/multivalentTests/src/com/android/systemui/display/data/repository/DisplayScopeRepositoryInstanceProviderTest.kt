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

package com.android.systemui.display.data.repository

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.applicationCoroutineScope
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
class DisplayScopeRepositoryInstanceProviderTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val displaySubcomponentRepository = kosmos.displaySubcomponentPerDisplayRepository

    private val underTest =
        DisplayScopeRepositoryInstanceProvider(
            kosmos.applicationCoroutineScope,
            displaySubcomponentRepository,
        )

    @Test
    fun createInstance_activeByDefault() =
        testScope.runTest {
            displaySubcomponentRepository.add(displayId = 1, kosmos.createFakeDisplaySubcomponent())
            val scopeForDisplay = underTest.createInstance(displayId = 1)!!

            assertThat(scopeForDisplay.isActive).isTrue()
        }

    @Test
    fun createInstance_forDefaultDisplay_returnsConstructorParam() =
        testScope.runTest {
            val scopeForDisplay = underTest.createInstance(displayId = Display.DEFAULT_DISPLAY)!!

            assertThat(scopeForDisplay).isEqualTo(kosmos.applicationCoroutineScope)
        }

    // no test for destruction, as it's not handled by this class. The scope is meant to be
    // destroyed by the PerDisplayRepository<SystemUIDisplaySubcomponent>
}
