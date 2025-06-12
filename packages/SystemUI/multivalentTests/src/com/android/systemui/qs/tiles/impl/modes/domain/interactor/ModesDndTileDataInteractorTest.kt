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

package com.android.systemui.qs.tiles.impl.modes.domain.interactor

import android.app.Flags
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.statusbar.policy.data.repository.fakeZenModeRepository
import com.android.systemui.statusbar.policy.domain.interactor.zenModeInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@EnableFlags(Flags.FLAG_MODES_UI)
@RunWith(AndroidJUnit4::class)
class ModesDndTileDataInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val dispatcher = kosmos.testDispatcher
    private val zenModeRepository = kosmos.fakeZenModeRepository

    private val underTest by lazy {
        ModesDndTileDataInteractor(context, kosmos.zenModeInteractor, dispatcher)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODES_UI_DND_TILE)
    fun availability_flagOn_isTrue() =
        testScope.runTest {
            val availability = underTest.availability(TEST_USER).toCollection(mutableListOf())

            assertThat(availability).containsExactly(true)
        }

    @Test
    @DisableFlags(Flags.FLAG_MODES_UI_DND_TILE)
    fun availability_flagOff_isFalse() =
        testScope.runTest {
            val availability = underTest.availability(TEST_USER).toCollection(mutableListOf())

            assertThat(availability).containsExactly(false)
        }

    @Test
    fun tileData_dndChanges_updateActivated() =
        testScope.runTest {
            val model by
                collectLastValue(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            runCurrent()
            assertThat(model!!.isActivated).isFalse()

            zenModeRepository.activateMode(TestModeBuilder.MANUAL_DND)
            runCurrent()
            assertThat(model!!.isActivated).isTrue()

            zenModeRepository.deactivateMode(TestModeBuilder.MANUAL_DND)
            runCurrent()
            assertThat(model!!.isActivated).isFalse()
        }

    @Test
    fun tileData_otherModeChanges_notActivated() =
        testScope.runTest {
            val model by
                collectLastValue(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            runCurrent()
            assertThat(model!!.isActivated).isFalse()

            zenModeRepository.addMode("Other mode")
            runCurrent()
            assertThat(model!!.isActivated).isFalse()

            zenModeRepository.activateMode("Other mode")
            runCurrent()
            assertThat(model!!.isActivated).isFalse()
        }

    private companion object {
        val TEST_USER = UserHandle.of(1)!!
    }
}
