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

import android.app.Notification
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.row.NotificationTestHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import org.junit.Before

private fun getCloseButton(row: ExpandableNotificationRow): View {
    val contractedView = row.showingLayout?.contractedChild!!
    return contractedView.findViewById(com.android.internal.R.id.close_button)
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationCloseButtonTest : SysuiTestCase() {
    private lateinit var helper: NotificationTestHelper

    @Before
    fun setUp() {
        helper = NotificationTestHelper(
            mContext,
            mDependency,
            TestableLooper.get(this)
        )
    }

    @Test
    @DisableFlags(Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS)
    fun verifyWhenFeatureDisabled() {
        // Enable the notification row to dismiss.
        helper.dismissibilityProvider.stub {
            on { isDismissable(any()) } doReturn true
        }

        // By default, the close button should be gone.
        val row = createNotificationRow()
        val closeButton = getCloseButton(row)
        assertThat(closeButton).isNotNull()
        assertThat(closeButton.visibility).isEqualTo(View.GONE)

        val hoverEnterEvent = MotionEvent.obtain(
            0/*downTime=*/,
            0/*eventTime=*/,
            MotionEvent.ACTION_HOVER_ENTER,
            0f/*x=*/,
            0f/*y=*/,
            0/*metaState*/
        )

        // The close button should not show if the feature is disabled.
        row.onInterceptHoverEvent(hoverEnterEvent)
        assertThat(closeButton.visibility).isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS)
    fun verifyOnDismissableNotification() {
        // Enable the notification row to dismiss.
        helper.dismissibilityProvider.stub {
            on { isDismissable(any()) } doReturn true
        }

        // By default, the close button should be gone.
        val row = createNotificationRow()
        val closeButton = getCloseButton(row)
        assertThat(closeButton).isNotNull()
        assertThat(closeButton.visibility).isEqualTo(View.GONE)

        val hoverEnterEvent = MotionEvent.obtain(
            0/*downTime=*/,
            0/*eventTime=*/,
            MotionEvent.ACTION_HOVER_ENTER,
            0f/*x=*/,
            0f/*y=*/,
            0/*metaState*/
        )

        // When the row is hovered, the close button should show.
        row.onInterceptHoverEvent(hoverEnterEvent)
        assertThat(closeButton.visibility).isEqualTo(View.VISIBLE)

        val hoverExitEvent = MotionEvent.obtain(
            0/*downTime=*/,
            0/*eventTime=*/,
            MotionEvent.ACTION_HOVER_EXIT,
            0f/*x=*/,
            0f/*y=*/,
            0/*metaState*/
        )

        // When hover exits the row, the close button should be gone again.
        row.onInterceptHoverEvent(hoverExitEvent)
        assertThat(closeButton.visibility).isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS)
    fun verifyOnUndismissableNotification() {
        // By default, the close button should be gone.
        val row = createNotificationRow()
        val closeButton = getCloseButton(row)
        assertThat(closeButton).isNotNull()
        assertThat(closeButton.visibility).isEqualTo(View.GONE)

        val hoverEnterEvent = MotionEvent.obtain(
            0/*downTime=*/,
            0/*eventTime=*/,
            MotionEvent.ACTION_HOVER_ENTER,
            0f/*x=*/,
            0f/*y=*/,
            0/*metaState*/
        )

        // Because the host notification cannot be dismissed, the close button should not show.
        row.onInterceptHoverEvent(hoverEnterEvent)
        assertThat(closeButton.visibility).isEqualTo(View.GONE)
    }

    private fun createNotificationRow(): ExpandableNotificationRow {
        val notification = Notification.Builder(context, "channel")
            .setContentTitle("title")
            .setContentText("text")
            .setSmallIcon(R.drawable.ic_person)
            .build()

       return helper.createRow(notification)
    }
}