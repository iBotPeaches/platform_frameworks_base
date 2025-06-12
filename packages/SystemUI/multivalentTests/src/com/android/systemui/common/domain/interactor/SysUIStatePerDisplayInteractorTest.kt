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

package com.android.systemui.common.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.model.StateChange
import com.android.systemui.model.fakeSysUIStatePerDisplayRepository
import com.android.systemui.model.sysUiStateFactory
import com.android.systemui.model.sysuiStateInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SysUIStatePerDisplayInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    val stateRepository = kosmos.fakeSysUIStatePerDisplayRepository
    val state0 = kosmos.sysUiStateFactory.create(0)
    val state1 = kosmos.sysUiStateFactory.create(1)
    val state2 = kosmos.sysUiStateFactory.create(2)

    val underTest = kosmos.sysuiStateInteractor

    @Before
    fun setup() {
        stateRepository.apply {
            add(0, state0)
            add(1, state1)
            add(2, state2)
        }
        runBlocking {
            kosmos.displayRepository.apply {
                addDisplay(0)
                addDisplay(1)
                addDisplay(2)
            }
        }
    }

    @Test
    fun setFlagsExclusivelyToDisplay_setsFlagsOnTargetStateAndClearsTheOthers() {
        val targetDisplayId = 0
        val stateChange = StateChange().setFlag(1L, true)

        underTest.setFlagsExclusivelyToDisplay(targetDisplayId, stateChange)

        assertThat(state0.isFlagEnabled(1)).isTrue()
        assertThat(state1.isFlagEnabled(1)).isFalse()
        assertThat(state2.isFlagEnabled(1)).isFalse()

        underTest.setFlagsExclusivelyToDisplay(1, stateChange)

        assertThat(state0.isFlagEnabled(1)).isFalse()
        assertThat(state1.isFlagEnabled(1)).isTrue()
        assertThat(state2.isFlagEnabled(1)).isFalse()

        underTest.setFlagsExclusivelyToDisplay(2, stateChange)

        assertThat(state0.isFlagEnabled(1)).isFalse()
        assertThat(state1.isFlagEnabled(1)).isFalse()
        assertThat(state2.isFlagEnabled(1)).isTrue()

        underTest.setFlagsExclusivelyToDisplay(3, stateChange)

        assertThat(state0.isFlagEnabled(1)).isFalse()
        assertThat(state1.isFlagEnabled(1)).isFalse()
        assertThat(state2.isFlagEnabled(1)).isFalse()
    }

    @Test
    fun setFlagsExclusivelyToDisplay_multipleFlags_setsFlagsOnTargetStateAndClearsTheOthers() {
        val stateChange = StateChange().setFlag(1L, true).setFlag(2L, true)

        underTest.setFlagsExclusivelyToDisplay(1, stateChange)

        assertThat(state0.isFlagEnabled(1)).isFalse()
        assertThat(state0.isFlagEnabled(2)).isFalse()
        assertThat(state1.isFlagEnabled(1)).isTrue()
        assertThat(state1.isFlagEnabled(2)).isTrue()
        assertThat(state2.isFlagEnabled(1)).isFalse()
        assertThat(state2.isFlagEnabled(1)).isFalse()
    }

    @Test
    fun setFlagsExclusivelyToDisplay_clearsFlags() {
        state0.setFlag(1, true).setFlag(2, true).commitUpdate()
        state1.setFlag(1, true).setFlag(2, true).commitUpdate()
        state2.setFlag(1, true).setFlag(2, true).commitUpdate()

        val stateChange = StateChange().setFlag(1L, false)

        underTest.setFlagsExclusivelyToDisplay(1, stateChange)

        // Sets it as false in display 1, but also the others.
        assertThat(state0.isFlagEnabled(1)).isFalse()
        assertThat(state1.isFlagEnabled(1)).isFalse()
        assertThat(state2.isFlagEnabled(1)).isFalse()
    }
}
