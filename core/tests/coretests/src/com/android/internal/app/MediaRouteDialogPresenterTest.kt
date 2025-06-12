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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@SmallTest
@RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidJUnit4::class)
class MediaRouteDialogPresenterTest {
    private var selectedRoute: MediaRouter.RouteInfo = mock()
    private var mediaRouter: MediaRouter = mock<MediaRouter> {
        on { selectedRoute } doReturn selectedRoute
    }
    private var context: Context = mock<Context> {
        on { getSystemServiceName(MediaRouter::class.java) } doReturn Context.MEDIA_ROUTER_SERVICE
        on { getSystemService(MediaRouter::class.java) } doReturn mediaRouter
    }

    @Test
    fun shouldShowChooserDialog_routeNotDefault_returnsFalse() {
        selectedRoute.stub {
            on { isDefault } doReturn false
            on { matchesTypes(anyInt()) } doReturn true
        }

        assertThat(MediaRouteDialogPresenter.shouldShowChooserDialog(
            context, MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY))
            .isEqualTo(false)
    }

    @Test
    fun shouldShowChooserDialog_routeDefault_returnsTrue() {
        selectedRoute.stub {
            on { isDefault } doReturn true
            on { matchesTypes(anyInt()) } doReturn true
        }

        assertThat(MediaRouteDialogPresenter.shouldShowChooserDialog(
            context, MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY))
            .isEqualTo(true)
    }

    @Test
    fun shouldShowChooserDialog_routeNotMatch_returnsTrue() {
        selectedRoute.stub {
            on { isDefault } doReturn false
            on { matchesTypes(anyInt()) } doReturn false
        }

        assertThat(MediaRouteDialogPresenter.shouldShowChooserDialog(
            context, MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY))
            .isEqualTo(true)
    }

    @Test
    fun shouldShowChooserDialog_routeDefaultAndNotMatch_returnsTrue() {
        selectedRoute.stub {
            on { isDefault } doReturn true
            on { matchesTypes(anyInt()) } doReturn false
        }

        assertThat(MediaRouteDialogPresenter.shouldShowChooserDialog(
            context, MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY))
            .isEqualTo(true)
    }
}