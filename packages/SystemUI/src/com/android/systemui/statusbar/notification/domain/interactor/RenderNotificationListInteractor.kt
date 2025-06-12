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
package com.android.systemui.statusbar.notification.domain.interactor

import android.app.Notification.CallStyle.CALL_TYPE_INCOMING
import android.app.Notification.CallStyle.CALL_TYPE_ONGOING
import android.app.Notification.CallStyle.CALL_TYPE_SCREENING
import android.app.Notification.CallStyle.CALL_TYPE_UNKNOWN
import android.app.Notification.EXTRA_CALL_TYPE
import android.app.Notification.FLAG_ONGOING_EVENT
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.util.ArrayMap
import com.android.app.tracing.traceSection
import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.PipelineEntry
import com.android.systemui.statusbar.notification.collection.provider.SectionStyleProvider
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.shared.ActiveNotificationEntryModel
import com.android.systemui.statusbar.notification.shared.ActiveNotificationGroupModel
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.notification.shared.CallType
import javax.inject.Inject
import kotlinx.coroutines.flow.update

/**
 * Logic for passing information from the
 * [com.android.systemui.statusbar.notification.collection.NotifPipeline] to the presentation
 * layers.
 */
class RenderNotificationListInteractor
@Inject
constructor(
    private val repository: ActiveNotificationListRepository,
    private val sectionStyleProvider: SectionStyleProvider,
    @Main private val context: Context,
) {
    /**
     * Sets the current list of rendered notification entries as displayed in the notification list.
     */
    fun setRenderedList(entries: List<PipelineEntry>) {
        traceSection("RenderNotificationListInteractor.setRenderedList") {
            repository.activeNotifications.update { existingModels ->
                buildActiveNotificationsStore(existingModels, sectionStyleProvider, context) {
                    entries.forEach(::addPipelineEntry)
                    setRankingsMap(entries)
                }
            }
        }
    }
}

private fun buildActiveNotificationsStore(
    existingModels: ActiveNotificationsStore,
    sectionStyleProvider: SectionStyleProvider,
    context: Context,
    block: ActiveNotificationsStoreBuilder.() -> Unit,
): ActiveNotificationsStore =
    ActiveNotificationsStoreBuilder(existingModels, sectionStyleProvider, context)
        .apply(block)
        .build()

