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

package com.android.systemui.media.controls.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.media.controls.data.repository.MediaFilterRepository
import com.android.systemui.media.controls.data.repository.mediaFilterRepository
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.controls.domain.pipeline.interactor.mediaCarouselInteractor
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDataLoadingModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MediaCarouselInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private val mediaFilterRepository: MediaFilterRepository =
        with(kosmos) { mediaFilterRepository }

    private val underTest: MediaCarouselInteractor = kosmos.mediaCarouselInteractor

    @Before
    fun setUp() {
        underTest.start()
    }

    @Test
    fun addUserMediaEntry_activeThenInactivate() =
        testScope.runTest {
            val hasActiveMediaOrRecommendation by
                collectLastValue(underTest.hasActiveMediaOrRecommendation)

            val userMedia = MediaData(active = true)

            mediaFilterRepository.addSelectedUserMediaEntry(userMedia)

            assertThat(hasActiveMediaOrRecommendation).isTrue()
            assertThat(underTest.hasActiveMedia()).isTrue()
            assertThat(underTest.hasAnyMedia()).isTrue()

            mediaFilterRepository.addSelectedUserMediaEntry(userMedia.copy(active = false))

            assertThat(hasActiveMediaOrRecommendation).isFalse()
            assertThat(underTest.hasActiveMedia()).isFalse()
            assertThat(underTest.hasAnyMedia()).isTrue()
        }

    @Test
    fun addInactiveUserMediaEntry_thenRemove() =
        testScope.runTest {
            val hasActiveMediaOrRecommendation by
                collectLastValue(underTest.hasActiveMediaOrRecommendation)

            val userMedia = MediaData(active = false)
            val instanceId = userMedia.instanceId

            mediaFilterRepository.addSelectedUserMediaEntry(userMedia)
            mediaFilterRepository.addMediaDataLoadingState(MediaDataLoadingModel.Loaded(instanceId))

            assertThat(hasActiveMediaOrRecommendation).isFalse()
            assertThat(underTest.hasActiveMedia()).isFalse()
            assertThat(underTest.hasAnyMedia()).isTrue()

            assertThat(mediaFilterRepository.removeSelectedUserMediaEntry(instanceId, userMedia))
                .isTrue()
            mediaFilterRepository.addMediaDataLoadingState(
                MediaDataLoadingModel.Removed(instanceId)
            )

            assertThat(hasActiveMediaOrRecommendation).isFalse()
            assertThat(underTest.hasActiveMedia()).isFalse()
            assertThat(underTest.hasAnyMedia()).isFalse()
        }

    @Test
    fun hasAnyMedia_noMediaSet_returnsFalse() =
        testScope.runTest { assertThat(underTest.hasAnyMedia()).isFalse() }

    @Test
    fun hasAnyMediaOrRecommendation_noMediaSet_returnsFalse() =
        testScope.runTest { assertThat(underTest.hasAnyMediaOrRecommendation.value).isFalse() }

    @Test
    fun hasActiveMedia_noMediaSet_returnsFalse() =
        testScope.runTest { assertThat(underTest.hasActiveMedia()).isFalse() }

    @Test
    fun hasActiveMediaOrRecommendation_nothingSet_returnsFalse() =
        testScope.runTest { assertThat(underTest.hasActiveMediaOrRecommendation.value).isFalse() }
}
