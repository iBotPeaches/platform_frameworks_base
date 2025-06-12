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

package com.android.systemui.statusbar

import android.app.Notification
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.EntryAdapterFactory
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotificationTestHelper
import com.android.systemui.statusbar.notification.row.entryAdapterFactory
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class NotificationGroupingUtilTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var underTest: NotificationGroupingUtil

    private val factory: EntryAdapterFactory = kosmos.entryAdapterFactory
    private lateinit var testHelper: NotificationTestHelper

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(NotificationBundleUi.FLAG_NAME)
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        testHelper = NotificationTestHelper(mContext, mDependency)
    }

    @Test
    fun showsTime() {
        val row = testHelper.createRow()

        underTest = NotificationGroupingUtil(row)
        assertThat(underTest.showsTime(row)).isTrue()
    }

    @Test
    fun iconExtractor_extractsSbn_notification() {
        val row = testHelper.createRow()

        underTest = NotificationGroupingUtil(row)

        assertThat(NotificationGroupingUtil.ICON_EXTRACTOR.extractData(row)).isInstanceOf(
            Notification::class.java)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun iconExtractor_noException_bundle() {
        val row = mock(ExpandableNotificationRow::class.java)
        val be = BundleEntry("promotions")
        `when`(row.entryAdapter).thenReturn(factory.create(be))

        underTest = NotificationGroupingUtil(row)

        assertThat(NotificationGroupingUtil.ICON_EXTRACTOR.extractData(row)).isNull()
    }

    @Test
    fun iconComparator_sameNotificationIcon() {
        val n1 = NotificationGroupingUtil.ICON_EXTRACTOR.extractData(testHelper.createRow())
        val n2 = NotificationGroupingUtil.ICON_EXTRACTOR.extractData(testHelper.createRow())

        assertThat(NotificationGroupingUtil.IconComparator().hasSameIcon(n1, n2)).isTrue()
    }

    @Test
    fun iconComparator_differentNotificationIcon() {
        val notif = Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_menu).build()
        val n1 = NotificationGroupingUtil.ICON_EXTRACTOR.extractData(testHelper.createRow(notif))
        val n2 = NotificationGroupingUtil.ICON_EXTRACTOR.extractData(testHelper.createRow())

        assertThat(NotificationGroupingUtil.IconComparator().hasSameIcon(n1, n2)).isFalse()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun iconComparator_bundleNotification() {
        assertThat(NotificationGroupingUtil.IconComparator().hasSameIcon(null,
            NotificationGroupingUtil.ICON_EXTRACTOR.extractData(testHelper.createRow()))).isFalse()
    }

    @Test
    fun iconComparator_twoBundles() {
        assertThat(NotificationGroupingUtil.IconComparator().hasSameIcon(null, null)).isFalse()
    }
}