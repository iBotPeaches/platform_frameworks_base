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
import android.provider.Settings
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.internal.logging.UiEventLogger
import com.android.systemui.CoreStartable
import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.communal.shared.log.CommunalUiEvent
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.communal.shared.model.CommunalScenes.isCommunal
import com.android.systemui.communal.shared.model.CommunalTransitionKeys
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.DREAMING
import com.android.systemui.keyguard.shared.model.KeyguardState.GLANCEABLE_HUB
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.util.kotlin.BooleanFlowOperators.anyOf
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.kotlin.sample
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import com.android.systemui.util.settings.SystemSettings
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * A [CoreStartable] responsible for automatically navigating between communal scenes when certain
 * conditions are met.
 */
@SysUISingleton
class CommunalSceneStartable
@Inject
constructor(
    private val communalInteractor: CommunalInteractor,
    private val communalSettingsInteractor: CommunalSettingsInteractor,
    private val communalSceneInteractor: CommunalSceneInteractor,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val systemSettings: SystemSettings,
    private val notificationShadeWindowController: NotificationShadeWindowController,
    @Background private val bgScope: CoroutineScope,
    @Application private val applicationScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
    private val uiEventLogger: UiEventLogger,
) : CoreStartable {
    private var screenTimeout: Int = DEFAULT_SCREEN_TIMEOUT

    private var timeoutJob: Job? = null

    private var isDreaming: Boolean = false

    override fun start() {
        if (!communalSettingsInteractor.isCommunalFlagEnabled()) {
            return
        }

        bgScope.launch {
            communalSceneInteractor.isIdleOnCommunal.collectLatest {
                withContext(mainDispatcher) {
                    notificationShadeWindowController.setGlanceableHubShowing(it)
                }
            }
        }

        // In V2, the timeout is handled by PowerManagerService since we no longer keep the dream
        // active underneath the hub.
        if (!communalSettingsInteractor.isV2FlagEnabled()) {
            systemSettings
                .observerFlow(Settings.System.SCREEN_OFF_TIMEOUT)
                // Read the setting value on start.
                .emitOnStart()
                .onEach {
                    screenTimeout =
                        systemSettings.getIntForUser(
                            Settings.System.SCREEN_OFF_TIMEOUT,
                            DEFAULT_SCREEN_TIMEOUT,
                            UserHandle.USER_CURRENT,
                        )
                }
                .launchIn(bgScope)

            // The hub mode timeout should start as soon as the user enters hub mode. At the end of
            // the
            // timer, if the device is dreaming, hub mode should closed and reveal the dream. If the
            // dream is not running, nothing will happen. However if the dream starts again
            // underneath
            // hub mode after the initial timeout expires, such as if the device is docked or the
            // dream
            // app is updated by the Play store, a new timeout should be started.
            bgScope.launch {
                combine(
                        communalSceneInteractor.currentScene,
                        // Emit a value on start so the combine starts.
                        communalInteractor.userActivity.emitOnStart(),
                    ) { scene, _ ->
                        // Only timeout if we're on the hub is open.
                        scene.isCommunal()
                    }
                    .collectLatest { shouldTimeout ->
                        cancelHubTimeout()
                        if (shouldTimeout) {
                            startHubTimeout()
                        }
                    }
            }

            bgScope.launch {
                keyguardInteractor.isDreaming
                    .sample(communalSceneInteractor.currentScene, ::Pair)
                    .collectLatest { (isDreaming, scene) ->
                        this@CommunalSceneStartable.isDreaming = isDreaming
                        if (scene.isCommunal() && isDreaming && timeoutJob == null) {
                            // If dreaming starts after timeout has expired, ex. if dream restarts
                            // under
                            // the hub, wait for IS_ABLE_TO_DREAM_DELAY_MS and then close the hub.
                            // The
                            // delay is necessary so the KeyguardInteractor.isAbleToDream flow
                            // passes
                            // through that same amount of delay and publishes a new value which is
                            // then
                            // picked up by the HomeSceneFamilyResolver such that the next call to
                            // SceneInteractor.changeScene(Home) will resolve "Home" to "Dream".
                            delay(KeyguardInteractor.IS_ABLE_TO_DREAM_DELAY_MS)
                            communalSceneInteractor.changeScene(
                                CommunalScenes.Blank,
                                "dream started after timeout",
                            )
                            uiEventLogger.log(CommunalUiEvent.COMMUNAL_HUB_TIMEOUT)
                        }
                    }
            }
        }

        if (communalSettingsInteractor.isV2FlagEnabled()) {
            applicationScope.launch(context = mainDispatcher) {
                anyOf(
                        communalSceneInteractor.isTransitioningToOrIdleOnCommunal,
                        // when transitioning from hub to dream, allow hub to stay at the current
                        // orientation, as keyguard doesn't allow rotation by default.
                        keyguardTransitionInteractor.isInTransition(
                            edge = Edge.create(from = Scenes.Communal, to = DREAMING),
                            edgeWithoutSceneContainer =
                                Edge.create(from = GLANCEABLE_HUB, to = DREAMING),
                        ),
                    )
                    .distinctUntilChanged()
                    .collectLatest {
                        notificationShadeWindowController.setGlanceableHubOrientationAware(it)
                    }
            }
        }
    }

    private fun cancelHubTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun startHubTimeout() {
        if (timeoutJob == null) {
            timeoutJob =
                bgScope.launch {
                    delay(screenTimeout.milliseconds)
                    if (isDreaming) {
                        communalSceneInteractor.changeScene(
                            newScene = CommunalScenes.Blank,
                            loggingReason = "hub timeout",
                            transitionKey =
                                if (communalSettingsInteractor.isV2FlagEnabled())
                                    CommunalTransitionKeys.SimpleFade
                                else null,
                        )
                        uiEventLogger.log(CommunalUiEvent.COMMUNAL_HUB_TIMEOUT)
                    }
                    timeoutJob = null
                }
        }
    }

    companion object {
        val DEFAULT_SCREEN_TIMEOUT = 15000
    }
}
