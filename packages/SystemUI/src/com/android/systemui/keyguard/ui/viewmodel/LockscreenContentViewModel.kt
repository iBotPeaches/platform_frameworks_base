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

package com.android.systemui.keyguard.ui.viewmodel

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.biometrics.AuthController
import com.android.systemui.customization.R as customR
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallbackDelegator
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class LockscreenContentViewModel
@AssistedInject
constructor(
    private val clockInteractor: KeyguardClockInteractor,
    interactor: KeyguardBlueprintInteractor,
    private val authController: AuthController,
    val touchHandling: KeyguardTouchHandlingViewModel,
    shadeModeInteractor: ShadeModeInteractor,
    unfoldTransitionInteractor: UnfoldTransitionInteractor,
    deviceEntryInteractor: DeviceEntryInteractor,
    transitionInteractor: KeyguardTransitionInteractor,
    private val keyguardTransitionAnimationCallbackDelegator:
        KeyguardTransitionAnimationCallbackDelegator,
    @Assisted private val keyguardTransitionAnimationCallback: KeyguardTransitionAnimationCallback,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("LockscreenContentViewModel.hydrator")

    val isUdfpsVisible: Boolean
        get() = authController.isUdfpsSupported

    /** Where to place the notifications stack on the lockscreen. */
    val notificationsPlacement: NotificationsPlacement by
        hydrator.hydratedStateOf(
            traceName = "notificationsPlacement",
            initialValue = NotificationsPlacement.BelowClock,
            source =
                combine(shadeModeInteractor.shadeMode, clockInteractor.clockSize) {
                    shadeMode,
                    clockSize ->
                    if (shadeMode is ShadeMode.Split) {
                        NotificationsPlacement.BesideClock(alignment = Alignment.TopEnd)
                    } else if (clockSize == ClockSize.SMALL) {
                        NotificationsPlacement.BelowClock
                    } else {
                        NotificationsPlacement.BesideClock(alignment = Alignment.TopStart)
                    }
                },
        )

    /** Amount of horizontal translation that should be applied to elements in the scene. */
    val unfoldTranslations: UnfoldTranslations by
        hydrator.hydratedStateOf(
            traceName = "unfoldTranslations",
            initialValue = UnfoldTranslations(),
            source =
                combine(
                    unfoldTransitionInteractor.unfoldTranslationX(isOnStartSide = true),
                    unfoldTransitionInteractor.unfoldTranslationX(isOnStartSide = false),
                    ::UnfoldTranslations,
                ),
        )

    /** Whether the content of the scene UI should be shown. */
    val isContentVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isContentVisible",
            initialValue = true,
            // Content is visible unless we're OCCLUDED. Currently, we don't have nice animations
            // into and out of OCCLUDED, so the lockscreen/AOD content is hidden immediately upon
            // entering/exiting OCCLUDED.
            source = transitionInteractor.transitionValue(KeyguardState.OCCLUDED).map { it == 0f },
        )

    /** Indicates whether lockscreen notifications should be rendered. */
    val areNotificationsVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "areNotificationsVisible",
            initialValue = false,
            // Content is visible unless we're OCCLUDED. Currently, we don't have nice animations
            // into and out of OCCLUDED, so the lockscreen/AOD content is hidden immediately upon
            // entering/exiting OCCLUDED.
            source =
                combine(clockInteractor.clockSize, shadeModeInteractor.isShadeLayoutWide) {
                    clockSize,
                    isShadeLayoutWide ->
                    clockSize == ClockSize.SMALL || isShadeLayoutWide
                },
        )

    /** @see DeviceEntryInteractor.isBypassEnabled */
    val isBypassEnabled: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isBypassEnabled",
            source = deviceEntryInteractor.isBypassEnabled,
        )

    val blueprintId: String by
        hydrator.hydratedStateOf(
            traceName = "blueprintId",
            initialValue = interactor.getCurrentBlueprint().id,
            source = interactor.blueprint.map { it.id }.distinctUntilChanged(),
        )

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            try {
                launch { hydrator.activate() }

                keyguardTransitionAnimationCallbackDelegator.delegate =
                    keyguardTransitionAnimationCallback

                awaitCancellation()
            } finally {
                keyguardTransitionAnimationCallbackDelegator.delegate = null
            }
        }
    }

    fun getSmartSpacePaddingTop(resources: Resources): Int {
        return if (clockInteractor.clockSize.value == ClockSize.LARGE) {
            resources.getDimensionPixelSize(customR.dimen.keyguard_smartspace_top_offset) +
                resources.getDimensionPixelSize(R.dimen.keyguard_clock_top_margin)
        } else {
            0
        }
    }

    data class UnfoldTranslations(

        /**
         * Amount of horizontal translation to apply to elements that are aligned to the start side
         * (left in left-to-right layouts). Can also be used as horizontal padding for elements that
         * need horizontal padding on both side. In pixels.
         */
        val start: Float = 0f,

        /**
         * Amount of horizontal translation to apply to elements that are aligned to the end side
         * (right in left-to-right layouts). In pixels.
         */
        val end: Float = 0f,
    )

    /** Where to place the notifications stack on the lockscreen. */
    sealed interface NotificationsPlacement {
        /** Show notifications below the lockscreen clock. */
        data object BelowClock : NotificationsPlacement

        /** Show notifications side-by-side with the clock. */
        data class BesideClock(val alignment: Alignment) : NotificationsPlacement
    }

    @AssistedFactory
    interface Factory {
        fun create(
            keyguardTransitionAnimationCallback: KeyguardTransitionAnimationCallback
        ): LockscreenContentViewModel
    }
}
