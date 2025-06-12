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

package com.android.systemui.statusbar.chips.notification.domain.interactor

import com.android.systemui.activity.data.model.AppVisibilityModel
import com.android.systemui.activity.data.repository.ActivityManagerRepository
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.notification.domain.model.NotificationChipModel
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Interactor representing a single notification's status bar chip.
 *
 * [startingModel.key] dictates which notification this interactor corresponds to - all updates sent
 * to this interactor via [setNotification] should only be for the notification with the same key.
 *
 * [StatusBarNotificationChipsInteractor] will collect all the individual instances of this
 * interactor and send all the necessary information to the UI layer.
 *
 * @property creationTime the time when the notification first appeared as promoted.
 */
class SingleNotificationChipInteractor
@AssistedInject
constructor(
    @Assisted startingModel: ActiveNotificationModel,
    @Assisted val creationTime: Long,
    private val activityManagerRepository: ActivityManagerRepository,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val key = startingModel.key
    private val uid = startingModel.uid
    private val logger = Logger(logBuffer, "Notif".pad())
    // [StatusBarChipLogTag] recommends a max tag length of 20, so [extraLogTag] should NOT be the
    // top-level tag. It should instead be provided as the first string in each log message.
    private val extraLogTag = "SingleNotifChipInteractor[key=$key][id=${hashCode()}]"

    init {
        if (startingModel.promotedContent == null) {
            logger.e({ "$str1: Starting model has promotedContent=null, which shouldn't happen" }) {
                str1 = extraLogTag
            }
        }
    }

    private val _notificationModel = MutableStateFlow(startingModel)

    /**
     * Sets the new notification info corresponding to this interactor. The key on [model] *must*
     * match the key on the original [startingModel], otherwise the update won't be processed.
     */
    fun setNotification(model: ActiveNotificationModel) {
        if (model.key != this.key) {
            logger.w({ "$str1: received model for different key $str2" }) {
                str1 = extraLogTag
                str2 = model.key
            }
            return
        }
        if (model.promotedContent == null) {
            logger.e({
                "$str1: received model with promotedContent=null, which shouldn't happen"
            }) {
                str1 = extraLogTag
            }
            return
        }

        if (model.uid != uid) {
            logger.e({
                "$str1: received model with different uid, which shouldn't happen. " +
                    "Original UID: $int1, New UID: $int2. " +
                    "Proceeding as usual, but app visibility changes will be for *old* UID."
            }) {
                str1 = extraLogTag
                int1 = uid
                int2 = model.uid
            }
        }
        _notificationModel.value = model
    }

    /** Details about when the app managing the notification was & is visible to the user. */
    private val appVisibility: Flow<AppVisibilityModel> =
        activityManagerRepository.createAppVisibilityFlow(uid, logger, extraLogTag)

    /**
     * Emits this notification's status bar chip, or null if this notification shouldn't show a
     * status bar chip.
     */
    val notificationChip: Flow<NotificationChipModel?> =
        combine(_notificationModel, appVisibility) { notif, appVisibility ->
            notif.toNotificationChipModel(appVisibility)
        }

    private fun ActiveNotificationModel.toNotificationChipModel(
        appVisibility: AppVisibilityModel
    ): NotificationChipModel? {
        val promotedContent = this.promotedContent
        if (promotedContent == null) {
            logger.w({
                "$str1: Can't show chip because promotedContent=null, which shouldn't happen"
            }) {
                str1 = extraLogTag
            }
            return null
        }
        val statusBarChipIconView = this.statusBarChipIconView
        if (statusBarChipIconView == null) {
            if (!StatusBarConnectedDisplays.isEnabled) {
                logger.w({ "$str1: Can't show chip because status bar chip icon view is null" }) {
                    str1 = extraLogTag
                }
                // When the flag is disabled, we keep the old behavior of returning null.
                // When the flag is enabled, the icon will always be null, and will later be
                // fetched in the UI layer using the notification key.
                return null
            }
        }

        return NotificationChipModel(
            key = key,
            appName = appName,
            statusBarChipIconView = statusBarChipIconView,
            promotedContent = promotedContent,
            creationTime = creationTime,
            isAppVisible = appVisibility.isAppCurrentlyVisible,
            lastAppVisibleTime = appVisibility.lastAppVisibleTime,
            instanceId = instanceId,
        )
    }

    @AssistedFactory
    fun interface Factory {
        fun create(
            startingModel: ActiveNotificationModel,
            creationTime: Long,
        ): SingleNotificationChipInteractor
    }
}
