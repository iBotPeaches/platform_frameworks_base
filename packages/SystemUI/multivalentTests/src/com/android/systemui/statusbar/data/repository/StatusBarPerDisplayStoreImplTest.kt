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

package com.android.systemui.statusbar.data.repository

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class StatusBarPerDisplayStoreImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val fakeDisplayRepository = kosmos.displayRepository

    private val store = kosmos.fakeStatusBarPerDisplayStore

    @Before
    fun start() {
        store.start()
    }

    @Before
    fun addDisplays() = runBlocking {
        fakeDisplayRepository.addDisplay(DEFAULT_DISPLAY_ID)
        fakeDisplayRepository.addDisplay(NON_DEFAULT_DISPLAY_ID)
    }

    @Test
    fun removeSystemDecoration_onDisplayRemovalActionInvoked() =
        testScope.runTest {
            val instance = store.forDisplay(NON_DEFAULT_DISPLAY_ID)

            fakeDisplayRepository.triggerRemoveSystemDecorationEvent(NON_DEFAULT_DISPLAY_ID)

            assertThat(store.removalActions).containsExactly(instance)
        }

    @Test
    fun removeSystemDecoration_twice_onDisplayRemovalActionInvokedOnce() =
        testScope.runTest {
            val instance = store.forDisplay(NON_DEFAULT_DISPLAY_ID)

            fakeDisplayRepository.triggerRemoveSystemDecorationEvent(NON_DEFAULT_DISPLAY_ID)
            fakeDisplayRepository.triggerRemoveSystemDecorationEvent(NON_DEFAULT_DISPLAY_ID)

            assertThat(store.removalActions).containsExactly(instance)
        }

    @Test
    fun forDisplay_withoutDisplayRemoval_onDisplayRemovalActionIsNotInvoked() =
        testScope.runTest {
            store.forDisplay(NON_DEFAULT_DISPLAY_ID)

            assertThat(store.removalActions).isEmpty()
        }

    companion object {
        private const val DEFAULT_DISPLAY_ID = Display.DEFAULT_DISPLAY
        private const val NON_DEFAULT_DISPLAY_ID = DEFAULT_DISPLAY_ID + 1
    }
}
