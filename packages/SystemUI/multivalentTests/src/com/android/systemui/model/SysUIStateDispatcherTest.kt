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

package com.android.systemui.model

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SysUIStateDispatcherTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private val stateFactory = kosmos.sysUiStateFactory
    private val state0 = stateFactory.create(Display.DEFAULT_DISPLAY)
    private val state1 = stateFactory.create(DISPLAY_1)
    private val state2 = stateFactory.create(DISPLAY_2)
    private val underTest = kosmos.sysUIStateDispatcher

    private val flagsChanges = mutableMapOf<Int, Long>() // display id -> flag value
    private val callback =
        SysUiState.SysUiStateCallback { sysUiFlags, displayId ->
            flagsChanges[displayId] = sysUiFlags
        }

    @Test
    @EnableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun registerUnregisterListener_notifiedOfChanges_receivedForAllDisplayIdsWithOneCallback() {
        underTest.registerListener(callback)

        state1.setFlag(FLAG_1, true).commitUpdate()
        state2.setFlag(FLAG_2, true).commitUpdate()

        assertThat(flagsChanges).containsExactly(DISPLAY_1, FLAG_1, DISPLAY_2, FLAG_2)

        underTest.unregisterListener(callback)

        state1.setFlag(0, true).commitUpdate()

        // Didn't change
        assertThat(flagsChanges).containsExactly(DISPLAY_1, FLAG_1, DISPLAY_2, FLAG_2)
    }

    @Test
    @DisableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun registerUnregisterListener_notifiedOfChangesForNonDefaultDisplay_NotPropagated() {
        underTest.registerListener(callback)

        state1.setFlag(FLAG_1, true).commitUpdate()

        assertThat(flagsChanges).isEmpty()

        state0.setFlag(FLAG_1, true).commitUpdate()

        assertThat(flagsChanges).containsExactly(Display.DEFAULT_DISPLAY, FLAG_1)
    }

    private companion object {
        const val DISPLAY_1 = 1
        const val DISPLAY_2 = 2
        const val FLAG_1 = 1L
        const val FLAG_2 = 2L
    }
}
