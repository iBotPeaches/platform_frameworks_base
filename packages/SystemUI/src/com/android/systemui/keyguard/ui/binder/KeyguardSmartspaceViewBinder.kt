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

package com.android.systemui.keyguard.ui.binder

import android.view.View
import androidx.constraintlayout.helper.widget.Layer
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.keyguard.ui.view.layout.blueprints.transitions.IntraBlueprintTransition.Config
import com.android.systemui.keyguard.ui.view.layout.blueprints.transitions.IntraBlueprintTransition.Type
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.plugins.clocks.VRectF
import com.android.systemui.res.R
import com.android.systemui.shared.R as sharedR
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.combine

object KeyguardSmartspaceViewBinder {
    @JvmStatic
    fun bind(
        keyguardRootView: ConstraintLayout,
        keyguardRootViewModel: KeyguardRootViewModel,
        clockViewModel: KeyguardClockViewModel,
        smartspaceViewModel: KeyguardSmartspaceViewModel,
        blueprintInteractor: KeyguardBlueprintInteractor,
    ): DisposableHandle {
        return keyguardRootView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch("$TAG#clockViewModel.hasCustomWeatherDataDisplay") {
                    combine(
                            smartspaceViewModel.isWeatherVisible,
                            clockViewModel.hasCustomWeatherDataDisplay,
                            ::Pair,
                        )
                        .collect {
                            updateDateWeatherToBurnInLayer(
                                keyguardRootView,
                                clockViewModel,
                                smartspaceViewModel,
                            )
                            blueprintInteractor.refreshBlueprint(
                                Config(
                                    Type.SmartspaceVisibility,
                                    checkPriority = false,
                                    terminatePrevious = false,
                                )
                            )
                        }
                }

                launch("$TAG#smartspaceViewModel.bcSmartspaceVisibility") {
                    smartspaceViewModel.bcSmartspaceVisibility.collect {
                        updateBCSmartspaceInBurnInLayer(keyguardRootView, clockViewModel)
                        blueprintInteractor.refreshBlueprint(
                            Config(
                                Type.SmartspaceVisibility,
                                checkPriority = false,
                                terminatePrevious = false,
                            )
                        )
                    }
                }

                if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                    val xBuffer =
                        keyguardRootView.context.resources.getDimensionPixelSize(
                            R.dimen.smartspace_padding_horizontal
                        )
                    val yBuffer =
                        keyguardRootView.context.resources.getDimensionPixelSize(
                            R.dimen.smartspace_padding_vertical
                        )

                    val smallViewId = sharedR.id.date_smartspace_view

                    val largeViewId = sharedR.id.date_smartspace_view_large

                    launch("$TAG#smartspaceViewModel.burnInLayerVisibility") {
                        combine(
                                keyguardRootViewModel.burnInLayerVisibility,
                                clockViewModel.isLargeClockVisible,
                                ::Pair,
                            )
                            .collect { (visibility, isLargeClock) ->
                                if (isLargeClock) {
                                    // hide small clock date/weather
                                    keyguardRootView.findViewById<View>(smallViewId)?.let {
                                        it.visibility = View.GONE
                                    }
                                }
                            }
                    }

                    launch("$TAG#clockEventController.onClockBoundsChanged") {
                        // Whenever the doze amount changes, the clock may update it's view bounds.
                        // We need to update our layout position as a result. We could do this via
                        // `requestLayout`, but that's quite expensive when enclosed in since this
                        // recomputes the entire ConstraintLayout, so instead we do it manually. We
                        // would use translationX/Y for this, but that's used by burnin.
                        combine(
                                clockViewModel.isLargeClockVisible,
                                clockViewModel.clockEventController.onClockBoundsChanged,
                                ::Pair,
                            )
                            .collect { (isLargeClock, clockBounds) ->
                                val viewId = if (isLargeClock) smallViewId else largeViewId
                                keyguardRootView.findViewById<View>(viewId)?.let {
                                    it.visibility = View.GONE
                                }

                                if (clockBounds == VRectF.ZERO) return@collect
                                if (isLargeClock) {
                                    val largeDateHeight =
                                        keyguardRootView
                                            .findViewById<View>(
                                                sharedR.id.date_smartspace_view_large
                                            )
                                            ?.height ?: 0

                                    keyguardRootView.findViewById<View>(largeViewId)?.let { view ->
                                        val viewHeight = view.height
                                        val offset = (largeDateHeight - viewHeight) / 2
                                        view.top = (clockBounds.bottom + yBuffer + offset).toInt()
                                        view.bottom = view.top + viewHeight
                                    }
                                } else if (
                                    !KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(
                                        keyguardRootView.resources.configuration
                                    )
                                ) {
                                    keyguardRootView.findViewById<View>(smallViewId)?.let { view ->
                                        val viewWidth = view.width
                                        if (view.isLayoutRtl()) {
                                            view.right = (clockBounds.left - xBuffer).toInt()
                                            view.left = view.right - viewWidth
                                        } else {
                                            view.left = (clockBounds.right + xBuffer).toInt()
                                            view.right = view.left + viewWidth
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    private fun updateBCSmartspaceInBurnInLayer(
        keyguardRootView: ConstraintLayout,
        clockViewModel: KeyguardClockViewModel,
    ) {
        // Visibility is controlled by updateTargetVisibility in CardPagerAdapter
        val burnInLayer = keyguardRootView.requireViewById<Layer>(R.id.burn_in_layer)
        burnInLayer.apply {
            val smartspaceView =
                keyguardRootView.requireViewById<View>(sharedR.id.bc_smartspace_view)
            if (smartspaceView.visibility == View.VISIBLE) {
                addView(smartspaceView)
            } else {
                removeView(smartspaceView)
            }
        }
        clockViewModel.burnInLayer?.updatePostLayout(keyguardRootView)
    }

    private fun updateDateWeatherToBurnInLayer(
        keyguardRootView: ConstraintLayout,
        clockViewModel: KeyguardClockViewModel,
        smartspaceViewModel: KeyguardSmartspaceViewModel,
    ) {
        if (clockViewModel.hasCustomWeatherDataDisplay.value) {
            removeDateWeatherFromBurnInLayer(keyguardRootView, smartspaceViewModel)
        } else {
            addDateWeatherToBurnInLayer(keyguardRootView, smartspaceViewModel)
        }
        clockViewModel.burnInLayer?.updatePostLayout(keyguardRootView)
    }

    private fun addDateWeatherToBurnInLayer(
        constraintLayout: ConstraintLayout,
        smartspaceViewModel: KeyguardSmartspaceViewModel,
    ) {
        val burnInLayer = constraintLayout.requireViewById<Layer>(R.id.burn_in_layer)
        burnInLayer.apply {
            if (
                smartspaceViewModel.isSmartspaceEnabled &&
                    smartspaceViewModel.isDateWeatherDecoupled
            ) {
                val dateView =
                    constraintLayout.requireViewById<View>(sharedR.id.date_smartspace_view)
                addView(dateView)
            }
        }
    }

    private fun removeDateWeatherFromBurnInLayer(
        constraintLayout: ConstraintLayout,
        smartspaceViewModel: KeyguardSmartspaceViewModel,
    ) {
        val burnInLayer = constraintLayout.requireViewById<Layer>(R.id.burn_in_layer)
        burnInLayer.apply {
            if (
                smartspaceViewModel.isSmartspaceEnabled &&
                    smartspaceViewModel.isDateWeatherDecoupled
            ) {
                val dateView =
                    constraintLayout.requireViewById<View>(sharedR.id.date_smartspace_view)
                removeView(dateView)
            }
        }
    }

    private const val TAG = "KeyguardSmartspaceViewBinder"
}
