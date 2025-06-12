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
package com.android.systemui.dreams.conditions

import android.platform.test.annotations.EnabledOnRavenwood
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.assist.AssistManager
import com.android.systemui.assist.AssistManager.VisualQueryAttentionListener
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.shared.condition.Condition
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnabledOnRavenwood
class AssistantAttentionConditionTest : SysuiTestCase() {
    private val kosmos = Kosmos()

    @Mock private lateinit var callback: Condition.Callback

    @Mock private lateinit var assistManager: AssistManager

    private lateinit var underTest: AssistantAttentionCondition

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        underTest = AssistantAttentionCondition(kosmos.testScope, assistManager)
        // Adding a callback also starts the condition.
        underTest.addCallback(callback)
    }

    @Test
    fun testEnableVisualQueryDetection() =
        kosmos.runTest { Mockito.verify(assistManager).addVisualQueryAttentionListener(any()) }

    @Test
    fun testDisableVisualQueryDetection() =
        kosmos.runTest {
            underTest.stop()
            Mockito.verify(assistManager).removeVisualQueryAttentionListener(any())
        }

    @Test
    fun testAttentionChangedTriggersCondition() =
        kosmos.runTest {
            val argumentCaptor = argumentCaptor<VisualQueryAttentionListener>()
            Mockito.verify(assistManager).addVisualQueryAttentionListener(argumentCaptor.capture())

            argumentCaptor.lastValue.onAttentionGained()
            Truth.assertThat(underTest.isConditionMet).isTrue()

            argumentCaptor.lastValue.onAttentionLost()
            Truth.assertThat(underTest.isConditionMet).isFalse()

            Mockito.verify(callback, Mockito.times(2)).onConditionChanged(eq(underTest))
        }
}
