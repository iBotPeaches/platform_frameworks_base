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

package com.android.systemui.qs.tiles.dialog

import android.content.Context
import android.media.MediaRouter
import android.provider.Settings
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.app.MediaRouteDialogPresenter
import com.android.systemui.SysuiTestCase
import com.android.systemui.qs.tiles.base.domain.actions.FakeQSTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.actions.intentInputs
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@SmallTest
@RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidJUnit4::class)
class CastDetailsViewModelTest : SysuiTestCase() {
    var inputHandler: FakeQSTileIntentUserInputHandler = FakeQSTileIntentUserInputHandler()
    private var context: Context = mock()
    private var mediaRouter: MediaRouter = mock()
    private var selectedRoute: MediaRouter.RouteInfo = mock()

    @Test
    fun testClickOnSettingsButton() {
        var viewModel = CastDetailsViewModel(inputHandler, context, MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY)

        viewModel.clickOnSettingsButton()

        assertThat(inputHandler.handledInputs).hasSize(1)
        val intentInput = inputHandler.intentInputs.last()
        assertThat(intentInput.expandable).isNull()
        assertThat(intentInput.intent.action).isEqualTo(Settings.ACTION_CAST_SETTINGS)
    }

    @Test
    fun testShouldShowChooserDialog() {
        context.stub {
            on { getSystemService(MediaRouter::class.java) } doReturn mediaRouter
        }
        mediaRouter.stub {
            on { selectedRoute } doReturn selectedRoute
        }

        var viewModel =
            CastDetailsViewModel(inputHandler, context, MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY)

        assertThat(viewModel.shouldShowChooserDialog())
            .isEqualTo(
                MediaRouteDialogPresenter.shouldShowChooserDialog(
                    context,
                    MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY,
                )
            )
    }
}
