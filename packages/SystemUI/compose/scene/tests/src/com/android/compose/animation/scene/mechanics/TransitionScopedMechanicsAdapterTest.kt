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

package com.android.compose.animation.scene.mechanics

import android.platform.test.annotations.MotionTest
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.MutableSceneTransitionLayoutStateForTests
import com.android.compose.animation.scene.MutableSceneTransitionLayoutStateImpl
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.SceneTransitionLayoutForTesting
import com.android.compose.animation.scene.SceneTransitionsBuilder
import com.android.compose.animation.scene.TestElements
import com.android.compose.animation.scene.TestOverlays
import com.android.compose.animation.scene.TestScenes
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.TransitionRecordingSpec
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.featureOfElement
import com.android.compose.animation.scene.mechanics.TransitionScopedMechanicsAdapter.Companion.appearDirection
import com.android.compose.animation.scene.recordTransition
import com.android.compose.animation.scene.testing.lastOffsetForTesting
import com.android.compose.animation.scene.transformation.CustomPropertyTransformation
import com.android.compose.animation.scene.transformation.PropertyTransformation
import com.android.compose.animation.scene.transformation.PropertyTransformationScope
import com.android.compose.animation.scene.transitions
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.buildDirectionalMotionSpec
import com.android.mechanics.spring.SpringParameters
import com.google.common.truth.Truth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.DataPoint
import platform.test.motion.golden.DataPointTypes
import platform.test.motion.golden.FeatureCapture
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
@MotionTest
class TransitionScopedMechanicsAdapterTest {

    private val goldenPaths =
        createGoldenPathManager("frameworks/base/packages/SystemUI/compose/scene/tests/goldens")

    private val testScope = TestScope()
    @get:Rule val motionRule = createFixedConfigurationComposeMotionTestRule(goldenPaths, testScope)
    private val composeRule = motionRule.toolkit.composeContentTestRule

    @Test
    fun motionValue_withoutAnimation_terminatesImmediately() =
        motionRule.runTest {
            val specFactory: SpecFactory = { _, _ ->
                MotionSpec(
                    // Linearly animate from 10 down to 0
                    buildDirectionalMotionSpec(TestSpring, Mapping.Fixed(50.dp.toPx())) {
                        targetFromCurrent(breakpoint = 0f, to = 0f)
                        constantValueFromCurrent(breakpoint = 1f)
                    }
                )
            }

            assertOffsetMatchesGolden(
                transition = {
                    spec = tween(16 * 6, easing = LinearEasing)
                    transformation(TestElements.Foo) { TestTransformation(specFactory) }
                }
            )
        }

    @Test
    fun motionValue_withAnimation_prolongsTransition() =
        motionRule.runTest {
            val specFactory: SpecFactory = { _, _ ->
                MotionSpec(
                    // Use a spring to toggle 10f -> 0f at a progress of 0.5
                    buildDirectionalMotionSpec(TestSpring, Mapping.Fixed(50.dp.toPx())) {
                        constantValue(breakpoint = 0.5f, value = 0f)
                    }
                )
            }

            assertOffsetMatchesGolden(
                transition = {
                    spec = tween(16 * 6, easing = LinearEasing)
                    transformation(TestElements.Foo) { TestTransformation(specFactory) }
                }
            )
        }

    @Test
    fun motionValue_interruptedAnimation_completes() =
        motionRule.runTest {
            val transitions = transitions {
                from(TestScenes.SceneA, to = TestScenes.SceneB) {
                    spec = tween(16 * 6, easing = LinearEasing)

                    transformation(TestElements.Foo) {
                        TestTransformation { _, _ ->
                            MotionSpec(
                                buildDirectionalMotionSpec(
                                    TestSpring,
                                    Mapping.Fixed(50.dp.toPx()),
                                ) {
                                    constantValue(breakpoint = 0.3f, value = 0f)
                                }
                            )
                        }
                    }
                }
            }

            val state =
                composeRule.runOnUiThread {
                    MutableSceneTransitionLayoutStateForTests(TestScenes.SceneA, transitions)
                }
            lateinit var coroutineScope: CoroutineScope

            val motionControl =
                MotionControl(delayRecording = { awaitFrames(4) }) {
                    awaitFrames(1)
                    val (transitionToB, firstTransitionJob) =
                        toolkit.composeContentTestRule.runOnUiThread {
                            checkNotNull(
                                state.setTargetScene(
                                    TestScenes.SceneB,
                                    animationScope = coroutineScope,
                                )
                            )
                        }

                    awaitCondition { transitionToB.progress > 0.5f }
                    val (transitionBackToA, secondTransitionJob) =
                        toolkit.composeContentTestRule.runOnUiThread {
                            checkNotNull(
                                state.setTargetScene(
                                    TestScenes.SceneA,
                                    animationScope = coroutineScope,
                                )
                            )
                        }

                    Truth.assertThat(transitionBackToA.replacedTransition)
                        .isSameInstanceAs(transitionToB)

                    awaitCondition { !state.isTransitioning() }

                    Truth.assertThat(firstTransitionJob.isCompleted).isTrue()
                    Truth.assertThat(secondTransitionJob.isCompleted).isTrue()
                }

            val motion =
                recordMotion(
                    content = {
                        coroutineScope = rememberCoroutineScope()
                        SceneTransitionLayoutForTesting(state, modifier = Modifier.size(50.dp)) {
                            scene(TestScenes.SceneA) { SceneAContent() }
                            scene(TestScenes.SceneB) { SceneBContent() }
                        }
                    },
                    ComposeRecordingSpec(motionControl, recordBefore = false) {
                        featureOfElement(TestElements.Foo, yOffsetFeature)
                    },
                )

            assertThat(motion).timeSeriesMatchesGolden()
        }

