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

package com.android.systemui.statusbar.featurepods.media.domain.interactor

import android.graphics.drawable.Drawable
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.parameterizeSceneContainerFlag
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.media.controls.data.repository.mediaFilterRepository
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.media.controls.shared.model.MediaButton
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDataLoadingModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@SmallTest
class MediaControlChipInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val mediaFilterRepository = kosmos.mediaFilterRepository
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.mediaControlChipInteractor }
    @Captor lateinit var listener: ArgumentCaptor<MediaDataManager.Listener>

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return parameterizeSceneContainerFlag()
        }
    }

    @Before
    fun setUp() {
        kosmos.underTest.initialize()
        MockitoAnnotations.initMocks(this)
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    fun mediaControlChipModel_noActiveMedia_null() =
        kosmos.runTest {
            val model by collectLastValue(underTest.mediaControlChipModel)

            assertThat(model).isNull()
        }

    @Test
    fun mediaControlChipModel_activeMedia_notNull() =
        kosmos.runTest {
            val model by collectLastValue(underTest.mediaControlChipModel)

            val userMedia = MediaData(active = true)

            updateMedia(userMedia)

            assertThat(model).isNotNull()
        }

    @Test
    fun mediaControlChipModel_mediaRemoved_null() =
        kosmos.runTest {
            val model by collectLastValue(underTest.mediaControlChipModel)

            val userMedia = MediaData(active = true)

            updateMedia(userMedia)

            assertThat(model).isNotNull()

            removeMedia(userMedia)

            assertThat(model).isNull()
        }

    @Test
    fun mediaControlChipModel_songNameChanged_emitsUpdatedModel() =
        kosmos.runTest {
            val model by collectLastValue(underTest.mediaControlChipModel)

            val initialSongName = "Initial Song"
            val newSongName = "New Song"
            val userMedia = MediaData(active = true, song = initialSongName)

            updateMedia(userMedia)

            assertThat(model).isNotNull()
            assertThat(model?.songName).isEqualTo(initialSongName)

            val updatedUserMedia = userMedia.copy(song = newSongName)
            updateMedia(updatedUserMedia)

            assertThat(model?.songName).isEqualTo(newSongName)
        }

    @Test
    fun mediaControlChipModel_playPauseActionChanges_emitsUpdatedModel() =
        kosmos.runTest {
            val model by collectLastValue(underTest.mediaControlChipModel)

            val mockDrawable = mock<Drawable>()

            val initialAction =
                MediaAction(
                    icon = mockDrawable,
                    action = {},
                    contentDescription = "Initial Action",
                    background = mockDrawable,
                )
            val mediaButton = MediaButton(playOrPause = initialAction)
            val userMedia = MediaData(active = true, semanticActions = mediaButton)
            updateMedia(userMedia)

            assertThat(model).isNotNull()
            assertThat(model?.playOrPause).isEqualTo(initialAction)

            val newAction =
                MediaAction(
                    icon = mockDrawable,
                    action = {},
                    contentDescription = "New Action",
                    background = mockDrawable,
                )
            val updatedMediaButton = MediaButton(playOrPause = newAction)
            val updatedUserMedia = userMedia.copy(semanticActions = updatedMediaButton)
            updateMedia(updatedUserMedia)

            assertThat(model?.playOrPause).isEqualTo(newAction)
        }

    @Test
    fun mediaControlChipModel_playPauseActionRemoved_playPauseNull() =
        kosmos.runTest {
            val model by collectLastValue(underTest.mediaControlChipModel)

            val mockDrawable = mock<Drawable>()

            val initialAction =
                MediaAction(
                    icon = mockDrawable,
                    action = {},
                    contentDescription = "Initial Action",
                    background = mockDrawable,
                )
            val mediaButton = MediaButton(playOrPause = initialAction)
            val userMedia = MediaData(active = true, semanticActions = mediaButton)
            updateMedia(userMedia)

            assertThat(model).isNotNull()
            assertThat(model?.playOrPause).isEqualTo(initialAction)

            val updatedUserMedia = userMedia.copy(semanticActions = MediaButton())
            updateMedia(updatedUserMedia)

            assertThat(model?.playOrPause).isNull()
        }

    private fun updateMedia(mediaData: MediaData) {
        if (SceneContainerFlag.isEnabled) {
            val instanceId = mediaData.instanceId
            mediaFilterRepository.addSelectedUserMediaEntry(mediaData)
            mediaFilterRepository.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(instanceId))
        } else {
            kosmos.underTest.updateMediaControlChipModelLegacy(mediaData)
        }
    }

    private fun removeMedia(mediaData: MediaData) {
        if (SceneContainerFlag.isEnabled) {
            val instanceId = mediaData.instanceId
            mediaFilterRepository.removeSelectedUserMediaEntry(instanceId, mediaData)
            mediaFilterRepository.addMediaDataLoadingState(
                MediaDataLoadingModel.Removed(instanceId)
            )
        } else {
            kosmos.underTest.updateMediaControlChipModelLegacy(null)
        }
    }
}
