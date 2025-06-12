/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.media.controls.ui.view

import android.content.res.Resources
import android.testing.TestableLooper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.media.controls.util.MediaUiEventLogger
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.PageIndicator
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidJUnit4::class)
class MediaCarouselScrollHandlerTest : SysuiTestCase() {

    private val carouselWidth = 1038
    private val settingsButtonWidth = 200
    private val motionEventUp = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
    private lateinit var testableLooper: TestableLooper

    @Mock lateinit var mediaCarousel: MediaScrollView
    @Mock lateinit var pageIndicator: PageIndicator
    @Mock lateinit var dismissCallback: () -> Unit
    @Mock lateinit var translationChangedListener: () -> Unit
    @Mock lateinit var seekBarUpdateListener: (visibleToUser: Boolean) -> Unit
    @Mock lateinit var closeGuts: (immediate: Boolean) -> Unit
    @Mock lateinit var falsingManager: FalsingManager
    @Mock lateinit var logger: MediaUiEventLogger
    @Mock lateinit var contentContainer: ViewGroup
    @Mock lateinit var settingsButton: View
    @Mock lateinit var resources: Resources

    lateinit var executor: FakeExecutor
    private val clock = FakeSystemClock()

    private lateinit var mediaCarouselScrollHandler: MediaCarouselScrollHandler

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        executor = FakeExecutor(clock)
        testableLooper = TestableLooper.get(this)
        PhysicsAnimatorTestUtils.prepareForTest()
        PhysicsAnimatorTestUtils.setAllAnimationsBlock(true)

