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

package com.android.systemui.statusbar.notification

import android.app.Flags
import android.app.Notification
import android.app.Notification.EXTRA_SUMMARIZED_CONTENT
import android.app.Person
import android.content.pm.LauncherApps
import android.content.pm.launcherApps
import android.graphics.drawable.Icon
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotificationRowContentBinderLogger
import com.android.systemui.statusbar.notification.row.NotificationTestHelper
import com.android.systemui.statusbar.notification.row.notificationRowContentBinderLogger
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class ConversationNotificationProcessorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private lateinit var conversationNotificationProcessor: ConversationNotificationProcessor
    private lateinit var testHelper: NotificationTestHelper
    private lateinit var launcherApps: LauncherApps
    private lateinit var logger: NotificationRowContentBinderLogger
    private lateinit var conversationNotificationManager: ConversationNotificationManager

    @Before
    fun setup() {
        launcherApps = kosmos.launcherApps
        conversationNotificationManager = kosmos.conversationNotificationManager
        logger = kosmos.notificationRowContentBinderLogger
        testHelper = NotificationTestHelper(mContext, mDependency)

        conversationNotificationProcessor =
            ConversationNotificationProcessor(
                context,
                launcherApps,
                conversationNotificationManager,
            )
    }

    @Test
    fun processNotification_notMessagingStyle() {
        val nb = Notification.Builder(mContext).setSmallIcon(R.drawable.ic_person)
        val newRow: ExpandableNotificationRow = testHelper.createRow(nb.build())

        assertThat(conversationNotificationProcessor.processNotification(newRow.entry, nb, logger))
            .isNull()
    }

    @Test
    @DisableFlags(Flags.FLAG_NM_SUMMARIZATION, Flags.FLAG_NM_SUMMARIZATION_UI)
    fun processNotification_messagingStyleWithSummarization_flagOff() {
        val summarization = "hello"
        val nb = getMessagingNotification()
        val newRow: ExpandableNotificationRow = testHelper.createRow(nb.build())
        newRow.entry.setRanking(
            RankingBuilder(newRow.entry.ranking).setSummarization(summarization).build()
        )

        assertThat(conversationNotificationProcessor.processNotification(newRow.entry, nb, logger))
            .isNotNull()
        assertThat(nb.build().extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_SUMMARIZATION)
    fun processNotification_messagingStyleWithSummarization() {
        val summarization = "hello"
        val nb = getMessagingNotification()
        val newRow: ExpandableNotificationRow = testHelper.createRow(nb.build())
        newRow.entry.setRanking(
            RankingBuilder(newRow.entry.ranking).setSummarization(summarization).build()
        )

        assertThat(conversationNotificationProcessor.processNotification(newRow.entry, nb, logger))
            .isNotNull()

        val processedSummary = nb.build().extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
        assertThat(processedSummary.toString()).isEqualTo("x $summarization")

        val checkSpans = SpannableStringBuilder(processedSummary)
        assertThat(
                checkSpans.getSpans(
                    /* queryStart = */ 0,
                    /* queryEnd = */ 2,
                    /* kind = */ ImageSpan::class.java,
                )
            )
            .isNotNull()

        assertThat(
                processedSummary?.let {
                    checkSpans.getSpans(
                        /* queryStart = */ 0,
                        /* queryEnd = */ it.length,
                        /* kind = */ StyleSpan::class.java,
                    )
                }
            )
            .isNotNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_SUMMARIZATION)
    fun processNotification_messagingStyleUpdateSummarizationToNull() {
        val nb = getMessagingNotification()
        val newRow: ExpandableNotificationRow = testHelper.createRow(nb.build())
        newRow.entry.setRanking(
            RankingBuilder(newRow.entry.ranking).setSummarization("hello").build()
        )
        assertThat(conversationNotificationProcessor.processNotification(newRow.entry, nb, logger))
            .isNotNull()

        newRow.entry.setRanking(RankingBuilder(newRow.entry.ranking).setSummarization(null).build())

        assertThat(conversationNotificationProcessor.processNotification(newRow.entry, nb, logger))
            .isNotNull()
        assertThat(nb.build().extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_SUMMARIZATION)
    fun processNotification_messagingStyleWithoutSummarization() {
        val nb = getMessagingNotification()
        val newRow: ExpandableNotificationRow = testHelper.createRow(nb.build())

        assertThat(conversationNotificationProcessor.processNotification(newRow.entry, nb, logger))
            .isNotNull()
        assertThat(nb.build().extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }

    private fun getMessagingNotification(): Notification.Builder {
        val displayName = "Display Name"
        val messageText = "Message Text"
        val personIcon = Icon.createWithResource(mContext, R.drawable.ic_person)
        val testPerson = Person.Builder().setName(displayName).setIcon(personIcon).build()
        val messagingStyle = Notification.MessagingStyle(testPerson)
        messagingStyle.addMessage(
            Notification.MessagingStyle.Message(messageText, System.currentTimeMillis(), testPerson)
        )
        return Notification.Builder(mContext)
            .setSmallIcon(R.drawable.ic_person)
            .setStyle(messagingStyle)
    }
}
