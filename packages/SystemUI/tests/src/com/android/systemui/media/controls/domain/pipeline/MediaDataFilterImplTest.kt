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

package com.android.systemui.media.controls.domain.pipeline

import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.media.controls.MediaTestUtils
import com.android.systemui.media.controls.data.repository.MediaFilterRepository
import com.android.systemui.media.controls.data.repository.mediaFilterRepository
import com.android.systemui.media.controls.shared.mockMediaLogger
import com.android.systemui.media.controls.shared.model.MediaCommonModel
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDataLoadingModel
import com.android.systemui.media.controls.ui.controller.MediaPlayerData
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.testKosmos
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

private const val KEY = "TEST_KEY"
private const val KEY_ALT = "TEST_KEY_2"
private const val USER_MAIN = 0
private const val USER_GUEST = 10
private const val PRIVATE_PROFILE = 12
private const val PACKAGE = "PKG"
private val INSTANCE_ID = InstanceId.fakeInstanceId(123)!!
private val INSTANCE_ID_GUEST = InstanceId.fakeInstanceId(321)!!
private const val APP_UID = 99

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class MediaDataFilterImplTest : SysuiTestCase() {
    val kosmos = testKosmos()

    @Mock private lateinit var listener: MediaDataProcessor.Listener
    @Mock private lateinit var userTracker: UserTracker
    @Mock private lateinit var mediaDataProcessor: MediaDataProcessor
    @Mock private lateinit var lockscreenUserManager: NotificationLockscreenUserManager
    @Mock private lateinit var executor: Executor

    private lateinit var mediaDataFilter: MediaDataFilterImpl
    private lateinit var testScope: TestScope
    private lateinit var dataMain: MediaData
    private lateinit var dataGuest: MediaData
    private lateinit var dataPrivateProfile: MediaData
    private val clock = FakeSystemClock()
    private val repository: MediaFilterRepository = with(kosmos) { mediaFilterRepository }
    private val mediaLogger = kosmos.mockMediaLogger

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        MediaPlayerData.clear()
        testScope = TestScope()
        mediaDataFilter =
            MediaDataFilterImpl(
                userTracker,
                lockscreenUserManager,
                executor,
                clock,
                repository,
                mediaLogger,
            )
        mediaDataFilter.mediaDataProcessor = mediaDataProcessor
        mediaDataFilter.addListener(listener)

        // Start all tests as main user
        setUser(USER_MAIN)

        // Set up test media data
        dataMain =
            MediaTestUtils.emptyMediaData.copy(
                userId = USER_MAIN,
                packageName = PACKAGE,
                instanceId = INSTANCE_ID,
                appUid = APP_UID,
            )
        dataGuest = dataMain.copy(userId = USER_GUEST, instanceId = INSTANCE_ID_GUEST)
        dataPrivateProfile = dataMain.copy(userId = PRIVATE_PROFILE, instanceId = INSTANCE_ID_GUEST)
    }

    private fun setUser(id: Int) {
        whenever(lockscreenUserManager.isCurrentProfile(anyInt())).thenReturn(false)
        whenever(lockscreenUserManager.isProfileAvailable(anyInt())).thenReturn(false)
        whenever(lockscreenUserManager.isCurrentProfile(eq(id))).thenReturn(true)
        whenever(lockscreenUserManager.isProfileAvailable(eq(id))).thenReturn(true)
        whenever(lockscreenUserManager.isProfileAvailable(eq(PRIVATE_PROFILE))).thenReturn(true)
        mediaDataFilter.handleUserSwitched()
    }

    private fun setPrivateProfileUnavailable() {
        whenever(lockscreenUserManager.isCurrentProfile(anyInt())).thenReturn(false)
        whenever(lockscreenUserManager.isCurrentProfile(eq(USER_MAIN))).thenReturn(true)
        whenever(lockscreenUserManager.isCurrentProfile(eq(PRIVATE_PROFILE))).thenReturn(true)
        whenever(lockscreenUserManager.isProfileAvailable(eq(PRIVATE_PROFILE))).thenReturn(false)
        mediaDataFilter.handleProfileChanged()
    }

    @Test
    fun onDataLoadedForCurrentUser_updatesLoadedStates() =
        testScope.runTest {
            val currentMedia by collectLastValue(repository.currentMedia)
            val mediaCommonModel =
                MediaCommonModel(MediaDataLoadingModel.Loaded(dataMain.instanceId))

            mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)

            verify(listener)
                .onMediaDataLoaded(eq(KEY), eq(null), eq(dataMain), eq(true), eq(0), eq(false))
            verify(mediaLogger)
                .logMediaLoaded(eq(dataMain.instanceId), eq(dataMain.active), anyString())
            assertThat(currentMedia).containsExactly(mediaCommonModel)
        }

    @Test
    fun onDataLoadedForGuest_doesNotUpdateLoadedStates() =
        testScope.runTest {
            val currentMedia by collectLastValue(repository.currentMedia)
            val mediaCommonModel =
                MediaCommonModel(MediaDataLoadingModel.Loaded(dataMain.instanceId))

            mediaDataFilter.onMediaDataLoaded(KEY, null, dataGuest)

            verify(listener, never())
                .onMediaDataLoaded(any(), any(), any(), anyBoolean(), anyInt(), anyBoolean())
            verify(mediaLogger, never()).logMediaLoaded(any(), anyBoolean(), anyString())
            assertThat(currentMedia).doesNotContain(mediaCommonModel)
        }

    @Test
    fun onRemovedForCurrent_updatesLoadedStates() =
        testScope.runTest {
            val currentMedia by collectLastValue(repository.currentMedia)
            val mediaCommonModel =
                MediaCommonModel(MediaDataLoadingModel.Loaded(dataMain.instanceId))

            // GIVEN a media was removed for main user
            mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)

            verify(mediaLogger)
                .logMediaLoaded(eq(dataMain.instanceId), eq(dataMain.active), anyString())
            assertThat(currentMedia).containsExactly(mediaCommonModel)

            mediaDataFilter.onMediaDataRemoved(KEY, false)

            verify(listener).onMediaDataRemoved(eq(KEY), eq(false))
            verify(mediaLogger).logMediaRemoved(eq(dataMain.instanceId), anyString())
            assertThat(currentMedia).doesNotContain(mediaCommonModel)
        }

    @Test
    fun onRemovedForGuest_doesNotUpdateLoadedStates() =
        testScope.runTest {
            val currentMedia by collectLastValue(repository.currentMedia)

            // GIVEN a media was removed for guest user
            mediaDataFilter.onMediaDataLoaded(KEY, null, dataGuest)
            mediaDataFilter.onMediaDataRemoved(KEY, false)

            verify(listener, never()).onMediaDataRemoved(eq(KEY), eq(false))
            verify(mediaLogger, never()).logMediaRemoved(eq(dataGuest.instanceId), anyString())
            assertThat(currentMedia).isEmpty()
        }

    @Test
    fun onUserSwitched_removesOldUserControls() =
        testScope.runTest {
            val currentMedia by collectLastValue(repository.currentMedia)
            val mediaLoaded = MediaDataLoadingModel.Loaded(dataMain.instanceId)

            // GIVEN that we have a media loaded for main user
            mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)

            verify(mediaLogger)
                .logMediaLoaded(eq(dataMain.instanceId), eq(dataMain.active), anyString())
            assertThat(currentMedia).containsExactly(MediaCommonModel(mediaLoaded))

            // and we switch to guest user
            setUser(USER_GUEST)

            // THEN we should remove the main user's media
            verify(listener).onMediaDataRemoved(eq(KEY), eq(false))
            verify(mediaLogger).logMediaRemoved(eq(dataMain.instanceId), anyString())
            assertThat(currentMedia).isEmpty()
        }

    @Test
    fun onUserSwitched_addsNewUserControls() =
        testScope.runTest {
            val currentMedia by collectLastValue(repository.currentMedia)
            val guestLoadedStatesModel = MediaDataLoadingModel.Loaded(dataGuest.instanceId)
            val mainLoadedStatesModel = MediaDataLoadingModel.Loaded(dataMain.instanceId)

            // GIVEN that we had some media for both users
            mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)
            mediaDataFilter.onMediaDataLoaded(KEY_ALT, null, dataGuest)

            // and we switch to guest user
            setUser(USER_GUEST)

            // THEN we should add back the guest user media
            verify(listener)
                .onMediaDataLoaded(eq(KEY_ALT), eq(null), eq(dataGuest), eq(true), eq(0), eq(false))
            verify(mediaLogger)
                .logMediaLoaded(eq(dataGuest.instanceId), eq(dataGuest.active), anyString())

            reset(mediaLogger)

            // but not the main user's
            verify(listener, never())
                .onMediaDataLoaded(
                    eq(KEY),
                    any(),
                    eq(dataMain),
                    anyBoolean(),
                    anyInt(),
                    anyBoolean(),
                )
            verify(mediaLogger, never())
                .logMediaLoaded(eq(dataMain.instanceId), anyBoolean(), anyString())
            assertThat(currentMedia).containsExactly(MediaCommonModel(guestLoadedStatesModel))
            assertThat(currentMedia).doesNotContain(MediaCommonModel(mainLoadedStatesModel))
        }

    @Test
    fun onProfileChanged_profileUnavailable_updateStates() =
        testScope.runTest {
            val currentMedia by collectLastValue(repository.currentMedia)

            // GIVEN that we had some media for both profiles
            mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)
            mediaDataFilter.onMediaDataLoaded(KEY_ALT, null, dataPrivateProfile)

            // and we change profile status
            setPrivateProfileUnavailable()

            val mediaLoadedStatesModel = MediaDataLoadingModel.Loaded(dataMain.instanceId)
            // THEN we should remove the private profile media
            verify(listener).onMediaDataRemoved(eq(KEY_ALT), eq(false))
            verify(mediaLogger).logMediaRemoved(eq(dataGuest.instanceId), anyString())
            assertThat(currentMedia).containsExactly(MediaCommonModel(mediaLoadedStatesModel))
        }

    @Test
    fun hasAnyMedia_mediaSet_returnsTrue() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(repository.selectedUserEntries)
            mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = dataMain)

            assertThat(hasAnyMedia(selectedUserEntries)).isTrue()
        }

    @Test
    fun hasActiveMedia_inactiveMediaSet_returnsFalse() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(repository.selectedUserEntries)

            val data = dataMain.copy(active = false)
            mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = data)

            assertThat(hasActiveMedia(selectedUserEntries)).isFalse()
        }

    @Test
    fun hasActiveMedia_activeMediaSet_returnsTrue() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(repository.selectedUserEntries)
            val data = dataMain.copy(active = true)
            mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = data)

            assertThat(hasActiveMedia(selectedUserEntries)).isTrue()
        }

    @Test
    fun hasAnyMedia_onlyCurrentUser() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(repository.selectedUserEntries)
            assertThat(hasAnyMedia(selectedUserEntries)).isFalse()

            mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = dataGuest)
            assertThat(hasAnyMedia(selectedUserEntries)).isFalse()
        }

    @Test
    fun hasActiveMedia_onlyCurrentUser() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(repository.selectedUserEntries)
            assertThat(hasActiveMedia(selectedUserEntries)).isFalse()
            val data = dataGuest.copy(active = true)

            mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = data)
            assertThat(hasActiveMedia(selectedUserEntries)).isFalse()
            assertThat(hasAnyMedia(selectedUserEntries)).isFalse()
        }

    @Test
    fun onNotificationRemoved_doesNotHaveMedia() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(repository.selectedUserEntries)

            mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = dataMain)
            mediaDataFilter.onMediaDataRemoved(KEY, false)
            assertThat(hasAnyMedia(selectedUserEntries)).isFalse()
        }

    @Test
    fun onSwipeToDismiss_setsTimedOut() {
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)
        mediaDataFilter.onSwipeToDismiss()

        verify(mediaDataProcessor).setInactive(eq(KEY), eq(true), eq(true))
    }

    private fun hasActiveMedia(entries: Map<InstanceId, MediaData>?): Boolean {
        return entries?.any { it.value.active } ?: false
    }

    private fun hasAnyMedia(entries: Map<InstanceId, MediaData>?): Boolean {
        return entries?.isNotEmpty() ?: false
    }
}
