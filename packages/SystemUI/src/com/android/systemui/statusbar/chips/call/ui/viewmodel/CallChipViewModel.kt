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

package com.android.systemui.statusbar.chips.call.ui.viewmodel

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.view.View
import com.android.internal.jank.Cuj
import com.android.internal.logging.InstanceId
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.ComposableControllerFactory
import com.android.systemui.animation.DelegateTransitionAnimatorController
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.call.domain.interactor.CallChipInteractor
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.view.ChipBackgroundContainer
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

/** View model for the ongoing phone call chip shown in the status bar. */
@SysUISingleton
class CallChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    interactor: CallChipInteractor,
    systemClock: SystemClock,
    private val activityStarter: ActivityStarter,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {
    private val logger = Logger(logBuffer, "OngoingCallVM".pad())
    /** The transition cookie used to register and unregister launch and return animations. */
    private val cookie =
        ActivityTransitionAnimator.TransitionCookie("${CallChipViewModel::class.java}")

    /**
     * Used internally to determine when a launch or return animation is in progress, as these
     * require special handling.
     */
    private val transitionState: MutableStateFlow<TransitionState> =
        MutableStateFlow(TransitionState.NoTransition)

    // Since we're combining the chip state and the transition state flows, getting the old value by
    // using [pairwise()] would confuse things. This is because if the calculation is triggered by
    // a change in transition state, the chip state will still show the previous and current values,
    // making it difficult to figure out what actually changed. Instead we cache the old value here,
    // so that at each update we can keep track of what actually changed.
    private var latestState: OngoingCallModel = OngoingCallModel.NoCall
    private var latestTransitionState: TransitionState = TransitionState.NoTransition

    private val chipWithReturnAnimation: StateFlow<OngoingActivityChipModel> =
        if (StatusBarChipsReturnAnimations.isEnabled) {
            combine(interactor.ongoingCallState, transitionState) { newState, newTransitionState ->
                    val oldState = latestState
                    latestState = newState
                    val oldTransitionState = latestTransitionState
                    latestTransitionState = newTransitionState

                    logger.d({
                        "Call chip state updated: $str1" +
                            " oldTransitionState=$str2" +
                            " newTransitionState=$str3"
                    }) {
                        str1 = "oldState=${oldState.logString()} newState=${newState.logString()}"
                        str2 = oldTransitionState::class.simpleName
                        str3 = newTransitionState::class.simpleName
                    }

                    when (newState) {
                        is OngoingCallModel.NoCall ->
                            OngoingActivityChipModel.Inactive(
                                transitionManager = getTransitionManager(newState)
                            )

                        is OngoingCallModel.InCall ->
                            prepareChip(
                                newState,
                                systemClock,
                                isHidden =
                                    shouldChipBeHidden(
                                        oldState = oldState,
                                        newState = newState,
                                        oldTransitionState = oldTransitionState,
                                        newTransitionState = newTransitionState,
                                    ),
                                transitionState = newTransitionState,
                            )
                    }
                }
                .stateIn(
                    scope,
                    SharingStarted.WhileSubscribed(),
                    OngoingActivityChipModel.Inactive(),
                )
        } else {
            MutableStateFlow(OngoingActivityChipModel.Inactive()).asStateFlow()
        }

    private val chipLegacy: StateFlow<OngoingActivityChipModel> =
        if (!StatusBarChipsReturnAnimations.isEnabled) {
            interactor.ongoingCallState
                .map { state ->
                    logger.d({ "Call chip state updated: newState=$str1" }) {
                        str1 = state.logString()
                    }

                    when (state) {
                        is OngoingCallModel.NoCall -> OngoingActivityChipModel.Inactive()
                        is OngoingCallModel.InCall ->
                            if (state.isAppVisible) {
                                OngoingActivityChipModel.Inactive()
                            } else {
                                prepareChip(state, systemClock, isHidden = false)
                            }
                    }
                }
                .stateIn(
                    scope,
                    SharingStarted.WhileSubscribed(),
                    OngoingActivityChipModel.Inactive(),
                )
        } else {
            MutableStateFlow(OngoingActivityChipModel.Inactive()).asStateFlow()
        }

    override val chip: StateFlow<OngoingActivityChipModel> =
        if (StatusBarChipsReturnAnimations.isEnabled) {
            chipWithReturnAnimation
        } else {
            chipLegacy
        }

    /**
     * The controller factory that the call chip uses to register and unregister its transition
     * animations.
     */
    private var transitionControllerFactory: ComposableControllerFactory? = null

    /** Builds an [OngoingActivityChipModel.Active] from all the relevant information. */
    private fun prepareChip(
        state: OngoingCallModel.InCall,
        systemClock: SystemClock,
        isHidden: Boolean,
        transitionState: TransitionState = TransitionState.NoTransition,
    ): OngoingActivityChipModel.Active {
        val key = "$KEY_PREFIX${state.notificationKey}"
        val contentDescription = getContentDescription(state.appName)
        val icon =
            if (state.notificationIconView != null) {
                StatusBarConnectedDisplays.assertInLegacyMode()
                OngoingActivityChipModel.ChipIcon.StatusBarView(
                    state.notificationIconView,
                    contentDescription,
                )
            } else if (StatusBarConnectedDisplays.isEnabled) {
                OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon(
                    state.notificationKey,
                    contentDescription,
                )
            } else {
                OngoingActivityChipModel.ChipIcon.SingleColorIcon(phoneIcon)
            }

        val colors = ColorsModel.AccentThemed
        val intent = state.intent
        val instanceId = state.notificationInstanceId

        // This block mimics OngoingCallController#updateChip.
        if (state.startTimeMs <= 0L) {
            // If the start time is invalid, don't show a timer and show just an icon.
            // See b/192379214.
            return OngoingActivityChipModel.Active.IconOnly(
                key = key,
                icon = icon,
                colors = colors,
                onClickListenerLegacy = getOnClickListener(intent, instanceId),
                clickBehavior = getClickBehavior(intent, instanceId),
                isHidden = isHidden,
                transitionManager = getTransitionManager(state, transitionState),
                instanceId = instanceId,
            )
        } else {
            val startTimeInElapsedRealtime =
                state.startTimeMs - systemClock.currentTimeMillis() + systemClock.elapsedRealtime()
            return OngoingActivityChipModel.Active.Timer(
                key = key,
                icon = icon,
                colors = colors,
                startTimeMs = startTimeInElapsedRealtime,
                onClickListenerLegacy = getOnClickListener(intent, instanceId),
                clickBehavior = getClickBehavior(intent, instanceId),
                isHidden = isHidden,
                transitionManager = getTransitionManager(state, transitionState),
                instanceId = instanceId,
            )
        }
    }

    private fun getOnClickListener(
        intent: PendingIntent?,
        instanceId: InstanceId?,
    ): View.OnClickListener? {
        if (intent == null) return null
        return View.OnClickListener { view ->
            StatusBarChipsModernization.assertInLegacyMode()

            logger.i({ "Chip clicked" }) {}
            uiEventLogger.logChipTapToShow(instanceId)

            val backgroundView =
                view.requireViewById<ChipBackgroundContainer>(R.id.ongoing_activity_chip_background)
            // This mimics OngoingCallController#updateChipClickListener.
            activityStarter.postStartActivityDismissingKeyguard(
                intent,
                ActivityTransitionAnimator.Controller.fromView(
                    backgroundView,
                    Cuj.CUJ_STATUS_BAR_APP_LAUNCH_FROM_CALL_CHIP,
                ),
            )
        }
    }

    private fun getClickBehavior(
        intent: PendingIntent?,
        instanceId: InstanceId?,
    ): OngoingActivityChipModel.ClickBehavior =
        if (intent == null) {
            OngoingActivityChipModel.ClickBehavior.None
        } else {
            OngoingActivityChipModel.ClickBehavior.ExpandAction(
                onClick = { expandable ->
                    StatusBarChipsModernization.unsafeAssertInNewMode()

                    logger.i({ "Chip clicked" }) {}
                    uiEventLogger.logChipTapToShow(instanceId)

                    val animationController =
                        if (
                            !StatusBarChipsReturnAnimations.isEnabled ||
                                transitionControllerFactory == null
                        ) {
                            expandable.activityTransitionController(
                                Cuj.CUJ_STATUS_BAR_APP_LAUNCH_FROM_CALL_CHIP
                            )
                        } else {
                            // When return animations are enabled, we use a long-lived registration
                            // with controllers created on-demand by the animation library instead
                            // of explicitly creating one at the time of the click. By not passing
                            // a controller here, we let the framework do its work. Otherwise, the
                            // explicit controller would take precedence and override the other one.
                            null
                        }
                    activityStarter.postStartActivityDismissingKeyguard(intent, animationController)
                }
            )
        }

    private fun getContentDescription(appName: String): ContentDescription {
        val ongoingCallDescription = context.getString(R.string.ongoing_call_content_description)
        return ContentDescription.Loaded(
            context.getString(
                R.string.accessibility_desc_notification_icon,
                appName,
                ongoingCallDescription,
            )
        )
    }

    private fun getTransitionManager(
        state: OngoingCallModel,
        transitionState: TransitionState = TransitionState.NoTransition,
    ): OngoingActivityChipModel.TransitionManager? {
        if (!StatusBarChipsReturnAnimations.isEnabled) return null
        return if (state is OngoingCallModel.NoCall) {
            OngoingActivityChipModel.TransitionManager(
                unregisterTransition = { activityStarter.unregisterTransition(cookie) }
            )
        } else {
            val component = (state as OngoingCallModel.InCall).intent?.intent?.component
            if (component != null) {
                val factory = getTransitionControllerFactory(component)
                OngoingActivityChipModel.TransitionManager(
                    factory,
                    registerTransition = {
                        activityStarter.registerTransition(cookie, factory, scope)
                    },
                    // Make the chip invisible at the beginning of the return transition to avoid
                    // it flickering.
                    hideChipForTransition = transitionState is TransitionState.ReturnRequested,
                )
            } else {
                // Without a component we can't instantiate a controller factory, and without a
                // factory registering an animation is impossible. In this case, the transition
                // manager is empty and inert.
                OngoingActivityChipModel.TransitionManager()
            }
        }
    }

    private fun getTransitionControllerFactory(
        component: ComponentName
    ): ComposableControllerFactory {
        var factory = transitionControllerFactory
        if (factory?.component == component) return factory

        factory =
            object :
                ComposableControllerFactory(
                    cookie,
                    component,
                    launchCujType = Cuj.CUJ_STATUS_BAR_APP_LAUNCH_FROM_CALL_CHIP,
                    returnCujType = Cuj.CUJ_STATUS_BAR_APP_RETURN_TO_CALL_CHIP,
                ) {
                override suspend fun createController(
                    forLaunch: Boolean
                ): ActivityTransitionAnimator.Controller {
                    transitionState.value =
                        if (forLaunch) {
                            TransitionState.LaunchRequested
                        } else {
                            TransitionState.ReturnRequested
                        }

                    val controller =
                        expandable
                            .mapNotNull {
                                it?.activityTransitionController(
                                    launchCujType,
                                    cookie,
                                    component,
                                    returnCujType,
                                    isEphemeral = false,
                                )
                            }
                            .first()

                    return object : DelegateTransitionAnimatorController(controller) {
                        override val isLaunching: Boolean
                            get() = forLaunch

                        override fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {
                            delegate.onTransitionAnimationStart(isExpandingFullyAbove)
                            transitionState.value =
                                if (isLaunching) {
                                    TransitionState.Launching
                                } else {
                                    TransitionState.Returning
                                }
                        }

                        override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                            delegate.onTransitionAnimationEnd(isExpandingFullyAbove)
                            transitionState.value = TransitionState.NoTransition
                        }

                        override fun onTransitionAnimationCancelled(
                            newKeyguardOccludedState: Boolean?
                        ) {
                            delegate.onTransitionAnimationCancelled(newKeyguardOccludedState)
                            transitionState.value = TransitionState.NoTransition
                        }
                    }
                }
            }

        transitionControllerFactory = factory
        return factory
    }

    /** Define the current state of this chip's transition animation. */
    private sealed interface TransitionState {
        /** Idle. */
        data object NoTransition : TransitionState

        /** Launch animation has been requested but hasn't started yet. */
        data object LaunchRequested : TransitionState

        /** Launch animation in progress. */
        data object Launching : TransitionState

        /** Return animation has been requested but hasn't started yet. */
        data object ReturnRequested : TransitionState

        /** Return animation in progress. */
        data object Returning : TransitionState
    }

    companion object {
        private val phoneIcon =
            Icon.Resource(
                com.android.internal.R.drawable.ic_phone,
                ContentDescription.Resource(R.string.ongoing_call_content_description),
            )

        const val KEY_PREFIX = "callChip-"

        /** Determines whether or not an active call chip should be hidden. */
        private fun shouldChipBeHidden(
            oldState: OngoingCallModel,
            newState: OngoingCallModel.InCall,
            oldTransitionState: TransitionState,
            newTransitionState: TransitionState,
        ): Boolean {
            // The app is in the background and no transitions are ongoing (during transitions,
            // [isAppVisible] must always be true). Show the chip.
            if (!newState.isAppVisible) return false

            // The call has just started and is visible. Hide the chip.
            if (oldState is OngoingCallModel.NoCall) return true

            // The state went from the app not being visible to visible. This happens when the chip
            // is tapped and a launch animation is about to start. Keep the chip showing.
            if (!(oldState as OngoingCallModel.InCall).isAppVisible) return false

            // The app was and remains visible, but the transition state has changed. A launch or
            // return animation has been requested or is ongoing. Keep the chip showing.
            if (
                newTransitionState is TransitionState.LaunchRequested ||
                    newTransitionState is TransitionState.Launching ||
                    newTransitionState is TransitionState.ReturnRequested ||
                    newTransitionState is TransitionState.Returning
            ) {
                return false
            }

            // The app was and remains visible, so we generally want to hide the chip. The only
            // exception is if a return transition has just ended. In this case, the transition
            // state changes shortly before the app visibility does. If we hide the chip between
            // these two updates, this results in a flicker. We bridge the gap by keeping the chip
            // showing.
            return oldTransitionState != TransitionState.Returning
        }
    }
}
