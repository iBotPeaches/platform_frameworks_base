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
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import com.android.internal.app.MediaRouteControllerContentManager
import com.android.internal.app.MediaRouteDialogPresenter
import com.android.systemui.plugins.qs.TileDetailsViewModel
import com.android.systemui.qs.tiles.base.domain.actions.QSTileIntentUserInputHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** The view model used for the screen record details view in the Quick Settings */
class CastDetailsViewModel
@AssistedInject
constructor(
    private val qsTileIntentUserActionHandler: QSTileIntentUserInputHandler,
    @Assisted private val context: Context,
    @Assisted private val routeTypes: Int,
) : MediaRouteControllerContentManager.Delegate, TileDetailsViewModel {
    @AssistedFactory
    fun interface Factory {
        fun create(context: Context, routeTypes: Int): CastDetailsViewModel
    }

    fun shouldShowChooserDialog(): Boolean {
        return MediaRouteDialogPresenter.shouldShowChooserDialog(context, routeTypes)
    }

    fun createControllerContentManager(): MediaRouteControllerContentManager {
        return MediaRouteControllerContentManager(context, this)
    }

    override fun clickOnSettingsButton() {
        qsTileIntentUserActionHandler.handle(
            /* expandable= */ null,
            Intent(Settings.ACTION_CAST_SETTINGS),
        )
    }

    // TODO(b/388321032): Replace this string with a string in a translatable xml file,
    override val title: String
        get() = "Cast screen to device"

    // TODO(b/388321032): Replace this string with a string in a translatable xml file,
    override val subTitle: String
        get() = "Searching for devices..."

    override fun setMediaRouteDeviceTitle(title: CharSequence?) {
        // TODO(b/378514236): Finish implementing this function.
    }

    override fun setMediaRouteDeviceIcon(icon: Drawable?) {
        // TODO(b/378514236): Finish implementing this function.
    }

    override fun dismissView() {
        // TODO(b/378514236): Finish implementing this function.
    }
}
