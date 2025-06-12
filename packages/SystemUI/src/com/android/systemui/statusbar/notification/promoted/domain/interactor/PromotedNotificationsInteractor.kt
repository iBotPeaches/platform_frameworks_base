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

package com.android.systemui.statusbar.notification.promoted.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.chips.call.domain.interactor.CallChipInteractor
import com.android.systemui.statusbar.chips.mediaprojection.domain.interactor.MediaProjectionChipInteractor
import com.android.systemui.statusbar.chips.mediaprojection.domain.model.ProjectionChipModel
import com.android.systemui.statusbar.chips.notification.domain.interactor.StatusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.screenrecord.domain.interactor.ScreenRecordChipInteractor
import com.android.systemui.statusbar.chips.screenrecord.domain.model.ScreenRecordChipModel
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style.Ineligible
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * An interactor that provides details about promoted notification precedence, based on the
 * presented order of current notification status bar chips.
 */
@SysUISingleton
@OptIn(ExperimentalCoroutinesApi::class)
class PromotedNotificationsInteractor
@Inject
constructor(
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
    screenRecordChipInteractor: ScreenRecordChipInteractor,
    mediaProjectionChipInteractor: MediaProjectionChipInteractor,
    callChipInteractor: CallChipInteractor,
    notifChipsInteractor: StatusBarNotificationChipsInteractor,
    @Background backgroundDispatcher: CoroutineDispatcher,
) {
    private val screenRecordChipNotification: Flow<NotifAndPromotedContent?> =
        screenRecordChipInteractor.screenRecordState.flatMapLatest { screenRecordModel ->
            when (screenRecordModel) {
                is ScreenRecordChipModel.DoingNothing -> flowOf(null)
                is ScreenRecordChipModel.Starting -> flowOf(null)
                is ScreenRecordChipModel.Recording ->
                    createRecordingNotificationFlow(hostPackage = screenRecordModel.hostPackage)
            }
        }

    private val mediaProjectionChipNotification: Flow<NotifAndPromotedContent?> =
        mediaProjectionChipInteractor.projection.flatMapLatest { projectionModel ->
            when (projectionModel) {
                is ProjectionChipModel.NotProjecting -> flowOf(null)
                is ProjectionChipModel.Projecting ->
                    createRecordingNotificationFlow(
                        hostPackage = projectionModel.projectionState.hostPackage
                    )
            }
        }

    /**
     * Creates a flow emitting the screen-recording-related notification corresponding to the given
     * package name (if we can find it).
     *
     * @param hostPackage the package name of the app that is receiving the content of the media
     *   projection (aka which app the phone screen contents are being sent to).
     */
    private fun createRecordingNotificationFlow(
        hostPackage: String?
    ): Flow<NotifAndPromotedContent?> =
        if (hostPackage == null) {
            flowOf(null)
        } else {
            activeNotificationsInteractor.allRepresentativeNotifications
                .map { allNotifs ->
                    findBestMatchingMediaProjectionNotif(allNotifs.values, hostPackage)
                }
                .distinctUntilChanged()
        }

    /**
     * Finds the best notification that matches the given [hostPackage] that looks like a recording
     * notification, or null if we couldn't find a uniquely good match.
     */
    private fun findBestMatchingMediaProjectionNotif(
        allNotifs: Collection<ActiveNotificationModel>,
        hostPackage: String,
    ): NotifAndPromotedContent? {
        val candidates = allNotifs.filter { it.packageName == hostPackage }
        if (candidates.isEmpty()) {
            return null
        }

        candidates
            .findOnlyOrNull { it.isForegroundService }
            ?.let {
                return it.toNotifAndPromotedContent()
            }
        candidates
            .findOnlyOrNull { it.isOngoingEvent }
            ?.let {
                return it.toNotifAndPromotedContent()
            }
        candidates
            .findOnlyOrNull { it.isForegroundService && it.isOngoingEvent }
            ?.let {
                return it.toNotifAndPromotedContent()
            }
        // We weren't able to find exactly 1 match for the given [hostPackage], so just don't match
        // at all.
        return null
    }

    /**
     * Returns the single notification matching the given [predicate] if there's only 1 match, or
     * null if there's 0 or 2+ matches.
     */
    private fun List<ActiveNotificationModel>.findOnlyOrNull(
        predicate: (ActiveNotificationModel) -> Boolean
    ): ActiveNotificationModel? {
        val list = this.filter(predicate)
        return if (list.size == 1) {
            list.first()
        } else {
            null
        }
    }

    private fun ActiveNotificationModel.toNotifAndPromotedContent(): NotifAndPromotedContent {
        return NotifAndPromotedContent(this.key, this.promotedContent)
    }

    private val callNotification: Flow<NotifAndPromotedContent?> =
        callChipInteractor.ongoingCallState
            .map {
                when (it) {
                    is OngoingCallModel.InCall ->
                        NotifAndPromotedContent(it.notificationKey, it.promotedContent)
                    is OngoingCallModel.NoCall -> null
                }
            }
            .distinctUntilChanged()

    private val promotedChipNotifications: Flow<List<NotifAndPromotedContent>> =
        notifChipsInteractor.allNotificationChips
            .map { chips -> chips.map { NotifAndPromotedContent(it.key, it.promotedContent) } }
            .distinctUntilChanged()

    /**
     * This is the ordered list of notifications (and the promoted content) represented as chips in
     * the status bar.
     */
    private val orderedChipNotifications: Flow<List<NotifAndPromotedContent>> =
        combine(
            screenRecordChipNotification,
            mediaProjectionChipNotification,
            callNotification,
            promotedChipNotifications,
        ) { screenRecordNotif, mediaProjectionNotif, callNotif, promotedNotifs ->
            val chipNotifications = mutableListOf<NotifAndPromotedContent>()
            val usedKeys = mutableListOf<String>()

            fun addToList(item: NotifAndPromotedContent?) {
                if (item != null && !usedKeys.contains(item.key)) {
                    chipNotifications.add(item)
                    usedKeys.add(item.key)
                }
            }

            // IMPORTANT: This ordering is prescribed by OngoingActivityChipsViewModel. Be sure to
            // always keep this ordering in sync with that view model.
            // TODO(b/402471288): Create a single source of truth for the ordering.
            addToList(screenRecordNotif)
            addToList(mediaProjectionNotif)
            addToList(callNotif)
            promotedNotifs.forEach { addToList(it) }

            chipNotifications
        }

    /**
     * The top promoted notification represented by a chip, with the order determined by the order
     * of the chips, not the notifications.
     */
    private val topPromotedChipNotification: Flow<PromotedNotificationContentModels?> =
        orderedChipNotifications
            .map { list -> list.firstNotNullOfOrNull { it.promotedContent } }
            .distinctUntilNewInstance()

    /** This is the AOD promoted notification, which should avoid regular changing. */
    val aodPromotedNotification: Flow<PromotedNotificationContentModels?> =
        combine(
                topPromotedChipNotification,
                activeNotificationsInteractor.topLevelRepresentativeNotifications,
            ) { topChipNotif, topLevelNotifs ->
                topChipNotif?.takeIfAodEligible() ?: topLevelNotifs.firstAodEligibleOrNull()
            }
            // #equals() can be a bit expensive on this object, but this flow will regularly try to
            // emit the same immutable instance over and over, so just prevent that.
            .distinctUntilNewInstance()

    /**
     * This is the ordered list of notifications (and the promoted content) represented as chips in
     * the status bar. Flows on the background context.
     */
    val orderedChipNotificationKeys: Flow<List<String>> =
        orderedChipNotifications
            .map { list -> list.map { it.key } }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)

    private fun List<ActiveNotificationModel>.firstAodEligibleOrNull():
        PromotedNotificationContentModels? {
        return this.firstNotNullOfOrNull { it.promotedContent?.takeIfAodEligible() }
    }

    private fun PromotedNotificationContentModels.takeIfAodEligible():
        PromotedNotificationContentModels? {
        return this.takeUnless { it.privateVersion.style == Ineligible }
    }

    /**
     * Returns flow where all subsequent repetitions of the same object instance are filtered out.
     */
    private fun <T> Flow<T>.distinctUntilNewInstance() = distinctUntilChanged { a, b -> a === b }

    /**
     * A custom pair, but providing clearer semantic names, and implementing equality as being the
     * same instance of the promoted content model, which allows us to use distinctUntilChanged() on
     * flows containing this without doing pixel comparisons on the Bitmaps inside Icon objects
     * provided by the Notification.
     */
    private data class NotifAndPromotedContent(
        val key: String,
        val promotedContent: PromotedNotificationContentModels?,
    ) {
        /**
         * Define the equals of this object to only check the reference equality of the promoted
         * content so that we can mark.
         */
        override fun equals(other: Any?): Boolean {
            return when {
                other == null -> false
                other === this -> true
                other !is NotifAndPromotedContent -> return false
                else -> key == other.key && promotedContent === other.promotedContent
            }
        }

        /** Define the hashCode to be very quick, even if it increases collisions. */
        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + (promotedContent?.key?.hashCode() ?: 0)
            return result
        }
    }
}
