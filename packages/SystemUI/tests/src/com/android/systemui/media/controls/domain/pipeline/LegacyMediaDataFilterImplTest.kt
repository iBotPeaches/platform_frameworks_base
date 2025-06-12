/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.systemui.media.controls.MediaTestUtils
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.ui.controller.MediaPlayerData
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
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
private const val APP_UID = 99

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class LegacyMediaDataFilterImplTest : SysuiTestCase() {

    @Mock private lateinit var listener: MediaDataManager.Listener
    @Mock private lateinit var userTracker: UserTracker
    @Mock private lateinit var mediaDataManager: MediaDataManager
    @Mock private lateinit var lockscreenUserManager: NotificationLockscreenUserManager
    @Mock private lateinit var executor: Executor

    private lateinit var mediaDataFilter: LegacyMediaDataFilterImpl
    private lateinit var dataMain: MediaData
    private lateinit var dataGuest: MediaData
    private lateinit var dataPrivateProfile: MediaData
    private val clock = FakeSystemClock()

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        MediaPlayerData.clear()
        mediaDataFilter =
            LegacyMediaDataFilterImpl(userTracker, lockscreenUserManager, executor, clock)
        mediaDataFilter.mediaDataManager = mediaDataManager
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
        dataGuest = dataMain.copy(userId = USER_GUEST)
        dataPrivateProfile = dataMain.copy(userId = PRIVATE_PROFILE)
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
    fun testOnDataLoadedForCurrentUser_callsListener() {
        // GIVEN a media for main user
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)

        // THEN we should tell the listener
        verify(listener)
            .onMediaDataLoaded(eq(KEY), eq(null), eq(dataMain), eq(true), eq(0), eq(false))
    }

    @Test
    fun testOnDataLoadedForGuest_doesNotCallListener() {
        // GIVEN a media for guest user
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataGuest)

        // THEN we should NOT tell the listener
        verify(listener, never())
            .onMediaDataLoaded(any(), any(), any(), anyBoolean(), anyInt(), anyBoolean())
    }

    @Test
    fun testOnRemovedForCurrent_callsListener() {
        // GIVEN a media was removed for main user
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)
        mediaDataFilter.onMediaDataRemoved(KEY, false)

        // THEN we should tell the listener
        verify(listener).onMediaDataRemoved(eq(KEY), eq(false))
    }

    @Test
    fun testOnRemovedForGuest_doesNotCallListener() {
        // GIVEN a media was removed for guest user
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataGuest)
        mediaDataFilter.onMediaDataRemoved(KEY, false)

        // THEN we should NOT tell the listener
        verify(listener, never()).onMediaDataRemoved(eq(KEY), anyBoolean())
    }

    @Test
    fun testOnUserSwitched_removesOldUserControls() {
        // GIVEN that we have a media loaded for main user
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)

        // and we switch to guest user
        setUser(USER_GUEST)

        // THEN we should remove the main user's media
        verify(listener).onMediaDataRemoved(eq(KEY), eq(false))
    }

    @Test
    fun testOnUserSwitched_addsNewUserControls() {
        // GIVEN that we had some media for both users
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)
        mediaDataFilter.onMediaDataLoaded(KEY_ALT, null, dataGuest)
        reset(listener)

        // and we switch to guest user
        setUser(USER_GUEST)

        // THEN we should add back the guest user media
        verify(listener)
            .onMediaDataLoaded(eq(KEY_ALT), eq(null), eq(dataGuest), eq(true), eq(0), eq(false))

        // but not the main user's
        verify(listener, never())
            .onMediaDataLoaded(eq(KEY), any(), eq(dataMain), anyBoolean(), anyInt(), anyBoolean())
    }

    @Test
    fun testOnProfileChanged_profileUnavailable_loadControls() {
        // GIVEN that we had some media for both profiles
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)
        mediaDataFilter.onMediaDataLoaded(KEY_ALT, null, dataPrivateProfile)
        reset(listener)

        // and we change profile status
        setPrivateProfileUnavailable()

        // THEN we should add the private profile media
        verify(listener).onMediaDataRemoved(eq(KEY_ALT), eq(false))
    }

    @Test
    fun hasAnyMedia_noMediaSet_returnsFalse() {
        assertThat(mediaDataFilter.hasAnyMedia()).isFalse()
    }

    @Test
    fun hasAnyMedia_mediaSet_returnsTrue() {
        mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = dataMain)

        assertThat(mediaDataFilter.hasAnyMedia()).isTrue()
    }

    @Test
    fun hasActiveMedia_noMediaSet_returnsFalse() {
        assertThat(mediaDataFilter.hasActiveMedia()).isFalse()
    }

    @Test
    fun hasActiveMedia_inactiveMediaSet_returnsFalse() {
        val data = dataMain.copy(active = false)
        mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = data)

        assertThat(mediaDataFilter.hasActiveMedia()).isFalse()
    }

    @Test
    fun hasActiveMedia_activeMediaSet_returnsTrue() {
        val data = dataMain.copy(active = true)
        mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = data)

        assertThat(mediaDataFilter.hasActiveMedia()).isTrue()
    }

    @Test
    fun testHasAnyMedia_onlyCurrentUser() {
        assertThat(mediaDataFilter.hasAnyMedia()).isFalse()

        mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = dataGuest)
        assertThat(mediaDataFilter.hasAnyMedia()).isFalse()
    }

    @Test
    fun testHasActiveMedia_onlyCurrentUser() {
        assertThat(mediaDataFilter.hasActiveMedia()).isFalse()
        val data = dataGuest.copy(active = true)

        mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = data)
        assertThat(mediaDataFilter.hasActiveMedia()).isFalse()
        assertThat(mediaDataFilter.hasAnyMedia()).isFalse()
    }

    @Test
    fun testOnNotificationRemoved_doesntHaveMedia() {
        mediaDataFilter.onMediaDataLoaded(KEY, oldKey = null, data = dataMain)
        mediaDataFilter.onMediaDataRemoved(KEY, false)
        assertThat(mediaDataFilter.hasAnyMedia()).isFalse()
    }

    @Test
    fun testOnSwipeToDismiss_setsTimedOut() {
        mediaDataFilter.onMediaDataLoaded(KEY, null, dataMain)
        mediaDataFilter.onSwipeToDismiss()

        verify(mediaDataManager).setInactive(eq(KEY), eq(true), eq(true))
    }
}
