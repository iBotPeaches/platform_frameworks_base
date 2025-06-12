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

package com.android.systemui.statusbar.featurepods.media.ui.viewmodel

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.parameterizeSceneContainerFlag
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.data.repository.mediaFilterRepository
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDataLoadingModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.featurepods.media.domain.interactor.mediaControlChipInteractor
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.MockitoAnnotations
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class MediaControlChipViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val mediaControlChipInteractor by lazy { kosmos.mediaControlChipInteractor }
    private val Kosmos.underTest by
        Kosmos.Fixture { kosmos.mediaControlChipViewModelFactory.create() }
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
        MockitoAnnotations.initMocks(this)
        mediaControlChipInteractor.initialize()
        kosmos.underTest.activateIn(kosmos.testScope)
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    fun chip_noActiveMedia_IsHidden() =
        kosmos.runTest {
            val chip = underTest.chip

            assertThat(chip).isInstanceOf(PopupChipModel.Hidden::class.java)
        }

    @Test
    fun chip_activeMedia_IsShown() =
        kosmos.runTest {
            val userMedia = MediaData(active = true, song = "test")
            updateMedia(userMedia)

            assertThat(underTest.chip).isInstanceOf(PopupChipModel.Shown::class.java)
        }

    @Test
    fun chip_songNameChanges_chipTextUpdated() =
        kosmos.runTest {
            val initialSongName = "Initial Song"
            val newSongName = "New Song"
            val userMedia = MediaData(active = true, song = initialSongName)
            updateMedia(userMedia)
            assertThat(underTest.chip).isInstanceOf(PopupChipModel.Shown::class.java)
            assertThat((underTest.chip as PopupChipModel.Shown).chipText).isEqualTo(initialSongName)

            val updatedUserMedia = userMedia.copy(song = newSongName)
            updateMedia(updatedUserMedia)

            assertThat((underTest.chip as PopupChipModel.Shown).chipText).isEqualTo(newSongName)
        }

    private fun updateMedia(mediaData: MediaData) {
        if (SceneContainerFlag.isEnabled) {
            val instanceId = mediaData.instanceId
            kosmos.mediaFilterRepository.addSelectedUserMediaEntry(mediaData)
            kosmos.mediaFilterRepository.addMediaDataLoadingState(
                MediaDataLoadingModel.Loaded(instanceId)
            )
        } else {
            mediaControlChipInteractor.updateMediaControlChipModelLegacy(mediaData)
        }
    }
}
