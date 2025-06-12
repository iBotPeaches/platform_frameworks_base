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

package com.android.systemui.communal

import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.provider.Settings
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Flags.FLAG_COMMUNAL_HUB
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_V2
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.domain.interactor.setCommunalAvailable
import com.android.systemui.communal.domain.interactor.setCommunalV2ConfigEnabled
import com.android.systemui.communal.shared.log.CommunalUiEvent
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.flags.Flags.COMMUNAL_SERVICE_ENABLED
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.notificationShadeWindowController
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.verify
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableFlags(FLAG_COMMUNAL_HUB)
class CommunalSceneStartableTest(flags: FlagsParameterization) : SysuiTestCase() {

    companion object {
        private const val SCREEN_TIMEOUT = 1000

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_GLANCEABLE_HUB_V2)
                .andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            CommunalSceneStartable(
                communalInteractor = communalInteractor,
                communalSettingsInteractor = communalSettingsInteractor,
                communalSceneInteractor = communalSceneInteractor,
                keyguardTransitionInteractor = keyguardTransitionInteractor,
                keyguardInteractor = keyguardInteractor,
                systemSettings = fakeSettings,
                notificationShadeWindowController = notificationShadeWindowController,
                bgScope = applicationCoroutineScope,
                applicationScope = applicationCoroutineScope,
                mainDispatcher = testDispatcher,
                uiEventLogger = uiEventLoggerFake,
            )
        }

    @Before
    fun setUp() {
        with(kosmos) {
            fakeSettings.putIntForUser(
                Settings.System.SCREEN_OFF_TIMEOUT,
                SCREEN_TIMEOUT,
                UserHandle.USER_CURRENT,
            )
            fakeFeatureFlagsClassic.set(COMMUNAL_SERVICE_ENABLED, true)
            setCommunalV2ConfigEnabled(true)

            underTest.start()

            // Make communal available so that communalInteractor.desiredScene accurately reflects
            // scene changes instead of just returning Blank.
            runBlocking { setCommunalAvailable(true) }
        }
    }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_whenDreaming_goesToBlank() =
        kosmos.runTest {
            // Device is dreaming and on communal.
            fakeKeyguardRepository.setDreaming(true)
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Scene times out back to blank after the screen timeout.
            advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
            assertThat(scene).isEqualTo(CommunalScenes.Blank)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_notDreaming_staysOnCommunal() =
        kosmos.runTest {
            // Device is not dreaming and on communal.
            fakeKeyguardRepository.setDreaming(false)
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            // Scene stays as Communal
            advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_dreamStopped_staysOnCommunal() =
        kosmos.runTest {
            // Device is dreaming and on communal.
            fakeKeyguardRepository.setDreaming(true)
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Wait a bit, but not long enough to timeout.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Dream stops, timeout is cancelled and device stays on hub, because the regular
            // screen timeout will take effect at this point.
            fakeKeyguardRepository.setDreaming(false)
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_dreamStartedHalfway_goesToCommunal() =
        kosmos.runTest {
            // Device is on communal, but not dreaming.
            fakeKeyguardRepository.setDreaming(false)
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Wait a bit, but not long enough to timeout, then start dreaming.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            fakeKeyguardRepository.setDreaming(true)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Device times out after one screen timeout interval, dream doesn't reset timeout.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(CommunalScenes.Blank)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_dreamAfterInitialTimeout_goesToBlank() =
        kosmos.runTest {
            // Device is on communal.
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            // Device stays on the hub after the timeout since we're not dreaming.
            testScope.advanceTimeBy(SCREEN_TIMEOUT.milliseconds * 2)
            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Start dreaming.
            fakeKeyguardRepository.setDreaming(true)
            advanceTimeBy(KeyguardInteractor.IS_ABLE_TO_DREAM_DELAY_MS.milliseconds)

            // Hub times out immediately.
            assertThat(scene).isEqualTo(CommunalScenes.Blank)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_userActivityTriggered_resetsTimeout() =
        kosmos.runTest {
            // Device is dreaming and on communal.
            fakeKeyguardRepository.setDreaming(true)
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Wait a bit, but not long enough to timeout.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)

            // Send user interaction to reset timeout.
            communalInteractor.signalUserInteraction()

            // If user activity didn't reset timeout, we would have gone back to Blank by now.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Timeout happens one interval after the user interaction.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(CommunalScenes.Blank)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER, FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_screenTimeoutChanged() =
        kosmos.runTest {
            fakeSettings.putInt(Settings.System.SCREEN_OFF_TIMEOUT, SCREEN_TIMEOUT * 2)

            // Device is dreaming and on communal.
            fakeKeyguardRepository.setDreaming(true)
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            // Scene times out back to blank after the screen timeout.
            advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
            assertThat(scene).isEqualTo(CommunalScenes.Blank)
            assertThat(uiEventLoggerFake.logs.first().eventId)
                .isEqualTo(CommunalUiEvent.COMMUNAL_HUB_TIMEOUT.id)
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_withSceneContainer_whenDreaming_goesToBlank() =
        kosmos.runTest {
            // Device is dreaming and on communal.
            fakeKeyguardRepository.setDreaming(true)
            sceneInteractor.changeScene(Scenes.Communal, "test")

            val scene by collectLastValue(sceneInteractor.currentScene)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Scene times out back to blank after the screen timeout.
            advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
            assertThat(scene).isEqualTo(Scenes.Dream)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_withSceneContainer_notDreaming_staysOnCommunal() =
        kosmos.runTest {
            // Device is not dreaming and on communal.
            fakeKeyguardRepository.setDreaming(false)
            sceneInteractor.changeScene(Scenes.Communal, "test")

            // Scene stays as Communal
            advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
            val scene by collectLastValue(sceneInteractor.currentScene)
            assertThat(scene).isEqualTo(Scenes.Communal)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_withSceneContainer_dreamStopped_staysOnCommunal() =
        kosmos.runTest {
            // Device is dreaming and on communal.
            fakeKeyguardRepository.setDreaming(true)
            sceneInteractor.changeScene(Scenes.Communal, "test")

            val scene by collectLastValue(sceneInteractor.currentScene)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Wait a bit, but not long enough to timeout.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Dream stops, timeout is cancelled and device stays on hub, because the regular
            // screen timeout will take effect at this point.
            fakeKeyguardRepository.setDreaming(false)
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(Scenes.Communal)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_withSceneContainer_dreamStartedHalfway_goesToCommunal() =
        kosmos.runTest {
            // Device is on communal, but not dreaming.
            fakeKeyguardRepository.setDreaming(false)
            sceneInteractor.changeScene(Scenes.Communal, "test")

            val scene by collectLastValue(sceneInteractor.currentScene)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Wait a bit, but not long enough to timeout, then start dreaming.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            fakeKeyguardRepository.setDreaming(true)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Device times out after one screen timeout interval, dream doesn't reset timeout.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(Scenes.Dream)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_withSceneContainer_dreamAfterInitialTimeout_goesToBlank() =
        kosmos.runTest {
            // Device is on communal.
            sceneInteractor.changeScene(Scenes.Communal, "test")

            // Device stays on the hub after the timeout since we're not dreaming.
            advanceTimeBy(SCREEN_TIMEOUT.milliseconds * 2)
            val scene by collectLastValue(sceneInteractor.currentScene)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Start dreaming.
            fakeKeyguardRepository.setDreaming(true)
            advanceTimeBy(KeyguardInteractor.IS_ABLE_TO_DREAM_DELAY_MS.milliseconds)

            // Hub times out immediately.
            assertThat(scene).isEqualTo(Scenes.Dream)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_withSceneContainer_userActivityTriggered_resetsTimeout() =
        kosmos.runTest {
            // Device is dreaming and on communal.
            fakeKeyguardRepository.setDreaming(true)
            sceneInteractor.changeScene(Scenes.Communal, "test")

            val scene by collectLastValue(sceneInteractor.currentScene)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Wait a bit, but not long enough to timeout.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)

            // Send user interaction to reset timeout.
            communalInteractor.signalUserInteraction()

            // If user activity didn't reset timeout, we would have gone back to Blank by now.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Timeout happens one interval after the user interaction.
            advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
            assertThat(scene).isEqualTo(Scenes.Dream)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun hubTimeout_withSceneContainer_screenTimeoutChanged() =
        kosmos.runTest {
            fakeSettings.putInt(Settings.System.SCREEN_OFF_TIMEOUT, SCREEN_TIMEOUT * 2)

            // Device is dreaming and on communal.
            fakeKeyguardRepository.setDreaming(true)
            sceneInteractor.changeScene(Scenes.Communal, "test")

            val scene by collectLastValue(sceneInteractor.currentScene)
            assertThat(scene).isEqualTo(Scenes.Communal)

            // Scene times out back to blank after the screen timeout.
            advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
            assertThat(scene).isEqualTo(Scenes.Communal)

            advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
            assertThat(scene).isEqualTo(Scenes.Dream)
            assertThat(uiEventLoggerFake.logs.first().eventId)
                .isEqualTo(CommunalUiEvent.COMMUNAL_HUB_TIMEOUT.id)
            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun glanceableHubOrientationAware_idleOnCommunal() =
        kosmos.runTest {
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Communal)

            verify(notificationShadeWindowController).setGlanceableHubOrientationAware(true)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun glanceableHubOrientationAware_transitioningToCommunal() =
        kosmos.runTest {
            val progress = MutableStateFlow(0f)
            val targetScene = CommunalScenes.Communal
            val currentScene = CommunalScenes.Blank
            val transitionState =
                MutableStateFlow(
                    ObservableTransitionState.Transition(
                        fromScene = currentScene,
                        toScene = targetScene,
                        currentScene = flowOf(targetScene),
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            communalSceneInteractor.setTransitionState(transitionState)

            // Partially transition.
            progress.value = .4f

            val scene by collectLastValue(communalSceneInteractor.currentScene)
            assertThat(scene).isEqualTo(CommunalScenes.Blank)

            verify(notificationShadeWindowController).setGlanceableHubOrientationAware(true)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun glanceableHubOrientationAware_communalToDreaming() =
        kosmos.runTest {
            communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

            verify(notificationShadeWindowController).setGlanceableHubOrientationAware(true)
            Mockito.clearInvocations(notificationShadeWindowController)

            val progress = MutableStateFlow(0f)
            val currentScene = CommunalScenes.Communal
            val targetScene = CommunalScenes.Blank
            val transitionState =
                MutableStateFlow(
                    ObservableTransitionState.Transition(
                        fromScene = currentScene,
                        toScene = targetScene,
                        currentScene = flowOf(targetScene),
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            communalSceneInteractor.setTransitionState(transitionState)

            // Partially transitioned out of Communal scene
            progress.value = .4f

            // Started keyguard transitioning from hub -> dreaming.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.DREAMING,
                    transitionState = TransitionState.STARTED,
                )
            )
            verify(notificationShadeWindowController).setGlanceableHubOrientationAware(true)
            Mockito.clearInvocations(notificationShadeWindowController)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                from = KeyguardState.GLANCEABLE_HUB,
                to = KeyguardState.DREAMING,
                transitionState = TransitionState.RUNNING,
                value = 0.5f,
            )

            // Transitioned to dreaming.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                from = KeyguardState.GLANCEABLE_HUB,
                to = KeyguardState.DREAMING,
                transitionState = TransitionState.FINISHED,
                value = 1f,
            )
            // Not on hub anymore, let other states take control
            verify(notificationShadeWindowController).setGlanceableHubOrientationAware(false)
        }

    /**
     * Advances time by duration + 1 millisecond, to ensure that tasks scheduled to run at
     * currentTime + duration are scheduled.
     */
    private fun Kosmos.advanceTimeBy(duration: Duration) =
        testScope.advanceTimeBy(duration + 1.milliseconds)
}
