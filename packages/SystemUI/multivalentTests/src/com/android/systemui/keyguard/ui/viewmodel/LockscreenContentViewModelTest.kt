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

package com.android.systemui.keyguard.ui.viewmodel

import android.platform.test.flag.junit.FlagsParameterization
import androidx.compose.ui.Alignment
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.authController
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardClockRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardClockInteractor
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.transition.fakeKeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.shared.transition.keyguardTransitionAnimationCallbackDelegator
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel.NotificationsPlacement.BelowClock
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel.NotificationsPlacement.BesideClock
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.testKosmos
import com.android.systemui.unfold.fakeUnfoldTransitionProgressProvider
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class LockscreenContentViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos: Kosmos = testKosmos()

    private lateinit var underTest: LockscreenContentViewModel
    private val activationJob = Job()

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        with(kosmos) {
            shadeRepository.setShadeLayoutWide(false)
            underTest =
                lockscreenContentViewModelFactory.create(fakeKeyguardTransitionAnimationCallback)
            underTest.activateIn(testScope, activationJob)
        }
    }

    @Test
    fun isUdfpsVisible_withUdfps_true() =
        kosmos.runTest {
            whenever(authController.isUdfpsSupported).thenReturn(true)
            assertThat(underTest.isUdfpsVisible).isTrue()
        }

    @Test
    fun isUdfpsVisible_withoutUdfps_false() =
        kosmos.runTest {
            whenever(authController.isUdfpsSupported).thenReturn(false)
            assertThat(underTest.isUdfpsVisible).isFalse()
        }

    @Test
    fun notificationsPlacement_splitShade_topEnd() =
        kosmos.runTest {
            setupState(shadeMode = ShadeMode.Split, clockSize = ClockSize.SMALL)

            assertThat(underTest.notificationsPlacement)
                .isEqualTo(BesideClock(alignment = Alignment.TopEnd))
        }

    @Test
    fun notificationsPlacement_singleShade_below() =
        kosmos.runTest {
            setupState(shadeMode = ShadeMode.Single, clockSize = ClockSize.SMALL)

            assertThat(underTest.notificationsPlacement).isEqualTo(BelowClock)
        }

    @Test
    @EnableSceneContainer
    fun notificationsPlacement_dualShadeSmallClock_below() =
        kosmos.runTest {
            setupState(
                shadeMode = ShadeMode.Dual,
                clockSize = ClockSize.SMALL,
                shadeLayoutWide = true,
            )

            assertThat(underTest.notificationsPlacement).isEqualTo(BelowClock)
        }

    @Test
    @EnableSceneContainer
    fun notificationsPlacement_dualShadeLargeClock_topStart() =
        kosmos.runTest {
            setupState(
                shadeMode = ShadeMode.Dual,
                clockSize = ClockSize.LARGE,
                shadeLayoutWide = true,
            )

            assertThat(underTest.notificationsPlacement)
                .isEqualTo(BesideClock(alignment = Alignment.TopStart))
        }

    @Test
    fun areNotificationsVisible_splitShadeTrue_true() =
        kosmos.runTest {
            setupState(shadeMode = ShadeMode.Split, clockSize = ClockSize.LARGE)

            assertThat(underTest.areNotificationsVisible).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun areNotificationsVisible_dualShadeWideOnLockscreen_true() =
        kosmos.runTest {
            setupState(
                shadeMode = ShadeMode.Dual,
                clockSize = ClockSize.LARGE,
                shadeLayoutWide = true,
            )

            assertThat(underTest.areNotificationsVisible).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun areNotificationsVisible_withSmallClock_true() =
        kosmos.runTest {
            setupState(shadeMode = ShadeMode.Single, clockSize = ClockSize.SMALL)

            assertThat(underTest.areNotificationsVisible).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun areNotificationsVisible_withLargeClock_false() =
        kosmos.runTest {
            setupState(shadeMode = ShadeMode.Single, clockSize = ClockSize.LARGE)

            assertThat(underTest.areNotificationsVisible).isFalse()
        }

    @Test
    fun unfoldTranslations() =
        kosmos.runTest {
            val maxTranslation = prepareConfiguration()

            val unfoldProvider = fakeUnfoldTransitionProgressProvider
            unfoldProvider.onTransitionStarted()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()

            repeat(10) { repetition ->
                val transitionProgress = 0.1f * (repetition + 1)
                unfoldProvider.onTransitionProgress(transitionProgress)
                runCurrent()
                assertThat(underTest.unfoldTranslations.start)
                    .isEqualTo((1 - transitionProgress) * maxTranslation)
                assertThat(underTest.unfoldTranslations.end)
                    .isEqualTo(-(1 - transitionProgress) * maxTranslation)
            }

            unfoldProvider.onTransitionFinishing()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()

            unfoldProvider.onTransitionFinished()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()
        }

    @Test
    fun isContentVisible_whenNotOccluded_visible() =
        kosmos.runTest {
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(false, null)
            runCurrent()
            assertThat(underTest.isContentVisible).isTrue()
        }

    @Test
    fun isContentVisible_whenOccluded_notVisible() =
        kosmos.runTest {
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true, null)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.OCCLUDED,
            )
            runCurrent()
            assertThat(underTest.isContentVisible).isFalse()
        }

    @Test
    fun isContentVisible_whenOccluded_notVisible_evenIfShadeShown() =
        kosmos.runTest {
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true, null)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.OCCLUDED,
            )
            runCurrent()

            sceneInteractor.snapToScene(Scenes.Shade, "")
            runCurrent()
            assertThat(underTest.isContentVisible).isFalse()
        }

    @Test
    fun activate_setsDelegate_onKeyguardTransitionAnimationCallbackDelegator() =
        kosmos.runTest {
            runCurrent()
            assertThat(keyguardTransitionAnimationCallbackDelegator.delegate)
                .isSameInstanceAs(fakeKeyguardTransitionAnimationCallback)
        }

    @Test
    fun deactivate_clearsDelegate_onKeyguardTransitionAnimationCallbackDelegator() =
        kosmos.runTest {
            activationJob.cancel()
            runCurrent()
            assertThat(keyguardTransitionAnimationCallbackDelegator.delegate).isNull()
        }

    @Test
    fun isContentVisible_whenOccluded_notVisibleInOccluded_visibleInAod() =
        kosmos.runTest {
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true, null)
            fakeKeyguardTransitionRepository.transitionTo(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.OCCLUDED,
            )
            runCurrent()

            sceneInteractor.snapToScene(Scenes.Shade, "")
            runCurrent()
            assertThat(underTest.isContentVisible).isFalse()

            fakeKeyguardTransitionRepository.transitionTo(KeyguardState.OCCLUDED, KeyguardState.AOD)
            runCurrent()

            sceneInteractor.snapToScene(Scenes.Lockscreen, "")
            runCurrent()

            assertThat(underTest.isContentVisible).isTrue()
        }

    private fun Kosmos.setupState(
        shadeMode: ShadeMode,
        clockSize: ClockSize,
        shadeLayoutWide: Boolean? = null,
    ) {
        val isShadeLayoutWide by collectLastValue(kosmos.shadeRepository.isShadeLayoutWide)
        val collectedClockSize by collectLastValue(kosmos.keyguardClockInteractor.clockSize)
        val collectedShadeMode by collectLastValue(kosmos.shadeModeInteractor.shadeMode)
        when (shadeMode) {
            ShadeMode.Dual -> kosmos.enableDualShade(wideLayout = shadeLayoutWide)
            ShadeMode.Single -> kosmos.enableSingleShade()
            ShadeMode.Split -> kosmos.enableSplitShade()
        }
        fakeKeyguardClockRepository.setShouldForceSmallClock(clockSize == ClockSize.SMALL)
        fakeKeyguardClockRepository.setClockSize(clockSize)
        runCurrent()
        if (shadeLayoutWide != null) {
            assertThat(isShadeLayoutWide).isEqualTo(shadeLayoutWide)
        }
        assertThat(collectedShadeMode).isEqualTo(shadeMode)
        assertThat(collectedClockSize).isEqualTo(clockSize)
    }

    private fun prepareConfiguration(): Int {
        val configuration = context.resources.configuration
        configuration.setLayoutDirection(Locale.US)
        kosmos.fakeConfigurationRepository.onConfigurationChange(configuration)
        val maxTranslation = 10
        kosmos.fakeConfigurationRepository.setDimensionPixelSize(
            R.dimen.notification_side_paddings,
            maxTranslation,
        )
        return maxTranslation
    }
}
