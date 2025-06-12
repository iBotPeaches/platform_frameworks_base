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

package com.android.compose.animation.scene.reveal

import android.platform.test.annotations.MotionTest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.FeatureCaptures.elementAlpha
import com.android.compose.animation.scene.MutableSceneTransitionLayoutStateForTests
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayoutForTesting
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.featureOfElement
import com.android.compose.animation.scene.transitions
import com.android.mechanics.behavior.VerticalExpandContainerSpec
import com.android.mechanics.behavior.verticalExpandContainerBackground
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeFeatureCaptures.dpSize
import platform.test.motion.compose.ComposeFeatureCaptures.positionInRoot
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.MotionControlScope
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.testing.createGoldenPathManager
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext

@MotionTest
@RunWith(ParameterizedAndroidJunit4::class)
class ContentRevealTest(private val isFloating: Boolean) {

    private val pathConfig =
        PathConfig(
            PathElementNoContext("floating", isDir = false) {
                if (isFloating) "floating" else "edge"
            }
        )

    private val goldenPaths =
        createGoldenPathManager(
            "frameworks/base/packages/SystemUI/compose/scene/tests/goldens",
            pathConfig,
        )

    @get:Rule val motionRule = createFixedConfigurationComposeMotionTestRule(goldenPaths)

    private val fakeHaptics = FakeHaptics()

    private val motionSpec = VerticalExpandContainerSpec(isFloating)

    @Test
    fun verticalReveal_triggeredRevealOpenTransition() {
        assertVerticalContainerRevealMotion(
            TriggeredRevealMotion(SceneClosed, SceneOpen),
            "verticalReveal_triggeredRevealOpenTransition",
        )
    }

    @Test
    fun verticalReveal_triggeredRevealCloseTransition() {
        assertVerticalContainerRevealMotion(
            TriggeredRevealMotion(SceneOpen, SceneClosed),
            "verticalReveal_triggeredRevealCloseTransition",
        )
    }

    @Test
    fun verticalReveal_gesture_magneticDetachAndReattach() {
        assertVerticalContainerRevealMotion(
            GestureRevealMotion(SceneClosed) {
                val gestureDurationMillis = 1000L
                // detach position for the floating container is larger
                val gestureHeight = if (isFloating) 160.dp.toPx() else 100.dp.toPx()
                swipe(
                    curve = {
                        val progress = it / gestureDurationMillis.toFloat()
                        val y = sin(progress * Math.PI).toFloat() * gestureHeight
                        Offset(centerX, y)
                    },
                    gestureDurationMillis,
                )
            },
            "verticalReveal_gesture_magneticDetachAndReattach",
        )
    }

    @Test
    fun verticalReveal_gesture_dragOpen() {
        assertVerticalContainerRevealMotion(
            GestureRevealMotion(SceneClosed) {
                swipeDown(endY = 200.dp.toPx(), durationMillis = 500)
            },
            "verticalReveal_gesture_dragOpen",
        )
    }

    @Test
    fun verticalReveal_gesture_flingOpen() {
        assertVerticalContainerRevealMotion(
            GestureRevealMotion(SceneClosed) {
                val end = Offset(centerX, 80.dp.toPx())
                swipeWithVelocity(start = topCenter, end = end, endVelocity = FlingVelocity.toPx())
            },
            "verticalReveal_gesture_flingOpen",
        )
    }

    @Test
    fun verticalReveal_gesture_dragFullyClose() {
        assertVerticalContainerRevealMotion(
            GestureRevealMotion(SceneOpen) {
                swipeUp(200.dp.toPx(), 0.dp.toPx(), durationMillis = 500)
            },
            "verticalReveal_gesture_dragFullyClose",
        )
    }

    @Test
    fun verticalReveal_gesture_dragHalfClose() {
        assertVerticalContainerRevealMotion(
            GestureRevealMotion(SceneOpen) {
                swipeUp(250.dp.toPx(), 100.dp.toPx(), durationMillis = 500)
            },
            "verticalReveal_gesture_dragHalfClose",
        )
    }

