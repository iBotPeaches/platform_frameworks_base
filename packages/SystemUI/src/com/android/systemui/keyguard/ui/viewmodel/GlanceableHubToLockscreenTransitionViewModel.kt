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

package com.android.systemui.keyguard.ui.viewmodel

import android.util.LayoutDirection
import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.dagger.GlanceableHubBlurComponent
import com.android.systemui.keyguard.domain.interactor.FromGlanceableHubTransitionInteractor.Companion.TO_LOCKSCREEN_DURATION
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.GLANCEABLE_HUB
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import com.android.systemui.keyguard.ui.StateToValue
import com.android.systemui.keyguard.ui.transitions.DeviceEntryIconTransition
import com.android.systemui.keyguard.ui.transitions.GlanceableHubTransition
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Breaks down GLANCEABLE_HUB->LOCKSCREEN transition into discrete steps for corresponding views to
 * consume.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class GlanceableHubToLockscreenTransitionViewModel
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    @ShadeDisplayAware configurationInteractor: ConfigurationInteractor,
    animationFlow: KeyguardTransitionAnimationFlow,
    communalSceneInteractor: CommunalSceneInteractor,
    communalSettingsInteractor: CommunalSettingsInteractor,
    private val blurFactory: GlanceableHubBlurComponent.Factory,
) : GlanceableHubTransition, DeviceEntryIconTransition {
    private val transitionAnimation =
        animationFlow
            .setup(
                duration = TO_LOCKSCREEN_DURATION,
                edge = Edge.create(from = Scenes.Communal, to = LOCKSCREEN),
            )
            .setupWithoutSceneContainer(edge = Edge.create(from = GLANCEABLE_HUB, to = LOCKSCREEN))

    // Whether screen rotation will happen with the transition. Only emit when idle so ongoing
    // animation won't be interrupted when orientation is updated during the transition.
    private val willRotateToPortraitInTransition: StateFlow<Boolean> =
        if (!communalSettingsInteractor.isV2FlagEnabled()) {
                flowOf(false)
            } else {
                communalSceneInteractor.isIdleOnCommunal.combineTransform(
                    communalSceneInteractor.willRotateToPortrait
                ) { isIdle, willRotate ->
                    if (isIdle) emit(willRotate)
                }
            }
            .stateIn(applicationScope, SharingStarted.Eagerly, false)

    override val windowBlurRadius: Flow<Float> =
        blurFactory.create(transitionAnimation).getBlurProvider().exitBlurRadius

    val keyguardAlpha: Flow<Float> =
        willRotateToPortraitInTransition.flatMapLatest { willRotate ->
            transitionAnimation.sharedFlow(
                duration = 167.milliseconds,
                // If will rotate, start later to leave time for screen rotation.
                startTime = if (willRotate) 500.milliseconds else 167.milliseconds,
                onStep = { step ->
                    if (willRotate) {
                        if (!communalSceneInteractor.rotatedToPortrait.value) {
                            0f
                        } else {
                            1f
                        }
                    } else {
                        step
                    }
                },
                onFinish = { 1f },
                onCancel = { 0f },
                name = "GLANCEABLE_HUB->LOCKSCREEN: keyguardAlpha",
            )
        }

    // Show UMO as long as keyguard is not visible.
    val showUmo: Flow<Boolean> = keyguardAlpha.map { alpha -> alpha == 0f }

    val keyguardTranslationX: Flow<StateToValue> =
        configurationInteractor
            .directionalDimensionPixelSize(
                LayoutDirection.LTR,
                R.dimen.hub_to_lockscreen_transition_lockscreen_translation_x,
            )
            .flatMapLatest { translatePx: Int ->
                transitionAnimation.sharedFlowWithState(
                    duration = TO_LOCKSCREEN_DURATION,
                    onStep = { value ->
                        // do not animate translation-x if screen rotation will happen
                        if (willRotateToPortraitInTransition.value) {
                            0f
                        } else {
                            -translatePx + value * translatePx
                        }
                    },
                    interpolator = EMPHASIZED,
                    // Move notifications back to their original position since they can be
                    // accessed from the shade, and also keyguard elements in case the animation
                    // is cancelled.
                    onFinish = { 0f },
                    onCancel = { 0f },
                    name = "GLANCEABLE_HUB->LOCKSCREEN: keyguardTranslationX",
                )
            }

    val notificationAlpha: Flow<Float> = keyguardAlpha

    val shortcutsAlpha: Flow<Float> = keyguardAlpha

    val statusBarAlpha: Flow<Float> = keyguardAlpha

    val notificationTranslationX: Flow<Float> =
        keyguardTranslationX.map { it.value }.filterNotNull()

    val deviceEntryBackgroundViewAlpha: Flow<Float> = keyguardAlpha

    override val deviceEntryParentViewAlpha: Flow<Float> = keyguardAlpha
}
