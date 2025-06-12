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

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class PerDisplayInstanceRepositoryImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val fakeDisplayRepository = kosmos.displayRepository
    private val fakePerDisplayInstanceProviderWithTeardown =
        kosmos.fakePerDisplayInstanceProviderWithTeardown
    private val lifecycleManager = kosmos.fakeDisplayInstanceLifecycleManager

    private val underTest: PerDisplayInstanceRepositoryImpl<TestPerDisplayInstance> =
        kosmos.createPerDisplayInstanceRepository(overrideLifecycleManager = null)

    @Before
    fun addDisplays() = runBlocking {
        fakeDisplayRepository += createDisplay(DEFAULT_DISPLAY_ID)
        fakeDisplayRepository += createDisplay(NON_DEFAULT_DISPLAY_ID)
    }

    @Test
    fun forDisplay_defaultDisplay_multipleCalls_returnsSameInstance() =
        testScope.runTest {
            val instance = underTest[DEFAULT_DISPLAY_ID]

            assertThat(underTest[DEFAULT_DISPLAY_ID]).isSameInstanceAs(instance)
        }

    @Test
    fun forDisplay_nonDefaultDisplay_multipleCalls_returnsSameInstance() =
        testScope.runTest {
            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            assertThat(underTest[NON_DEFAULT_DISPLAY_ID]).isSameInstanceAs(instance)
        }

    @Test
    fun forDisplay_nonDefaultDisplay_afterDisplayRemoved_returnsNewInstance() =
        testScope.runTest {
            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            fakeDisplayRepository -= NON_DEFAULT_DISPLAY_ID
            fakeDisplayRepository += createDisplay(NON_DEFAULT_DISPLAY_ID)

            assertThat(underTest[NON_DEFAULT_DISPLAY_ID]).isNotSameInstanceAs(instance)
        }

    @Test
    fun forDisplay_nonExistingDisplayId_returnsNull() =
        testScope.runTest { assertThat(underTest[NON_EXISTING_DISPLAY_ID]).isNull() }

    @Test
    fun forDisplay_afterDisplayRemoved_destroyInstanceInvoked() =
        testScope.runTest {
            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            fakeDisplayRepository -= NON_DEFAULT_DISPLAY_ID

            assertThat(fakePerDisplayInstanceProviderWithTeardown.destroyed)
                .containsExactly(instance)
        }

    @Test
    fun forDisplay_withoutDisplayRemoval_destroyInstanceIsNotInvoked() =
        testScope.runTest {
            underTest[NON_DEFAULT_DISPLAY_ID]

            assertThat(fakePerDisplayInstanceProviderWithTeardown.destroyed).isEmpty()
        }

    @Test
    fun start_registersDumpable() {
        verify(kosmos.dumpManager).registerNormalDumpable(anyString(), any())
    }

    @Test
    fun perDisplay_afterCustomLifecycleManagerRemovesDisplay_destroyInstanceInvoked() =
        testScope.runTest {
            val underTest =
                kosmos.createPerDisplayInstanceRepository(
                    overrideLifecycleManager = lifecycleManager
                )
            // Let's start with both
            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID, NON_DEFAULT_DISPLAY_ID)

            val instance = underTest[NON_DEFAULT_DISPLAY_ID]

            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID)

            // Now that the lifecycle manager says so, let's make sure it was destroyed
            assertThat(fakePerDisplayInstanceProviderWithTeardown.destroyed)
                .containsExactly(instance)
        }

    @Test
    fun perDisplay_lifecycleManagerDoesNotContainIt_displayRepositoryDoes_returnsNull() =
        testScope.runTest {
            val underTest =
                kosmos.createPerDisplayInstanceRepository(
                    overrideLifecycleManager = lifecycleManager
                )
            // only default display, so getting for the non-default one should fail, despite the
            // repository having both displays already
            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID)

            assertThat(underTest[NON_DEFAULT_DISPLAY_ID]).isNull()

            lifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY_ID, NON_DEFAULT_DISPLAY_ID)

            assertThat(underTest[NON_DEFAULT_DISPLAY_ID]).isNotNull()
        }

    private fun createDisplay(displayId: Int): Display =
        display(type = Display.TYPE_INTERNAL, id = displayId)

    companion object {
        private const val DEFAULT_DISPLAY_ID = Display.DEFAULT_DISPLAY
        private const val NON_DEFAULT_DISPLAY_ID = DEFAULT_DISPLAY_ID + 1
        private const val NON_EXISTING_DISPLAY_ID = DEFAULT_DISPLAY_ID + 2
    }
}
