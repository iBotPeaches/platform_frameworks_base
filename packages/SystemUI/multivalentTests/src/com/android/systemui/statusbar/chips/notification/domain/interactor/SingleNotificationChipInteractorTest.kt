/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.chips.notification.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.data.repository.activityManagerRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.chips.notification.shared.StatusBarNotifChips
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(StatusBarNotifChips.FLAG_NAME)
class SingleNotificationChipInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    val factory = kosmos.singleNotificationChipInteractorFactory

    @Test
    fun notificationChip_startsWithStartingModel() =
        kosmos.runTest {
            val icon = mock<StatusBarIconView>()
            val instanceId = InstanceId.fakeInstanceId(4)
            val startingNotif =
                activeNotificationModel(
                    key = "notif1",
                    appName = "Fake Name",
                    statusBarChipIcon = icon,
                    promotedContent = PROMOTED_CONTENT,
                    instanceId = instanceId,
                )

            val underTest = factory.create(startingNotif, creationTime = 1)

            val latest by collectLastValue(underTest.notificationChip)

            assertThat(latest!!.key).isEqualTo("notif1")
            assertThat(latest!!.appName).isEqualTo("Fake Name")
            assertThat(latest!!.statusBarChipIconView).isEqualTo(icon)
            assertThat(latest!!.promotedContent).isEqualTo(PROMOTED_CONTENT)
            assertThat(latest!!.instanceId).isEqualTo(instanceId)
        }

    @Test
    fun notificationChip_updatesAfterSet() =
        kosmos.runTest {
            val originalIconView = mock<StatusBarIconView>()
            val originalInstanceId = InstanceId.fakeInstanceId(4)
            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif1",
                        appName = "Fake Name",
                        statusBarChipIcon = originalIconView,
                        promotedContent = PROMOTED_CONTENT,
                        instanceId = originalInstanceId,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            val newIconView = mock<StatusBarIconView>()
            val newInstanceId = InstanceId.fakeInstanceId(4)
            underTest.setNotification(
                activeNotificationModel(
                    key = "notif1",
                    appName = "New Name",
                    statusBarChipIcon = newIconView,
                    promotedContent = PROMOTED_CONTENT,
                    instanceId = newInstanceId,
                )
            )

            assertThat(latest!!.key).isEqualTo("notif1")
            assertThat(latest!!.appName).isEqualTo("New Name")
            assertThat(latest!!.statusBarChipIconView).isEqualTo(newIconView)
            assertThat(latest!!.instanceId).isEqualTo(newInstanceId)
        }

    @Test
    fun notificationChip_ignoresSetWithDifferentKey() =
        kosmos.runTest {
            val originalIconView = mock<StatusBarIconView>()
            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif1",
                        statusBarChipIcon = originalIconView,
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            val newIconView = mock<StatusBarIconView>()
            underTest.setNotification(
                activeNotificationModel(
                    key = "other_notif",
                    statusBarChipIcon = newIconView,
                    promotedContent = PROMOTED_CONTENT,
                )
            )

            assertThat(latest!!.key).isEqualTo("notif1")
            assertThat(latest!!.statusBarChipIconView).isEqualTo(originalIconView)
        }

    @Test
    fun notificationChip_ignoresSetWithNullPromotedContent() =
        kosmos.runTest {
            val originalIconView = mock<StatusBarIconView>()
            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif1",
                        statusBarChipIcon = originalIconView,
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            val newIconView = mock<StatusBarIconView>()
            underTest.setNotification(
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = newIconView,
                    promotedContent = null,
                )
            )

            assertThat(latest!!.statusBarChipIconView).isEqualTo(originalIconView)
        }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun notificationChip_missingStatusBarIconChipView_cdFlagDisabled_inConstructor_emitsNull() =
        kosmos.runTest {
            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif1",
                        statusBarChipIcon = null,
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            assertThat(latest).isNull()
        }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun notificationChip_missingStatusBarIconChipView_cdFlagEnabled_inConstructor_emitsNotNull() =
        kosmos.runTest {
            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif1",
                        statusBarChipIcon = null,
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 32L,
                )

            val latest by collectLastValue(underTest.notificationChip)

            assertThat(latest).isNotNull()
        }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun notificationChip_cdEnabled_missingStatusBarIconChipView_inConstructor_emitsNotNull() =
        kosmos.runTest {
            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif1",
                        statusBarChipIcon = null,
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            assertThat(latest).isNotNull()
            assertThat(latest!!.key).isEqualTo("notif1")
        }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun notificationChip_cdFlagDisabled_missingStatusBarIconChipView_inSet_emitsNull() =
        kosmos.runTest {
            val startingNotif =
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = mock(),
                    promotedContent = PROMOTED_CONTENT,
                )
            val underTest = factory.create(startingNotif, creationTime = 1)
            val latest by collectLastValue(underTest.notificationChip)
            assertThat(latest).isNotNull()

            underTest.setNotification(
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = null,
                    promotedContent = PROMOTED_CONTENT,
                )
            )

            assertThat(latest).isNull()
        }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun notificationChip_cdFlagEnabled_missingStatusBarIconChipView_inSet_emitsNotNull() =
        kosmos.runTest {
            val startingNotif =
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = mock(),
                    promotedContent = PROMOTED_CONTENT,
                )
            val underTest = factory.create(startingNotif, creationTime = 123L)
            val latest by collectLastValue(underTest.notificationChip)
            assertThat(latest).isNotNull()

            underTest.setNotification(
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = null,
                    promotedContent = PROMOTED_CONTENT,
                )
            )

            assertThat(latest).isNotNull()
        }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun notificationChip_missingStatusBarIconChipView_inSet_cdEnabled_emitsNotNull() =
        kosmos.runTest {
            val startingNotif =
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = mock(),
                    promotedContent = PROMOTED_CONTENT,
                )
            val underTest = factory.create(startingNotif, creationTime = 1)
            val latest by collectLastValue(underTest.notificationChip)
            assertThat(latest).isNotNull()

            underTest.setNotification(
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = null,
                    promotedContent = PROMOTED_CONTENT,
                )
            )

            assertThat(latest).isNotNull()
            assertThat(latest!!.key).isEqualTo("notif1")
        }

    @Test
    fun notificationChip_missingPromotedContent_inConstructor_emitsNull() =
        kosmos.runTest {
            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif1",
                        statusBarChipIcon = mock(),
                        promotedContent = null,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            assertThat(latest).isNull()
        }

    @Test
    fun notificationChip_appIsVisibleOnCreation_emitsIsAppVisibleTrueWithTime() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = true
            fakeSystemClock.setCurrentTimeMillis(9000)

            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif",
                        uid = UID,
                        statusBarChipIcon = mock(),
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            assertThat(latest).isNotNull()
            assertThat(latest!!.isAppVisible).isTrue()
            assertThat(latest!!.lastAppVisibleTime).isEqualTo(9000)
        }

    @Test
    fun notificationChip_appNotVisibleOnCreation_emitsIsAppVisibleFalseWithNoTime() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = false
            fakeSystemClock.setCurrentTimeMillis(9000)

            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif",
                        uid = UID,
                        statusBarChipIcon = mock(),
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            assertThat(latest).isNotNull()
            assertThat(latest!!.isAppVisible).isFalse()
            assertThat(latest!!.lastAppVisibleTime).isNull()
        }

    @Test
    fun notificationChip_updatesWhenAppIsVisible() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = false
            fakeSystemClock.setCurrentTimeMillis(9000)

            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif",
                        uid = UID,
                        statusBarChipIcon = mock(),
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 1,
                )

            val latest by collectLastValue(underTest.notificationChip)

            activityManagerRepository.fake.setIsAppVisible(UID, isAppVisible = false)
            assertThat(latest!!.isAppVisible).isFalse()
            assertThat(latest!!.lastAppVisibleTime).isNull()

            fakeSystemClock.setCurrentTimeMillis(11000)
            activityManagerRepository.fake.setIsAppVisible(UID, isAppVisible = true)
            assertThat(latest!!.isAppVisible).isTrue()
            assertThat(latest!!.lastAppVisibleTime).isEqualTo(11000)

            fakeSystemClock.setCurrentTimeMillis(13000)
            activityManagerRepository.fake.setIsAppVisible(UID, isAppVisible = false)
            assertThat(latest!!.isAppVisible).isFalse()
            assertThat(latest!!.lastAppVisibleTime).isEqualTo(11000)

            fakeSystemClock.setCurrentTimeMillis(15000)
            activityManagerRepository.fake.setIsAppVisible(UID, isAppVisible = true)
            assertThat(latest!!.isAppVisible).isTrue()
            assertThat(latest!!.lastAppVisibleTime).isEqualTo(15000)
        }

    @Test
    fun notificationChip_updatedUid_newUidIsIgnoredButOtherDataNotIgnored() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = false

            val originalUid = 100
            val newUid = 101

            val underTest =
                factory.create(
                    activeNotificationModel(
                        key = "notif",
                        uid = originalUid,
                        statusBarChipIcon = mock(),
                        promotedContent = PROMOTED_CONTENT,
                    ),
                    creationTime = 1,
                )
            val latest by collectLastValue(underTest.notificationChip)
            assertThat(latest).isNotNull()
            assertThat(latest!!.isAppVisible).isFalse()

            // WHEN the notif gets a new UID that starts as visible
            activityManagerRepository.fake.startingIsAppVisibleValue = true
            val newPromotedContentBuilder =
                PromotedNotificationContentBuilder("notif").applyToShared {
                    this.shortCriticalText = "Arrived"
                }
            val newPromotedContent = newPromotedContentBuilder.build()
            underTest.setNotification(
                activeNotificationModel(
                    key = "notif",
                    uid = newUid,
                    statusBarChipIcon = mock(),
                    promotedContent = newPromotedContent,
                )
            )

            // THEN we do update other fields like promoted content
            assertThat(latest!!.promotedContent).isEqualTo(newPromotedContent)

            // THEN we don't fetch the app visibility state for the new UID
            assertThat(latest!!.isAppVisible).isFalse()

            // AND don't listen to updates for the new UID
            activityManagerRepository.fake.setIsAppVisible(newUid, isAppVisible = false)
            activityManagerRepository.fake.setIsAppVisible(newUid, isAppVisible = true)
            assertThat(latest!!.isAppVisible).isFalse()

            // AND we still use updates from the old UID
            // TODO(b/364653005): This particular behavior isn't great, can we do better?
            activityManagerRepository.fake.setIsAppVisible(originalUid, isAppVisible = true)
            assertThat(latest!!.isAppVisible).isTrue()
        }

    companion object {
        private const val UID = 885
        private val PROMOTED_CONTENT = PromotedNotificationContentBuilder("notif1").build()
    }
}
