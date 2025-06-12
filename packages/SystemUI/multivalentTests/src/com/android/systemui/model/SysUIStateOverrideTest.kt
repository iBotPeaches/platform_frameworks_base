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

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.dumpManager
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class SysUIStateOverrideTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val defaultState = kosmos.sysUiState
    private val callbackOnOverride = mock<SysUiState.SysUiStateCallback>()
    private val dumpManager = kosmos.dumpManager

    private val underTest = kosmos.sysUiStateOverrideFactory.invoke(DISPLAY_1)

    @Before
    fun setup() {
        underTest.start()
        underTest.addCallback(callbackOnOverride)
        reset(callbackOnOverride)
    }

    @Test
    fun setFlag_setOnDefaultState_propagatedToOverride() {
        defaultState.setFlag(FLAG_1, true).commitUpdate()

        verify(callbackOnOverride).onSystemUiStateChanged(FLAG_1, Display.DEFAULT_DISPLAY)
        verify(callbackOnOverride).onSystemUiStateChanged(FLAG_1, DISPLAY_1)
    }

    @Test
    fun setFlag_onOverride_overridesDefaultOnes() {
        defaultState.setFlag(FLAG_1, false).setFlag(FLAG_2, true).commitUpdate()
        underTest.setFlag(FLAG_1, true).setFlag(FLAG_2, false).commitUpdate()

        assertThat(underTest.isFlagEnabled(FLAG_1)).isTrue()
        assertThat(underTest.isFlagEnabled(FLAG_2)).isFalse()

        assertThat(defaultState.isFlagEnabled(FLAG_1)).isFalse()
        assertThat(defaultState.isFlagEnabled(FLAG_2)).isTrue()
    }

    @Test
    fun destroy_callbacksForDefaultStateNotReceivedAnymore() {
        defaultState.setFlag(FLAG_1, true).commitUpdate()

        verify(callbackOnOverride).onSystemUiStateChanged(FLAG_1, Display.DEFAULT_DISPLAY)

        reset(callbackOnOverride)
        underTest.destroy()
        defaultState.setFlag(FLAG_1, false).commitUpdate()

        verify(callbackOnOverride, never()).onSystemUiStateChanged(FLAG_1, Display.DEFAULT_DISPLAY)
    }

    @Test
    fun init_registersWithDumpManager() {
        verify(dumpManager).registerNormalDumpable(any(), eq(underTest))
    }

    @Test
    fun destroy_unregistersWithDumpManager() {
        underTest.destroy()

        verify(dumpManager).unregisterDumpable(ArgumentMatchers.anyString())
    }

    private companion object {
        const val DISPLAY_1 = 1
        const val FLAG_1 = 1L
        const val FLAG_2 = 2L
    }
}
