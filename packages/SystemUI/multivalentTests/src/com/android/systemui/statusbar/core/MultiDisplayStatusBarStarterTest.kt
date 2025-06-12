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

package com.android.systemui.statusbar.core

import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.data.repository.fakeLightBarControllerStore
import com.android.systemui.statusbar.data.repository.fakePrivacyDotWindowControllerStore
import com.android.systemui.testKosmos
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
@OptIn(ExperimentalCoroutinesApi::class)
class MultiDisplayStatusBarStarterTest : SysuiTestCase() {
    @get:Rule val expect: Expect = Expect.create()

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val fakeDisplayRepository = kosmos.displayRepository
    private val fakeOrchestratorFactory = kosmos.fakeStatusBarOrchestratorFactory
    private val fakeInitializerStore = kosmos.fakeStatusBarInitializerStore
    private val fakePrivacyDotStore = kosmos.fakePrivacyDotWindowControllerStore
    private val fakeLightBarStore = kosmos.fakeLightBarControllerStore

    // Lazy, so that @EnableFlags is set before initializer is instantiated.
    private val underTest by lazy { kosmos.multiDisplayStatusBarStarter }

    @Before
    fun setUp() = runBlocking {
        fakeDisplayRepository.addDisplay(DEFAULT_DISPLAY)
        fakeDisplayRepository.addDisplay(DISPLAY_2)
    }

    @Test
    fun start_triggerAddDisplaySystemDecoration_startsInitializersForDisplay() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(
                displayId = DEFAULT_DISPLAY
            )
            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(displayId = DISPLAY_2)

            expect
                .that(
                    fakeInitializerStore
                        .forDisplay(displayId = DEFAULT_DISPLAY)
                        .startedByCoreStartable
                )
                .isTrue()
            expect
                .that(fakeInitializerStore.forDisplay(displayId = DISPLAY_2).startedByCoreStartable)
                .isTrue()
        }

    @Test
    fun start_triggerAddDisplaySystemDecoration_startsOrchestratorForDisplay() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(
                displayId = DEFAULT_DISPLAY
            )
            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(displayId = DISPLAY_2)
            runCurrent()

            verify(
                    fakeOrchestratorFactory.createdOrchestratorForDisplay(
                        displayId = DEFAULT_DISPLAY
                    )!!
                )
                .start()
            verify(fakeOrchestratorFactory.createdOrchestratorForDisplay(displayId = DISPLAY_2)!!)
                .start()
        }

    @Test
    fun start_triggerAddDisplaySystemDecoration_startsPrivacyDotForNonDefaultDisplay() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(displayId = DISPLAY_2)

            verify(fakePrivacyDotStore.forDisplay(displayId = DISPLAY_2)).start()
        }

    @Test
    fun start_triggerAddDisplaySystemDecoration_doesNotStartPrivacyDotForDefaultDisplay() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(
                displayId = DEFAULT_DISPLAY
            )

            verify(fakePrivacyDotStore.forDisplay(displayId = DEFAULT_DISPLAY), never()).start()
        }

    @Test
    fun start_triggerAddDisplaySystemDecoration_doesNotStartLightBarControllerForDisplays() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(
                displayId = DEFAULT_DISPLAY
            )
            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(displayId = DISPLAY_2)

            verify(fakeLightBarStore.forDisplay(displayId = DEFAULT_DISPLAY), never()).start()
            verify(fakeLightBarStore.forDisplay(displayId = DISPLAY_2), never()).start()
        }

    @Test
    fun start_triggerAddDisplaySystemDecoration_createsLightBarControllerForDisplay() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(
                displayId = DEFAULT_DISPLAY
            )
            fakeDisplayRepository.triggerAddDisplaySystemDecorationEvent(displayId = DISPLAY_2)

            assertThat(fakeLightBarStore.perDisplayMocks.keys)
                .containsExactly(DEFAULT_DISPLAY, DISPLAY_2)
        }

    companion object {
        const val DISPLAY_2 = 2
    }
}