    @Test
    fun verticalReveal_gesture_flingClose() {
        assertVerticalContainerRevealMotion(
            GestureRevealMotion(SceneOpen) {
                val start = Offset(centerX, 260.dp.toPx())
                val end = Offset(centerX, 200.dp.toPx())
                swipeWithVelocity(start, end, FlingVelocity.toPx())
            },
            "verticalReveal_gesture_flingClose",
        )
    }

    private interface RevealMotion {
        val startScene: SceneKey
    }

    private class TriggeredRevealMotion(
        override val startScene: SceneKey,
        val targetScene: SceneKey,
    ) : RevealMotion

    private class GestureRevealMotion(
        override val startScene: SceneKey,
        val gestureControl: TouchInjectionScope.() -> Unit,
    ) : RevealMotion

    private fun assertVerticalContainerRevealMotion(
        testInstructions: RevealMotion,
        goldenName: String,
    ) =
        motionRule.runTest {
            val transitions = transitions {
                from(SceneClosed, to = SceneOpen) {
                    verticalContainerReveal(RevealElement, motionSpec, fakeHaptics)
                }
            }

            val state =
                toolkit.composeContentTestRule.runOnUiThread {
                    MutableSceneTransitionLayoutStateForTests(
                        testInstructions.startScene,
                        transitions,
                    )
                }
            lateinit var coroutineScope: CoroutineScope

            val recordTransition: suspend MotionControlScope.() -> Unit = {
                when (testInstructions) {
                    is TriggeredRevealMotion -> {
                        val transition =
                            toolkit.composeContentTestRule.runOnUiThread {
                                state.setTargetScene(
                                    testInstructions.targetScene,
                                    animationScope = coroutineScope,
                                )
                            }
                        checkNotNull(transition).second.join()
                    }

                    is GestureRevealMotion -> {
                        performTouchInputAsync(
                            onNodeWithTag("stl"),
                            testInstructions.gestureControl,
                        )
                        awaitCondition { !state.isTransitioning() }
                    }
                }
            }
            val recordingSpec =
                ComposeRecordingSpec(
                    recordBefore = false,
                    recordAfter = false,
                    motionControl = MotionControl(recording = recordTransition),
                ) {
                    featureOfElement(RevealElement, positionInRoot)
                    featureOfElement(RevealElement, dpSize)
                    featureOfElement(RevealElement, elementAlpha)
                }

            val motion =
                recordMotion(
                    content = {
                        coroutineScope = rememberCoroutineScope()
                        SceneTransitionLayoutForTesting(
                            state,
                            modifier =
                                Modifier.padding(5.dp)
                                    .background(Color.Yellow)
                                    .size(ContainerSize.width, ContainerSize.height + 100.dp)
                                    .testTag("stl"),
                        ) {
                            scene(
                                SceneClosed,
                                mapOf(Swipe.Down to SceneOpen),
                                content = { ClosedContainer() },
                            )
                            scene(
                                SceneOpen,
                                mapOf(Swipe.Up to SceneClosed),
                                content = { OpenContainer() },
                            )
                        }
                    },
                    recordingSpec,
                )

            assertThat(motion).timeSeriesMatchesGolden(goldenName)
        }

    @Composable
    fun ContentScope.ClosedContainer() {
        Box(modifier = Modifier.fillMaxSize())
    }

    @Composable
    fun ContentScope.OpenContainer() {
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier.element(RevealElement)
                        .size(ContainerSize)
                        .verticalExpandContainerBackground(Color.DarkGray, motionSpec)
            )
        }
    }

    private class FakeHaptics : ContainerRevealHaptics {
        override fun onRevealThresholdCrossed(revealed: Boolean) {}
    }

    companion object {
        @get:Parameters @JvmStatic val parameterValues = listOf(true, false)

        val ContainerSize = DpSize(150.dp, 300.dp)

        val FlingVelocity = 1000.dp // dp/sec

        val SceneClosed = SceneKey("SceneA")
        val SceneOpen = SceneKey("SceneB")

        val RevealElement = ElementKey("RevealElement")
    }
}
