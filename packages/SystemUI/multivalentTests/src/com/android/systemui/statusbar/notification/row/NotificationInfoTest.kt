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
 * limitations under the License
 */
package com.android.systemui.statusbar.notification.row

import android.app.Flags
import android.app.INotificationManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannel.SOCIAL_MEDIA_ID
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.Person
import android.content.ComponentName
import android.content.Intent
import android.content.mockPackageManager
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.RemoteException
import android.os.UserHandle
import android.os.testableLooper
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.print.PrintManager
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.testing.TestableLooper.RunWithLooper
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.UiEventLogger
import com.android.internal.logging.metricsLogger
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Dependency
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.AssistantFeedbackController
import com.android.systemui.statusbar.notification.collection.EntryAdapter
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.coordinator.mockVisualStabilityCoordinator
import com.android.systemui.statusbar.notification.promoted.domain.interactor.PackageDemotionInteractor
import com.android.systemui.statusbar.notification.row.icon.AppIconProvider
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider
import com.android.systemui.statusbar.notification.row.icon.mockAppIconProvider
import com.android.systemui.statusbar.notification.row.icon.mockNotificationIconStyleProvider
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.android.telecom.telecomManager
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class NotificationInfoTest : SysuiTestCase() {
    private val kosmos = testKosmos().also { it.testCase = this }

    private lateinit var underTest: NotificationInfo
    private lateinit var notificationChannel: NotificationChannel
    private lateinit var defaultNotificationChannel: NotificationChannel
    private lateinit var classifiedNotificationChannel: NotificationChannel
    private lateinit var sbn: StatusBarNotification
    private lateinit var entry: NotificationEntry
    private lateinit var entryAdapter: EntryAdapter

    private val mockPackageManager = kosmos.mockPackageManager
    private val mockAppIconProvider = kosmos.mockAppIconProvider
    private val mockIconStyleProvider = kosmos.mockNotificationIconStyleProvider
    private val uiEventLogger = kosmos.uiEventLoggerFake
    private val testableLooper by lazy { kosmos.testableLooper }

    private val onUserInteractionCallback = mock<OnUserInteractionCallback>()
    private val mockINotificationManager = mock<INotificationManager>()
    private val channelEditorDialogController = mock<ChannelEditorDialogController>()
    private val packageDemotionInteractor = mock<PackageDemotionInteractor>()
    private val assistantFeedbackController = mock<AssistantFeedbackController>()

    @Before
    fun setUp() {
        mContext.addMockSystemService(TelecomManager::class.java, kosmos.telecomManager)

        mDependency.injectTestDependency(Dependency.BG_LOOPER, testableLooper.looper)

        // Inflate the layout
        val inflater = LayoutInflater.from(mContext)
        underTest = inflater.inflate(R.layout.notification_info, null) as NotificationInfo

        underTest.setGutsParent(mock<NotificationGuts>())

        // Our view is never attached to a window so the View#post methods in NotificationInfo never
        // get called. Setting this will skip the post and do the action immediately.
        underTest.mSkipPost = true

        // PackageManager must return a packageInfo and applicationInfo.
        val packageInfo = PackageInfo()
        packageInfo.packageName = TEST_PACKAGE_NAME
        whenever(mockPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), anyInt()))
            .thenReturn(packageInfo)
        val applicationInfo = ApplicationInfo()
        applicationInfo.uid = TEST_UID // non-zero
        val systemPackageInfo = PackageInfo()
        systemPackageInfo.packageName = TEST_SYSTEM_PACKAGE_NAME
        whenever(mockPackageManager.getPackageInfo(eq(TEST_SYSTEM_PACKAGE_NAME), anyInt()))
            .thenReturn(systemPackageInfo)
        whenever(mockPackageManager.getPackageInfo(eq("android"), anyInt())).thenReturn(packageInfo)

        val assistant = ComponentName("package", "service")
        whenever(mockINotificationManager.allowedNotificationAssistant).thenReturn(assistant)
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = assistant.packageName
        ri.activityInfo.name = "activity"
        whenever(mockPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(listOf(ri))

        // Package has one channel by default.
        whenever(
                mockINotificationManager.getNumNotificationChannelsForPackage(
                    eq(TEST_PACKAGE_NAME),
                    eq(TEST_UID),
                    anyBoolean(),
                )
            )
            .thenReturn(1)

        // Some test channels.
        notificationChannel = NotificationChannel(TEST_CHANNEL, TEST_CHANNEL_NAME, IMPORTANCE_LOW)
        defaultNotificationChannel =
            NotificationChannel(
                NotificationChannel.DEFAULT_CHANNEL_ID,
                TEST_CHANNEL_NAME,
                IMPORTANCE_LOW,
            )
        classifiedNotificationChannel =
            NotificationChannel(SOCIAL_MEDIA_ID, "social", IMPORTANCE_LOW)

        val notification = Notification()
        notification.extras.putParcelable(
            Notification.EXTRA_BUILDER_APPLICATION_INFO,
            applicationInfo,
        )
        sbn =
            StatusBarNotification(
                TEST_PACKAGE_NAME,
                TEST_PACKAGE_NAME,
                0,
                null,
                TEST_UID,
                0,
                notification,
                UserHandle.getUserHandleForUid(TEST_UID),
                null,
                0,
            )
        entry =
            NotificationEntryBuilder()
                .setSbn(sbn)
                .updateRanking { it.setChannel(notificationChannel) }
                .build()
        entryAdapter = kosmos.entryAdapterFactory.create(entry)
        whenever(assistantFeedbackController.isFeedbackEnabled).thenReturn(false)
        whenever(assistantFeedbackController.getInlineDescriptionResource(any()))
            .thenReturn(R.string.notification_channel_summary_automatic)
    }

    @Test
    fun testBindNotification_SetsTextApplicationName() {
        whenever(mockPackageManager.getApplicationLabel(any())).thenReturn("App Name")
        bindNotification()
        val textView = underTest.findViewById<TextView>(R.id.pkg_name)
        assertThat(textView.text.toString()).contains("App Name")
        assertThat(underTest.findViewById<View>(R.id.header).visibility).isEqualTo(VISIBLE)
    }

    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_NOTIFICATIONS_REDESIGN_GUTS)
    fun testBindNotification_SetsPackageIcon_flagOff() {
        val iconDrawable = mock<Drawable>()
        whenever(mockPackageManager.getApplicationIcon(any<ApplicationInfo>()))
            .thenReturn(iconDrawable)
        bindNotification()
        val iconView = underTest.findViewById<ImageView>(R.id.pkg_icon)
        assertThat(iconView.drawable).isEqualTo(iconDrawable)
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_NOTIFICATIONS_REDESIGN_GUTS)
    fun testBindNotification_SetsPackageIcon_flagOn() {
        val iconDrawable = mock<Drawable>()
        whenever(mockIconStyleProvider.shouldShowWorkProfileBadge(anyOrNull(), anyOrNull()))
            .thenReturn(false)
        whenever(
                mockAppIconProvider.getOrFetchAppIcon(
                    anyOrNull(),
                    anyOrNull(),
                    anyBoolean(),
                    anyBoolean(),
                )
            )
            .thenReturn(iconDrawable)
        bindNotification()
        val iconView = underTest.findViewById<ImageView>(R.id.pkg_icon)
        assertThat(iconView.drawable).isEqualTo(iconDrawable)
    }

    @Test
    fun testBindNotification_noDelegate() {
        bindNotification()
        val nameView = underTest.findViewById<TextView>(R.id.delegate_name)
        assertThat(nameView.visibility).isEqualTo(GONE)
    }

    @Test
    fun testBindNotification_delegate() {
        sbn =
            StatusBarNotification(
                TEST_PACKAGE_NAME,
                "other",
                0,
                null,
                TEST_UID,
                0,
                Notification(),
                UserHandle.CURRENT,
                null,
                0,
            )
        val applicationInfo = ApplicationInfo()
        applicationInfo.uid = 7 // non-zero
        whenever(mockPackageManager.getApplicationInfo(eq("other"), anyInt()))
            .thenReturn(applicationInfo)
        whenever(mockPackageManager.getApplicationLabel(any())).thenReturn("Other")

        val entry = NotificationEntryBuilder(entry).setSbn(sbn).build()
        bindNotification(entry = entry)
        val nameView = underTest.findViewById<TextView>(R.id.delegate_name)
        assertThat(nameView.visibility).isEqualTo(VISIBLE)
        assertThat(nameView.text.toString()).contains("Proxied")
    }

    @Test
    fun testBindNotification_GroupNameHiddenIfNoGroup() {
        bindNotification()
        val groupNameView = underTest.findViewById<TextView>(R.id.group_name)
        assertThat(groupNameView.visibility).isEqualTo(GONE)
    }

    @Test
    fun testBindNotification_SetsGroupNameIfNonNull() {
        notificationChannel.group = "test_group_id"
        val notificationChannelGroup = NotificationChannelGroup("test_group_id", "Test Group Name")
        whenever(
                mockINotificationManager.getNotificationChannelGroupForPackage(
                    eq("test_group_id"),
                    eq(TEST_PACKAGE_NAME),
                    eq(TEST_UID),
                )
            )
            .thenReturn(notificationChannelGroup)
        bindNotification()
        val groupNameView = underTest.findViewById<TextView>(R.id.group_name)
        assertThat(groupNameView.visibility).isEqualTo(VISIBLE)
        assertThat(groupNameView.text).isEqualTo("Test Group Name")
    }

    @Test
    fun testBindNotification_SetsTextChannelName() {
        bindNotification()
        val textView = underTest.findViewById<TextView>(R.id.channel_name)
        assertThat(textView.text).isEqualTo(TEST_CHANNEL_NAME)
    }

    @Test
    fun testBindNotification_DefaultChannelDoesNotUseChannelName() {
        entry =
            NotificationEntryBuilder(entry)
                .updateRanking { it.setChannel(defaultNotificationChannel) }
                .build()
        bindNotification(notificationChannel = defaultNotificationChannel)
        val textView = underTest.findViewById<TextView>(R.id.channel_name)
        assertThat(textView.visibility).isEqualTo(GONE)
    }

    @Test
    fun testBindNotification_DefaultChannelUsesChannelNameIfMoreChannelsExist() {
        // Package has more than one channel by default.
        whenever(
                mockINotificationManager.getNumNotificationChannelsForPackage(
                    eq(TEST_PACKAGE_NAME),
                    eq(TEST_UID),
                    anyBoolean(),
                )
            )
            .thenReturn(10)
        entry =
            NotificationEntryBuilder(entry)
                .updateRanking { it.setChannel(notificationChannel) }
                .build()
        bindNotification(notificationChannel = defaultNotificationChannel)
        val textView = underTest.findViewById<TextView>(R.id.channel_name)
        assertThat(textView.visibility).isEqualTo(VISIBLE)
    }

    @Test
    fun testBindNotification_UnblockablePackageUsesChannelName() {
        bindNotification(isNonblockable = true)
        val textView = underTest.findViewById<TextView>(R.id.channel_name)
        assertThat(textView.visibility).isEqualTo(VISIBLE)
    }

    @Test
    fun testBindNotification_SetsOnClickListenerForSettings() {
        val latch = CountDownLatch(1)
        bindNotification(
            onSettingsClick = { _: View?, c: NotificationChannel?, _: Int ->
                assertThat(c).isEqualTo(notificationChannel)
                latch.countDown()
            }
        )

        val settingsButton = underTest.findViewById<View>(R.id.info)
        settingsButton.performClick()
        // Verify that listener was triggered.
        assertThat(latch.count).isEqualTo(0)
    }

    @Test
    fun testBindNotification_SettingsButtonInvisibleWhenNoClickListener() {
        bindNotification()
        val settingsButton = underTest.findViewById<View>(R.id.info)
        assertThat(settingsButton.visibility != VISIBLE).isTrue()
    }

    @Test
    fun testBindNotification_SettingsButtonInvisibleWhenDeviceUnprovisioned() {
        bindNotification(
            onSettingsClick = { _: View?, c: NotificationChannel?, _: Int ->
                assertThat(c).isEqualTo(notificationChannel)
            },
            isDeviceProvisioned = false,
        )
        val settingsButton = underTest.findViewById<View>(R.id.info)
        assertThat(settingsButton.visibility != VISIBLE).isTrue()
    }

    @Test
    fun testBindNotification_SettingsButtonReappearsAfterSecondBind() {
        bindNotification()
        bindNotification(onSettingsClick = { _: View?, _: NotificationChannel?, _: Int -> })
        val settingsButton = underTest.findViewById<View>(R.id.info)
        assertThat(settingsButton.visibility).isEqualTo(VISIBLE)
    }

    @Test
    fun testBindNotification_whenAppUnblockable() {
        bindNotification(isNonblockable = true)
        val view = underTest.findViewById<TextView>(R.id.non_configurable_text)
        assertThat(view.visibility).isEqualTo(VISIBLE)
        assertThat(view.text).isEqualTo(mContext.getString(R.string.notification_unblockable_desc))
        assertThat(underTest.findViewById<View>(R.id.interruptiveness_settings).visibility)
            .isEqualTo(GONE)
    }

    @Test
    fun testBindNotification_whenCurrentlyInCall() {
        whenever(mockINotificationManager.isInCall(anyString(), anyInt())).thenReturn(true)

        val person = Person.Builder().setName("caller").build()
        val nb =
            Notification.Builder(mContext, notificationChannel.id)
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setStyle(Notification.CallStyle.forOngoingCall(person, mock<PendingIntent>()))
                .setFullScreenIntent(mock<PendingIntent>(), true)
                .addAction(Notification.Action.Builder(null, "test", null).build())

        sbn =
            StatusBarNotification(
                TEST_PACKAGE_NAME,
                TEST_PACKAGE_NAME,
                0,
                null,
                TEST_UID,
                0,
                nb.build(),
                UserHandle.getUserHandleForUid(TEST_UID),
                null,
                0,
            )
        entry.sbn = sbn
        bindNotification()
        val view = underTest.findViewById<TextView>(R.id.non_configurable_call_text)
        assertThat(view.visibility).isEqualTo(VISIBLE)
        assertThat(view.text)
            .isEqualTo(mContext.getString(R.string.notification_unblockable_call_desc))
        assertThat(underTest.findViewById<View>(R.id.interruptiveness_settings).visibility)
            .isEqualTo(GONE)
        assertThat(underTest.findViewById<View>(R.id.non_configurable_text).visibility)
            .isEqualTo(GONE)
    }

    @Test
    fun testBindNotification_whenCurrentlyInCall_notCall() {
        whenever(mockINotificationManager.isInCall(anyString(), anyInt())).thenReturn(true)

        val nb =
            Notification.Builder(mContext, notificationChannel.id)
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setFullScreenIntent(mock<PendingIntent>(), true)
                .addAction(Notification.Action.Builder(null, "test", null).build())

        sbn =
            StatusBarNotification(
                TEST_PACKAGE_NAME,
                TEST_PACKAGE_NAME,
                0,
                null,
                TEST_UID,
                0,
                nb.build(),
                UserHandle.getUserHandleForUid(TEST_UID),
                null,
                0,
            )
        entry.sbn = sbn
        bindNotification()
        assertThat(underTest.findViewById<View>(R.id.non_configurable_call_text).visibility)
            .isEqualTo(GONE)
        assertThat(underTest.findViewById<View>(R.id.interruptiveness_settings).visibility)
            .isEqualTo(VISIBLE)
        assertThat(underTest.findViewById<View>(R.id.non_configurable_text).visibility)
            .isEqualTo(GONE)
    }

    @Test
    fun testBindNotification_automaticIsVisible() {
        whenever(assistantFeedbackController.isFeedbackEnabled).thenReturn(true)
        bindNotification()
        assertThat(underTest.findViewById<View>(R.id.automatic).visibility).isEqualTo(VISIBLE)
        assertThat(underTest.findViewById<View>(R.id.automatic_summary).visibility)
            .isEqualTo(VISIBLE)
    }

    @Test
    fun testBindNotification_automaticIsGone() {
        bindNotification()
        assertThat(underTest.findViewById<View>(R.id.automatic).visibility).isEqualTo(GONE)
        assertThat(underTest.findViewById<View>(R.id.automatic_summary).visibility).isEqualTo(GONE)
    }

    @Test
    fun testBindNotification_automaticIsSelected() {
        whenever(assistantFeedbackController.isFeedbackEnabled).thenReturn(true)
        notificationChannel.unlockFields(NotificationChannel.USER_LOCKED_IMPORTANCE)
        bindNotification()
        assertThat(underTest.findViewById<View>(R.id.automatic).isSelected).isTrue()
    }

    @Test
    fun testBindNotification_alertIsSelected() {
        bindNotification()
        assertThat(underTest.findViewById<View>(R.id.alert).isSelected).isTrue()
    }

    @Test
    fun testBindNotification_silenceIsSelected() {
        bindNotification(wasShownHighPriority = false)
        assertThat(underTest.findViewById<View>(R.id.silence).isSelected).isTrue()
    }

    @Test
    fun testBindNotification_DoesNotUpdateNotificationChannel() {
        bindNotification()
        testableLooper.processAllMessages()
        verify(mockINotificationManager, never())
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), any())
    }

    @Test
    fun testBindNotification_LogsOpen() {
        bindNotification()
        assertThat(uiEventLogger.numLogs()).isEqualTo(1)
        assertThat(uiEventLogger.eventId(0))
            .isEqualTo(NotificationControlsEvent.NOTIFICATION_CONTROLS_OPEN.id)
    }

    @Test
    fun testDoesNotUpdateNotificationChannelAfterImportanceChanged() {
        notificationChannel.importance = IMPORTANCE_LOW
        bindNotification(wasShownHighPriority = false)

        underTest.findViewById<View>(R.id.alert).performClick()
        testableLooper.processAllMessages()
        verify(mockINotificationManager, never())
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), any())
    }

    @Test
    fun testDoesNotUpdateNotificationChannelAfterImportanceChangedSilenced() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_DEFAULT
        bindNotification()

        underTest.findViewById<View>(R.id.silence).performClick()
        testableLooper.processAllMessages()
        verify(mockINotificationManager, never())
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), any())
    }

    @Test
    fun testDoesNotUpdateNotificationChannelAfterImportanceChangedAutomatic() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_DEFAULT
        bindNotification()

        underTest.findViewById<View>(R.id.automatic).performClick()
        testableLooper.processAllMessages()
        verify(mockINotificationManager, never())
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), any())
    }

    @Test
    fun testHandleCloseControls_persistAutomatic() {
        whenever(assistantFeedbackController.isFeedbackEnabled).thenReturn(true)
        notificationChannel.unlockFields(NotificationChannel.USER_LOCKED_IMPORTANCE)
        bindNotification()

        underTest.handleCloseControls(true, false)
        testableLooper.processAllMessages()
        verify(mockINotificationManager).unlockNotificationChannel(anyString(), eq(TEST_UID), any())
    }

    @Test
    fun testHandleCloseControls_DoesNotUpdateNotificationChannelIfUnchanged() {
        val originalImportance = notificationChannel.importance
        bindNotification()

        underTest.handleCloseControls(true, false)
        testableLooper.processAllMessages()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), any())
        assertThat(notificationChannel.importance).isEqualTo(originalImportance)

        assertThat(uiEventLogger.numLogs()).isEqualTo(2)
        assertThat(uiEventLogger.eventId(0))
            .isEqualTo(NotificationControlsEvent.NOTIFICATION_CONTROLS_OPEN.id)
        // The SAVE_IMPORTANCE event is logged whenever importance is saved, even if unchanged.
        assertThat(uiEventLogger.eventId(1))
            .isEqualTo(NotificationControlsEvent.NOTIFICATION_CONTROLS_SAVE_IMPORTANCE.id)
    }

    @Test
    fun testHandleCloseControls_DoesNotUpdateNotificationChannelIfUnspecified() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_UNSPECIFIED
        bindNotification()

        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), any())
        assertThat(notificationChannel.importance)
            .isEqualTo(NotificationManager.IMPORTANCE_UNSPECIFIED)
    }

    @Test
    fun testSilenceCallsUpdateNotificationChannel() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_DEFAULT
        bindNotification()

        underTest.findViewById<View>(R.id.silence).performClick()
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        val updated = argumentCaptor<NotificationChannel>()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), updated.capture())
        assertThat(
                updated.firstValue.userLockedFields and NotificationChannel.USER_LOCKED_IMPORTANCE
            )
            .isNotEqualTo(0)
        assertThat(updated.firstValue.importance).isEqualTo(IMPORTANCE_LOW)

        assertThat(uiEventLogger.numLogs()).isEqualTo(2)
        assertThat(uiEventLogger.eventId(0))
            .isEqualTo(NotificationControlsEvent.NOTIFICATION_CONTROLS_OPEN.id)
        assertThat(uiEventLogger.eventId(1))
            .isEqualTo(NotificationControlsEvent.NOTIFICATION_CONTROLS_SAVE_IMPORTANCE.id)
        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testUnSilenceCallsUpdateNotificationChannel() {
        notificationChannel.importance = IMPORTANCE_LOW
        bindNotification(wasShownHighPriority = false)

        underTest.findViewById<View>(R.id.alert).performClick()
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        val updated = argumentCaptor<NotificationChannel>()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), updated.capture())
        assertThat(
                updated.firstValue.userLockedFields and NotificationChannel.USER_LOCKED_IMPORTANCE
            )
            .isNotEqualTo(0)
        assertThat(updated.firstValue.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testAutomaticUnlocksUserImportance() {
        whenever(assistantFeedbackController.isFeedbackEnabled).thenReturn(true)
        notificationChannel.importance = NotificationManager.IMPORTANCE_DEFAULT
        notificationChannel.lockFields(NotificationChannel.USER_LOCKED_IMPORTANCE)
        bindNotification()

        underTest.findViewById<View>(R.id.automatic).performClick()
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        verify(mockINotificationManager).unlockNotificationChannel(anyString(), eq(TEST_UID), any())
        assertThat(notificationChannel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testSilenceCallsUpdateNotificationChannel_channelImportanceUnspecified() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_UNSPECIFIED
        bindNotification()

        underTest.findViewById<View>(R.id.silence).performClick()
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        val updated = argumentCaptor<NotificationChannel>()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), updated.capture())
        assertThat(
                updated.firstValue.userLockedFields and NotificationChannel.USER_LOCKED_IMPORTANCE
            )
            .isNotEqualTo(0)
        assertThat(updated.firstValue.importance).isEqualTo(IMPORTANCE_LOW)
        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testSilenceCallsUpdateNotificationChannel_channelImportanceMin() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_MIN
        bindNotification(wasShownHighPriority = false)

        assertThat((underTest.findViewById<View>(R.id.done) as TextView).text)
            .isEqualTo(mContext.getString(R.string.inline_done_button))
        underTest.findViewById<View>(R.id.silence).performClick()
        assertThat((underTest.findViewById<View>(R.id.done) as TextView).text)
            .isEqualTo(mContext.getString(R.string.inline_done_button))
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        val updated = argumentCaptor<NotificationChannel>()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), updated.capture())
        assertThat(
                updated.firstValue.userLockedFields and NotificationChannel.USER_LOCKED_IMPORTANCE
            )
            .isNotEqualTo(0)
        assertThat(updated.firstValue.importance).isEqualTo(NotificationManager.IMPORTANCE_MIN)
        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    @Throws(RemoteException::class)
    fun testSilence_closeGutsThenTryToSave() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_DEFAULT
        bindNotification(wasShownHighPriority = false)

        underTest.findViewById<View>(R.id.silence).performClick()
        underTest.handleCloseControls(false, false)
        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()

        assertThat(notificationChannel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testAlertCallsUpdateNotificationChannel_channelImportanceMin() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_MIN
        bindNotification(wasShownHighPriority = false)

        assertThat((underTest.findViewById<View>(R.id.done) as TextView).text)
            .isEqualTo(mContext.getString(R.string.inline_done_button))
        underTest.findViewById<View>(R.id.alert).performClick()
        assertThat((underTest.findViewById<View>(R.id.done) as TextView).text)
            .isEqualTo(mContext.getString(R.string.inline_ok_button))
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        val updated = argumentCaptor<NotificationChannel>()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), updated.capture())
        assertThat(
                updated.firstValue.userLockedFields and NotificationChannel.USER_LOCKED_IMPORTANCE
            )
            .isNotEqualTo(0)
        assertThat(updated.firstValue.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testAdjustImportanceTemporarilyAllowsReordering() {
        notificationChannel.importance = NotificationManager.IMPORTANCE_DEFAULT
        bindNotification()

        underTest.findViewById<View>(R.id.silence).performClick()
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(true, false)

        if (NotificationBundleUi.isEnabled) {
            verify(kosmos.mockVisualStabilityCoordinator)
                .temporarilyAllowSectionChanges(eq(entry), any())
        } else {
            verify(onUserInteractionCallback).onImportanceChanged(entry)
        }

        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testDoneText() {
        notificationChannel.importance = IMPORTANCE_LOW
        bindNotification(wasShownHighPriority = false)

        assertThat((underTest.findViewById<View>(R.id.done) as TextView).text)
            .isEqualTo(mContext.getString(R.string.inline_done_button))
        underTest.findViewById<View>(R.id.alert).performClick()
        assertThat((underTest.findViewById<View>(R.id.done) as TextView).text)
            .isEqualTo(mContext.getString(R.string.inline_ok_button))
        underTest.findViewById<View>(R.id.silence).performClick()
        assertThat((underTest.findViewById<View>(R.id.done) as TextView).text)
            .isEqualTo(mContext.getString(R.string.inline_done_button))
    }

    @Test
    fun testUnSilenceCallsUpdateNotificationChannel_channelImportanceUnspecified() {
        notificationChannel.importance = IMPORTANCE_LOW
        bindNotification(wasShownHighPriority = false)

        underTest.findViewById<View>(R.id.alert).performClick()
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        val updated = argumentCaptor<NotificationChannel>()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(anyString(), eq(TEST_UID), updated.capture())
        assertThat(
                updated.firstValue.userLockedFields and NotificationChannel.USER_LOCKED_IMPORTANCE
            )
            .isNotEqualTo(0)
        assertThat(updated.firstValue.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testCloseControlsDoesNotUpdateIfSaveIsFalse() {
        notificationChannel.importance = IMPORTANCE_LOW
        bindNotification(wasShownHighPriority = false)

        underTest.findViewById<View>(R.id.alert).performClick()
        underTest.findViewById<View>(R.id.done).performClick()
        underTest.handleCloseControls(false, false)

        testableLooper.processAllMessages()
        verify(mockINotificationManager, never())
            .updateNotificationChannelForPackage(
                eq(TEST_PACKAGE_NAME),
                eq(TEST_UID),
                eq(notificationChannel),
            )

        assertThat(uiEventLogger.numLogs()).isEqualTo(1)
        assertThat(uiEventLogger.eventId(0))
            .isEqualTo(NotificationControlsEvent.NOTIFICATION_CONTROLS_OPEN.id)
    }

    @Test
    fun testCloseControlsUpdatesWhenCheckSaveListenerUsesCallback() {
        notificationChannel.importance = IMPORTANCE_LOW
        bindNotification(wasShownHighPriority = false)

        underTest.findViewById<View>(R.id.alert).performClick()
        underTest.findViewById<View>(R.id.done).performClick()
        testableLooper.processAllMessages()
        verify(mockINotificationManager, never())
            .updateNotificationChannelForPackage(
                eq(TEST_PACKAGE_NAME),
                eq(TEST_UID),
                eq(notificationChannel),
            )

        underTest.handleCloseControls(true, false)

        testableLooper.processAllMessages()
        verify(mockINotificationManager)
            .updateNotificationChannelForPackage(
                eq(TEST_PACKAGE_NAME),
                eq(TEST_UID),
                eq(notificationChannel),
            )
    }

    @Test
    fun testCloseControls_withoutHittingApply() {
        notificationChannel.importance = IMPORTANCE_LOW
        bindNotification(wasShownHighPriority = false)

        underTest.findViewById<View>(R.id.alert).performClick()

        assertThat(underTest.shouldBeSavedOnClose()).isFalse()
    }

    @Test
    fun testWillBeRemovedReturnsFalse() {
        assertThat(underTest.willBeRemoved()).isFalse()

        bindNotification(wasShownHighPriority = false)

        assertThat(underTest.willBeRemoved()).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI)
    @Throws(Exception::class)
    fun testBindNotification_HidesFeedbackLink_flagOff() {
        bindNotification()
        assertThat(underTest.findViewById<View>(R.id.feedback).visibility).isEqualTo(GONE)
    }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI)
    @Throws(RemoteException::class)
    fun testBindNotification_SetsFeedbackLink_isReservedChannel() {
        entry.setRanking(
            RankingBuilder(entry.ranking)
                .setSummarization("something")
                .setChannel(classifiedNotificationChannel)
                .build()
        )
        val latch = CountDownLatch(1)
        bindNotification(
            notificationChannel = classifiedNotificationChannel,
            onFeedbackClickListener = { _: View?, _: Intent? -> latch.countDown() },
            wasShownHighPriority = false,
        )

        val feedback: View = underTest.findViewById(R.id.feedback)
        assertThat(feedback.visibility).isEqualTo(VISIBLE)
        feedback.performClick()
        // Verify that listener was triggered.
        assertThat(latch.count).isEqualTo(0)
    }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI)
    @Throws(Exception::class)
    fun testBindNotification_hidesFeedbackLink_notReservedChannel() {
        bindNotification()

        assertThat(underTest.findViewById<View>(R.id.feedback).visibility).isEqualTo(GONE)
    }

    @Test
    @Throws(RemoteException::class)
    fun testDismissListenerBound() {
        val latch = CountDownLatch(1)
        bindNotification(onCloseClick = { _: View? -> latch.countDown() })

        val dismissView = underTest.findViewById<View>(R.id.inline_dismiss)
        assertThat(dismissView.isVisible).isTrue()
        dismissView.performClick()

        // Verify that listener was triggered.
        assertThat(latch.count).isEqualTo(0)
    }

    @Test
    @Throws(RemoteException::class)
    fun testDismissHiddenWhenUndismissable() {

        entry.sbn.notification.flags =
            entry.sbn.notification.flags or android.app.Notification.FLAG_NO_DISMISS
        bindNotification(isDismissable = false)
        val dismissView = underTest.findViewById<View>(R.id.inline_dismiss)
        assertThat(dismissView.isVisible).isFalse()
    }

    private fun bindNotification(
        pm: PackageManager = this.mockPackageManager,
        iNotificationManager: INotificationManager = this.mockINotificationManager,
        appIconProvider: AppIconProvider = this.mockAppIconProvider,
        iconStyleProvider: NotificationIconStyleProvider = this.mockIconStyleProvider,
        onUserInteractionCallback: OnUserInteractionCallback = this.onUserInteractionCallback,
        channelEditorDialogController: ChannelEditorDialogController =
            this.channelEditorDialogController,
        packageDemotionInteractor: PackageDemotionInteractor = this.packageDemotionInteractor,
        pkg: String = TEST_PACKAGE_NAME,
        notificationChannel: NotificationChannel = this.notificationChannel,
        entry: NotificationEntry = this.entry,
        entryAdapter: EntryAdapter = this.entryAdapter,
        onSettingsClick: NotificationInfo.OnSettingsClickListener? = null,
        onAppSettingsClick: NotificationInfo.OnAppSettingsClickListener? = null,
        onFeedbackClickListener: NotificationInfo.OnFeedbackClickListener? = null,
        uiEventLogger: UiEventLogger = this.uiEventLogger,
        isDeviceProvisioned: Boolean = true,
        isNonblockable: Boolean = false,
        isDismissable: Boolean = true,
        wasShownHighPriority: Boolean = true,
        assistantFeedbackController: AssistantFeedbackController = this.assistantFeedbackController,
        metricsLogger: MetricsLogger = kosmos.metricsLogger,
        onCloseClick: View.OnClickListener? = null,
    ) {
        underTest.bindNotification(
            pm,
            iNotificationManager,
            appIconProvider,
            iconStyleProvider,
            onUserInteractionCallback,
            channelEditorDialogController,
            packageDemotionInteractor,
            pkg,
            entry.ranking,
            entry.sbn,
            entry,
            entryAdapter,
            onSettingsClick,
            onAppSettingsClick,
            onFeedbackClickListener,
            uiEventLogger,
            isDeviceProvisioned,
            isNonblockable,
            isDismissable,
            wasShownHighPriority,
            assistantFeedbackController,
            metricsLogger,
            onCloseClick,
        )
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "test_package"
        private const val TEST_SYSTEM_PACKAGE_NAME = PrintManager.PRINT_SPOOLER_PACKAGE_NAME
        private const val TEST_UID = 1
        private const val TEST_CHANNEL = "test_channel"
        private const val TEST_CHANNEL_NAME = "TEST CHANNEL NAME"
    }
}
