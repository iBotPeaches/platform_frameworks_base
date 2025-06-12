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

package com.android.systemui.statusbar.notification.collection.render

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.DumpManager
import com.android.systemui.log.assertLogsWtf
import com.android.systemui.statusbar.notification.collection.EntryAdapterFactoryImpl
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.GroupEntryBuilder
import com.android.systemui.statusbar.notification.collection.ListEntry
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.listbuilder.OnBeforeRenderListListener
import com.android.systemui.statusbar.notification.collection.render.GroupExpansionManager.OnGroupExpansionChangeListener
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotificationTestHelper
import com.android.systemui.statusbar.notification.row.entryAdapterFactory
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when` as whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class GroupExpansionManagerTest : SysuiTestCase() {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private lateinit var underTest: GroupExpansionManagerImpl

    private val kosmos = testKosmos()
    private lateinit var testHelper: NotificationTestHelper
    private val dumpManager: DumpManager = mock()
    private val groupMembershipManager: GroupMembershipManager = mock()

    private val pipeline: NotifPipeline = mock()
    private lateinit var beforeRenderListListener: OnBeforeRenderListListener

    private val factory: EntryAdapterFactoryImpl = kosmos.entryAdapterFactory
    private lateinit var summary1: NotificationEntry
    private lateinit var summary2: NotificationEntry
    private lateinit var entries: List<ListEntry>

    private fun notificationEntry(pkg: String, id: Int, parent: ExpandableNotificationRow?) =
        NotificationEntryBuilder().setPkg(pkg).setId(id).build().apply {
            row = testHelper.createRow().apply { setIsChildInGroup(true, parent) }
        }

    @Before
    fun setUp() {
        testHelper = NotificationTestHelper(mContext, mDependency)

        summary1 = testHelper.createRow().entry
        summary2 = testHelper.createRow().entry
        entries =
            listOf<ListEntry>(
                GroupEntryBuilder()
                    .setSummary(summary1)
                    .setChildren(
                        listOf(
                            notificationEntry("foo", 2, summary1.row),
                            notificationEntry("foo", 3, summary1.row),
                            notificationEntry("foo", 4, summary1.row),
                        )
                    )
                    .build(),
                GroupEntryBuilder()
                    .setSummary(summary2)
                    .setChildren(
                        listOf(
                            notificationEntry("bar", 2, summary2.row),
                            notificationEntry("bar", 3, summary2.row),
                            notificationEntry("bar", 4, summary2.row),
                        )
                    )
                    .build(),
                notificationEntry("baz", 1, null),
            )

        whenever(groupMembershipManager.getGroupSummary(summary1)).thenReturn(summary1)
        whenever(groupMembershipManager.getGroupSummary(summary2)).thenReturn(summary2)

        underTest = GroupExpansionManagerImpl(dumpManager, groupMembershipManager)
    }

    @Test
    @DisableFlags(NotificationBundleUi.FLAG_NAME)
    fun notifyOnlyOnChange() {
        var listenerCalledCount = 0
        underTest.registerGroupExpansionChangeListener { _, _ -> listenerCalledCount++ }

        underTest.setGroupExpanded(summary1, false)
        assertThat(listenerCalledCount).isEqualTo(0)
        underTest.setGroupExpanded(summary1, true)
        assertThat(listenerCalledCount).isEqualTo(1)
        underTest.setGroupExpanded(summary2, true)
        assertThat(listenerCalledCount).isEqualTo(2)
        underTest.setGroupExpanded(summary1, true)
        assertThat(listenerCalledCount).isEqualTo(2)
        underTest.setGroupExpanded(summary2, false)
        assertThat(listenerCalledCount).isEqualTo(3)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun notifyOnlyOnChange_withEntryAdapter() {
        val entryAdapter1 = factory.create(summary1)
        val entryAdapter2 = factory.create(summary2)
        var listenerCalledCount = 0
        underTest.registerGroupExpansionChangeListener { _, _ -> listenerCalledCount++ }

        underTest.setGroupExpanded(entryAdapter1, false)
        assertThat(listenerCalledCount).isEqualTo(0)
        underTest.setGroupExpanded(entryAdapter1, true)
        assertThat(listenerCalledCount).isEqualTo(1)
        underTest.setGroupExpanded(entryAdapter2, true)
        assertThat(listenerCalledCount).isEqualTo(2)
        underTest.setGroupExpanded(entryAdapter1, true)
        assertThat(listenerCalledCount).isEqualTo(2)
        underTest.setGroupExpanded(entryAdapter2, false)
        assertThat(listenerCalledCount).isEqualTo(3)
    }

    @Test
    @DisableFlags(NotificationBundleUi.FLAG_NAME)
    fun expandUnattachedEntry() {
        // First, expand the entry when it is attached.
        underTest.setGroupExpanded(summary1, true)
        assertThat(underTest.isGroupExpanded(summary1)).isTrue()

        // Un-attach it, and un-expand it.
        NotificationEntryBuilder.setNewParent(summary1, null)
        underTest.setGroupExpanded(summary1, false)

        // Expanding again should throw.
        assertLogsWtf { underTest.setGroupExpanded(summary1, true) }
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun expandUnattachedEntryAdapter() {
        val entryAdapter = factory.create(summary1)
        // First, expand the entry when it is attached.
        underTest.setGroupExpanded(entryAdapter, true)
        assertThat(underTest.isGroupExpanded(entryAdapter)).isTrue()

        // Un-attach it, and un-expand it.
        NotificationEntryBuilder.setNewParent(summary1, null)
        underTest.setGroupExpanded(entryAdapter, false)

        // Expanding again should throw.
        assertLogsWtf { underTest.setGroupExpanded(entryAdapter, true) }
    }

    @Test
    @DisableFlags(NotificationBundleUi.FLAG_NAME)
    fun syncWithPipeline() {
        underTest.attach(pipeline)
        beforeRenderListListener = withArgCaptor {
            verify(pipeline).addOnBeforeRenderListListener(capture())
        }

        val listener: OnGroupExpansionChangeListener = mock()
        underTest.registerGroupExpansionChangeListener(listener)

        beforeRenderListListener.onBeforeRenderList(entries)
        verify(listener, never()).onGroupExpansionChange(any(), any())

        // Expand one of the groups.
        underTest.setGroupExpanded(summary1, true)
        verify(listener).onGroupExpansionChange(summary1.row, true)

        // Empty the pipeline list and verify that the group is no longer expanded.
        beforeRenderListListener.onBeforeRenderList(emptyList())
        verify(listener).onGroupExpansionChange(summary1.row, false)
        verifyNoMoreInteractions(listener)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun syncWithPipeline_withEntryAdapter() {
        val entryAdapter = factory.create(summary1)
        underTest.attach(pipeline)
        beforeRenderListListener = withArgCaptor {
            verify(pipeline).addOnBeforeRenderListListener(capture())
        }

        val listener: OnGroupExpansionChangeListener = mock()
        underTest.registerGroupExpansionChangeListener(listener)

        beforeRenderListListener.onBeforeRenderList(entries)
        verify(listener, never()).onGroupExpansionChange(any(), any())

        // Expand one of the groups.
        underTest.setGroupExpanded(entryAdapter, true)
        verify(listener).onGroupExpansionChange(summary1.row, true)

        // Empty the pipeline list and verify that the group is no longer expanded.
        beforeRenderListListener.onBeforeRenderList(emptyList())
        verify(listener).onGroupExpansionChange(summary1.row, false)
        verifyNoMoreInteractions(listener)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isGroupExpanded() {
        val entryAdapter = summary1.row.entryAdapter
        underTest.setGroupExpanded(entryAdapter, true)

        assertThat(underTest.isGroupExpanded(entryAdapter)).isTrue()
        assertThat(
                underTest.isGroupExpanded(
                    (entries[0] as? GroupEntry)?.getChildren()?.get(0)?.row?.entryAdapter
                )
            )
            .isTrue()
    }
}
