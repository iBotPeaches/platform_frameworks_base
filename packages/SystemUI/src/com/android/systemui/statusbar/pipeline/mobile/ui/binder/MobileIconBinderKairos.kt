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

package com.android.systemui.statusbar.pipeline.mobile.ui.binder

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Space
import androidx.core.view.isVisible
import com.android.settingslib.graph.SignalDrawable
import com.android.systemui.Flags
import com.android.systemui.common.ui.binder.IconViewBinder
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.MutableState
import com.android.systemui.kairos.effect
import com.android.systemui.lifecycle.repeatWhenAttachedToWindow
import com.android.systemui.lifecycle.repeatWhenWindowIsVisible
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.MobileViewLogger
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.LocationBasedMobileViewModelKairos
import com.android.systemui.statusbar.pipeline.shared.ui.binder.ModernStatusBarViewBinding
import com.android.systemui.statusbar.pipeline.shared.ui.binder.ModernStatusBarViewVisibilityHelper
import com.android.systemui.statusbar.pipeline.shared.ui.binder.StatusBarViewBinderConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

object MobileIconBinderKairos {

    @ExperimentalKairosApi
    fun bind(
        view: ViewGroup,
        viewModel: BuildSpec<LocationBasedMobileViewModelKairos>,
        @StatusBarIconView.VisibleState
        initialVisibilityState: Int = StatusBarIconView.STATE_HIDDEN,
        logger: MobileViewLogger,
        scope: CoroutineScope,
        kairosNetwork: KairosNetwork,
    ): Pair<ModernStatusBarViewBinding, Job> {
        val binding = ModernStatusBarViewBindingKairosImpl(kairosNetwork, initialVisibilityState)
        return binding to
            scope.launch {
                view.repeatWhenAttachedToWindow {
                    kairosNetwork.activateSpec {
                        bind(
                            view = view,
                            viewModel = viewModel.applySpec(),
                            logger = logger,
                            binding = binding,
                        )
                    }
                }
            }
    }

    @ExperimentalKairosApi
    private class ModernStatusBarViewBindingKairosImpl(
        kairosNetwork: KairosNetwork,
        initialVisibilityState: Int,
    ) : ModernStatusBarViewBinding {

        @JvmField var shouldIconBeVisible: Boolean = false
        @JvmField var isCollecting: Boolean = false

        // TODO(b/238425913): We should log this visibility state.
        val visibility = MutableState(kairosNetwork, initialVisibilityState)
        val iconTint =
            MutableState(
                kairosNetwork,
                MobileIconColors(
                    tint = DarkIconDispatcher.DEFAULT_ICON_TINT,
                    contrast = DarkIconDispatcher.DEFAULT_INVERSE_ICON_TINT,
                ),
            )
        val decorTint = MutableState(kairosNetwork, Color.WHITE)

        override fun getShouldIconBeVisible(): Boolean = shouldIconBeVisible

        override fun onVisibilityStateChanged(state: Int) {
            visibility.setValue(state)
        }

        override fun onIconTintChanged(newTint: Int, contrastTint: Int) {
            iconTint.setValue(MobileIconColors(tint = newTint, contrast = contrastTint))
        }

        override fun onDecorTintChanged(newTint: Int) {
            decorTint.setValue(newTint)
        }

        override fun isCollecting(): Boolean = isCollecting
    }

