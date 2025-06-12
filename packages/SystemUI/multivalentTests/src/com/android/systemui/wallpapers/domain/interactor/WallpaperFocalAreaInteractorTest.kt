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

package com.android.systemui.wallpapers.domain.interactor

import android.content.mockedContext
import android.content.res.Resources
import android.graphics.PointF
import android.graphics.RectF
import android.util.DisplayMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.shade.data.repository.ShadeRepository
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.testKosmos
import com.android.systemui.wallpapers.data.repository.wallpaperFocalAreaRepository
import com.android.systemui.wallpapers.ui.viewmodel.wallpaperFocalAreaViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@SmallTest
@RunWith(AndroidJUnit4::class)
class WallpaperFocalAreaInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    lateinit var shadeRepository: ShadeRepository
    private lateinit var mockedResources: Resources
    lateinit var underTest: WallpaperFocalAreaInteractor

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockedResources = mock<Resources>()
        whenever(kosmos.mockedContext.resources).thenReturn(mockedResources)
        whenever(
                mockedResources.getFloat(
                    Resources.getSystem()
                        .getIdentifier(
                            /* name= */ "config_wallpaperMaxScale",
                            /* defType= */ "dimen",
                            /* defPackage= */ "android",
                        )
                )
            )
            .thenReturn(2f)
        underTest =
            WallpaperFocalAreaInteractor(
                context = kosmos.mockedContext,
                wallpaperFocalAreaRepository = kosmos.wallpaperFocalAreaRepository,
                shadeRepository = kosmos.shadeRepository,
            )
    }

    @Test
    fun focalAreaBounds_withoutNotifications_inHandheldDevices() =
        testScope.runTest {
            overrideMockedResources(
                mockedResources,
                OverrideResources(
                    screenWidth = 1000,
                    screenHeight = 2000,
                    centerAlignFocalArea = false,
                ),
            )
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.shadeRepository.setShadeLayoutWide(false)

            kosmos.wallpaperFocalAreaRepository.setShortcutAbsoluteTop(1800F)
            kosmos.wallpaperFocalAreaRepository.setNotificationDefaultTop(400F)
            kosmos.wallpaperFocalAreaRepository.setNotificationStackAbsoluteBottom(400F)

            assertThat(bounds).isEqualTo(RectF(250f, 700F, 750F, 1400F))
        }

    @Test
    fun focalAreaBounds_withNotifications_inHandheldDevices() =
        testScope.runTest {
            overrideMockedResources(
                mockedResources,
                OverrideResources(
                    screenWidth = 1000,
                    screenHeight = 2000,
                    centerAlignFocalArea = false,
                ),
            )
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.shadeRepository.setShadeLayoutWide(false)
            kosmos.wallpaperFocalAreaRepository.setShortcutAbsoluteTop(1800F)
            kosmos.wallpaperFocalAreaRepository.setNotificationDefaultTop(400F)
            kosmos.wallpaperFocalAreaRepository.setNotificationStackAbsoluteBottom(600F)

            assertThat(bounds).isEqualTo(RectF(250f, 800F, 750F, 1400F))
        }

    @Test
    fun focalAreaBounds_inUnfoldLandscape() =
        testScope.runTest {
            overrideMockedResources(
                mockedResources,
                OverrideResources(
                    screenWidth = 2000,
                    screenHeight = 1600,
                    centerAlignFocalArea = false,
                ),
            )
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.shadeRepository.setShadeLayoutWide(true)
            kosmos.wallpaperFocalAreaRepository.setShortcutAbsoluteTop(1400F)
            kosmos.wallpaperFocalAreaRepository.setNotificationDefaultTop(400F)
            kosmos.wallpaperFocalAreaRepository.setNotificationStackAbsoluteBottom(400F)

            assertThat(bounds).isEqualTo(RectF(600f, 600F, 1400F, 1100F))
        }

    @Test
    fun focalAreaBounds_withNotifications_inUnfoldPortrait() =
        testScope.runTest {
            overrideMockedResources(
                mockedResources,
                OverrideResources(
                    screenWidth = 1600,
                    screenHeight = 2000,
                    centerAlignFocalArea = false,
                ),
            )
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.shadeRepository.setShadeLayoutWide(false)
            kosmos.wallpaperFocalAreaRepository.setShortcutAbsoluteTop(1800F)
            kosmos.wallpaperFocalAreaRepository.setNotificationDefaultTop(400F)
            kosmos.wallpaperFocalAreaRepository.setNotificationStackAbsoluteBottom(600F)

            assertThat(bounds).isEqualTo(RectF(400f, 800F, 1200F, 1400F))
        }

    @Test
    fun focalAreaBounds_withoutNotifications_inUnfoldPortrait() =
        testScope.runTest {
            overrideMockedResources(
                mockedResources,
                OverrideResources(
                    screenWidth = 1600,
                    screenHeight = 2000,
                    centerAlignFocalArea = false,
                ),
            )
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.shadeRepository.setShadeLayoutWide(false)
            kosmos.wallpaperFocalAreaRepository.setShortcutAbsoluteTop(1800F)
            kosmos.wallpaperFocalAreaRepository.setNotificationDefaultTop(400F)
            kosmos.wallpaperFocalAreaRepository.setNotificationStackAbsoluteBottom(600F)

            assertThat(bounds).isEqualTo(RectF(400f, 800F, 1200F, 1400F))
        }

    @Test
    fun focalAreaBounds_inTabletLandscape() =
        testScope.runTest {
            overrideMockedResources(
                mockedResources,
                OverrideResources(
                    screenWidth = 3000,
                    screenHeight = 2000,
                    centerAlignFocalArea = true,
                ),
            )
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.shadeRepository.setShadeLayoutWide(true)
            kosmos.wallpaperFocalAreaRepository.setShortcutAbsoluteTop(1800F)
            kosmos.wallpaperFocalAreaRepository.setNotificationDefaultTop(200F)
            kosmos.wallpaperFocalAreaRepository.setNotificationStackAbsoluteBottom(200F)

            assertThat(bounds).isEqualTo(RectF(1000f, 600F, 2000F, 1400F))
        }

    @Test
    fun onTap_inFocalBounds() =
        testScope.runTest {
            kosmos.wallpaperFocalAreaRepository.setTapPosition(PointF(0F, 0F))
            overrideMockedResources(
                mockedResources,
                OverrideResources(
                    screenWidth = 1000,
                    screenHeight = 2000,
                    centerAlignFocalArea = false,
                ),
            )
            kosmos.wallpaperFocalAreaRepository.setWallpaperFocalAreaBounds(
                RectF(250f, 700F, 750F, 1400F)
            )
            advanceUntilIdle()
            assertThat(currentValue(kosmos.wallpaperFocalAreaRepository.wallpaperFocalAreaBounds))
                .isEqualTo(RectF(250f, 700F, 750F, 1400F))
            underTest.setTapPosition(750F, 750F)
            assertThat(
                    currentValue(kosmos.wallpaperFocalAreaRepository.wallpaperFocalAreaTapPosition)
                )
                .isEqualTo(PointF(625F, 875F))
        }

    @Test
    fun onTap_outFocalBounds() =
        testScope.runTest {
            kosmos.wallpaperFocalAreaRepository.setTapPosition(PointF(0F, 0F))
            overrideMockedResources(
                mockedResources,
                OverrideResources(
                    screenWidth = 1000,
                    screenHeight = 2000,
                    centerAlignFocalArea = false,
                ),
            )
            kosmos.wallpaperFocalAreaViewModel = mock()
            kosmos.wallpaperFocalAreaRepository.setWallpaperFocalAreaBounds(
                RectF(500F, 500F, 1000F, 1000F)
            )
            underTest.setTapPosition(250F, 250F)
            assertThat(
                    currentValue(kosmos.wallpaperFocalAreaRepository.wallpaperFocalAreaTapPosition)
                )
                .isEqualTo(PointF(0F, 0F))
        }

    data class OverrideResources(
        val screenWidth: Int,
        val screenHeight: Int,
        val centerAlignFocalArea: Boolean,
    )

    companion object {
        fun overrideMockedResources(
            mockedResources: Resources,
            overrideResources: OverrideResources,
        ) {
            val displayMetrics =
                DisplayMetrics().apply {
                    widthPixels = overrideResources.screenWidth
                    heightPixels = overrideResources.screenHeight
                    density = 2f
                }
            whenever(mockedResources.displayMetrics).thenReturn(displayMetrics)
            whenever(mockedResources.getBoolean(R.bool.center_align_focal_area_shape))
                .thenReturn(overrideResources.centerAlignFocalArea)
        }
    }
}
