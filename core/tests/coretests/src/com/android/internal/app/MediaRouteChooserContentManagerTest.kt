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

package com.android.internal.app

import android.content.Context
import android.media.MediaRouter
import android.testing.TestableLooper.RunWithLooper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidJUnit4::class)
class MediaRouteChooserContentManagerTest {
    private val context: Context = getInstrumentation().context

    @Test
    fun bindViews_showProgressBarWhenEmptyTrue_progressBarVisible() {
        val delegate = mock<MediaRouteChooserContentManager.Delegate> {
            on { showProgressBarWhenEmpty() } doReturn true
        }
        val contentManager = MediaRouteChooserContentManager(context, delegate)
        val containerView = inflateMediaRouteChooserDialog()
        contentManager.bindViews(containerView)

        assertThat(containerView.findViewById<View>(R.id.media_route_progress_bar).visibility)
            .isEqualTo(View.VISIBLE)
    }

    @Test
    fun bindViews_showProgressBarWhenEmptyFalse_progressBarNotVisible() {
        val delegate = mock<MediaRouteChooserContentManager.Delegate> {
            on { showProgressBarWhenEmpty() } doReturn false
        }
        val contentManager = MediaRouteChooserContentManager(context, delegate)
        val containerView = inflateMediaRouteChooserDialog()
        contentManager.bindViews(containerView)
        val emptyView = containerView.findViewById<View>(android.R.id.empty)
        val emptyViewLayout = emptyView.layoutParams as? LinearLayout.LayoutParams

        assertThat(containerView.findViewById<View>(R.id.media_route_progress_bar).visibility)
            .isEqualTo(View.GONE)
        assertThat(emptyView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyViewLayout?.gravity).isEqualTo(Gravity.CENTER)
    }

    @Test
    fun onFilterRoute_routeDefault_returnsFalse() {
        val delegate: MediaRouteChooserContentManager.Delegate = mock()
        val contentManager = MediaRouteChooserContentManager(context, delegate)
        val route: MediaRouter.RouteInfo = mock<MediaRouter.RouteInfo> {
            on { isDefault } doReturn true
        }

        assertThat(contentManager.onFilterRoute(route)).isEqualTo(false)
    }

    @Test
    fun onFilterRoute_routeNotEnabled_returnsFalse() {
        val delegate: MediaRouteChooserContentManager.Delegate = mock()
        val contentManager = MediaRouteChooserContentManager(context, delegate)
        val route: MediaRouter.RouteInfo = mock<MediaRouter.RouteInfo> {
            on { isEnabled } doReturn false
        }

        assertThat(contentManager.onFilterRoute(route)).isEqualTo(false)
    }

    @Test
    fun onFilterRoute_routeNotMatch_returnsFalse() {
        val delegate: MediaRouteChooserContentManager.Delegate = mock()
        val contentManager = MediaRouteChooserContentManager(context, delegate)
        val route: MediaRouter.RouteInfo = mock<MediaRouter.RouteInfo> {
            on { matchesTypes(anyInt()) } doReturn false
        }

        assertThat(contentManager.onFilterRoute(route)).isEqualTo(false)
    }

    @Test
    fun onFilterRoute_returnsTrue() {
        val delegate: MediaRouteChooserContentManager.Delegate = mock()
        val contentManager = MediaRouteChooserContentManager(context, delegate)
        val route: MediaRouter.RouteInfo = mock<MediaRouter.RouteInfo> {
            on { isDefault } doReturn false
            on { isEnabled } doReturn true
            on { matchesTypes(anyInt()) } doReturn true
        }

        assertThat(contentManager.onFilterRoute(route)).isEqualTo(true)
    }

    @Test
    fun onAttachedToWindow() {
        val delegate: MediaRouteChooserContentManager.Delegate = mock()
        val mediaRouter: MediaRouter = mock()
        val layoutInflater: LayoutInflater = mock()
        val context: Context = mock<Context> {
            on { getSystemServiceName(MediaRouter::class.java) } doReturn Context.MEDIA_ROUTER_SERVICE
            on { getSystemService(MediaRouter::class.java) } doReturn mediaRouter
            on { getSystemService(Context.LAYOUT_INFLATER_SERVICE) } doReturn layoutInflater
        }
        val contentManager = MediaRouteChooserContentManager(context, delegate)
        contentManager.routeTypes = MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY

        contentManager.onAttachedToWindow()

        verify(mediaRouter).addCallback(eq(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY), any(),
            eq(MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN))
    }

    @Test
    fun onDetachedFromWindow() {
        val delegate: MediaRouteChooserContentManager.Delegate = mock()
        val layoutInflater: LayoutInflater = mock()
        val mediaRouter: MediaRouter = mock()
        val context: Context = mock<Context> {
            on { getSystemServiceName(MediaRouter::class.java) } doReturn Context.MEDIA_ROUTER_SERVICE
            on { getSystemService(MediaRouter::class.java) } doReturn mediaRouter
            on { getSystemService(Context.LAYOUT_INFLATER_SERVICE) } doReturn layoutInflater
        }
        val contentManager = MediaRouteChooserContentManager(context, delegate)

        contentManager.onDetachedFromWindow()

        verify(mediaRouter).removeCallback(any())
    }

    @Test
    fun setRouteTypes() {
        val delegate: MediaRouteChooserContentManager.Delegate = mock()
        val mediaRouter: MediaRouter = mock()
        val layoutInflater: LayoutInflater = mock()
        val context: Context = mock<Context> {
            on { getSystemServiceName(MediaRouter::class.java) } doReturn Context.MEDIA_ROUTER_SERVICE
            on { getSystemService(MediaRouter::class.java) } doReturn mediaRouter
            on { getSystemService(Context.LAYOUT_INFLATER_SERVICE) } doReturn layoutInflater
        }
        val contentManager = MediaRouteChooserContentManager(context, delegate)
        contentManager.onAttachedToWindow()

        contentManager.routeTypes = MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY

        assertThat(contentManager.routeTypes).isEqualTo(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY)
        verify(mediaRouter).addCallback(eq(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY), any(),
            eq(MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN))
    }

    private fun inflateMediaRouteChooserDialog(): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.media_route_chooser_dialog, null, false)
    }
}
