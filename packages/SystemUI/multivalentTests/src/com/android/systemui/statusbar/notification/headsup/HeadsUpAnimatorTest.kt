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

package com.android.systemui.statusbar.notification.headsup

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.ui.fakeSystemBarUtilsProxy
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(NotificationsHunSharedAnimationValues.FLAG_NAME)
class HeadsUpAnimatorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Before
    fun setUp() {
        context.getOrCreateTestableResources().apply {
            this.addOverride(R.dimen.heads_up_appear_y_above_screen, TEST_Y_ABOVE_SCREEN)
        }
    }

    @Test
    fun getHeadsUpYTranslation_fromBottomTrue_hasStatusBarChipFalse_usesBottomAndYAbove() {
        val underTest = HeadsUpAnimator(context, kosmos.fakeSystemBarUtilsProxy)
        underTest.stackTopMargin = 30
        underTest.headsUpAppearHeightBottom = 300

        val yTranslation =
            underTest.getHeadsUpYTranslation(isHeadsUpFromBottom = true, hasStatusBarChip = false)

        assertThat(yTranslation).isEqualTo(TEST_Y_ABOVE_SCREEN + 300)
    }

    @Test
    fun getHeadsUpYTranslation_fromBottomTrue_hasStatusBarChipTrue_usesBottomAndYAbove() {
        val underTest = HeadsUpAnimator(context, kosmos.fakeSystemBarUtilsProxy)
        underTest.stackTopMargin = 30
        underTest.headsUpAppearHeightBottom = 300

        val yTranslation =
            underTest.getHeadsUpYTranslation(isHeadsUpFromBottom = true, hasStatusBarChip = true)

        // fromBottom takes priority
        assertThat(yTranslation).isEqualTo(TEST_Y_ABOVE_SCREEN + 300)
    }

    @Test
    fun getHeadsUpYTranslation_fromBottomFalse_hasStatusBarChipFalse_usesTopMarginAndYAbove() {
        val underTest = HeadsUpAnimator(context, kosmos.fakeSystemBarUtilsProxy)
        underTest.stackTopMargin = 30
        underTest.headsUpAppearHeightBottom = 300

        val yTranslation =
            underTest.getHeadsUpYTranslation(isHeadsUpFromBottom = false, hasStatusBarChip = false)

        assertThat(yTranslation).isEqualTo(-30 - TEST_Y_ABOVE_SCREEN)
    }

    @Test
    fun getHeadsUpYTranslation_fromBottomFalse_hasStatusBarChipTrue_usesTopMarginAndStatusBarHeight() {
        val underTest = HeadsUpAnimator(context, kosmos.fakeSystemBarUtilsProxy)
        underTest.stackTopMargin = 30
        underTest.headsUpAppearHeightBottom = 300
        kosmos.fakeSystemBarUtilsProxy.fakeStatusBarHeight = 75
        underTest.updateResources(context)

        val yTranslation =
            underTest.getHeadsUpYTranslation(isHeadsUpFromBottom = false, hasStatusBarChip = true)

        assertThat(yTranslation).isEqualTo(75 - 30)
    }

    @Test
    fun getHeadsUpYTranslation_resourcesUpdated() {
        val underTest = HeadsUpAnimator(context, kosmos.fakeSystemBarUtilsProxy)
        underTest.stackTopMargin = 30
        underTest.headsUpAppearHeightBottom = 300

        val yTranslation =
            underTest.getHeadsUpYTranslation(isHeadsUpFromBottom = true, hasStatusBarChip = false)

        assertThat(yTranslation).isEqualTo(TEST_Y_ABOVE_SCREEN + 300)

        // WHEN the resource is updated
        val newYAbove = 600
        context.getOrCreateTestableResources().apply {
            this.addOverride(R.dimen.heads_up_appear_y_above_screen, newYAbove)
        }
        underTest.updateResources(context)

        // THEN HeadsUpAnimator knows about it
        assertThat(
                underTest.getHeadsUpYTranslation(
                    isHeadsUpFromBottom = true,
                    hasStatusBarChip = false,
                )
            )
            .isEqualTo(newYAbove + 300)
    }

    companion object {
        private const val TEST_Y_ABOVE_SCREEN = 50
    }
}
