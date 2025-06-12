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

package com.android.systemui.statusbar.chips.ui.model

import android.annotation.CurrentTimeMillisLong
import android.annotation.ElapsedRealtimeLong
import android.os.SystemClock
import android.view.View
import com.android.internal.logging.InstanceId
import com.android.systemui.animation.ComposableControllerFactory
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.chips.notification.shared.StatusBarNotifChips
import com.android.systemui.statusbar.chips.ui.viewmodel.TimeSource
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays

/** Model representing the display of an ongoing activity as a chip in the status bar. */
sealed class OngoingActivityChipModel {
    /** Condensed name representing the model, used for logs. */
    abstract val logName: String

    /** Object used to manage the behavior of this chip during activity launch and returns. */
    abstract val transitionManager: TransitionManager?

    /**
     * This chip shouldn't be shown.
     *
     * @property shouldAnimate true if the transition from [Active] to [Inactive] should be
     *   animated, and false if that transition should *not* be animated (i.e. the chip view should
     *   immediately disappear).
     */
    data class Inactive(
        val shouldAnimate: Boolean = true,
        override val transitionManager: TransitionManager? = null,
    ) : OngoingActivityChipModel() {
        override val logName = "Inactive(anim=$shouldAnimate)"
    }

    /** This chip should be shown with the given information. */
    sealed class Active(
        /**
         * A key that uniquely identifies this chip. Used for better visual effects, like animation.
         */
        open val key: String,
        /**
         * True if this chip is critical for privacy so we should keep it visible at all times, and
         * false otherwise.
         */
        open val isImportantForPrivacy: Boolean = false,
        /** The icon to show on the chip. If null, no icon will be shown. */
        open val icon: ChipIcon?,
        /** What colors to use for the chip. */
        open val colors: ColorsModel,
        /**
         * Listener method to invoke when this chip is clicked. If null, the chip won't be
         * clickable. Will be deprecated after [StatusBarChipsModernization] is enabled.
         */
        open val onClickListenerLegacy: View.OnClickListener?,
        /** Data class that determines how clicks on the chip should be handled. */
        open val clickBehavior: ClickBehavior,
        override val transitionManager: TransitionManager?,
        /**
         * Whether this chip should be hidden. This can be the case depending on system states (like
         * which apps are in the foreground and whether there is an ongoing transition.
         */
        open val isHidden: Boolean,
        /** Whether the transition from hidden to shown should be animated. */
        open val shouldAnimate: Boolean,
        /**
         * An optional per-chip ID used for logging. Should stay the same throughout the lifetime of
         * a single chip.
         */
        open val instanceId: InstanceId? = null,
    ) : OngoingActivityChipModel() {

        /** This chip shows only an icon and nothing else. */
        data class IconOnly(
            override val key: String,
            override val isImportantForPrivacy: Boolean = false,
            override val icon: ChipIcon,
            override val colors: ColorsModel,
            override val onClickListenerLegacy: View.OnClickListener?,
            override val clickBehavior: ClickBehavior,
            override val transitionManager: TransitionManager? = null,
            override val isHidden: Boolean = false,
            override val shouldAnimate: Boolean = true,
            override val instanceId: InstanceId? = null,
        ) :
            Active(
                key,
                isImportantForPrivacy,
                icon,
                colors,
                onClickListenerLegacy,
                clickBehavior,
                transitionManager,
                isHidden,
                shouldAnimate,
                instanceId,
            ) {
            override val logName = "Active.Icon"
        }

        /** The chip shows a timer, counting up from [startTimeMs]. */
        data class Timer(
            override val key: String,
            override val isImportantForPrivacy: Boolean = false,
            override val icon: ChipIcon,
            override val colors: ColorsModel,
            /**
             * The time this event started, used to show the timer.
             *
             * This time should be relative to
             * [com.android.systemui.util.time.SystemClock.elapsedRealtime], *not*
             * [com.android.systemui.util.time.SystemClock.currentTimeMillis] because the
             * [ChipChronometer] is based off of elapsed realtime. See
             * [android.widget.Chronometer.setBase].
             */
            @ElapsedRealtimeLong val startTimeMs: Long,

            /**
             * The [TimeSource] that should be used to track the current time for this timer. Should
             * be compatible units with [startTimeMs]. Only used in the Compose version of the
             * chips.
             */
            val timeSource: TimeSource = TimeSource { SystemClock.elapsedRealtime() },

            /**
             * True if this chip represents an event starting in the future and false if this chip
             * represents an event that has already started. If true, [startTimeMs] should be in the
             * future. Otherwise, [startTimeMs] should be in the past.
             */
            val isEventInFuture: Boolean = false,
            override val onClickListenerLegacy: View.OnClickListener?,
            override val clickBehavior: ClickBehavior,
            override val transitionManager: TransitionManager? = null,
            override val isHidden: Boolean = false,
            override val shouldAnimate: Boolean = true,
            override val instanceId: InstanceId? = null,
        ) :
            Active(
                key,
                isImportantForPrivacy,
                icon,
                colors,
                onClickListenerLegacy,
                clickBehavior,
                transitionManager,
                isHidden,
                shouldAnimate,
                instanceId,
            ) {
            override val logName = "Active.Timer"
        }

        /**
         * The chip shows the time delta between now and [time] in a short format, e.g. "15min" or
         * "1hr ago".
         */
        data class ShortTimeDelta(
            override val key: String,
            override val isImportantForPrivacy: Boolean = false,
            override val icon: ChipIcon,
            override val colors: ColorsModel,
            /**
             * The time of the event that this chip represents. Relative to
             * [com.android.systemui.util.time.SystemClock.currentTimeMillis] because that's what's
             * required by [android.widget.DateTimeView].
             *
             * TODO(b/372657935): When the Compose chips are launched, we should convert this to be
             *   relative to [com.android.systemui.util.time.SystemClock.elapsedRealtime] so that
             *   this model and the [Timer] model use the same units.
             */
            @CurrentTimeMillisLong val time: Long,

            /**
             * The [TimeSource] that should be used to track the current time for this timer. Should
             * be compatible units with [time]. Only used in the Compose version of the chips.
             */
            val timeSource: TimeSource = TimeSource { System.currentTimeMillis() },
            override val onClickListenerLegacy: View.OnClickListener?,
            override val clickBehavior: ClickBehavior,
            override val transitionManager: TransitionManager? = null,
            override val isHidden: Boolean = false,
            override val shouldAnimate: Boolean = true,
            override val instanceId: InstanceId? = null,
        ) :
            Active(
                key,
                isImportantForPrivacy,
                icon,
                colors,
                onClickListenerLegacy,
                clickBehavior,
                transitionManager,
                isHidden,
                shouldAnimate,
                instanceId,
            ) {
            init {
                StatusBarNotifChips.unsafeAssertInNewMode()
            }

            override val logName = "Active.ShortTimeDelta"
        }

        /**
         * This chip shows a countdown using [secondsUntilStarted]. Used to inform users that an
         * event is about to start. Typically, a [Countdown] chip will turn into a [Timer] chip.
         */
        data class Countdown(
            override val key: String,
            override val isImportantForPrivacy: Boolean = false,
            override val colors: ColorsModel,
            /** The number of seconds until an event is started. */
            val secondsUntilStarted: Long,
            override val transitionManager: TransitionManager? = null,
            override val isHidden: Boolean = false,
            override val shouldAnimate: Boolean = true,
            override val instanceId: InstanceId? = null,
        ) :
            Active(
                key,
                isImportantForPrivacy,
                icon = null,
                colors,
                onClickListenerLegacy = null,
                clickBehavior = ClickBehavior.None,
                transitionManager,
                isHidden,
                shouldAnimate,
                instanceId,
            ) {
            override val logName = "Active.Countdown"
        }

        /** This chip shows the specified [text] in the chip. */
        data class Text(
            override val key: String,
            override val isImportantForPrivacy: Boolean = false,
            override val icon: ChipIcon,
            override val colors: ColorsModel,
            val text: String,
            override val onClickListenerLegacy: View.OnClickListener? = null,
            override val clickBehavior: ClickBehavior,
            override val transitionManager: TransitionManager? = null,
            override val isHidden: Boolean = false,
            override val shouldAnimate: Boolean = true,
            override val instanceId: InstanceId? = null,
        ) :
            Active(
                key,
                isImportantForPrivacy,
                icon,
                colors,
                onClickListenerLegacy,
                clickBehavior,
                transitionManager,
                isHidden,
                shouldAnimate,
                instanceId,
            ) {
            override val logName = "Active.Text"
        }
    }

