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

package com.android.systemui.communal.data.repository

import android.app.UiModeManager
import android.app.UiModeManager.OnProjectionStateChangedListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.launchIn
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@SmallTest
@RunWith(AndroidJUnit4::class)
class CarProjectionRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val capturedListeners = mutableListOf<OnProjectionStateChangedListener>()

    private val Kosmos.uiModeManager by
        Kosmos.Fixture<UiModeManager> {
            mock {
                on {
                    addOnProjectionStateChangedListener(
                        eq(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE),
                        any(),
                        any(),
                    )
                } doAnswer
                    {
                        val listener = it.getArgument<OnProjectionStateChangedListener>(2)
                        capturedListeners.add(listener)
                        Unit
                    }

                on { removeOnProjectionStateChangedListener(any()) } doAnswer
                    {
                        val listener = it.getArgument<OnProjectionStateChangedListener>(0)
                        capturedListeners.remove(listener)
                        Unit
                    }

                on { activeProjectionTypes } doReturn UiModeManager.PROJECTION_TYPE_NONE
            }
        }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            CarProjectionRepositoryImpl(
                uiModeManager = uiModeManager,
                bgDispatcher = testDispatcher,
            )
        }

    @Test
    fun testProjectionActiveUpdatesAfterCallback() =
        kosmos.runTest {
            val projectionActive by collectLastValue(underTest.projectionActive)
            assertThat(projectionActive).isFalse()

            setActiveProjectionType(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE)
            assertThat(projectionActive).isTrue()

            setActiveProjectionType(UiModeManager.PROJECTION_TYPE_NONE)
            assertThat(projectionActive).isFalse()
        }

    @Test
    fun testProjectionInitialValueTrue() =
        kosmos.runTest {
            setActiveProjectionType(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE)

            val projectionActive by collectLastValue(underTest.projectionActive)
            assertThat(projectionActive).isTrue()
        }

    @Test
    fun testUnsubscribeWhenCancelled() =
        kosmos.runTest {
            val job = underTest.projectionActive.launchIn(backgroundScope)
            assertThat(capturedListeners).hasSize(1)

            job.cancel()
            assertThat(capturedListeners).isEmpty()
        }

    private fun Kosmos.setActiveProjectionType(@UiModeManager.ProjectionType projectionType: Int) {
        uiModeManager.stub { on { activeProjectionTypes } doReturn projectionType }
        capturedListeners.forEach { it.onProjectionStateChanged(projectionType, emptySet()) }
    }
}
