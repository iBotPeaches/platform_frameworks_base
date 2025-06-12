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

package com.android.systemui.statusbar.notification.promoted.domain.interactor

import android.app.Notification
import android.content.applicationContext
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.dumpManager
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.chips.notification.domain.interactor.statusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.notification.shared.StatusBarNotifChips
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.buildPromotedOngoingEntry
import com.android.systemui.statusbar.notification.domain.interactor.renderNotificationListInteractor
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.phone.ongoingcall.EnableChipsModernization
import com.android.systemui.statusbar.policy.domain.interactor.sensitiveNotificationProtectionInteractor
import com.android.systemui.statusbar.policy.mockSensitiveNotificationProtectionController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(PromotedNotificationUi.FLAG_NAME, StatusBarNotifChips.FLAG_NAME)
@EnableChipsModernization
class AODPromotedNotificationsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Fixture {
        AODPromotedNotificationInteractor(
            promotedNotificationsInteractor = promotedNotificationsInteractor,
            keyguardInteractor = keyguardInteractor,
            sensitiveNotificationProtectionInteractor = sensitiveNotificationProtectionInteractor,
            dumpManager = dumpManager,
        )
    }

    @Before
    fun setUp() {
        kosmos.statusBarNotificationChipsInteractor.start()
    }

    private fun Kosmos.buildPublicPrivatePromotedOngoing(): NotificationEntry =
        buildPromotedOngoingEntry {
            modifyNotification(applicationContext)
                .setContentTitle("SENSITIVE")
                .setPublicVersion(
                    Notification.Builder(applicationContext, "channel")
                        .setContentTitle("REDACTED")
                        .build()
                )
        }

    @Test
    fun content_sensitive_unlocked() =
        kosmos.runTest {
            // GIVEN a promoted entry
            val ronEntry = buildPublicPrivatePromotedOngoing()

            setKeyguardLocked(false)
            setScreenSharingProtectionActive(false)

            renderNotificationListInteractor.setRenderedList(listOf(ronEntry))

            // THEN aod content is sensitive
            val content by collectLastValue(underTest.content)
            assertThat(content?.title).isEqualTo("SENSITIVE")
        }

    @Test
    fun content_sensitive_locked() =
        kosmos.runTest {
            // GIVEN a promoted entry
            val ronEntry = buildPublicPrivatePromotedOngoing()

            setKeyguardLocked(true)
            setScreenSharingProtectionActive(false)

            renderNotificationListInteractor.setRenderedList(listOf(ronEntry))

            // THEN aod content is redacted
            val content by collectLastValue(underTest.content)
            assertThat(content).isNotNull()
            assertThat(content!!.title).isEqualTo("REDACTED")
        }

    @Test
    fun content_sensitive_unlocked_screensharing() =
        kosmos.runTest {
            // GIVEN a promoted entry
            val ronEntry = buildPublicPrivatePromotedOngoing()

            setKeyguardLocked(false)
            setScreenSharingProtectionActive(true)

            renderNotificationListInteractor.setRenderedList(listOf(ronEntry))

            // THEN aod content is redacted
            val content by collectLastValue(underTest.content)
            assertThat(content).isNotNull()
            assertThat(content!!.title).isEqualTo("REDACTED")
        }

    private fun Kosmos.setKeyguardLocked(locked: Boolean) {
        fakeKeyguardRepository.setKeyguardDismissible(!locked)
    }

    private fun Kosmos.setScreenSharingProtectionActive(active: Boolean) {
        whenever(mockSensitiveNotificationProtectionController.isSensitiveStateActive)
            .thenReturn(active)
        whenever(mockSensitiveNotificationProtectionController.shouldProtectNotification(any()))
            .thenReturn(active)
    }
}
