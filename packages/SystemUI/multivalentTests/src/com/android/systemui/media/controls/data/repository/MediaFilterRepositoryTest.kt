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

package com.android.systemui.media.controls.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.media.controls.shared.model.MediaCommonModel
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDataLoadingModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MediaFilterRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private val underTest: MediaFilterRepository = with(kosmos) { mediaFilterRepository }

    @Test
    fun addSelectedUserMediaEntry_activeThenInactivate() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(underTest.selectedUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia = MediaData().copy(active = true, instanceId = instanceId)

            underTest.addSelectedUserMediaEntry(userMedia)

            assertThat(selectedUserEntries?.get(instanceId)).isEqualTo(userMedia)
            assertThat(underTest.hasActiveMedia()).isTrue()
            assertThat(underTest.hasAnyMedia()).isTrue()

            underTest.addSelectedUserMediaEntry(userMedia.copy(active = false))

            assertThat(selectedUserEntries?.get(instanceId)).isNotEqualTo(userMedia)
            assertThat(selectedUserEntries?.get(instanceId)?.active).isFalse()
            assertThat(underTest.hasActiveMedia()).isFalse()
            assertThat(underTest.hasAnyMedia()).isTrue()
        }

    @Test
    fun addSelectedUserMediaEntry_thenRemove_returnsBoolean() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(underTest.selectedUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia = MediaData().copy(instanceId = instanceId)

            underTest.addSelectedUserMediaEntry(userMedia)

            assertThat(selectedUserEntries?.get(instanceId)).isEqualTo(userMedia)
            assertThat(underTest.hasActiveMedia()).isTrue()
            assertThat(underTest.hasAnyMedia()).isTrue()

            assertThat(underTest.removeSelectedUserMediaEntry(instanceId, userMedia)).isTrue()
            assertThat(underTest.hasActiveMedia()).isFalse()
            assertThat(underTest.hasAnyMedia()).isFalse()
        }

    @Test
    fun addSelectedUserMediaEntry_thenRemove_returnsValue() =
        testScope.runTest {
            val selectedUserEntries by collectLastValue(underTest.selectedUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia = MediaData().copy(instanceId = instanceId)

            underTest.addSelectedUserMediaEntry(userMedia)

            assertThat(selectedUserEntries?.get(instanceId)).isEqualTo(userMedia)

            assertThat(underTest.removeSelectedUserMediaEntry(instanceId)).isEqualTo(userMedia)
        }

    @Test
    fun addAllUserMediaEntry_activeThenInactivate() =
        testScope.runTest {
            val allUserEntries by collectLastValue(underTest.allUserEntries)

            val userMedia = MediaData().copy(active = true)

            underTest.addMediaEntry(KEY, userMedia)

            assertThat(allUserEntries?.get(KEY)).isEqualTo(userMedia)

            underTest.addMediaEntry(KEY, userMedia.copy(active = false))

            assertThat(allUserEntries?.get(KEY)).isNotEqualTo(userMedia)
            assertThat(allUserEntries?.get(KEY)?.active).isFalse()
        }

    @Test
    fun addAllUserMediaEntry_thenRemove_returnsValue() =
        testScope.runTest {
            val allUserEntries by collectLastValue(underTest.allUserEntries)

            val userMedia = MediaData()

            underTest.addMediaEntry(KEY, userMedia)

            assertThat(allUserEntries?.get(KEY)).isEqualTo(userMedia)

            assertThat(underTest.removeMediaEntry(KEY)).isEqualTo(userMedia)
        }

    @Test
    fun addMediaControlPlayingThenRemote() =
        testScope.runTest {
            val currentMedia by collectLastValue(underTest.currentMedia)
            val playingInstanceId = InstanceId.fakeInstanceId(123)
            val remoteInstanceId = InstanceId.fakeInstanceId(321)
            val playingData = createMediaData("app1", true, LOCAL, false, playingInstanceId)
            val remoteData = createMediaData("app2", true, REMOTE, false, remoteInstanceId)

            underTest.addSelectedUserMediaEntry(playingData)
            underTest.addMediaDataLoadingState(
                MediaDataLoadingModel.Loaded(playingInstanceId),
                false,
            )

            underTest.addSelectedUserMediaEntry(remoteData)
            underTest.addMediaDataLoadingState(
                MediaDataLoadingModel.Loaded(remoteInstanceId),
                false,
            )

            assertThat(currentMedia?.size).isEqualTo(2)
            assertThat(currentMedia)
                .containsExactly(
                    MediaCommonModel(MediaDataLoadingModel.Loaded(playingInstanceId)),
                    MediaCommonModel(MediaDataLoadingModel.Loaded(remoteInstanceId)),
                )
                .inOrder()
        }

    @Test
    fun switchMediaControlsPlaying() =
        testScope.runTest {
            val currentMedia by collectLastValue(underTest.currentMedia)
            val playingInstanceId1 = InstanceId.fakeInstanceId(123)
            val playingInstanceId2 = InstanceId.fakeInstanceId(321)
            var playingData1 = createMediaData("app1", true, LOCAL, false, playingInstanceId1)
            var playingData2 = createMediaData("app2", false, LOCAL, false, playingInstanceId2)

            underTest.addSelectedUserMediaEntry(playingData1)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(playingInstanceId1))
            underTest.addSelectedUserMediaEntry(playingData2)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(playingInstanceId2))

            assertThat(currentMedia?.size).isEqualTo(2)
            assertThat(currentMedia)
                .containsExactly(
                    MediaCommonModel(MediaDataLoadingModel.Loaded(playingInstanceId1)),
                    MediaCommonModel(MediaDataLoadingModel.Loaded(playingInstanceId2)),
                )
                .inOrder()

            playingData1 = createMediaData("app1", false, LOCAL, false, playingInstanceId1)
            playingData2 = createMediaData("app2", true, LOCAL, false, playingInstanceId2)

            underTest.addSelectedUserMediaEntry(playingData1)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(playingInstanceId1))
            underTest.addSelectedUserMediaEntry(playingData2)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(playingInstanceId2))

            assertThat(currentMedia?.size).isEqualTo(2)
            assertThat(currentMedia)
                .containsExactly(
                    MediaCommonModel(MediaDataLoadingModel.Loaded(playingInstanceId1)),
                    MediaCommonModel(MediaDataLoadingModel.Loaded(playingInstanceId2)),
                )
                .inOrder()

            underTest.setOrderedMedia()

            assertThat(currentMedia?.size).isEqualTo(2)
            assertThat(currentMedia)
                .containsExactly(
                    MediaCommonModel(MediaDataLoadingModel.Loaded(playingInstanceId2)),
                    MediaCommonModel(MediaDataLoadingModel.Loaded(playingInstanceId1)),
                )
                .inOrder()
        }

    @Test
    fun fullOrderTest() =
        testScope.runTest {
            val currentMedia by collectLastValue(underTest.currentMedia)
            val instanceId1 = InstanceId.fakeInstanceId(123)
            val instanceId2 = InstanceId.fakeInstanceId(456)
            val instanceId3 = InstanceId.fakeInstanceId(321)
            val instanceId4 = InstanceId.fakeInstanceId(654)
            val instanceId5 = InstanceId.fakeInstanceId(124)
            val playingAndLocalData = createMediaData("app1", true, LOCAL, false, instanceId1)
            val playingAndRemoteData = createMediaData("app2", true, REMOTE, false, instanceId2)
            val stoppedAndLocalData = createMediaData("app3", false, LOCAL, false, instanceId3)
            val stoppedAndRemoteData = createMediaData("app4", false, REMOTE, false, instanceId4)
            val canResumeData = createMediaData("app5", false, LOCAL, true, instanceId5)

            underTest.addSelectedUserMediaEntry(stoppedAndLocalData)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(instanceId3))

            underTest.addSelectedUserMediaEntry(stoppedAndRemoteData)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(instanceId4))

            underTest.addSelectedUserMediaEntry(canResumeData)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(instanceId5))

            underTest.addSelectedUserMediaEntry(playingAndLocalData)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(instanceId1))

            underTest.addSelectedUserMediaEntry(playingAndRemoteData)
            underTest.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(instanceId2))

            underTest.setOrderedMedia()

            assertThat(currentMedia?.size).isEqualTo(5)
            assertThat(currentMedia)
                .containsExactly(
                    MediaCommonModel(MediaDataLoadingModel.Loaded(instanceId1)),
                    MediaCommonModel(MediaDataLoadingModel.Loaded(instanceId2)),
                    MediaCommonModel(MediaDataLoadingModel.Loaded(instanceId4)),
                    MediaCommonModel(MediaDataLoadingModel.Loaded(instanceId3)),
                    MediaCommonModel(MediaDataLoadingModel.Loaded(instanceId5)),
                )
                .inOrder()
        }

    @Test
    fun hasAnyMedia_noMediaSet_returnsFalse() =
        testScope.runTest { assertThat(underTest.hasAnyMedia()).isFalse() }

    @Test
    fun hasActiveMedia_noMediaSet_returnsFalse() =
        testScope.runTest { assertThat(underTest.hasActiveMedia()).isFalse() }

    private fun createMediaData(
        app: String,
        playing: Boolean,
        playbackLocation: Int,
        isResume: Boolean,
        instanceId: InstanceId,
    ): MediaData {
        return MediaData(
            playbackLocation = playbackLocation,
            resumption = isResume,
            notificationKey = "key: $app",
            isPlaying = playing,
            instanceId = instanceId,
        )
    }

    companion object {
        private const val LOCAL = MediaData.PLAYBACK_LOCAL
        private const val REMOTE = MediaData.PLAYBACK_CAST_LOCAL
        private const val KEY = "KEY"
    }
}
