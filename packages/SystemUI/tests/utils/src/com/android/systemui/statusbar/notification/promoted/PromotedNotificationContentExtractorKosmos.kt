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

package com.android.systemui.statusbar.notification.promoted

import android.app.Notification
import android.content.applicationContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.NotificationLockscreenUserManager.REDACTION_TYPE_PUBLIC
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.row.RowImageInflater
import com.android.systemui.statusbar.notification.row.shared.skeletonImageTransform
import com.android.systemui.util.time.systemClock

var Kosmos.promotedNotificationContentExtractor by
    Kosmos.Fixture {
        PromotedNotificationContentExtractorImpl(
            applicationContext,
            skeletonImageTransform,
            systemClock,
            promotedNotificationLogger,
        )
    }

fun Kosmos.setPromotedContent(entry: NotificationEntry) {
    val extractedContent =
        promotedNotificationContentExtractor.extractContent(
            entry,
            Notification.Builder.recoverBuilder(applicationContext, entry.sbn.notification),
            REDACTION_TYPE_PUBLIC,
            RowImageInflater.newInstance(previousIndex = null, reinflating = false)
                .useForContentModel(),
        )
    entry.promotedNotificationContentModels =
        requireNotNull(extractedContent) { "extractContent returned null" }
}
