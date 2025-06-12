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

package com.android.systemui.animation

import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test

@SmallTest
@RunWith(AndroidJUnit4::class)
class ViewTransitionRegistryTest : SysuiTestCase() {

    private lateinit var view: View
    private lateinit var underTest: ViewTransitionRegistry

    @Before
    fun setup() {
        view = FrameLayout(mContext)
        underTest = ViewTransitionRegistry()
    }

    @Test
    fun testSuccessfulRegisterInViewTransitionRegistry() {
        val token = underTest.register(view)
        assertThat(underTest.getView(token)).isNotNull()
    }

    @Test
    fun testSuccessfulUnregisterInViewTransitionRegistry() {
        val token = underTest.register(view)
        assertThat(underTest.getView(token)).isNotNull()

        underTest.unregister(token)
        assertThat(underTest.getView(token)).isNull()
    }

    @Test
    fun testSuccessfulUnregisterOnViewDetachedFromWindow() {
        val view: View = mock()

        val token = underTest.register(view)
        assertThat(token).isEqualTo(token)
        assertThat(underTest.getView(token)).isNotNull()

        whenever(view.getTag(R.id.tag_view_transition_token)).thenReturn(token)

        argumentCaptor<View.OnAttachStateChangeListener>()
            .apply { verify(view).addOnAttachStateChangeListener(capture()) }
            .firstValue
            .onViewDetachedFromWindow(view)

        assertThat(underTest.getView(token)).isNull()
    }

    @Test
    fun testMultipleRegisterOnSameView() {
        val token = underTest.register(view)

        // multiple register on same view should return same token
        assertThat(underTest.register(view)).isEqualTo(token)

        // 1st unregister doesn't remove the token from registry as refCount = 2
        underTest.unregister(token)
        assertThat(underTest.getView(token)).isNotNull()

        // 2nd unregister removes the token from registry
        underTest.unregister(token)
        assertThat(underTest.getView(token)).isNull()
    }

    @Test
    fun testMultipleRegisterOnSameViewRemovedAfterViewDetached() {
        val view: View = mock()

        val token = underTest.register(view)
        whenever(view.getTag(R.id.tag_view_transition_token)).thenReturn(token)

        assertThat(underTest.getViewToken(view)).isEqualTo(token)

        // mock view's detach event
        val caller = argumentCaptor<View.OnAttachStateChangeListener>()
            .apply { verify(view).addOnAttachStateChangeListener(capture()) }
            .firstValue

        // register 3 times
        underTest.register(view)
        underTest.register(view)
        underTest.register(view)

        // unregister 1 time and verify entry should still be present in registry
        underTest.unregister(token)
        assertThat(underTest.getView(token)).isNotNull()

        // view's associated entry should be gone from registry, after view detaches
        caller.onViewDetachedFromWindow(view)
        assertThat(underTest.getView(token)).isNull()
    }

    @Test
    fun testDistinctViewsSameClassRegisterWithDifferentToken() {
        var prev: ViewTransitionToken? = underTest.register(FrameLayout(mContext))
        for (i in 0 until 10) {
            val curr = underTest.register(FrameLayout(mContext))
            assertThat(curr).isNotEqualTo(prev)
            prev = curr
        }
    }
}
