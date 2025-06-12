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

package com.android.systemui.wallpapers.ui.viewmodel

import android.graphics.RectF
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class WallpaperFocalAreaViewModel
@Inject
constructor(
    private val wallpaperFocalAreaInteractor: WallpaperFocalAreaInteractor,
    val keyguardTransitionInteractor: KeyguardTransitionInteractor,
) {
    val hasFocalArea = wallpaperFocalAreaInteractor.hasFocalArea

    val wallpaperFocalAreaBounds =
        combine(
                wallpaperFocalAreaInteractor.wallpaperFocalAreaBounds,
                keyguardTransitionInteractor.startedKeyguardTransitionStep,
                // Emit transition state when FINISHED instead of STARTED to avoid race with
                // wakingup command, causing layout change command not be received.
                keyguardTransitionInteractor
                    .transition(
                        edge = Edge.create(to = Scenes.Lockscreen),
                        edgeWithoutSceneContainer = Edge.create(to = KeyguardState.LOCKSCREEN),
                    )
                    .filter { it.transitionState == TransitionState.FINISHED },
                ::Triple,
            )
            .map { (bounds, startedStep, _) ->
                // Avoid sending wrong bounds when transitioning from LOCKSCREEN to GONE
                if (
                    startedStep.to == KeyguardState.LOCKSCREEN &&
                        startedStep.from != KeyguardState.LOCKSCREEN
                ) {
                    bounds
                } else {
                    null
                }
            }
            .filterNotNull()

    fun setFocalAreaBounds(bounds: RectF) {
        wallpaperFocalAreaInteractor.setFocalAreaBounds(bounds)
    }
}