private class ActiveNotificationsStoreBuilder(
    private val existingModels: ActiveNotificationsStore,
    private val sectionStyleProvider: SectionStyleProvider,
    private val context: Context,
) {
    private val builder = ActiveNotificationsStore.Builder()

    fun build(): ActiveNotificationsStore = builder.build()

    /**
     * Convert a [PipelineEntry] into [ActiveNotificationEntryModel]s, and add them to the
     * [ActiveNotificationsStore]. Special care is taken to avoid re-allocating models if the result
     * would be identical to an existing model (at the expense of additional computations).
     */
    fun addPipelineEntry(entry: PipelineEntry) {
        when (entry) {
            is GroupEntry -> {
                entry.summary?.let { summary ->
                    val summaryModel = summary.toModel()
                    val childModels = entry.children.map { it.toModel() }
                    builder.addNotifGroup(
                        existingModels.createOrReuse(
                            key = entry.key,
                            summary = summaryModel,
                            children = childModels,
                        )
                    )
                }
            }
            else -> {
                entry.representativeEntry?.let { notifEntry ->
                    builder.addIndividualNotif(notifEntry.toModel())
                }
            }
        }
    }

    fun setRankingsMap(entries: List<PipelineEntry>) {
        builder.setRankingsMap(flatMapToRankingsMap(entries))
    }

    fun flatMapToRankingsMap(entries: List<PipelineEntry>): Map<String, Int> {
        val result = ArrayMap<String, Int>()
        for (entry in entries) {
            if (entry is NotificationEntry) {
                entry.representativeEntry?.let { representativeEntry ->
                    result[representativeEntry.key] = representativeEntry.ranking.rank
                }
            } else if (entry is GroupEntry) {
                entry.summary?.let { summary -> result[summary.key] = summary.ranking.rank }
                for (child in entry.children) {
                    result[child.key] = child.ranking.rank
                }
            }
        }
        return result
    }

    private fun NotificationEntry.toModel(): ActiveNotificationModel {
        val promotedContent =
            if (PromotedNotificationContentModel.featureFlagEnabled()) {
                promotedNotificationContentModels
            } else {
                null
            }

        return existingModels.createOrReuse(
            key = key,
            groupKey = sbn.groupKey,
            whenTime = sbn.notification.`when`,
            isForegroundService = sbn.notification.isForegroundService,
            isOngoingEvent = (sbn.notification.flags and FLAG_ONGOING_EVENT) != 0,
            isAmbient = sectionStyleProvider.isMinimized(this),
            isRowDismissed = isRowDismissed,
            isSilent = sectionStyleProvider.isSilent(this),
            isLastMessageFromReply = isLastMessageFromReply,
            isSuppressedFromStatusBar = shouldSuppressStatusBar(),
            isPulsing = showingPulsing(),
            aodIcon = icons.aodIcon?.sourceIcon,
            shelfIcon = icons.shelfIcon?.sourceIcon,
            statusBarIcon = icons.statusBarIcon?.sourceIcon,
            statusBarChipIconView = icons.statusBarChipIcon,
            uid = sbn.uid,
            packageName = sbn.packageName,
            appName = sbn.notification.loadHeaderAppName(context) ?: "",
            contentIntent = sbn.notification.contentIntent,
            instanceId = sbn.instanceId,
            isGroupSummary = sbn.notification.isGroupSummary,
            bucket = bucket,
            callType = sbn.toCallType(),
            promotedContent = promotedContent,
        )
    }
}

private fun ActiveNotificationsStore.createOrReuse(
    key: String,
    groupKey: String?,
    whenTime: Long,
    isForegroundService: Boolean,
    isOngoingEvent: Boolean,
    isAmbient: Boolean,
    isRowDismissed: Boolean,
    isSilent: Boolean,
    isLastMessageFromReply: Boolean,
    isSuppressedFromStatusBar: Boolean,
    isPulsing: Boolean,
    aodIcon: Icon?,
    shelfIcon: Icon?,
    statusBarIcon: Icon?,
    statusBarChipIconView: StatusBarIconView?,
    uid: Int,
    packageName: String,
    appName: String,
    contentIntent: PendingIntent?,
    instanceId: InstanceId?,
    isGroupSummary: Boolean,
    bucket: Int,
    callType: CallType,
    promotedContent: PromotedNotificationContentModels?,
): ActiveNotificationModel {
    return individuals[key]?.takeIf {
        it.isCurrent(
            key = key,
            groupKey = groupKey,
            whenTime = whenTime,
            isForegroundService = isForegroundService,
            isOngoingEvent = isOngoingEvent,
            isAmbient = isAmbient,
            isRowDismissed = isRowDismissed,
            isSilent = isSilent,
            isLastMessageFromReply = isLastMessageFromReply,
            isSuppressedFromStatusBar = isSuppressedFromStatusBar,
            isPulsing = isPulsing,
            aodIcon = aodIcon,
            shelfIcon = shelfIcon,
            statusBarIcon = statusBarIcon,
            statusBarChipIconView = statusBarChipIconView,
            uid = uid,
            instanceId = instanceId,
            isGroupSummary = isGroupSummary,
            packageName = packageName,
            appName = appName,
            contentIntent = contentIntent,
            bucket = bucket,
            callType = callType,
            promotedContent = promotedContent,
        )
    }
        ?: ActiveNotificationModel(
            key = key,
            groupKey = groupKey,
            whenTime = whenTime,
            isForegroundService = isForegroundService,
            isOngoingEvent = isOngoingEvent,
            isAmbient = isAmbient,
            isRowDismissed = isRowDismissed,
            isSilent = isSilent,
            isLastMessageFromReply = isLastMessageFromReply,
            isSuppressedFromStatusBar = isSuppressedFromStatusBar,
            isPulsing = isPulsing,
            aodIcon = aodIcon,
            shelfIcon = shelfIcon,
            statusBarIcon = statusBarIcon,
            statusBarChipIconView = statusBarChipIconView,
            uid = uid,
            instanceId = instanceId,
            isGroupSummary = isGroupSummary,
            packageName = packageName,
            appName = appName,
            contentIntent = contentIntent,
            bucket = bucket,
            callType = callType,
            promotedContent = promotedContent,
        )
}