    @Test
    fun animationDirection_sceneTransition_forward() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneA,
                transitionBuilder = {
                    from(TestScenes.SceneA, to = TestScenes.SceneB) { it(TestElements.Foo) }
                },
            ) { state, animationScope, _ ->
                state.setTargetScene(TestScenes.SceneB, animationScope)
                false
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Max)
    }

    @Test
    fun animationDirection_sceneTransition_backwards() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneB,
                transitionBuilder = {
                    from(TestScenes.SceneA, to = TestScenes.SceneB) { it(TestElements.Foo) }
                },
            ) { state, animationScope, _ ->
                state.setTargetScene(TestScenes.SceneA, animationScope)
                false
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Min)
    }

    @Test
    fun animationDirection_interruptedTransition_flipsDirection() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneA,
                transitionBuilder = {
                    from(TestScenes.SceneA, to = TestScenes.SceneB) { it(TestElements.Foo) }
                },
            ) { state, animationScope, iteration ->
                when (iteration) {
                    0 -> {
                        state.setTargetScene(TestScenes.SceneB, animationScope)
                        true
                    }
                    1 -> {
                        state.setTargetScene(TestScenes.SceneA, animationScope)
                        false
                    }
                    else -> throw AssertionError()
                }
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Min)
    }

    @Test
    fun animationDirection_showOverlay_animatesInMaxDirection() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneA,
                transitionBuilder = { this.to(TestOverlays.OverlayA) { it(TestElements.Bar) } },
            ) { state, animationScope, _ ->
                state.showOverlay(TestOverlays.OverlayA, animationScope)
                false
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Max)
    }

    @Test
    fun animationDirection_hideOverlay_animatesInMinDirection() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneA,
                initialOverlays = setOf(TestOverlays.OverlayA),
                transitionBuilder = { this.to(TestOverlays.OverlayA) { it(TestElements.Bar) } },
            ) { state, animationScope, _ ->
                state.hideOverlay(TestOverlays.OverlayA, animationScope)
                false
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Min)
    }

    @Test
    fun animationDirection_hideOverlayMidTransition_animatesInMinDirection() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneA,
                transitionBuilder = { this.to(TestOverlays.OverlayA) { it(TestElements.Bar) } },
            ) { state, animationScope, iteration ->
                when (iteration) {
                    0 -> {
                        state.showOverlay(TestOverlays.OverlayA, animationScope)
                        true
                    }
                    1 -> {
                        state.hideOverlay(TestOverlays.OverlayA, animationScope)
                        false
                    }
                    else -> throw AssertionError()
                }
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Min)
    }

    @Test
    fun animationDirection_replaceOverlay_showingContent_animatesInMaxDirection() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneA,
                initialOverlays = setOf(TestOverlays.OverlayB),
                transitionBuilder = { this.to(TestOverlays.OverlayA) { it(TestElements.Bar) } },
            ) { state, animationScope, _ ->
                state.replaceOverlay(TestOverlays.OverlayB, TestOverlays.OverlayA, animationScope)
                false
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Max)
    }

    @Test
    fun animationDirection_replaceOverlay_hidingContent_animatesInMinDirection() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneA,
                initialOverlays = setOf(TestOverlays.OverlayA),
                transitionBuilder = { this.to(TestOverlays.OverlayA) { it(TestElements.Bar) } },
            ) { state, animationScope, _ ->
                state.replaceOverlay(TestOverlays.OverlayA, TestOverlays.OverlayB, animationScope)
                false
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Min)
    }

    @Test
    fun animationDirection_replaceOverlay_revertMidTransition_animatesInMinDirection() {
        val transitionDirection =
            composeRule.getAppearDirectionOnTransition(
                initialScene = TestScenes.SceneA,
                initialOverlays = setOf(TestOverlays.OverlayB),
                transitionBuilder = { this.to(TestOverlays.OverlayA) { it(TestElements.Bar) } },
            ) { state, animationScope, iteration ->
                when (iteration) {
                    0 -> {
                        state.replaceOverlay(
                            TestOverlays.OverlayB,
                            TestOverlays.OverlayA,
                            animationScope,
                        )
                        true
                    }
                    1 -> {
                        state.replaceOverlay(
                            TestOverlays.OverlayA,
                            TestOverlays.OverlayB,
                            animationScope,
                        )
                        false
                    }
                    else -> throw AssertionError()
                }
            }

        Truth.assertThat(transitionDirection).isEqualTo(InputDirection.Min)
    }

    private fun ComposeContentTestRule.getAppearDirectionOnTransition(
        initialScene: SceneKey,
        transitionBuilder: SceneTransitionsBuilder.(foo: DirectionAssertionTransition) -> Unit,
        initialOverlays: Set<OverlayKey> = emptySet(),
        runTransition:
            (
                state: MutableSceneTransitionLayoutStateImpl,
                animationScope: CoroutineScope,
                iteration: Int,
            ) -> Boolean,
    ): InputDirection {

        lateinit var result: InputDirection

        val x: DirectionAssertionTransition = {
            transformation(it) {
                object : CustomPropertyTransformation<IntSize> {
                    override val property = PropertyTransformation.Property.Size

                    override fun PropertyTransformationScope.transform(
                        content: ContentKey,
                        element: ElementKey,
                        transition: TransitionState.Transition,
                        transitionScope: CoroutineScope,
                    ): IntSize {
                        result = appearDirection(content, element, transition)
                        return IntSize.Zero
                    }
                }
            }
        }

        val state = runOnUiThread {
            MutableSceneTransitionLayoutStateForTests(
                initialScene,
                transitions { transitionBuilder(x) },
                initialOverlays,
            )
        }
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            SceneTransitionLayout(state) {
                scene(TestScenes.SceneA) { SceneAContent() }
                scene(TestScenes.SceneB) { SceneBContent() }
                overlay(TestOverlays.OverlayA) { OverlayAContent() }
                overlay(TestOverlays.OverlayB) {}
            }
        }

        waitForIdle()
        mainClock.autoAdvance = false
        var keepOnAnimating = true
        var iterationCount = 0
        while (keepOnAnimating) {
            runOnUiThread { keepOnAnimating = runTransition(state, coroutineScope, iterationCount) }
            composeRule.mainClock.advanceTimeByFrame()
            waitForIdle()
            iterationCount++
        }
        waitForIdle()

        return result
    }

    private class TestTransformation(specFactory: SpecFactory) :
        CustomPropertyTransformation<Offset> {
        override val property = PropertyTransformation.Property.Offset

        val motionValue =
            TransitionScopedMechanicsAdapter(createSpec = specFactory, stableThreshold = 1f)

        override fun PropertyTransformationScope.transform(
            content: ContentKey,
            element: ElementKey,
            transition: TransitionState.Transition,
            transitionScope: CoroutineScope,
        ): Offset {
            val yOffset =
                with(motionValue) { update(content, element, transition, transitionScope) }

            return Offset(x = 0f, y = yOffset)
        }
    }

    private fun assertOffsetMatchesGolden(transition: TransitionBuilder.() -> Unit) {
        val recordingSpec =
            TransitionRecordingSpec(recordBefore = false, recordAfter = true) {
                featureOfElement(TestElements.Foo, yOffsetFeature)
            }

        val motion =
            motionRule.recordTransition(
                fromSceneContent = { SceneAContent() },
                toSceneContent = { SceneBContent() },
                transition = transition,
                recordingSpec = recordingSpec,
                layoutModifier = Modifier.size(50.dp),
            )

        motionRule.assertThat(motion).timeSeriesMatchesGolden()
    }

    companion object {

        @Composable
        fun ContentScope.SceneAContent() {
            Box(modifier = Modifier.fillMaxSize())
        }

        @Composable
        fun ContentScope.SceneBContent() {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.element(TestElements.Foo).size(50.dp).background(Color.Red))
            }
        }

        @Composable
        fun ContentScope.OverlayAContent() {
            Box(Modifier.element(TestElements.Bar).size(50.dp).background(Color.Red))
        }

        @Composable
        fun ContentScope.OverlayBContent() {
            Box(modifier = Modifier.size(50.dp).background(Color.Green))
        }

        val TestSpring = SpringParameters(1200f, 1f)

        val yOffsetFeature =
            FeatureCapture<SemanticsNode, Float>("yOffset") {
                DataPoint.of(it.lastOffsetForTesting?.y, DataPointTypes.float)
            }
    }
}

typealias DirectionAssertionTransition = TransitionBuilder.(container: ElementKey) -> Unit