    /** Represents an icon to show on the chip. */
    sealed class ChipIcon(
        /** True if this icon will have padding embedded within its view. */
        open val hasEmbeddedPadding: Boolean
    ) {
        /**
         * The icon is a custom icon, which is set on [impl]. The icon was likely created by an
         * external app.
         */
        data class StatusBarView(
            val impl: StatusBarIconView,
            val contentDescription: ContentDescription,
        ) : ChipIcon(hasEmbeddedPadding = true) {
            init {
                StatusBarConnectedDisplays.assertInLegacyMode()
            }
        }

        /**
         * The icon is a custom icon, which is set on a notification, and can be looked up using the
         * provided [notificationKey]. The icon was likely created by an external app.
         */
        data class StatusBarNotificationIcon(
            val notificationKey: String,
            val contentDescription: ContentDescription,
        ) : ChipIcon(hasEmbeddedPadding = true) {
            init {
                StatusBarConnectedDisplays.unsafeAssertInNewMode()
            }
        }

        /**
         * This icon is a single color and it came from basic resource or drawable icon that System
         * UI created internally.
         */
        data class SingleColorIcon(val impl: Icon) : ChipIcon(hasEmbeddedPadding = false)
    }

    /** Defines the behavior of the chip when it is clicked. */
    sealed interface ClickBehavior {
        /** No specific click behavior. */
        data object None : ClickBehavior

        /** The chip expands into a dialog or activity on click. */
        data class ExpandAction(val onClick: (Expandable) -> Unit) : ClickBehavior

        /** Clicking the chip will show the heads up notification associated with the chip. */
        data class ShowHeadsUpNotification(val onClick: () -> Unit) : ClickBehavior
    }

    /** Defines the behavior of the chip with respect to activity launch and return transitions. */
    data class TransitionManager(
        /** The factory used to create the controllers that animate the chip. */
        val controllerFactory: ComposableControllerFactory? = null,
        /**
         * Used to create a registration for this chip using [controllerFactory]. Must be
         * idempotent.
         */
        val registerTransition: () -> Unit = {},
        /** Used to remove the existing registration for this chip, if any. */
        val unregisterTransition: () -> Unit = {},
        /**
         * Whether the chip should be made invisible (0 opacity) while still being composed. This is
         * necessary to avoid flickers at the beginning of return transitions, when the chip must
         * not be visible but must be composed in order for the animation to start.
         */
        val hideChipForTransition: Boolean = false,
    )
}
