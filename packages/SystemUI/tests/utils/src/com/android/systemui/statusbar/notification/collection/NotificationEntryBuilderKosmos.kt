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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.content.applicationContext
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import com.android.systemui.activity.EmptyTestActivity
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.icon.IconPack
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.PeopleNotificationType
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_FULL_PERSON
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_IMPORTANT_PERSON
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_NON_PERSON
import com.android.systemui.statusbar.notification.promoted.setPromotedContent
import org.mockito.kotlin.mock

fun Kosmos.setIconPackWithMockIconViews(entry: NotificationEntry) {
    entry.icons =
        IconPack.buildPack(
            /* statusBarIcon = */ mock(),
            /* statusBarChipIcon = */ mock(),
            /* shelfIcon = */ mock(),
            /* aodIcon = */ mock(),
            /* source = */ null,
        )
}

fun Kosmos.buildPromotedOngoingEntry(
    block: NotificationEntryBuilder.() -> Unit = {}
): NotificationEntry =
    buildNotificationEntry(tag = "ron", promoted = true, style = null, block = block)

fun Kosmos.buildOngoingCallEntry(
    promoted: Boolean = false,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry =
    buildNotificationEntry(
        tag = "call",
        promoted = promoted,
        style = makeOngoingCallStyle(),
        block = block,
    )

fun Kosmos.buildNotificationEntry(
    tag: String? = null,
    promoted: Boolean = false,
    style: Notification.Style? = null,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry =
    NotificationEntryBuilder()
        .apply {
            setTag(tag)
            setFlag(applicationContext, Notification.FLAG_PROMOTED_ONGOING, promoted)
            modifyNotification(applicationContext)
                .setSmallIcon(Icon.createWithContentUri("content://null"))
                .setStyle(style)
        }
        .apply(block)
        .build()
        .also {
            setIconPackWithMockIconViews(it)
            if (promoted) setPromotedContent(it)
        }

private fun Kosmos.makeOngoingCallStyle(): Notification.CallStyle {
    val pendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent("action"),
            PendingIntent.FLAG_IMMUTABLE,
        )
    val person = Person.Builder().setName("person").build()
    return Notification.CallStyle.forOngoingCall(person, pendingIntent)
}

private fun Kosmos.makeMessagingStyleNotification(): Notification.Builder {
    val personIcon = Icon.createWithBitmap(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
    val person = Person.Builder().setIcon(personIcon).setName("Person").build()
    val message = Notification.MessagingStyle.Message("Message!", 4323, person)
    val bubbleIntent =
        PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, EmptyTestActivity::class.java),
            PendingIntent.FLAG_MUTABLE,
        )

    return Notification.Builder(applicationContext, "channelId")
        .setSmallIcon(R.drawable.ic_person)
        .setContentTitle("Title")
        .setContentText("Text")
        .setStyle(Notification.MessagingStyle(person).addMessage(message))
        .setBubbleMetadata(
            Notification.BubbleMetadata.Builder(
                    bubbleIntent,
                    Icon.createWithResource(applicationContext, R.drawable.android),
                )
                .setDeleteIntent(mock<PendingIntent>())
                .setDesiredHeight(314)
                .setAutoExpandBubble(false)
                .build()
        )
}

fun Kosmos.makeEntryOfPeopleType(@PeopleNotificationType type: Int): NotificationEntryBuilder {
    val channel = NotificationChannel("messages", "messages", IMPORTANCE_DEFAULT)
    channel.isImportantConversation = (type == TYPE_IMPORTANT_PERSON)
    channel.setConversationId("parent", "convo")

    val entry =
        NotificationEntryBuilder().apply {
            updateRanking {
                it.setIsConversation(type != TYPE_NON_PERSON)
                it.setShortcutInfo(if (type >= TYPE_FULL_PERSON) mock() else null)
                it.setChannel(channel)
            }
            setNotification(makeMessagingStyleNotification().build())
        }
    return entry
}

fun Kosmos.makeClassifiedConversation(channelId: String): NotificationEntry {
    val channel = NotificationChannel(channelId, channelId, IMPORTANCE_LOW)
    val entry =
        NotificationEntryBuilder()
            .updateRanking {
                it.setIsConversation(true)
                it.setShortcutInfo(mock())
                it.setChannel(channel)
            }
            .build()
    return entry
}