        whenever(mediaCarousel.contentContainer).thenReturn(contentContainer)
        mediaCarouselScrollHandler =
            MediaCarouselScrollHandler(
                mediaCarousel,
                pageIndicator,
                executor,
                dismissCallback,
                translationChangedListener,
                seekBarUpdateListener,
                closeGuts,
                falsingManager,
                logger,
            )
        mediaCarouselScrollHandler.playerWidthPlusPadding = carouselWidth
        whenever(mediaCarousel.touchListener).thenReturn(mediaCarouselScrollHandler.touchListener)
    }

    @After
    fun tearDown() {
        PhysicsAnimatorTestUtils.tearDown()
    }

    @Test
    fun testCarouselScroll_shortScroll() {
        whenever(mediaCarousel.isLayoutRtl).thenReturn(false)
        whenever(mediaCarousel.relativeScrollX).thenReturn(300)
        whenever(mediaCarousel.scrollX).thenReturn(300)

        mediaCarousel.touchListener?.onTouchEvent(motionEventUp)
        executor.runAllReady()

        verify(mediaCarousel).smoothScrollTo(eq(0), anyInt())
    }

    @Test
    fun testCarouselScroll_shortScroll_isRTL() {
        whenever(mediaCarousel.isLayoutRtl).thenReturn(true)
        whenever(mediaCarousel.relativeScrollX).thenReturn(300)
        whenever(mediaCarousel.scrollX).thenReturn(carouselWidth - 300)

        mediaCarousel.touchListener?.onTouchEvent(motionEventUp)
        executor.runAllReady()

        verify(mediaCarousel).smoothScrollTo(eq(carouselWidth), anyInt())
    }

    @Test
    fun testCarouselScroll_longScroll() {
        whenever(mediaCarousel.isLayoutRtl).thenReturn(false)
        whenever(mediaCarousel.relativeScrollX).thenReturn(600)
        whenever(mediaCarousel.scrollX).thenReturn(600)

        mediaCarousel.touchListener?.onTouchEvent(motionEventUp)
        executor.runAllReady()

        verify(mediaCarousel).smoothScrollTo(eq(carouselWidth), anyInt())
    }

    @Test
    fun testCarouselScroll_longScroll_isRTL() {
        whenever(mediaCarousel.isLayoutRtl).thenReturn(true)
        whenever(mediaCarousel.relativeScrollX).thenReturn(600)
        whenever(mediaCarousel.scrollX).thenReturn(carouselWidth - 600)

        mediaCarousel.touchListener?.onTouchEvent(motionEventUp)
        executor.runAllReady()

        verify(mediaCarousel).smoothScrollTo(eq(0), anyInt())
    }

    @Test
    fun testCarouselScrollByStep_scrollRight() {
        setupMediaContainer(visibleIndex = 0)

        mediaCarouselScrollHandler.scrollByStep(1)
        clock.advanceTime(DISMISS_DELAY)
        executor.runAllReady()

        verify(mediaCarousel).smoothScrollTo(eq(carouselWidth), anyInt())
    }

    @Test
    fun testCarouselScrollByStep_scrollLeft() {
        setupMediaContainer(visibleIndex = 1)

        mediaCarouselScrollHandler.scrollByStep(-1)
        clock.advanceTime(DISMISS_DELAY)
        executor.runAllReady()

        verify(mediaCarousel).smoothScrollTo(eq(0), anyInt())
    }

    @Test
    fun testCarouselScrollByStep_scrollRight_alreadyAtEnd() {
        setupMediaContainer(visibleIndex = 1)

        mediaCarouselScrollHandler.scrollByStep(1)
        clock.advanceTime(DISMISS_DELAY)
        executor.runAllReady()

        verify(mediaCarousel, never()).smoothScrollTo(anyInt(), anyInt())
        verify(mediaCarousel).animationTargetX = eq(-settingsButtonWidth.toFloat())
    }

    @Test
    fun testCarouselScrollByStep_scrollLeft_alreadyAtStart() {
        setupMediaContainer(visibleIndex = 0)

        mediaCarouselScrollHandler.scrollByStep(-1)
        clock.advanceTime(DISMISS_DELAY)
        executor.runAllReady()

        verify(mediaCarousel, never()).smoothScrollTo(anyInt(), anyInt())
        verify(mediaCarousel).animationTargetX = eq(settingsButtonWidth.toFloat())
    }

    @Test
    fun testCarouselScrollByStep_scrollLeft_alreadyAtStart_isRTL() {
        setupMediaContainer(visibleIndex = 0)
        PhysicsAnimatorTestUtils.setAllAnimationsBlock(true)
        whenever(mediaCarousel.isLayoutRtl).thenReturn(true)

        mediaCarouselScrollHandler.scrollByStep(-1)
        clock.advanceTime(DISMISS_DELAY)
        executor.runAllReady()

        verify(mediaCarousel, never()).smoothScrollTo(anyInt(), anyInt())
        verify(mediaCarousel).animationTargetX = eq(-settingsButtonWidth.toFloat())
    }

    @Test
    fun testCarouselScrollByStep_scrollRight_alreadyAtEnd_isRTL() {
        setupMediaContainer(visibleIndex = 1)
        PhysicsAnimatorTestUtils.setAllAnimationsBlock(true)
        whenever(mediaCarousel.isLayoutRtl).thenReturn(true)

        mediaCarouselScrollHandler.scrollByStep(1)
        clock.advanceTime(DISMISS_DELAY)
        executor.runAllReady()

        verify(mediaCarousel, never()).smoothScrollTo(anyInt(), anyInt())
        verify(mediaCarousel).animationTargetX = eq(settingsButtonWidth.toFloat())
    }

    @Test
    fun testScrollByStep_noScroll_notDismissible() {
        setupMediaContainer(visibleIndex = 1, showsSettingsButton = false)

        mediaCarouselScrollHandler.scrollByStep(1)
        clock.advanceTime(DISMISS_DELAY)
        executor.runAllReady()

        verify(mediaCarousel, never()).smoothScrollTo(anyInt(), anyInt())
        verify(mediaCarousel, never()).animationTargetX = anyFloat()
    }

    @Test
    fun testScrollingDisabled_noScroll_notDismissible() {
        setupMediaContainer(visibleIndex = 1, showsSettingsButton = false)

        mediaCarouselScrollHandler.scrollingDisabled = true

        clock.advanceTime(DISMISS_DELAY)
        executor.runAllReady()

        verify(mediaCarousel, never()).smoothScrollTo(anyInt(), anyInt())
        verify(mediaCarousel, never()).animationTargetX = anyFloat()
    }

    private fun setupMediaContainer(visibleIndex: Int, showsSettingsButton: Boolean = true) {
        whenever(contentContainer.childCount).thenReturn(2)
        val child1: View = mock()
        val child2: View = mock()
        whenever(child1.left).thenReturn(0)
        whenever(child2.left).thenReturn(carouselWidth)
        whenever(contentContainer.getChildAt(0)).thenReturn(child1)
        whenever(contentContainer.getChildAt(1)).thenReturn(child2)

        whenever(settingsButton.width).thenReturn(settingsButtonWidth)
        whenever(settingsButton.context).thenReturn(context)
        whenever(settingsButton.resources).thenReturn(resources)
        whenever(settingsButton.resources.getDimensionPixelSize(anyInt())).thenReturn(20)
        mediaCarouselScrollHandler.onSettingsButtonUpdated(settingsButton)

        mediaCarouselScrollHandler.visibleMediaIndex = visibleIndex
        mediaCarouselScrollHandler.showsSettingsButton = showsSettingsButton
    }
}
