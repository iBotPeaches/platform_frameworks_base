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

import android.content.res.Configuration
import android.content.res.mainResources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.util.LayoutDirection
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_V2
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.communal.data.repository.communalSceneRepository
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.setCommunalV2ConfigEnabled
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.testKosmos
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class GlanceableHubToLockscreenTransitionViewModelTest(flags: FlagsParameterization) :
    SysuiTestCase() {
    val kosmos = testKosmos().apply { mainResources = mContext.orCreateTestableResources.resources }
    val testScope = kosmos.testScope

    val keyguardTransitionRepository = kosmos.fakeKeyguardTransitionRepository
    val configurationRepository = kosmos.fakeConfigurationRepository
    val keyguardStateController: KeyguardStateController = kosmos.keyguardStateController
    val underTest by lazy { kosmos.glanceableHubToLockscreenTransitionViewModel }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_GLANCEABLE_HUB_V2)
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    fun lockscreenFadeIn() =
        kosmos.runTest {
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val values by collectValues(underTest.keyguardAlpha)
            assertThat(values).isEmpty()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    // Should start running here...
                    step(0.1f),
                    step(0.2f),
                    step(0.3f),
                    step(0.4f),
                    // ...up to here
                    step(0.5f),
                    step(0.6f),
                    step(0.7f),
                    step(0.8f),
                    step(1f),
                ),
                testScope,
            )

            assertThat(values).hasSize(4)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun lockscreenFadeIn_fromHubInLandscape() =
        kosmos.runTest {
            kosmos.setCommunalV2ConfigEnabled(true)
            whenever(keyguardStateController.isKeyguardScreenRotationAllowed).thenReturn(false)
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            communalSceneRepository.setCommunalContainerOrientation(
                Configuration.ORIENTATION_LANDSCAPE
            )

            val values by collectValues(underTest.keyguardAlpha)
            assertThat(values).isEmpty()

            // Exit hub to lockscreen
            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow(
                    ObservableTransitionState.Transition(
                        fromScene = CommunalScenes.Communal,
                        toScene = CommunalScenes.Blank,
                        currentScene = flowOf(CommunalScenes.Blank),
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            communalSceneInteractor.setTransitionState(transitionState)
            progress.value = .2f

            // Still in landscape
            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0.1f),
                    // start here..
                    step(0.5f),
                ),
                testScope,
            )

            // Communal container is rotated to portrait
            communalSceneRepository.setCommunalContainerOrientation(
                Configuration.ORIENTATION_PORTRAIT
            )
            runCurrent()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0.6f),
                    step(0.7f),
                    // should stop here..
                    step(0.8f),
                    step(1f),
                ),
                testScope,
            )
            // Scene transition finished.
            progress.value = 1f
            keyguardTransitionRepository.sendTransitionSteps(
                listOf(step(1f, TransitionState.FINISHED)),
                testScope,
            )

            assertThat(values).hasSize(4)
            // onStart
            assertThat(values[0]).isEqualTo(0f)
            assertThat(values[1]).isEqualTo(0f)
            assertThat(values[2]).isEqualTo(1f)
            // onFinish
            assertThat(values[3]).isEqualTo(1f)
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun lockscreenFadeIn_v2FlagDisabledAndFromHubInLandscape() =
        kosmos.runTest {
            whenever(keyguardStateController.isKeyguardScreenRotationAllowed).thenReturn(false)
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            // Rotation is not enabled so communal container is in portrait.
            communalSceneRepository.setCommunalContainerOrientation(
                Configuration.ORIENTATION_PORTRAIT
            )

            val values by collectValues(underTest.keyguardAlpha)
            assertThat(values).isEmpty()

            // Exit hub to lockscreen
            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    // Should start running here...
                    step(0.1f),
                    step(0.2f),
                    step(0.3f),
                    step(0.4f),
                    // ...up to here
                    step(0.5f),
                    step(0.6f),
                    step(0.7f),
                    step(0.8f),
                    step(1f),
                ),
                testScope,
            )

            assertThat(values).hasSize(4)
            values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
        }

    @Test
    fun lockscreenTranslationX() =
        kosmos.runTest {
            val config: Configuration = mock()
            whenever(config.layoutDirection).thenReturn(LayoutDirection.LTR)
            configurationRepository.onConfigurationChange(config)

            configurationRepository.setDimensionPixelSize(
                R.dimen.hub_to_lockscreen_transition_lockscreen_translation_x,
                100,
            )
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val values by collectValues(underTest.keyguardTranslationX)
            assertThat(values).isEmpty()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0f),
                    step(0.3f),
                    step(0.5f),
                    step(1f),
                ),
                testScope,
            )

            assertThat(values).hasSize(5)
            values.forEach { assertThat(it.value).isIn(Range.closed(-100f, 0f)) }
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun lockscreenTranslationX_fromHubInLandscape() =
        kosmos.runTest {
            kosmos.setCommunalV2ConfigEnabled(true)
            val config: Configuration = mock()
            whenever(config.layoutDirection).thenReturn(LayoutDirection.LTR)
            configurationRepository.onConfigurationChange(config)

            configurationRepository.setDimensionPixelSize(
                R.dimen.hub_to_lockscreen_transition_lockscreen_translation_x,
                100,
            )
            whenever(keyguardStateController.isKeyguardScreenRotationAllowed).thenReturn(false)

            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            communalSceneRepository.setCommunalContainerOrientation(
                Configuration.ORIENTATION_LANDSCAPE
            )

            val values by collectValues(underTest.keyguardTranslationX)
            assertThat(values).isEmpty()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0.3f),
                    step(0.5f),
                    step(0.7f),
                    step(1f),
                    step(1f, TransitionState.FINISHED),
                ),
                testScope,
            )
            // no translation-x animation
            values.forEach { assertThat(it.value).isEqualTo(0f) }
        }

    @Test
    fun lockscreenTranslationX_resetsAfterCancellation() =
        kosmos.runTest {
            val config: Configuration = mock()
            whenever(config.layoutDirection).thenReturn(LayoutDirection.LTR)
            configurationRepository.onConfigurationChange(config)

            configurationRepository.setDimensionPixelSize(
                R.dimen.hub_to_lockscreen_transition_lockscreen_translation_x,
                100,
            )

            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val values by collectValues(underTest.keyguardTranslationX)
            assertThat(values).isEmpty()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0.3f),
                    step(0.5f),
                    step(0.9f, TransitionState.CANCELED),
                ),
                testScope,
            )

            assertThat(values).hasSize(4)
            values.forEach { assertThat(it.value).isIn(Range.closed(-100f, 0f)) }
            assertThat(values.last().value).isEqualTo(0f)
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun lockscreenTranslationX_resetsAfterCancellation_fromHubInLandscape() =
        kosmos.runTest {
            kosmos.setCommunalV2ConfigEnabled(true)
            val config: Configuration = mock()
            whenever(config.layoutDirection).thenReturn(LayoutDirection.LTR)
            configurationRepository.onConfigurationChange(config)

            configurationRepository.setDimensionPixelSize(
                R.dimen.hub_to_lockscreen_transition_lockscreen_translation_x,
                100,
            )
            whenever(keyguardStateController.isKeyguardScreenRotationAllowed).thenReturn(false)

            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")
            communalSceneRepository.setCommunalContainerOrientation(
                Configuration.ORIENTATION_LANDSCAPE
            )

            val values by collectValues(underTest.keyguardTranslationX)
            assertThat(values).isEmpty()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0.3f),
                    step(0.6f),
                    step(0.9f, TransitionState.CANCELED),
                ),
                testScope,
            )
            // no translation-x animation
            values.forEach { assertThat(it.value).isEqualTo(0f) }
        }

    @Test
    @DisableSceneContainer
    fun blurBecomesMinValueImmediately() =
        kosmos.runTest {
            val values by collectValues(underTest.windowBlurRadius)

            keyguardWindowBlurTestUtil.assertTransitionToBlurRadius(
                transitionProgress = listOf(0.0f, 0.2f, 0.3f, 0.65f, 0.7f, 1.0f),
                startValue = blurConfig.maxBlurRadiusPx,
                endValue = blurConfig.minBlurRadiusPx,
                actualValuesProvider = { values },
                transitionFactory = { step, transitionState ->
                    TransitionStep(
                        from = KeyguardState.GLANCEABLE_HUB,
                        to = KeyguardState.LOCKSCREEN,
                        value = step,
                        transitionState = transitionState,
                        ownerName = "GlanceableHubToLockscreenTransitionViewModelTest",
                    )
                },
                checkInterpolatedValues = false,
            )
        }

    private fun step(
        value: Float,
        state: TransitionState = TransitionState.RUNNING,
    ): TransitionStep {
        return TransitionStep(
            from = KeyguardState.GLANCEABLE_HUB,
            to = KeyguardState.LOCKSCREEN,
            value = value,
            transitionState = state,
            ownerName = this::class.java.simpleName,
        )
    }
}
