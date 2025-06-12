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

package com.android.systemui.keyboard.shortcut.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyboard.shortcut.shortcutHelperCustomizationModeRepository
import com.android.systemui.keyboard.shortcut.shortcutHelperTestHelper
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShortcutCustomizationModeRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val repo = kosmos.shortcutHelperCustomizationModeRepository
    private val testScope = kosmos.testScope
    private val helper = kosmos.shortcutHelperTestHelper

    @Test
    fun customizationMode_disabledByDefault() {
        testScope.runTest {
            val customizationMode by collectLastValue(repo.isCustomizationModeEnabled)

            assertThat(customizationMode).isFalse()
        }
    }

    @Test
    fun customizationMode_enabledOnRequest_whenShortcutHelperIsOpen() {
        testScope.runTest {
            val customizationMode by collectLastValue(repo.isCustomizationModeEnabled)
            helper.showFromActivity()
            repo.toggleCustomizationMode(isCustomizing = true)
            assertThat(customizationMode).isTrue()
        }
    }

    @Test
    fun customizationMode_disabledOnRequest_whenShortcutHelperIsOpen() {
        testScope.runTest {
            val customizationMode by collectLastValue(repo.isCustomizationModeEnabled)
            helper.showFromActivity()
            repo.toggleCustomizationMode(isCustomizing = true)
            repo.toggleCustomizationMode(isCustomizing = false)
            assertThat(customizationMode).isFalse()
        }
    }

    @Test
    fun customizationMode_disabledWhenShortcutHelperIsDismissed() {
        testScope.runTest {
            val customizationMode by collectLastValue(repo.isCustomizationModeEnabled)
            helper.showFromActivity()
            repo.toggleCustomizationMode(isCustomizing = true)
            helper.hideFromActivity()
            assertThat(customizationMode).isFalse()
        }
    }
}
