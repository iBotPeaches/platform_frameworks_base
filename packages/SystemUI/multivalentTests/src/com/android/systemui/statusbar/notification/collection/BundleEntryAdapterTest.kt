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
import android.graphics.Color
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_NON_PERSON
import com.android.systemui.statusbar.notification.row.entryAdapterFactory
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class BundleEntryAdapterTest : SysuiTestCase() {
    private lateinit var entry: BundleEntry

    private val kosmos = testKosmos()
    private lateinit var underTest: BundleEntryAdapter

    @get:Rule val setFlagsRule = SetFlagsRule()
    private val factory: EntryAdapterFactory = kosmos.entryAdapterFactory

    @Before
    fun setUp() {
        entry = BundleEntry("key")
        underTest = factory.create(entry) as BundleEntryAdapter
    }

    @Test
    fun getBackingHashCode() {
        assertThat(underTest.backingHashCode).isEqualTo(entry.hashCode())
    }

    @Test
    fun getParent_adapter() {
        assertThat(underTest.parent).isEqualTo(GroupEntry.ROOT_ENTRY)
    }

    @Test
    fun isTopLevelEntry_adapter() {
        assertThat(underTest.isTopLevelEntry).isTrue()
    }

    @Test
    fun getRow_adapter() {
        assertThat(underTest.row).isNull()
    }

    @Test
    fun isGroupRoot_adapter() {
        assertThat(underTest.isGroupRoot).isTrue()
    }

    @Test
    fun getKey_adapter() {
        assertThat(underTest.key).isEqualTo("key")
    }

    @Test
    fun isClearable_adapter() {
        assertThat(underTest.isClearable).isTrue()
    }

    @Test
    fun getSummarization_adapter() {
        assertThat(underTest.summarization).isNull()
    }

    @Test
    fun getContrastedColor_adapter() {
        assertThat(underTest.getContrastedColor(context, false, Color.WHITE))
            .isEqualTo(Notification.COLOR_DEFAULT)
    }

    @Test
    fun canPeek_adapter() {
        assertThat(underTest.canPeek()).isFalse()
    }

    @Test
    fun getWhen_adapter() {
        assertThat(underTest.`when`).isEqualTo(0)
    }

    @Test
    fun isColorized() {
        assertThat(underTest.isColorized).isFalse()
    }

    @Test
    fun getSbn() {
        assertThat(underTest.sbn).isNull()
    }

    @Test
    fun canDragAndDrop() {
        assertThat(underTest.canDragAndDrop()).isFalse()
    }

    @Test
    fun isBubble() {
        assertThat(underTest.isBubble).isFalse()
    }

    @Test
    fun getStyle() {
        assertThat(underTest.style).isNull()
    }

    @Test
    fun getSectionBucket() {
        assertThat(underTest.sectionBucket).isEqualTo(underTest.entry.bucket)
    }

    @Test
    fun isAmbient() {
        assertThat(underTest.isAmbient).isFalse()
    }

    @Test
    fun canShowFullScreen() {
        assertThat(underTest.isFullScreenCapable()).isFalse()
    }

    @Test
    fun getPeopleNotificationType() {
        assertThat(underTest.getPeopleNotificationType()).isEqualTo(TYPE_NON_PERSON)
    }
}
