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

package com.android.systemui.qs.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.controls.ui.controller.MediaCarouselController
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.dagger.MediaModule
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileGridViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Named
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class QuickSettingsContainerViewModel
@AssistedInject
constructor(
    brightnessSliderViewModelFactory: BrightnessSliderViewModel.Factory,
    shadeHeaderViewModelFactory: ShadeHeaderViewModel.Factory,
    tileGridViewModelFactory: TileGridViewModel.Factory,
    @Assisted supportsBrightnessMirroring: Boolean,
    @Assisted private val expansion: Float?,
    val editModeViewModel: EditModeViewModel,
    val detailsViewModel: DetailsViewModel,
    toolbarViewModelFactory: ToolbarViewModel.Factory,
    shadeModeInteractor: ShadeModeInteractor,
    mediaCarouselInteractor: MediaCarouselInteractor,
    val mediaCarouselController: MediaCarouselController,
    @Named(MediaModule.QS_PANEL) val mediaHost: MediaHost,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("QuickSettingsContainerViewModel.hydrator")

    val brightnessSliderViewModel =
        brightnessSliderViewModelFactory.create(supportsBrightnessMirroring)

    val toolbarViewModel = toolbarViewModelFactory.create()

    val shadeHeaderViewModel = shadeHeaderViewModelFactory.create()

    val tileGridViewModel = tileGridViewModelFactory.create()

    val showHeader: Boolean by
        hydrator.hydratedStateOf(
            traceName = "showHeader",
            initialValue = !shadeModeInteractor.isShadeLayoutWide.value,
            source = shadeModeInteractor.isShadeLayoutWide.map { !it },
        )

    val showMedia: Boolean by
        hydrator.hydratedStateOf(
            traceName = "showMedia",
            source = mediaCarouselInteractor.hasAnyMediaOrRecommendation,
        )

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            expansion?.let { mediaHost.expansion = it }
            launch { hydrator.activate() }
            launch { brightnessSliderViewModel.activate() }
            launch { toolbarViewModel.activate() }
            launch { shadeHeaderViewModel.activate() }
            launch { tileGridViewModel.activate() }
            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            supportsBrightnessMirroring: Boolean,
            expansion: Float? = null,
        ): QuickSettingsContainerViewModel
    }
}
