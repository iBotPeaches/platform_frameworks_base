/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.Notification
import android.app.Notification.FLAG_FOREGROUND_SERVICE
import android.app.NotificationChannel.SYSTEM_RESERVED_IDS
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.graphics.Color
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.fakeMediaProjectionRepository
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.screenRecordRepository
import com.android.systemui.statusbar.chips.notification.domain.interactor.statusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.notification.shared.StatusBarNotifChips
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.notification.collection.buildEntry
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.buildOngoingCallEntry
import com.android.systemui.statusbar.notification.collection.buildPromotedOngoingEntry
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifPromoter
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifSectioner
import com.android.systemui.statusbar.notification.collection.makeClassifiedConversation
import com.android.systemui.statusbar.notification.collection.notifPipeline
import com.android.systemui.statusbar.notification.domain.interactor.renderNotificationListInteractor
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.promoted.domain.interactor.promotedNotificationsInteractor
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class ColorizedFgsCoordinatorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val notifPipeline
        get() = kosmos.notifPipeline

    private lateinit var colorizedFgsCoordinator: ColorizedFgsCoordinator
    private lateinit var sectioner: NotifSectioner

    @Before
    fun setup() {
        allowTestableLooperAsMainThread()

        kosmos.statusBarNotificationChipsInteractor.start()

        colorizedFgsCoordinator =
            ColorizedFgsCoordinator(
                kosmos.applicationCoroutineScope,
                kosmos.promotedNotificationsInteractor,
            )
        colorizedFgsCoordinator.attach(notifPipeline)
        sectioner = colorizedFgsCoordinator.sectioner
    }

    @Test
    fun testIncludeFGSInSection_importanceDefault() {
        // GIVEN the notification represents a colorized foreground service with > min importance
        val entry = buildEntry {
            setFlag(mContext, Notification.FLAG_FOREGROUND_SERVICE, true)
            setImportance(NotificationManager.IMPORTANCE_DEFAULT)
            modifyNotification(mContext).setColorized(true).setColor(Color.WHITE)
        }

        // THEN the entry is in the fgs section
        assertTrue(sectioner.isInSection(entry))
    }

    @Test
    fun testSectioner_reject_classifiedConversation() {
        kosmos.runTest {
            for (id in SYSTEM_RESERVED_IDS) {
                assertFalse(sectioner.isInSection(kosmos.makeClassifiedConversation(id)))
            }
        }
    }

    @Test
    fun testDiscludeFGSInSection_importanceMin() {
        // GIVEN the notification represents a colorized foreground service with min importance
        val entry = buildEntry {
            setFlag(mContext, Notification.FLAG_FOREGROUND_SERVICE, true)
            setImportance(NotificationManager.IMPORTANCE_MIN)
            modifyNotification(mContext).setColorized(true).setColor(Color.WHITE)
        }

        // THEN the entry is NOT in the fgs section
        assertFalse(sectioner.isInSection(entry))
    }

    @Test
    fun testDiscludeNonFGSInSection() {
        // GIVEN the notification represents a colorized notification with high importance that
        // is NOT a foreground service
        val entry = buildEntry {
            setImportance(NotificationManager.IMPORTANCE_HIGH)
            setFlag(mContext, Notification.FLAG_FOREGROUND_SERVICE, false)
            modifyNotification(mContext).setColorized(false)
        }

        // THEN the entry is NOT in the fgs section
        assertFalse(sectioner.isInSection(entry))
    }

    @Test
    fun testIncludeCallInSection_importanceDefault() {
        // GIVEN the notification represents a call with > min importance
        val entry = buildEntry {
            setImportance(NotificationManager.IMPORTANCE_DEFAULT)
            modifyNotification(mContext).setStyle(makeCallStyle())
        }

        // THEN the entry is in the fgs section
        assertTrue(sectioner.isInSection(entry))
    }

    @Test
    fun testDiscludeCallInSection_importanceMin() {
        // GIVEN the notification represents a call with min importance
        val entry = buildEntry {
            setImportance(NotificationManager.IMPORTANCE_MIN)
            modifyNotification(mContext).setStyle(makeCallStyle())
        }

        // THEN the entry is NOT in the fgs section
        assertFalse(sectioner.isInSection(entry))
    }

    @Test
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    fun testIncludePromotedOngoingInSection_flagEnabled() {
        // GIVEN the notification has FLAG_PROMOTED_ONGOING
        val entry = buildEntry { setFlag(mContext, Notification.FLAG_PROMOTED_ONGOING, true) }

        // THEN the entry is in the fgs section
        assertTrue(sectioner.isInSection(entry))
    }

    @Test
    @DisableFlags(PromotedNotificationUi.FLAG_NAME)
    fun testDiscludePromotedOngoingInSection_flagDisabled() {
        // GIVEN the notification has FLAG_PROMOTED_ONGOING
        val entry = buildEntry { setFlag(mContext, Notification.FLAG_PROMOTED_ONGOING, true) }

        // THEN the entry is NOT in the fgs section
        assertFalse(sectioner.isInSection(entry))
    }

    @Test
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    fun testIncludeScreenRecordNotifInSection_importanceDefault() =
        kosmos.runTest {
            // GIVEN a screen record event + screen record notif that has a status bar chip
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(hostPackage = "test_pkg")
            val screenRecordEntry =
                buildNotificationEntry(tag = "screenRecord", promoted = false) {
                    setImportance(NotificationManager.IMPORTANCE_DEFAULT)
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }

            renderNotificationListInteractor.setRenderedList(listOf(screenRecordEntry))

            val orderedChipNotificationKeys by
                collectLastValue(promotedNotificationsInteractor.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys)
                .containsExactly("0|test_pkg|0|screenRecord|0")
                .inOrder()

            // THEN the entry is in the fgs section
            assertTrue(sectioner.isInSection(screenRecordEntry))
        }

    @Test
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    fun testDiscludeScreenRecordNotifInSection_importanceMin() =
        kosmos.runTest {
            // GIVEN a screen record event + screen record notif that has a status bar chip
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(hostPackage = "test_pkg")
            val screenRecordEntry =
                buildNotificationEntry(tag = "screenRecord", promoted = false) {
                    setImportance(NotificationManager.IMPORTANCE_MIN)
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }

            renderNotificationListInteractor.setRenderedList(listOf(screenRecordEntry))

            val orderedChipNotificationKeys by
                collectLastValue(promotedNotificationsInteractor.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys)
                .containsExactly("0|test_pkg|0|screenRecord|0")
                .inOrder()

            // THEN the entry is NOT in the fgs section
            assertFalse(sectioner.isInSection(screenRecordEntry))
        }

    @Test
    @DisableFlags(PromotedNotificationUi.FLAG_NAME)
    fun testDiscludeScreenRecordNotifInSection_flagDisabled() =
        kosmos.runTest {
            // GIVEN a screen record event + screen record notif that has a status bar chip
            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(hostPackage = "test_pkg")
            val screenRecordEntry =
                buildNotificationEntry(tag = "screenRecord", promoted = false) {
                    setImportance(NotificationManager.IMPORTANCE_DEFAULT)
                    setFlag(context, FLAG_FOREGROUND_SERVICE, true)
                }

            renderNotificationListInteractor.setRenderedList(listOf(screenRecordEntry))

            val orderedChipNotificationKeys by
                collectLastValue(promotedNotificationsInteractor.orderedChipNotificationKeys)

            assertThat(orderedChipNotificationKeys)
                .containsExactly("0|test_pkg|0|screenRecord|0")
                .inOrder()

            // THEN the entry is NOT in the fgs section
            assertFalse(sectioner.isInSection(screenRecordEntry))
        }

    @Test
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    fun promoterSelectsPromotedOngoing_flagEnabled() {
        val promoter: NotifPromoter = withArgCaptor { verify(notifPipeline).addPromoter(capture()) }

        // GIVEN the notification has FLAG_PROMOTED_ONGOING
        val entry = buildEntry { setFlag(mContext, Notification.FLAG_PROMOTED_ONGOING, true) }

        // THEN the entry is promoted to top level
        assertTrue(promoter.shouldPromoteToTopLevel(entry))
    }

    @Test
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    fun promoterIgnoresNonPromotedOngoing_flagEnabled() {
        val promoter: NotifPromoter = withArgCaptor { verify(notifPipeline).addPromoter(capture()) }

        // GIVEN the notification does not have FLAG_PROMOTED_ONGOING
        val entry = buildEntry { setFlag(mContext, Notification.FLAG_PROMOTED_ONGOING, false) }

        // THEN the entry is NOT promoted to top level
        assertFalse(promoter.shouldPromoteToTopLevel(entry))
    }

    @Test
    @DisableFlags(PromotedNotificationUi.FLAG_NAME)
    fun noPromoterAdded_flagDisabled() {
        verify(notifPipeline, never()).addPromoter(any())
    }

    @Test
    @EnableFlags(
        PromotedNotificationUi.FLAG_NAME,
        StatusBarNotifChips.FLAG_NAME,
        StatusBarChipsModernization.FLAG_NAME,
        StatusBarRootModernization.FLAG_NAME,
    )
    fun comparatorPutsCallBeforeOther() =
        kosmos.runTest {
            // GIVEN a call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = false)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            kosmos.renderNotificationListInteractor.setRenderedList(
                listOf(callEntry, ronEntry, otherEntry)
            )

            val orderedChipNotificationKeys by
                collectLastValue(kosmos.promotedNotificationsInteractor.orderedChipNotificationKeys)

            // THEN the order of the notification keys should be the call then the RON
            assertThat(orderedChipNotificationKeys)
                .containsExactly("0|test_pkg|0|call|0", "0|test_pkg|0|ron|0")

            // VERIFY that the comparator puts the call before the ron
            assertThat(sectioner.comparator!!.compare(callEntry, ronEntry)).isLessThan(0)
            // VERIFY that the comparator puts the ron before the other
            assertThat(sectioner.comparator!!.compare(ronEntry, otherEntry)).isLessThan(0)
        }

    private fun makeCallStyle(): Notification.CallStyle {
        val pendingIntent =
            PendingIntent.getBroadcast(mContext, 0, Intent("action"), PendingIntent.FLAG_IMMUTABLE)
        val person = Person.Builder().setName("person").build()
        return Notification.CallStyle.forOngoingCall(person, pendingIntent)
    }
}