    @ExperimentalKairosApi
    private fun BuildScope.bind(
        view: ViewGroup,
        viewModel: LocationBasedMobileViewModelKairos,
        logger: MobileViewLogger,
        binding: ModernStatusBarViewBindingKairosImpl,
    ) {
        viewModel.isVisible.observe { binding.shouldIconBeVisible = it }

        val mobileGroupView = view.requireViewById<ViewGroup>(R.id.mobile_group)
        val activityContainer = view.requireViewById<View>(R.id.inout_container)
        val activityIn = view.requireViewById<ImageView>(R.id.mobile_in)
        val activityOut = view.requireViewById<ImageView>(R.id.mobile_out)
        val networkTypeView = view.requireViewById<ImageView>(R.id.mobile_type)
        val networkTypeContainer = view.requireViewById<FrameLayout>(R.id.mobile_type_container)
        val iconView = view.requireViewById<ImageView>(R.id.mobile_signal)
        val mobileDrawable = SignalDrawable(view.context)
        val roamingView = view.requireViewById<ImageView>(R.id.mobile_roaming)
        val roamingSpace = view.requireViewById<Space>(R.id.mobile_roaming_space)
        val dotView = view.requireViewById<StatusBarIconView>(R.id.status_bar_dot)

        effect {
            view.isVisible = viewModel.isVisible.sample()
            iconView.isVisible = true
            launch {
                view.repeatWhenAttachedToWindow {
                    // isVisible controls the visibility state of the outer group, and thus it needs
                    // to run in the CREATED lifecycle so it can continue to watch while invisible
                    // See (b/291031862) for details
                    kairosNetwork.activateSpec {
                        viewModel.isVisible.observe { isVisible ->
                            viewModel.verboseLogger?.logBinderReceivedVisibility(
                                view,
                                viewModel.subscriptionId,
                                isVisible,
                            )
                            view.isVisible = isVisible
                            // [StatusIconContainer] can get out of sync sometimes. Make sure to
                            // request another layout when this changes.
                            view.requestLayout()
                        }
                    }
                }
            }
            launch {
                view.repeatWhenWindowIsVisible {
                    logger.logCollectionStarted(view, viewModel)
                    binding.isCollecting = true
                    kairosNetwork.activateSpec {
                        binding.visibility.observe { state ->
                            ModernStatusBarViewVisibilityHelper.setVisibilityState(
                                state,
                                mobileGroupView,
                                dotView,
                            )
                            view.requestLayout()
                        }

                        // Set the icon for the triangle
                        viewModel.icon.observe { icon ->
                            viewModel.verboseLogger?.logBinderReceivedSignalIcon(
                                view,
                                viewModel.subscriptionId,
                                icon,
                            )
                            if (icon is SignalIconModel.Cellular) {
                                iconView.setImageDrawable(mobileDrawable)
                                mobileDrawable.level = icon.toSignalDrawableState()
                            } else if (icon is SignalIconModel.Satellite) {
                                IconViewBinder.bind(icon.icon, iconView)
                            }
                        }

                        viewModel.contentDescription.observe {
                            MobileContentDescriptionViewBinder.bind(it, view)
                        }

                        // Set the network type icon
                        viewModel.networkTypeIcon.observe { dataTypeId ->
                            viewModel.verboseLogger?.logBinderReceivedNetworkTypeIcon(
                                view,
                                viewModel.subscriptionId,
                                dataTypeId,
                            )
                            dataTypeId?.let { IconViewBinder.bind(dataTypeId, networkTypeView) }
                            val prevVis = networkTypeContainer.visibility
                            networkTypeContainer.visibility =
                                if (dataTypeId != null) View.VISIBLE else View.GONE

                            if (prevVis != networkTypeContainer.visibility) {
                                view.requestLayout()
                            }
                        }

                        // Set the network type background
                        viewModel.networkTypeBackground.observe { background ->
                            networkTypeContainer.setBackgroundResource(background?.res ?: 0)

                            // Tint will invert when this bit changes
                            if (background?.res != null) {
                                networkTypeContainer.backgroundTintList =
                                    ColorStateList.valueOf(binding.iconTint.sample().tint)
                                networkTypeView.imageTintList =
                                    ColorStateList.valueOf(binding.iconTint.sample().contrast)
                            } else {
                                networkTypeView.imageTintList =
                                    ColorStateList.valueOf(binding.iconTint.sample().tint)
                            }
                        }

                        // Set the roaming indicator
                        viewModel.roaming.observe { isRoaming ->
                            roamingView.isVisible = isRoaming
                            roamingSpace.isVisible = isRoaming
                        }

                        if (Flags.statusBarStaticInoutIndicators()) {
                            // Set the opacity of the activity indicators
                            viewModel.activityInVisible.observe { visible ->
                                activityIn.imageAlpha =
                                    (if (visible) StatusBarViewBinderConstants.ALPHA_ACTIVE
                                    else StatusBarViewBinderConstants.ALPHA_INACTIVE)
                            }
                            viewModel.activityOutVisible.observe { visible ->
                                activityOut.imageAlpha =
                                    (if (visible) StatusBarViewBinderConstants.ALPHA_ACTIVE
                                    else StatusBarViewBinderConstants.ALPHA_INACTIVE)
                            }
                        } else {
                            // Set the activity indicators
                            viewModel.activityInVisible.observe { activityIn.isVisible = it }
                            viewModel.activityOutVisible.observe { activityOut.isVisible = it }
                        }

                        viewModel.activityContainerVisible.observe {
                            activityContainer.isVisible = it
                        }

                        // Set the tint
                        binding.iconTint.observe { colors ->
                            val tint = ColorStateList.valueOf(colors.tint)
                            val contrast = ColorStateList.valueOf(colors.contrast)

                            iconView.imageTintList = tint

                            // If the bg is visible, tint it and use the contrast for the fg
                            if (viewModel.networkTypeBackground.sample() != null) {
                                networkTypeContainer.backgroundTintList = tint
                                networkTypeView.imageTintList = contrast
                            } else {
                                networkTypeView.imageTintList = tint
                            }

                            roamingView.imageTintList = tint
                            activityIn.imageTintList = tint
                            activityOut.imageTintList = tint
                            dotView.setDecorColor(colors.tint)
                        }

                        binding.decorTint.observe { tint -> dotView.setDecorColor(tint) }
                    }

                    try {
                        awaitCancellation()
                    } finally {
                        binding.isCollecting = false
                        logger.logCollectionStopped(view, viewModel)
                    }
                }
            }
        }
    }
}
