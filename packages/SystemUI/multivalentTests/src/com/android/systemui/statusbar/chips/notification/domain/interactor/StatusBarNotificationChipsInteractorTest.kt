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
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.data.repository.activityManagerRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.chips.notification.shared.StatusBarNotifChips
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.addNotif
import com.android.systemui.statusbar.notification.data.repository.removeNotif
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.notification.shared.CallType
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarNotificationChipsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by
        Kosmos.Fixture { statusBarNotificationChipsInteractor.also { it.start() } }

    @Test
    @DisableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_flagOff_noNotifs() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = mock<StatusBarIconView>(),
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).isEmpty()
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_noNotifs_empty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            setNotifs(emptyList())

            assertThat(latest).isEmpty()
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun allNotificationChips_notifMissingStatusBarChipIconView_cdFlagOff_empty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).isEmpty()
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME, StatusBarConnectedDisplays.FLAG_NAME)
    fun allNotificationChips_notifMissingStatusBarChipIconView_cdFlagOn_notEmpty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).isNotEmpty()
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_onePromotedNotif_statusBarIconViewMatches() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val icon = mock<StatusBarIconView>()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = icon,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif")
            assertThat(latest!![0].statusBarChipIconView).isEqualTo(icon)
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_onlyForPromotedNotifs() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val firstIcon = mock<StatusBarIconView>()
            val secondIcon = mock<StatusBarIconView>()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif1",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("notif1").build(),
                    ),
                    activeNotificationModel(
                        key = "notif2",
                        statusBarChipIcon = secondIcon,
                        promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                    ),
                    activeNotificationModel(
                        key = "notif3",
                        statusBarChipIcon = mock<StatusBarIconView>(),
                        promotedContent = null,
                    ),
                )
            )

            assertThat(latest).hasSize(2)
            assertThat(latest!![0].key).isEqualTo("notif1")
            assertThat(latest!![0].statusBarChipIconView).isEqualTo(firstIcon)
            assertThat(latest!![1].key).isEqualTo("notif2")
            assertThat(latest!![1].statusBarChipIconView).isEqualTo(secondIcon)
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_appVisibilityInfoCorrect() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = false

            val latest by collectLastValue(underTest.allNotificationChips)

            val uid = 433
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        uid = uid,
                        statusBarChipIcon = mock<StatusBarIconView>(),
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = false)
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].isAppVisible).isFalse()

            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = true)
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].isAppVisible).isTrue()

            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = false)
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].isAppVisible).isFalse()
        }

    /** Regression test for b/388521980. */
    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_callNotifIsAlsoPromoted_callNotifExcluded() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "promotedNormal",
                        statusBarChipIcon = mock(),
                        promotedContent =
                            PromotedNotificationContentBuilder("promotedNormal").build(),
                        callType = CallType.None,
                    ),
                    activeNotificationModel(
                        key = "promotedCall",
                        statusBarChipIcon = mock(),
                        promotedContent =
                            PromotedNotificationContentBuilder("promotedCall").build(),
                        callType = CallType.Ongoing,
                    ),
                )
            )

            // Verify the promoted call notification is not included
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("promotedNormal")
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_notifUpdatesGoThrough() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val firstIcon = mock<StatusBarIconView>()
            val secondIcon = mock<StatusBarIconView>()
            val thirdIcon = mock<StatusBarIconView>()

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif")
            assertThat(latest!![0].statusBarChipIconView).isEqualTo(firstIcon)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = secondIcon,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif")
            assertThat(latest!![0].statusBarChipIconView).isEqualTo(secondIcon)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = thirdIcon,
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif")
            assertThat(latest!![0].statusBarChipIconView).isEqualTo(thirdIcon)
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_promotedNotifDisappearsThenReappears() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = mock(),
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif")

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = mock(),
                        promotedContent = null,
                    )
                )
            )
            assertThat(latest).isEmpty()

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = mock(),
                        promotedContent = PromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif")
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_sortedByFirstAppearanceTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val firstIcon = mock<StatusBarIconView>()
            val secondIcon = mock<StatusBarIconView>()

            // First, add notif1 at t=1000
            fakeSystemClock.setCurrentTimeMillis(1000)
            val notif1 =
                activeNotificationModel(
                    key = "notif1",
                    statusBarChipIcon = firstIcon,
                    promotedContent = PromotedNotificationContentBuilder("notif1").build(),
                )
            setNotifs(listOf(notif1))
            assertThat(latest!!.map { it.key }).containsExactly("notif1").inOrder()

            // WHEN we add notif2 at t=2000
            fakeSystemClock.advanceTime(1000)
            val notif2 =
                activeNotificationModel(
                    key = "notif2",
                    statusBarChipIcon = secondIcon,
                    promotedContent = PromotedNotificationContentBuilder("notif2").build(),
                )
            setNotifs(listOf(notif1, notif2))

            // THEN notif2 is ranked above notif1 because notif2 appeared later
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()

            // WHEN notif1 and notif2 swap places
            setNotifs(listOf(notif2, notif1))

            // THEN notif2 is still ranked above notif1 to preserve chip ordering
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()

            // WHEN notif1 and notif2 swap places again
            setNotifs(listOf(notif1, notif2))

            // THEN notif2 is still ranked above notif1 to preserve chip ordering
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()

            // WHEN notif1 gets an update
            val notif1NewPromotedContent =
                PromotedNotificationContentBuilder("notif1").applyToShared {
                    this.shortCriticalText = "Arrived"
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif1",
                        statusBarChipIcon = firstIcon,
                        promotedContent = notif1NewPromotedContent.build(),
                    ),
                    notif2,
                )
            )

            // THEN notif2 is still ranked above notif1 to preserve chip ordering
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()

            // WHEN notif1 disappears and then reappears
            fakeSystemClock.advanceTime(1000)
            setNotifs(listOf(notif2))
            assertThat(latest).hasSize(1)

            fakeSystemClock.advanceTime(1000)
            setNotifs(listOf(notif2, notif1))

            // THEN notif1 is now ranked first
            assertThat(latest!!.map { it.key }).containsExactly("notif1", "notif2").inOrder()
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_lastAppVisibleTimeMaintainedAcrossNotifAddsAndRemoves() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val notif1Info = NotifInfo("notif1", mock<StatusBarIconView>(), uid = 100)
            val notif2Info = NotifInfo("notif2", mock<StatusBarIconView>(), uid = 200)

            // Have notif1's app start as showing and then hide later so we get the chip
            activityManagerRepository.fake.startingIsAppVisibleValue = true
            fakeSystemClock.setCurrentTimeMillis(9_000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = notif1Info.key,
                    uid = notif1Info.uid,
                    statusBarChipIcon = notif1Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif1Info.key).build(),
                )
            )
            activityManagerRepository.fake.setIsAppVisible(notif1Info.uid, isAppVisible = false)

            assertThat(latest!![0].key).isEqualTo(notif1Info.key)
            assertThat(latest!![0].lastAppVisibleTime).isEqualTo(9_000)

            // WHEN a new notification is added
            activityManagerRepository.fake.startingIsAppVisibleValue = true
            fakeSystemClock.setCurrentTimeMillis(10_000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = notif2Info.key,
                    uid = notif2Info.uid,
                    statusBarChipIcon = notif2Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif2Info.key).build(),
                )
            )
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = false)

            // THEN the new notification is first
            assertThat(latest!![0].key).isEqualTo(notif2Info.key)
            assertThat(latest!![0].lastAppVisibleTime).isEqualTo(10_000)

            // And THEN the original notification maintains its lastAppVisibleTime
            assertThat(latest!![1].key).isEqualTo(notif1Info.key)
            assertThat(latest!![1].lastAppVisibleTime).isEqualTo(9_000)

            // WHEN notif1 is removed
            fakeSystemClock.setCurrentTimeMillis(11_000)
            activeNotificationListRepository.removeNotif(notif1Info.key)

            // THEN notif2 still has its lastAppVisibleTime
            assertThat(latest!![0].key).isEqualTo(notif2Info.key)
            assertThat(latest!![0].lastAppVisibleTime).isEqualTo(10_000)
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_sortedByLastAppVisibleTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val notif1Info = NotifInfo("notif1", mock<StatusBarIconView>(), uid = 100)
            val notif2Info = NotifInfo("notif2", mock<StatusBarIconView>(), uid = 200)

            activityManagerRepository.fake.startingIsAppVisibleValue = false
            fakeSystemClock.setCurrentTimeMillis(1000)
            val notif1 =
                activeNotificationModel(
                    key = notif1Info.key,
                    uid = notif1Info.uid,
                    statusBarChipIcon = notif1Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif1Info.key).build(),
                )
            val notif2 =
                activeNotificationModel(
                    key = notif2Info.key,
                    uid = notif2Info.uid,
                    statusBarChipIcon = notif2Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif2Info.key).build(),
                )
            setNotifs(listOf(notif1, notif2))
            assertThat(latest!!.map { it.key }).containsExactly("notif1", "notif2").inOrder()

            // WHEN notif2's app becomes visible
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = true)

            // THEN notif2 is ranked above notif1 because it was more recently visible
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()
            assertThat(latest!![0].isAppVisible).isTrue() // notif2
            assertThat(latest!![1].isAppVisible).isFalse() // notif1

            // WHEN notif2's app is no longer visible
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = false)

            // THEN notif2 is still ranked above notif1
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()
            assertThat(latest!![0].isAppVisible).isFalse() // notif2
            assertThat(latest!![1].isAppVisible).isFalse() // notif1

            // WHEN the app associated with notif1 becomes visible then un-visible
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif1Info.uid, isAppVisible = true)
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif1Info.uid, isAppVisible = false)

            // THEN notif1 is now ranked above notif2 because it was more recently visible
            assertThat(latest!!.map { it.key }).containsExactly("notif1", "notif2").inOrder()
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_newNotificationTakesPriorityOverLastAppVisible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val notif1Info = NotifInfo("notif1", mock<StatusBarIconView>(), uid = 100)
            val notif2Info = NotifInfo("notif2", mock<StatusBarIconView>(), uid = 200)
            val notif3Info = NotifInfo("notif3", mock<StatusBarIconView>(), uid = 300)

            activityManagerRepository.fake.startingIsAppVisibleValue = false
            fakeSystemClock.setCurrentTimeMillis(1000)
            val notif1 =
                activeNotificationModel(
                    key = notif1Info.key,
                    uid = notif1Info.uid,
                    statusBarChipIcon = notif1Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif1Info.key).build(),
                )
            val notif2 =
                activeNotificationModel(
                    key = notif2Info.key,
                    uid = notif2Info.uid,
                    statusBarChipIcon = notif2Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif2Info.key).build(),
                )
            setNotifs(listOf(notif1, notif2))
            assertThat(latest!!.map { it.key }).containsExactly("notif1", "notif2").inOrder()

            // WHEN notif2's app becomes visible then not visible
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = true)
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = false)

            // THEN notif2 is ranked above notif1 because it was more recently visible
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()

            // WHEN a new notif3 appears
            fakeSystemClock.advanceTime(1000)
            val notif3 =
                activeNotificationModel(
                    key = notif3Info.key,
                    uid = notif3Info.uid,
                    statusBarChipIcon = notif3Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif3Info.key).build(),
                )
            setNotifs(listOf(notif1, notif2, notif3))

            // THEN notif3 is ranked above everything else
            // AND notif2 is still before notif1 because it was more recently visible
            assertThat(latest!!.map { it.key })
                .containsExactly("notif3", "notif2", "notif1")
                .inOrder()
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_fullSort() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val notif1Info = NotifInfo("notif1", mock<StatusBarIconView>(), uid = 100)
            val notif2Info = NotifInfo("notif2", mock<StatusBarIconView>(), uid = 200)
            val notif3Info = NotifInfo("notif3", mock<StatusBarIconView>(), uid = 300)

            // First, add notif1 at t=1000
            activityManagerRepository.fake.startingIsAppVisibleValue = false
            fakeSystemClock.setCurrentTimeMillis(1000)
            val notif1 =
                activeNotificationModel(
                    key = notif1Info.key,
                    uid = notif1Info.uid,
                    statusBarChipIcon = notif1Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif1Info.key).build(),
                )
            setNotifs(listOf(notif1))

            // WHEN we add notif2 at t=2000
            fakeSystemClock.advanceTime(1000)
            val notif2 =
                activeNotificationModel(
                    key = notif2Info.key,
                    uid = notif2Info.uid,
                    statusBarChipIcon = notif2Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif2Info.key).build(),
                )
            setNotifs(listOf(notif1, notif2))

            // THEN notif2 is ranked above notif1 because notif2 appeared later
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()

            // WHEN notif2's app becomes visible then un-visible
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = true)
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = false)

            // THEN notif2 is ranked above notif1 because it was more recently visible
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()

            // WHEN the app associated with notif1 becomes visible then un-visible
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif1Info.uid, isAppVisible = true)
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif1Info.uid, isAppVisible = false)

            // THEN notif1 is ranked above notif2 because it was more recently visible
            assertThat(latest!!.map { it.key }).containsExactly("notif1", "notif2").inOrder()

            // WHEN notif2 gets an update
            val notif2NewPromotedContent =
                PromotedNotificationContentBuilder("notif2").applyToShared {
                    this.shortCriticalText = "Arrived"
                }
            setNotifs(
                listOf(
                    notif1,
                    activeNotificationModel(
                        key = notif2Info.key,
                        uid = notif2Info.uid,
                        statusBarChipIcon = notif2Info.icon,
                        promotedContent = notif2NewPromotedContent.build(),
                    ),
                )
            )

            // THEN notif1 is still ranked above notif2 to preserve chip ordering
            assertThat(latest!!.map { it.key }).containsExactly("notif1", "notif2").inOrder()

            // WHEN a new notification appears
            fakeSystemClock.advanceTime(1000)
            val notif3 =
                activeNotificationModel(
                    key = notif3Info.key,
                    uid = notif3Info.uid,
                    statusBarChipIcon = notif3Info.icon,
                    promotedContent = PromotedNotificationContentBuilder(notif3Info.key).build(),
                )
            setNotifs(listOf(notif1, notif2, notif3))

            // THEN it's ranked first because it's new
            assertThat(latest!!.map { it.key })
                .containsExactly("notif3", "notif1", "notif2")
                .inOrder()

            // WHEN notif2 becomes visible then un-visible again
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = true)
            fakeSystemClock.advanceTime(1000)
            activityManagerRepository.fake.setIsAppVisible(notif2Info.uid, isAppVisible = false)

            // THEN it moves to the front
            assertThat(latest!!.map { it.key })
                .containsExactly("notif2", "notif3", "notif1")
                .inOrder()

            // WHEN notif1 disappears and then reappears
            fakeSystemClock.advanceTime(1000)
            setNotifs(listOf(notif2, notif3))
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif3").inOrder()

            fakeSystemClock.advanceTime(1000)
            setNotifs(listOf(notif2, notif1, notif3))

            // THEN notif1 is now ranked first
            assertThat(latest!!.map { it.key })
                .containsExactly("notif1", "notif2", "notif3")
                .inOrder()
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun allNotificationChips_notifChangesKey() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.allNotificationChips)

            val firstIcon = mock<StatusBarIconView>()
            val secondIcon = mock<StatusBarIconView>()
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif|uid1",
                        statusBarChipIcon = firstIcon,
                        promotedContent = PromotedNotificationContentBuilder("notif|uid1").build(),
                    )
                )
            )
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif|uid1")
            assertThat(latest!![0].statusBarChipIconView).isEqualTo(firstIcon)

            // WHEN a notification changes UID, which is a key change
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif|uid2",
                        statusBarChipIcon = secondIcon,
                        promotedContent = PromotedNotificationContentBuilder("notif|uid2").build(),
                    )
                )
            )

            // THEN we correctly update
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif|uid2")
            assertThat(latest!![0].statusBarChipIconView).isEqualTo(secondIcon)
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun onPromotedNotificationChipTapped_emitsKeys() =
        kosmos.runTest {
            val latest by collectValues(underTest.promotedNotificationChipTapEvent)

            underTest.onPromotedNotificationChipTapped("fakeKey")

            assertThat(latest).hasSize(1)
            assertThat(latest[0]).isEqualTo("fakeKey")

            underTest.onPromotedNotificationChipTapped("fakeKey2")

            assertThat(latest).hasSize(2)
            assertThat(latest[1]).isEqualTo("fakeKey2")
        }

    @Test
    @EnableFlags(StatusBarNotifChips.FLAG_NAME)
    fun onPromotedNotificationChipTapped_sameKeyTwice_emitsTwice() =
        kosmos.runTest {
            val latest by collectValues(underTest.promotedNotificationChipTapEvent)

            underTest.onPromotedNotificationChipTapped("fakeKey")
            underTest.onPromotedNotificationChipTapped("fakeKey")

            assertThat(latest).hasSize(2)
            assertThat(latest[0]).isEqualTo("fakeKey")
            assertThat(latest[1]).isEqualTo("fakeKey")
        }

    private fun Kosmos.setNotifs(notifs: List<ActiveNotificationModel>) {
        activeNotificationListRepository.activeNotifications.value =
            ActiveNotificationsStore.Builder()
                .apply { notifs.forEach { addIndividualNotif(it) } }
                .build()
    }

    private data class NotifInfo(val key: String, val icon: StatusBarIconView, val uid: Int)
}
