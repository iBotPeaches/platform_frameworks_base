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

package com.android.systemui.statusbar.chips.notification.ui.viewmodel

import android.content.Context
import android.view.View
import com.android.systemui.Flags
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.notification.domain.interactor.StatusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.notification.domain.model.NotificationChipModel
import com.android.systemui.statusbar.chips.notification.shared.StatusBarNotifChips
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.statusbar.notification.domain.model.TopPinnedState
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** A view model for status bar chips for promoted ongoing notifications. */
@SysUISingleton
class NotifChipsViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    private val notifChipsInteractor: StatusBarNotificationChipsInteractor,
    headsUpNotificationInteractor: HeadsUpNotificationInteractor,
    private val systemClock: SystemClock,
) {
    /**
     * A flow modeling the current notification chips. Emits an empty list if there are no
     * notifications that are eligible to show a status bar chip.
     */
    val chips: Flow<List<OngoingActivityChipModel.Active>> =
        combine(
                notifChipsInteractor.allNotificationChips,
                headsUpNotificationInteractor.statusBarHeadsUpState,
            ) { notifications, headsUpState ->
                notifications.map { it.toActivityChipModel(headsUpState) }
            }
            .distinctUntilChanged()

    /** Converts the notification to the [OngoingActivityChipModel] object. */
    private fun NotificationChipModel.toActivityChipModel(
        headsUpState: TopPinnedState
    ): OngoingActivityChipModel.Active {
        StatusBarNotifChips.unsafeAssertInNewMode()
        // Chips are never shown when locked, so it's safe to use the version with sensitive content
        val chipContent = promotedContent.privateVersion
        val contentDescription = getContentDescription(this.appName)
        val icon =
            if (this.statusBarChipIconView != null) {
                StatusBarConnectedDisplays.assertInLegacyMode()
                OngoingActivityChipModel.ChipIcon.StatusBarView(
                    this.statusBarChipIconView,
                    contentDescription,
                )
            } else {
                StatusBarConnectedDisplays.unsafeAssertInNewMode()
                OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon(
                    this.key,
                    contentDescription,
                )
            }
        val colors = ColorsModel.SystemThemed
        val clickListener: () -> Unit = {
            // The notification pipeline needs everything to run on the main thread, so keep
            // this event on the main thread.
            applicationScope.launch {
                // TODO(b/364653005): Move accessibility focus to the HUN when chip is tapped.
                notifChipsInteractor.onPromotedNotificationChipTapped(this@toActivityChipModel.key)
            }
        }
        // If the app that posted this notification is visible, we want to hide the chip
        // because information between the status bar chip and the app itself could be
        // out-of-sync (like a timer that's slightly off)
        val isHidden = this.isAppVisible
        val onClickListenerLegacy =
            View.OnClickListener {
                StatusBarChipsModernization.assertInLegacyMode()
                clickListener.invoke()
            }
        val clickBehavior =
            OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification({
                StatusBarChipsModernization.unsafeAssertInNewMode()
                clickListener.invoke()
            })

        val isShowingHeadsUpFromChipTap =
            headsUpState is TopPinnedState.Pinned &&
                headsUpState.status == PinnedStatus.PinnedByUser &&
                headsUpState.key == this.key
        if (isShowingHeadsUpFromChipTap) {
            // If the user tapped this chip to show the HUN, we want to just show the icon because
            // the HUN will show the rest of the information.
            return OngoingActivityChipModel.Active.IconOnly(
                key = this.key,
                icon = icon,
                colors = colors,
                onClickListenerLegacy = onClickListenerLegacy,
                clickBehavior = clickBehavior,
                isHidden = isHidden,
                instanceId = instanceId,
            )
        }

        if (chipContent.shortCriticalText != null) {
            return OngoingActivityChipModel.Active.Text(
                key = this.key,
                icon = icon,
                colors = colors,
                text = chipContent.shortCriticalText,
                onClickListenerLegacy = onClickListenerLegacy,
                clickBehavior = clickBehavior,
                isHidden = isHidden,
                instanceId = instanceId,
            )
        }

        if (Flags.promoteNotificationsAutomatically() && chipContent.wasPromotedAutomatically) {
            // When we're promoting notifications automatically, the `when` time set on the
            // notification will likely just be set to the current time, which would cause the chip
            // to always show "now". We don't want early testers to get that experience since it's
            // not what will happen at launch, so just don't show any time.onometerstate
            return OngoingActivityChipModel.Active.IconOnly(
                key = this.key,
                icon = icon,
                colors = colors,
                onClickListenerLegacy = onClickListenerLegacy,
                clickBehavior = clickBehavior,
                isHidden = isHidden,
                instanceId = instanceId,
            )
        }

        if (chipContent.time == null) {
            return OngoingActivityChipModel.Active.IconOnly(
                key = this.key,
                icon = icon,
                colors = colors,
                onClickListenerLegacy = onClickListenerLegacy,
                clickBehavior = clickBehavior,
                isHidden = isHidden,
                instanceId = instanceId,
            )
        }

        when (chipContent.time) {
            is PromotedNotificationContentModel.When.Time -> {
                return if (
                    chipContent.time.currentTimeMillis >=
                        systemClock.currentTimeMillis() + FUTURE_TIME_THRESHOLD_MILLIS
                ) {
                    OngoingActivityChipModel.Active.ShortTimeDelta(
                        key = this.key,
                        icon = icon,
                        colors = colors,
                        time = chipContent.time.currentTimeMillis,
                        onClickListenerLegacy = onClickListenerLegacy,
                        clickBehavior = clickBehavior,
                        isHidden = isHidden,
                        instanceId = instanceId,
                    )
                } else {
                    // Don't show a `when` time that's close to now or in the past because it's
                    // likely that the app didn't intentionally set the `when` time to be shown in
                    // the status bar chip.
                    // TODO(b/393369213): If a notification sets a `when` time in the future and
                    // then that time comes and goes, the chip *will* start showing times in the
                    // past. Not going to fix this right now because the Compose implementation
                    // automatically handles this for us and we're hoping to launch the notification
                    // chips at the same time as the Compose chips.
                    return OngoingActivityChipModel.Active.IconOnly(
                        key = this.key,
                        icon = icon,
                        colors = colors,
                        onClickListenerLegacy = onClickListenerLegacy,
                        clickBehavior = clickBehavior,
                        isHidden = isHidden,
                        instanceId = instanceId,
                    )
                }
            }
            is PromotedNotificationContentModel.When.Chronometer -> {
                return OngoingActivityChipModel.Active.Timer(
                    key = this.key,
                    icon = icon,
                    colors = colors,
                    startTimeMs = chipContent.time.elapsedRealtimeMillis,
                    isEventInFuture = chipContent.time.isCountDown,
                    onClickListenerLegacy = onClickListenerLegacy,
                    clickBehavior = clickBehavior,
                    isHidden = isHidden,
                    instanceId = instanceId,
                )
            }
        }
    }

    private fun getContentDescription(appName: String): ContentDescription {
        val ongoingDescription =
            context.getString(R.string.ongoing_notification_extra_content_description)
        return ContentDescription.Loaded(
            context.getString(
                R.string.accessibility_desc_notification_icon,
                appName,
                ongoingDescription,
            )
        )
    }

    companion object {
        /**
         * Notifications must have a `when` time of at least 1 minute in the future in order for the
         * status bar chip to show the time.
         */
        private const val FUTURE_TIME_THRESHOLD_MILLIS = 60 * 1000
    }
}
