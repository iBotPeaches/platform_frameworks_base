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
 * limitations under the License.
 */

package com.android.systemui.volume.dialog.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.accessibilityRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.data.repository.volumeDialogVisibilityRepository
import com.android.systemui.volume.dialog.domain.interactor.volumeDialogVisibilityInteractor
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VolumeDialogPluginViewModelTest : SysuiTestCase() {

    private val kosmos: Kosmos = testKosmos()

    private val underTest: VolumeDialogPluginViewModel by lazy {
        kosmos.volumeDialogPluginViewModel
    }

    @Before
    fun setUp() =
        with(kosmos) {
            volumeDialogVisibilityRepository.updateVisibility {
                VolumeDialogVisibilityModel.Visible(Events.SHOW_REASON_VOLUME_CHANGED, false, 0)
            }
        }

    @Test
    fun safetyWarningAppears_timeoutReset() =
        kosmos.runTest {
            accessibilityRepository.setRecommendedTimeout(3.seconds)
            val visibility by collectLastValue(volumeDialogVisibilityInteractor.dialogVisibility)
            testScope.advanceTimeBy(2.seconds)
            assertThat(visibility).isInstanceOf(VolumeDialogVisibilityModel.Visible::class.java)

            underTest.onSafetyWarningDialogShown()
            testScope.advanceTimeBy(2.seconds)
            assertThat(visibility).isInstanceOf(VolumeDialogVisibilityModel.Visible::class.java)
        }

    @Test
    fun csdWarningAppears_timeoutReset() =
        kosmos.runTest {
            accessibilityRepository.setRecommendedTimeout(3.seconds)
            val visibility by collectLastValue(volumeDialogVisibilityInteractor.dialogVisibility)
            testScope.advanceTimeBy(2.seconds)
            assertThat(visibility).isInstanceOf(VolumeDialogVisibilityModel.Visible::class.java)

            underTest.onCsdWarningDialogShown()
            testScope.advanceTimeBy(2.seconds)
            assertThat(visibility).isInstanceOf(VolumeDialogVisibilityModel.Visible::class.java)
        }
}
