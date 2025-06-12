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

package com.android.systemui.scene.ui.view

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.classifier.fakeFalsingCollector
import com.android.systemui.keyevent.domain.interactor.mockSysUIKeyEventHandler
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class WindowRootViewKeyEventHandlerTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest: WindowRootViewKeyEventHandler = kosmos.windowRootViewKeyEventHandler

    @Test
    fun dispatchKeyEvent_forwardsDispatchKeyEvent() =
        kosmos.runTest {
            val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B)
            underTest.dispatchKeyEvent(keyEvent)
            verify(mockSysUIKeyEventHandler).dispatchKeyEvent(keyEvent)
        }

    @Test
    fun dispatchKeyEventPreIme_forwardsDispatchKeyEventPreIme() =
        kosmos.runTest {
            val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B)
            underTest.dispatchKeyEventPreIme(keyEvent)
            verify(mockSysUIKeyEventHandler).dispatchKeyEventPreIme(keyEvent)
        }

    @Test
    fun interceptMediaKey_forwardsInterceptMediaKey() =
        kosmos.runTest {
            val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
            underTest.interceptMediaKey(keyEvent)
            verify(mockSysUIKeyEventHandler).interceptMediaKey(keyEvent)
        }

    @Test
    fun collectKeyEvent_forwardsCollectKeyEvent() =
        kosmos.runTest {
            val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
            underTest.collectKeyEvent(keyEvent)
            assertEquals(keyEvent, fakeFalsingCollector.lastKeyEvent)
        }
}
