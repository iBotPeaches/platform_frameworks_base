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

package com.android.systemui.statusbar.notification.collection

import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.android.internal.logging.MetricsLogger
import com.android.systemui.statusbar.notification.NotificationActivityStarter
import com.android.systemui.statusbar.notification.collection.coordinator.VisualStabilityCoordinator
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender
import com.android.systemui.statusbar.notification.collection.provider.HighPriorityProvider
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.icon.IconPack
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotificationActionClickManager
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider
import kotlinx.coroutines.flow.StateFlow

class NotificationEntryAdapter(
    private val notificationActivityStarter: NotificationActivityStarter,
    private val metricsLogger: MetricsLogger,
    private val peopleNotificationIdentifier: PeopleNotificationIdentifier,
    private val iconStyleProvider: NotificationIconStyleProvider,
    private val visualStabilityCoordinator: VisualStabilityCoordinator,
    private val notificationActionClickManager: NotificationActionClickManager,
    private val highPriorityProvider: HighPriorityProvider,
    private val headsUpManager: HeadsUpManager,
    private val entry: NotificationEntry,
) : EntryAdapter {
    override fun getBackingHashCode(): Int {
        return entry.hashCode()
    }

    override fun getParent(): PipelineEntry? {
        return entry.parent
    }

    override fun isTopLevelEntry(): Boolean {
        return parent != null &&
            (parent === GroupEntry.ROOT_ENTRY || BundleEntry.ROOT_BUNDLES.contains(parent))
    }

    override fun getKey(): String {
        return entry.key
    }

    override fun getRow(): ExpandableNotificationRow {
        return entry.row
    }

    override fun isGroupRoot(): Boolean {
        if (isTopLevelEntry || parent == null) {
            return false
        }
        return (entry.parent as? GroupEntry)?.summary == entry
    }

    override fun isSensitive(): StateFlow<Boolean> {
        return entry.isSensitive
    }

    override fun isClearable(): Boolean {
        return entry.isClearable
    }

    override fun getTargetSdk(): Int {
        return entry.targetSdk
    }

    override fun getSummarization(): String? {
        return entry.ranking?.summarization
    }

    override fun prepareForInflation() {
        entry.sbn.clearPackageContext()
    }

    override fun getContrastedColor(
        context: Context?,
        isLowPriority: Boolean,
        backgroundColor: Int,
    ): Int {
        return entry.getContrastedColor(context, isLowPriority, backgroundColor)
    }

    override fun canPeek(): Boolean {
        return entry.isStickyAndNotDemoted
    }

    override fun getWhen(): Long {
        return entry.sbn.notification.getWhen()
    }

    override fun getIcons(): IconPack {
        return entry.icons
    }

    override fun isColorized(): Boolean {
        return entry.sbn.notification.isColorized
    }

    override fun getSbn(): StatusBarNotification {
        return entry.sbn
    }

    override fun getRanking(): NotificationListenerService.Ranking? {
        return entry.ranking
    }

    override fun endLifetimeExtension(
        callback: NotifLifetimeExtender.OnEndLifetimeExtensionCallback?,
        extender: NotifLifetimeExtender,
    ) {
        callback?.onEndLifetimeExtension(extender, entry)
    }

    override fun onImportanceChanged() {
        visualStabilityCoordinator.temporarilyAllowSectionChanges(entry, SystemClock.uptimeMillis())
    }

    override fun markForUserTriggeredMovement() {
        entry.markForUserTriggeredMovement(true)
    }

    override fun isMarkedForUserTriggeredMovement(): Boolean {
        return entry.isMarkedForUserTriggeredMovement
    }

    override fun isHighPriority(): Boolean {
        return highPriorityProvider.isHighPriority(entry)
    }

    override fun setInlineControlsShown(currentlyVisible: Boolean) {
        headsUpManager.setGutsShown(entry, currentlyVisible)
    }

    override fun isBlockable(): Boolean {
        return entry.isBlockable
    }

    override fun canDragAndDrop(): Boolean {
        val canBubble: Boolean = entry.canBubble()
        val notif = entry.sbn.notification
        val dragIntent =
            if (notif.contentIntent != null) notif.contentIntent else notif.fullScreenIntent
        if (dragIntent != null && dragIntent.isActivity && !canBubble) {
            return true
        }
        return false
    }

    override fun isBubble(): Boolean {
        return entry.isBubble
    }

    override fun getStyle(): String? {
        return entry.notificationStyle
    }

    override fun getSectionBucket(): Int {
        return entry.bucket
    }

    override fun isAmbient(): Boolean {
        return entry.ranking.isAmbient
    }

    override fun getPeopleNotificationType(): Int {
        return peopleNotificationIdentifier.getPeopleNotificationType(entry)
    }

    override fun isPromotedOngoing(): Boolean {
        return entry.isPromotedOngoing
    }

    override fun isFullScreenCapable(): Boolean {
        return entry.sbn.notification.fullScreenIntent != null
    }

    override fun onDragSuccess() {
        notificationActivityStarter.onDragSuccess(entry)
    }

    override fun onNotificationBubbleIconClicked() {
        notificationActivityStarter.onNotificationBubbleIconClicked(entry)
    }

    override fun onNotificationActionClicked() {
        notificationActionClickManager.onNotificationActionClicked(entry)
    }

    override fun getDismissState(): NotificationEntry.DismissState {
        return entry.dismissState
    }

    override fun onEntryClicked(row: ExpandableNotificationRow) {
        notificationActivityStarter.onNotificationClicked(entry, row)
    }
}
