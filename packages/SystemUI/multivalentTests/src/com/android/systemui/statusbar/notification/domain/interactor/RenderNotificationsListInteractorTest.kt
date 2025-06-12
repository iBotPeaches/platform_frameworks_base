/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package com.android.systemui.statusbar.notification.domain.interactor

import android.app.Notification
import android.platform.test.annotations.EnableFlags
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.shared.byKey
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RenderNotificationsListInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val Kosmos.outputInteractor by Kosmos.Fixture { activeNotificationsInteractor }
    private val Kosmos.underTest by Kosmos.Fixture { renderNotificationListInteractor }

    @Test
    fun setRenderedList_preservesOrdering() =
        kosmos.runTest {
            val notifs by collectLastValue(outputInteractor.topLevelRepresentativeNotifications)
            val keys = (1..50).shuffled().map { "$it" }
            val entries = keys.map { mockNotificationEntry(key = it) }
            underTest.setRenderedList(entries)
            assertThat(notifs)
                .comparingElementsUsing(byKey)
                .containsExactlyElementsIn(keys)
                .inOrder()
        }

    @Test
    fun setRenderList_flatMapsRankings() =
        kosmos.runTest {
            val ranks by collectLastValue(outputInteractor.activeNotificationRanks)

            val single = mockNotificationEntry("single", 0)
            val group =
                mockGroupEntry(
                    key = "group",
                    summary = mockNotificationEntry("summary", 1),
                    children =
                        listOf(
                            mockNotificationEntry("child0", 2),
                            mockNotificationEntry("child1", 3),
                        ),
                )

            underTest.setRenderedList(listOf(single, group))

            assertThat(ranks)
                .containsExactlyEntriesIn(
                    mapOf("single" to 0, "summary" to 1, "child0" to 2, "child1" to 3)
                )
        }

    @Test
    fun setRenderList_singleItems_mapsRankings() =
        kosmos.runTest {
            val actual by collectLastValue(outputInteractor.activeNotificationRanks)
            val expected =
                (0..10).shuffled().mapIndexed { index, value -> "$value" to index }.toMap()

            val entries = expected.map { (key, rank) -> mockNotificationEntry(key, rank) }

            underTest.setRenderedList(entries)

            assertThat(actual).containsAtLeastEntriesIn(expected)
        }

    @Test
    fun setRenderList_groupWithNoSummary_flatMapsRankings() =
        kosmos.runTest {
            val actual by collectLastValue(outputInteractor.activeNotificationRanks)
            val expected =
                (0..10).shuffled().mapIndexed { index, value -> "$value" to index }.toMap()

            val group =
                mockGroupEntry(
                    key = "group",
                    summary = null,
                    children = expected.map { (key, rank) -> mockNotificationEntry(key, rank) },
                )

            underTest.setRenderedList(listOf(group))

            assertThat(actual).containsAtLeastEntriesIn(expected)
        }

    @Test
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    fun setRenderList_setsPromotionContent() =
        kosmos.runTest {
            val actual by collectLastValue(outputInteractor.topLevelRepresentativeNotifications)

            val notPromoted1 = mockNotificationEntry("key1", promotedContent = null)
            val promoted2 =
                mockNotificationEntry(
                    "key2",
                    promotedContent = PromotedNotificationContentBuilder("key2").build(),
                )

            underTest.setRenderedList(listOf(notPromoted1, promoted2))

            assertThat(actual!!.size).isEqualTo(2)

            val first = actual!![0]
            assertThat(first.key).isEqualTo("key1")
            assertThat(first.promotedContent).isNull()

            val second = actual!![1]
            assertThat(second.key).isEqualTo("key2")
            assertThat(second.promotedContent).isNotNull()
        }

    private fun mockNotificationEntry(
        key: String,
        rank: Int = 0,
        promotedContent: PromotedNotificationContentModels? = null,
    ): NotificationEntry {
        val nBuilder = Notification.Builder(context, "a")
        val notification = nBuilder.build()

        val mockSbn =
            mock<StatusBarNotification>() {
                whenever(this.notification).thenReturn(notification)
                whenever(packageName).thenReturn("com.android")
            }
        return mock<NotificationEntry> {
            whenever(this.key).thenReturn(key)
            whenever(this.icons).thenReturn(mock())
            whenever(this.representativeEntry).thenReturn(this)
            whenever(this.ranking).thenReturn(RankingBuilder().setRank(rank).build())
            whenever(this.sbn).thenReturn(mockSbn)
            whenever(this.promotedNotificationContentModels).thenReturn(promotedContent)
        }
    }
}

private fun mockGroupEntry(
    key: String,
    summary: NotificationEntry?,
    children: List<NotificationEntry>,
): GroupEntry {
    return mock<GroupEntry> {
        whenever(this.key).thenReturn(key)
        whenever(this.summary).thenReturn(summary)
        whenever(this.children).thenReturn(children)
    }
}
