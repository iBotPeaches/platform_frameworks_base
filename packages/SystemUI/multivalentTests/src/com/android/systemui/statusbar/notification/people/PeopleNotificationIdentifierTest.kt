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
package com.android.systemui.statusbar.notification.people

import android.app.Notification
import android.app.NotificationChannel
import android.content.pm.ShortcutInfo
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.GroupEntryBuilder
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManagerImpl
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_FULL_PERSON
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_IMPORTANT_PERSON
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_NON_PERSON
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_PERSON
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock


@SmallTest
@RunWith(AndroidJUnit4::class)
class PeopleNotificationIdentifierTest : SysuiTestCase() {

    private lateinit var underTest: PeopleNotificationIdentifierImpl

    private val summary1 = notificationEntry("foo", 1, summary = true)
    private val summary2 = notificationEntry("bar", 1, summary = true)
    private val entries =
        listOf<GroupEntry>(
            GroupEntryBuilder()
                .setSummary(summary1)
                .setChildren(
                    listOf(
                        notificationEntry("foo", 2),
                        notificationEntry("foo", 3),
                        notificationEntry("foo", 4)
                    )
                )
                .build(),
            GroupEntryBuilder()
                .setSummary(summary2)
                .setChildren(
                    listOf(
                        notificationEntry("bar", 2),
                        notificationEntry("bar", 3),
                        notificationEntry("bar", 4)
                    )
                )
                .build()
        )

    private fun notificationEntry(
        pkg: String,
        id: Int,
        summary: Boolean = false
    ): NotificationEntry {
        val sbn = mock(StatusBarNotification::class.java)
        Mockito.`when`(sbn.key).thenReturn("key")
        Mockito.`when`(sbn.notification).thenReturn(mock(Notification::class.java))
        if (summary)
            Mockito.`when`(sbn.notification.isGroupSummary).thenReturn(true)
        return NotificationEntryBuilder().setPkg(pkg)
            .setId(id)
            .setSbn(sbn)
            .build().apply {
                row = mock(ExpandableNotificationRow::class.java)
            }
    }

    private fun personRanking(entry: NotificationEntry, personType: Int): Ranking {
        val channel = NotificationChannel("person", "person", 4)
        channel.setConversationId("parent", "person")
        channel.setImportantConversation(true)

        val br = RankingBuilder(entry.ranking)

        when (personType) {
            TYPE_NON_PERSON -> br.setIsConversation(false)
            TYPE_PERSON -> {
                br.setIsConversation(true)
                br.setShortcutInfo(null)
            }

            TYPE_IMPORTANT_PERSON -> {
                br.setIsConversation(true)
                br.setShortcutInfo(mock(ShortcutInfo::class.java))
                br.setChannel(channel)
            }

            else -> {
                br.setIsConversation(true)
                br.setShortcutInfo(mock(ShortcutInfo::class.java))
            }
        }

        return br.build()
    }

    @Before
    fun setUp() {
        val personExtractor = object : NotificationPersonExtractor {
            public override fun isPersonNotification(sbn: StatusBarNotification): Boolean {
                return true
            }
        }

        underTest = PeopleNotificationIdentifierImpl(
            personExtractor,
            GroupMembershipManagerImpl()
        )
    }

    private val Ranking.personTypeInfo
        get() = when {
            !isConversation -> TYPE_NON_PERSON
            conversationShortcutInfo == null -> TYPE_PERSON
            channel?.isImportantConversation == true -> TYPE_IMPORTANT_PERSON
            else -> TYPE_FULL_PERSON
        }

    @Test
    fun getPeopleNotificationType_entryIsImportant() {
        summary1.setRanking(personRanking(summary1, TYPE_IMPORTANT_PERSON))

        assertThat(underTest.getPeopleNotificationType(summary1)).isEqualTo(TYPE_IMPORTANT_PERSON)
    }

    @Test
    fun getPeopleNotificationType_importantChild() {
        entries.get(0).getChildren().get(0).setRanking(
            personRanking(entries.get(0).getChildren().get(0), TYPE_IMPORTANT_PERSON)
        )

        assertThat(entries.get(0).summary?.let { underTest.getPeopleNotificationType(it) })
            .isEqualTo(TYPE_IMPORTANT_PERSON)
    }
}