private fun ActiveNotificationModel.isCurrent(
    key: String,
    groupKey: String?,
    whenTime: Long,
    isForegroundService: Boolean,
    isOngoingEvent: Boolean,
    isAmbient: Boolean,
    isRowDismissed: Boolean,
    isSilent: Boolean,
    isLastMessageFromReply: Boolean,
    isSuppressedFromStatusBar: Boolean,
    isPulsing: Boolean,
    aodIcon: Icon?,
    shelfIcon: Icon?,
    statusBarIcon: Icon?,
    statusBarChipIconView: StatusBarIconView?,
    uid: Int,
    packageName: String,
    appName: String,
    contentIntent: PendingIntent?,
    instanceId: InstanceId?,
    isGroupSummary: Boolean,
    bucket: Int,
    callType: CallType,
    promotedContent: PromotedNotificationContentModels?,
): Boolean {
    return when {
        key != this.key -> false
        groupKey != this.groupKey -> false
        whenTime != this.whenTime -> false
        isForegroundService != this.isForegroundService -> false
        isOngoingEvent != this.isOngoingEvent -> false
        isAmbient != this.isAmbient -> false
        isRowDismissed != this.isRowDismissed -> false
        isSilent != this.isSilent -> false
        isLastMessageFromReply != this.isLastMessageFromReply -> false
        isSuppressedFromStatusBar != this.isSuppressedFromStatusBar -> false
        isPulsing != this.isPulsing -> false
        aodIcon != this.aodIcon -> false
        shelfIcon != this.shelfIcon -> false
        statusBarIcon != this.statusBarIcon -> false
        statusBarChipIconView != this.statusBarChipIconView -> false
        uid != this.uid -> false
        instanceId != this.instanceId -> false
        isGroupSummary != this.isGroupSummary -> false
        packageName != this.packageName -> false
        appName != this.appName -> false
        contentIntent != this.contentIntent -> false
        bucket != this.bucket -> false
        callType != this.callType -> false
        // QQQ: Do we need to do the same `isCurrent` thing within the content model to avoid
        // recreating the active notification model constantly?
        promotedContent != this.promotedContent -> false
        else -> true
    }
}

private fun ActiveNotificationsStore.createOrReuse(
    key: String,
    summary: ActiveNotificationModel,
    children: List<ActiveNotificationModel>,
): ActiveNotificationGroupModel {
    return groups[key]?.takeIf { it.isCurrent(key, summary, children) }
        ?: ActiveNotificationGroupModel(key, summary, children)
}

private fun ActiveNotificationGroupModel.isCurrent(
    key: String,
    summary: ActiveNotificationModel,
    children: List<ActiveNotificationModel>,
): Boolean {
    return when {
        key != this.key -> false
        summary != this.summary -> false
        children != this.children -> false
        else -> true
    }
}

private fun StatusBarNotification.toCallType(): CallType =
    when (this.notification.extras.getInt(EXTRA_CALL_TYPE, -1)) {
        -1 -> CallType.None
        CALL_TYPE_INCOMING -> CallType.Incoming
        CALL_TYPE_ONGOING -> CallType.Ongoing
        CALL_TYPE_SCREENING -> CallType.Screening
        CALL_TYPE_UNKNOWN -> CallType.Unknown
        else -> CallType.Unknown
    }
