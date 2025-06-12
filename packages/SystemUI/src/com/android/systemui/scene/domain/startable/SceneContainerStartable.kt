/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.systemui.scene.domain.startable

import android.app.StatusBarManager
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.internal.logging.UiEventLogger
import com.android.keyguard.AuthInteractionProperties
import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor
import com.android.systemui.bouncer.domain.interactor.SimBouncerInteractor
import com.android.systemui.bouncer.shared.logging.BouncerUiEvent
import com.android.systemui.classifier.FalsingCollector
import com.android.systemui.classifier.FalsingCollectorActual
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryHapticsInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.keyguard.DismissCallbackRegistry
import com.android.systemui.keyguard.domain.interactor.KeyguardEnabledInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.TrustInteractor
import com.android.systemui.keyguard.domain.interactor.WindowManagerLockscreenVisibilityInteractor.Companion.keyguardContent
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.model.SceneContainerPlugin
import com.android.systemui.model.SysUiState
import com.android.systemui.model.updateFlags
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.FalsingManager.FalsingBeliefListener
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.data.model.sceneStackOf
import com.android.systemui.scene.domain.SceneFrameworkTableLog
import com.android.systemui.scene.domain.interactor.DisabledContentInteractor
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneContainerOcclusionInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.logger.SceneLogger
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.statusbar.SysuiStatusBarStateController
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.statusbar.phone.CentralSurfaces
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import com.android.systemui.util.asIndenting
import com.android.systemui.util.kotlin.getOrNull
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.kotlin.sample
import com.android.systemui.util.printSection
import com.android.systemui.util.println
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.MSDLPlayer
import dagger.Lazy
import java.io.PrintWriter
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Hooks up business logic that manipulates the state of the [SceneInteractor] for the system UI
 * scene container based on state from other systems.
 */
@SysUISingleton
class SceneContainerStartable
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val sceneInteractor: SceneInteractor,
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val deviceEntryHapticsInteractor: DeviceEntryHapticsInteractor,
    private val deviceUnlockedInteractor: DeviceUnlockedInteractor,
    private val bouncerInteractor: BouncerInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val sysUiState: SysUiState,
    private val sceneLogger: SceneLogger,
    @FalsingCollectorActual private val falsingCollector: FalsingCollector,
    private val falsingManager: FalsingManager,
    private val powerInteractor: PowerInteractor,
    private val simBouncerInteractor: Lazy<SimBouncerInteractor>,
    private val authenticationInteractor: Lazy<AuthenticationInteractor>,
    private val windowController: NotificationShadeWindowController,
    private val deviceProvisioningInteractor: DeviceProvisioningInteractor,
    private val centralSurfacesOptLazy: Lazy<Optional<CentralSurfaces>>,
    private val headsUpInteractor: HeadsUpNotificationInteractor,
    private val occlusionInteractor: SceneContainerOcclusionInteractor,
    private val faceUnlockInteractor: DeviceEntryFaceAuthInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val uiEventLogger: UiEventLogger,
    private val sceneBackInteractor: SceneBackInteractor,
    private val shadeSessionStorage: SessionStorage,
    private val keyguardEnabledInteractor: KeyguardEnabledInteractor,
    private val dismissCallbackRegistry: DismissCallbackRegistry,
    private val statusBarStateController: SysuiStatusBarStateController,
    private val alternateBouncerInteractor: AlternateBouncerInteractor,
    private val vibratorHelper: VibratorHelper,
    private val msdlPlayer: MSDLPlayer,
    private val disabledContentInteractor: DisabledContentInteractor,
    private val activityTransitionAnimator: ActivityTransitionAnimator,
    private val shadeModeInteractor: ShadeModeInteractor,
    @SceneFrameworkTableLog private val tableLogBuffer: TableLogBuffer,
    private val trustInteractor: TrustInteractor,
) : CoreStartable {
    private val centralSurfaces: CentralSurfaces?
        get() = centralSurfacesOptLazy.get().getOrNull()

    private val authInteractionProperties = AuthInteractionProperties()

    override fun start() {
        if (SceneContainerFlag.isEnabled) {
            sceneLogger.logFrameworkEnabled(isEnabled = true)
            applicationScope.launch { hydrateTableLogBuffer() }
            hydrateVisibility()
            automaticallySwitchScenes()
            hydrateSystemUiState()
            collectFalsingSignals()
            respondToFalsingDetections()
            hydrateInteractionState()
            handleBouncerOverscroll()
            handleDeviceEntryHapticsWhileDeviceLocked()
            hydrateWindowController()
            hydrateBackStack()
            resetShadeSessions()
            handleKeyguardEnabledness()
            notifyKeyguardDismissCancelledCallbacks()
            refreshLockscreenEnabled()
            hydrateActivityTransitionAnimationState()
            lockWhenDeviceBecomesUntrusted()
        } else {
            sceneLogger.logFrameworkEnabled(
                isEnabled = false,
                reason = SceneContainerFlag.requirementDescription(),
            )
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        with(pw.asIndenting()) {
            printSection("SceneContainerFlag") {
                printSection("Framework availability") {
                    println("isEnabled", SceneContainerFlag.isEnabled)
                    println(SceneContainerFlag.requirementDescription())
                }

                if (!SceneContainerFlag.isEnabled) {
                    return
                }

                printSection("Framework state") {
                    println("isVisible", sceneInteractor.isVisible.value)
                    println("currentScene", sceneInteractor.currentScene.value.debugName)
                    println(
                        "currentOverlays",
                        sceneInteractor.currentOverlays.value.joinToString(", ") { overlay ->
                            overlay.debugName
                        },
                    )
                    println("backStack", sceneBackInteractor.backStack.value)
                    println("shadeMode", shadeModeInteractor.shadeMode.value)
                }

                printSection("Authentication state") {
                    println("isKeyguardEnabled", keyguardEnabledInteractor.isKeyguardEnabled.value)
                    println(
                        "isUnlocked",
                        deviceUnlockedInteractor.deviceUnlockStatus.value.isUnlocked,
                    )
                    println("isDeviceEntered", deviceEntryInteractor.isDeviceEntered.value)
                    println(
                        "isFaceAuthEnabledAndEnrolled",
                        faceUnlockInteractor.isFaceAuthEnabledAndEnrolled(),
                    )
                    println("canSwipeToEnter", deviceEntryInteractor.canSwipeToEnter.value)
                }

                printSection("Power state") {
                    println("detailedWakefulness", powerInteractor.detailedWakefulness.value)
                    println("isDozing", keyguardInteractor.isDozing.value)
                    println("isAodAvailable", keyguardInteractor.isAodAvailable.value)
                }
            }
        }
    }

    private suspend fun hydrateTableLogBuffer() {
        coroutineScope {
            launch { sceneInteractor.hydrateTableLogBuffer(tableLogBuffer) }
            launch { keyguardEnabledInteractor.hydrateTableLogBuffer(tableLogBuffer) }
            launch { faceUnlockInteractor.hydrateTableLogBuffer(tableLogBuffer) }
            launch { powerInteractor.hydrateTableLogBuffer(tableLogBuffer) }
            launch { keyguardInteractor.hydrateTableLogBuffer(tableLogBuffer) }
        }
    }

    private fun resetShadeSessions() {
        applicationScope.launch {
            combine(
                    sceneBackInteractor.backStack
                        // We are in a session if either Shade or QuickSettings is on the back stack
                        .map { backStack ->
                            backStack.asIterable().any {
                                // TODO(b/356596436): Include overlays in the back stack as well.
                                it == Scenes.Shade || it == Scenes.QuickSettings
                            }
                        }
                        .distinctUntilChanged(),
                    // We are also in a session if either Notifications Shade or QuickSettings Shade
                    // is currently shown (whether idle or animating).
                    shadeInteractor.isAnyExpanded,
                ) { inBackStack, isShadeShown ->
                    inBackStack || isShadeShown
                }
                // Once a session has ended, clear the session storage.
                .filter { inSession -> !inSession }
                .collect { shadeSessionStorage.clear() }
        }
    }

    /** Updates the visibility of the scene container. */
    private fun hydrateVisibility() {
        applicationScope.launch {
            deviceProvisioningInteractor.isDeviceProvisioned
                .flatMapLatest { isAllowedToBeVisible ->
                    if (isAllowedToBeVisible) {
                        combine(
                                sceneInteractor.transitionState.mapNotNull { state ->
                                    when (state) {
                                        is ObservableTransitionState.Idle -> {
                                            if (state.currentScene == Scenes.Dream) {
                                                false to "dream is showing"
                                            } else if (state.currentScene != Scenes.Gone) {
                                                true to "scene is not Gone"
                                            } else if (state.currentOverlays.isNotEmpty()) {
                                                true to "overlay is shown"
                                            } else {
                                                false to "scene is Gone and no overlays are shown"
                                            }
                                        }
                                        is ObservableTransitionState.Transition -> {
                                            if (state.fromContent == Scenes.Gone) {
                                                true to "scene transitioning away from Gone"
                                            } else if (state.fromContent == Scenes.Dream) {
                                                true to "scene transitioning away from dream"
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                },
                                sceneInteractor.transitionState.map { state ->
                                    state.isTransitioningFromOrTo(Scenes.Communal) ||
                                        state.isIdle(Scenes.Communal)
                                },
                                headsUpInteractor.isHeadsUpOrAnimatingAway,
                                occlusionInteractor.invisibleDueToOcclusion,
                                alternateBouncerInteractor.isVisible,
                            ) {
                                visibilityForTransitionState,
                                isCommunalShowing,
                                isHeadsUpOrAnimatingAway,
                                invisibleDueToOcclusion,
                                isAlternateBouncerVisible ->
                                when {
                                    isCommunalShowing ->
                                        true to "on or transitioning to/from communal"
                                    isHeadsUpOrAnimatingAway -> true to "showing a HUN"
                                    isAlternateBouncerVisible -> true to "showing alternate bouncer"
                                    invisibleDueToOcclusion -> false to "invisible due to occlusion"
                                    else -> visibilityForTransitionState
                                }
                            }
                            .distinctUntilChanged()
                    } else {
                        flowOf(false to "Device not provisioned or Factory Reset Protection active")
                    }
                }
                .collect { (isVisible, loggingReason) ->
                    sceneInteractor.setVisible(isVisible, loggingReason)
                }
        }
    }

    /** Switches between scenes based on ever-changing application state. */
    private fun automaticallySwitchScenes() {
        handleBouncerImeVisibility()
        handleBouncerHiding()
        handleSimUnlock()
        handleDeviceUnlockStatus()
        handlePowerState()
        handleDreamState()
        handleShadeTouchability()
        handleDisableFlags()
    }

    private fun handleBouncerImeVisibility() {
        applicationScope.launch {
            // TODO (b/308001302): Move this to a bouncer specific interactor.
            bouncerInteractor.onImeHiddenByUser.collectLatest {
                sceneInteractor.hideOverlay(
                    overlay = Overlays.Bouncer,
                    loggingReason = "IME hidden.",
                )
            }
        }
    }

    private fun handleBouncerHiding() {
        applicationScope.launch {
            repeatWhen(
                condition =
                    authenticationInteractor
                        .get()
                        .authenticationMethod
                        .map { !it.isSecure }
                        .distinctUntilChanged()
            ) {
                sceneInteractor.hideOverlay(
                    overlay = Overlays.Bouncer,
                    loggingReason = "Authentication method changed to a non-secure one.",
                )
            }
        }
    }

    private fun handleSimUnlock() {
        applicationScope.launch {
            simBouncerInteractor
                .get()
                .isAnySimSecure
                .sample(deviceUnlockedInteractor.deviceUnlockStatus, ::Pair)
                .collect { (isAnySimLocked, unlockStatus) ->
                    when {
                        isAnySimLocked -> {
                            sceneInteractor.showOverlay(
                                overlay = Overlays.Bouncer,
                                loggingReason = "Need to authenticate locked SIM card.",
                            )
                        }
                        unlockStatus.isUnlocked &&
                            deviceEntryInteractor.canSwipeToEnter.value == false -> {
                            val loggingReason =
                                "All SIM cards unlocked and device already unlocked and " +
                                    "lockscreen doesn't require a swipe to dismiss."
                            switchToScene(
                                targetSceneKey = Scenes.Gone,
                                loggingReason = loggingReason,
                            )
                        }
                        else -> {
                            val loggingReason =
                                "All SIM cards unlocked and device still locked" +
                                    " or lockscreen still requires a swipe to dismiss."
                            switchToScene(
                                targetSceneKey = Scenes.Lockscreen,
                                loggingReason = loggingReason,
                            )
                        }
                    }
                }
        }
    }

    private fun handleDeviceUnlockStatus() {
        applicationScope.launch {
            // Track the previous scene, so that we know where to go when the device is unlocked
            // whilst on the bouncer.
            val previousScene =
                sceneBackInteractor.backScene.stateIn(
                    this,
                    SharingStarted.Eagerly,
                    initialValue = null,
                )
            deviceUnlockedInteractor.deviceUnlockStatus
                .mapNotNull { deviceUnlockStatus ->
                    val (renderedScenes: List<SceneKey>, renderedOverlays) =
                        when (val transitionState = sceneInteractor.transitionState.value) {
                            is ObservableTransitionState.Idle ->
                                listOf(transitionState.currentScene) to
                                    transitionState.currentOverlays
                            is ObservableTransitionState.Transition.ChangeScene ->
                                listOf(transitionState.fromScene, transitionState.toScene) to
                                    transitionState.currentOverlays
                            is ObservableTransitionState.Transition.OverlayTransition ->
                                listOf(transitionState.currentScene) to
                                    setOfNotNull(
                                        transitionState.toContent.takeIf { it is OverlayKey },
                                        transitionState.fromContent.takeIf { it is OverlayKey },
                                    )
                        }
                    val isOnLockscreen = renderedScenes.contains(Scenes.Lockscreen)
                    val isAlternateBouncerVisible = alternateBouncerInteractor.isVisibleState()
                    val isOnPrimaryBouncer = Overlays.Bouncer in renderedOverlays
                    if (!deviceUnlockStatus.isUnlocked) {
                        return@mapNotNull if (
                            renderedScenes.any { it in keyguardContent } ||
                                Overlays.Bouncer in renderedOverlays
                        ) {
                            // Already on a keyguard scene or bouncer, no need to change scenes.
                            null
                        } else {
                            // The device locked while on a scene that's not a keyguard scene, go
                            // to Lockscreen.
                            Scenes.Lockscreen to "device locked in a non-keyguard scene"
                        }
                    }

                    if (powerInteractor.detailedWakefulness.value.isAsleep()) {
                        // The logic below is for when the device becomes unlocked. That must be a
                        // no-op if the device is not awake.
                        return@mapNotNull null
                    }

                    if (
                        isOnPrimaryBouncer &&
                            deviceUnlockStatus.deviceUnlockSource == DeviceUnlockSource.TrustAgent
                    ) {
                        uiEventLogger.log(BouncerUiEvent.BOUNCER_DISMISS_EXTENDED_ACCESS)
                    }
                    when {
                        isAlternateBouncerVisible -> {
                            // When the device becomes unlocked when the alternate bouncer is
                            // showing, always hide the alternate bouncer and notify dismiss
                            // succeeded
                            alternateBouncerInteractor.hide()
                            dismissCallbackRegistry.notifyDismissSucceeded()

                            // ... and go to Gone or stay on the current scene
                            if (
                                isOnLockscreen ||
                                    !statusBarStateController.leaveOpenOnKeyguardHide()
                            ) {
                                Scenes.Gone to
                                    "device was unlocked with alternate bouncer showing" +
                                        " and shade didn't need to be left open"
                            } else {
                                replaceLockscreenSceneOnBackStack()
                                null
                            }
                        }
                        isOnPrimaryBouncer -> {
                            // When the device becomes unlocked in primary Bouncer,
                            // notify dismiss succeeded and remain in current scene or switch to
                            // Gone.
                            dismissCallbackRegistry.notifyDismissSucceeded()
                            // if transition is a scene change, take the destination scene
                            val targetScene = renderedScenes.last()
                            if (
                                targetScene == Scenes.Lockscreen ||
                                    !statusBarStateController.leaveOpenOnKeyguardHide()
                            ) {
                                Scenes.Gone to
                                    "device was unlocked with bouncer showing and shade" +
                                        " didn't need to be left open"
                            } else {
                                if (previousScene.value != Scenes.Gone) {
                                    replaceLockscreenSceneOnBackStack()
                                }
                                targetScene to
                                    "device was unlocked with primary bouncer showing," +
                                        " from sceneKey=$targetScene"
                            }
                        }
                        isOnLockscreen ->
                            // The lockscreen should be dismissed automatically in 2 scenarios:
                            // 1. When face auth bypass is enabled and authentication happens while
                            //    the user is on the lockscreen.
                            // 2. Whenever the user authenticates using an active authentication
                            //    mechanism like fingerprint auth. Since canSwipeToEnter is true
                            //    when the user is passively authenticated, the false value here
                            //    when the unlock state changes indicates this is an active
                            //    authentication attempt.
                            when {
                                deviceUnlockStatus.deviceUnlockSource?.dismissesLockscreen ==
                                    true ->
                                    Scenes.Gone to
                                        "device has been unlocked on lockscreen with bypass " +
                                            "enabled or using an active authentication " +
                                            "mechanism: ${deviceUnlockStatus.deviceUnlockSource}"
                                else -> null
                            }
                        // Not on lockscreen or bouncer, so remain in the current scene but since
                        // unlocked, replace the Lockscreen scene from the bottom of the navigation
                        // back stack with the Gone scene.
                        else -> {
                            replaceLockscreenSceneOnBackStack()
                            null
                        }
                    }
                }
                .collect { (targetSceneKey, loggingReason) ->
                    switchToScene(targetSceneKey = targetSceneKey, loggingReason = loggingReason)
                }
        }
    }

    /**
     * If the [Scenes.Lockscreen] is on the bottom of the navigation backstack, replaces it with
     * [Scenes.Gone].
     */
    private fun replaceLockscreenSceneOnBackStack() {
        sceneBackInteractor.updateBackStack { stack ->
            val list = stack.asIterable().toMutableList()
            if (list.lastOrNull() == Scenes.Lockscreen) {
                list[list.size - 1] = Scenes.Gone
                sceneStackOf(*list.toTypedArray())
            } else {
                stack
            }
        }
    }

    private fun handlePowerState() {
        applicationScope.launch {
            powerInteractor.detailedWakefulness.collect { wakefulness ->
                // Detect a double-tap-power-button gesture that was started while the device was
                // still awake.
                if (wakefulness.isAsleep()) return@collect
                if (!wakefulness.powerButtonLaunchGestureTriggered) return@collect
                if (wakefulness.lastSleepReason != WakeSleepReason.POWER_BUTTON) return@collect

                // If we're mid-transition from Gone to Lockscreen due to the first power button
                // press, then return to Gone.
                val transition: ObservableTransitionState.Transition =
                    sceneInteractor.transitionState.value as? ObservableTransitionState.Transition
                        ?: return@collect
                if (
                    transition.fromContent == Scenes.Gone &&
                        transition.toContent == Scenes.Lockscreen
                ) {
                    switchToScene(
                        targetSceneKey = Scenes.Gone,
                        loggingReason = "double-tap power gesture",
                    )
                }
            }
        }
        applicationScope.launch {
            powerInteractor.isAsleep.collect { isAsleep ->
                if (isAsleep) {
                    alternateBouncerInteractor.hide()
                    dismissCallbackRegistry.notifyDismissCancelled()

                    switchToScene(
                        targetSceneKey = Scenes.Lockscreen,
                        loggingReason = "device is starting to sleep",
                        sceneState = keyguardInteractor.asleepKeyguardState.value,
                        freezeAndAnimateToCurrentState = true,
                    )
                } else {
                    val canSwipeToEnter = deviceEntryInteractor.canSwipeToEnter.value
                    val isUnlocked = deviceUnlockedInteractor.deviceUnlockStatus.value.isUnlocked
                    if (isUnlocked && canSwipeToEnter == false) {
                        val isTransitioningToLockscreen =
                            sceneInteractor.transitioningTo.value == Scenes.Lockscreen
                        if (!isTransitioningToLockscreen) {
                            switchToScene(
                                targetSceneKey = Scenes.Gone,
                                loggingReason =
                                    "device is waking up while unlocked without the ability to" +
                                        " swipe up on lockscreen to enter and not on or" +
                                        " transitioning to, the lockscreen scene.",
                            )
                        }
                    } else if (
                        authenticationInteractor.get().getAuthenticationMethod() ==
                            AuthenticationMethodModel.Sim
                    ) {
                        sceneInteractor.showOverlay(
                            overlay = Overlays.Bouncer,
                            loggingReason = "device is starting to wake up with a locked sim",
                        )
                    }
                }
            }
        }
    }

    private fun handleDreamState() {
        applicationScope.launch {
            keyguardInteractor.isAbleToDream
                .sample(sceneInteractor.transitionState, ::Pair)
                .collect { (isAbleToDream, transitionState) ->
                    if (transitionState.isIdle(Scenes.Communal)) {
                        // The dream is automatically started underneath the hub, don't transition
                        // to dream when this is happening as communal is still visible on top.
                        return@collect
                    }
                    if (isAbleToDream) {
                        switchToScene(
                            targetSceneKey = Scenes.Dream,
                            loggingReason = "dream started",
                        )
                    } else {
                        switchToScene(
                            targetSceneKey = SceneFamilies.Home,
                            loggingReason = "dream stopped",
                        )
                    }
                }
        }
    }

    private fun handleShadeTouchability() {
        applicationScope.launch {
            repeatWhen(deviceEntryInteractor.isDeviceEntered.map { !it }) {
                // Run logic only when the device isn't entered.
                repeatWhen(
                    sceneInteractor.transitionState.map { !it.isTransitioning(to = Scenes.Gone) }
                ) {
                    // Run logic only when not transitioning to gone.
                    shadeInteractor.isShadeTouchable
                        .distinctUntilChanged()
                        .filter { !it }
                        .collect {
                            switchToScene(
                                targetSceneKey = Scenes.Lockscreen,
                                loggingReason =
                                    "device became non-interactive (SceneContainerStartable)",
                            )
                        }
                }
            }
        }
    }

    private fun handleDisableFlags() {
        applicationScope.launch {
            launch {
                sceneInteractor.currentScene.collectLatest { currentScene ->
                    disabledContentInteractor.repeatWhenDisabled(currentScene) {
                        switchToScene(
                            targetSceneKey = SceneFamilies.Home,
                            loggingReason =
                                "Current scene ${currentScene.debugName} became" + " disabled",
                        )
                    }
                }
            }

            launch {
                sceneInteractor.currentOverlays.collectLatest { overlays ->
                    overlays.forEach { overlay ->
                        launch {
                            disabledContentInteractor.repeatWhenDisabled(overlay) {
                                sceneInteractor.hideOverlay(
                                    overlay = overlay,
                                    loggingReason =
                                        "Overlay ${overlay.debugName} became" + " disabled",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleDeviceEntryHapticsWhileDeviceLocked() {
        applicationScope.launch {
            deviceEntryInteractor.isDeviceEntered.collectLatest { isDeviceEntered ->
                // Only check for haptics signals before device is entered
                if (!isDeviceEntered) {
                    coroutineScope {
                        launch {
                            deviceEntryHapticsInteractor.playSuccessHapticOnDeviceEntry
                                .sample(sceneInteractor.currentScene)
                                .collect { currentScene ->
                                    if (Flags.msdlFeedback()) {
                                        msdlPlayer.playToken(
                                            MSDLToken.UNLOCK,
                                            authInteractionProperties,
                                        )
                                    } else {
                                        vibratorHelper.vibrateAuthSuccess(
                                            "$TAG, $currentScene device-entry::success"
                                        )
                                    }
                                }
                        }

                        launch {
                            deviceEntryHapticsInteractor.playErrorHaptic
                                .sample(sceneInteractor.currentScene)
                                .collect { currentScene ->
                                    if (Flags.msdlFeedback()) {
                                        msdlPlayer.playToken(
                                            MSDLToken.FAILURE,
                                            authInteractionProperties,
                                        )
                                    } else {
                                        vibratorHelper.vibrateAuthError(
                                            "$TAG, $currentScene device-entry::error"
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    /** Keeps [SysUiState] up-to-date */
    private fun hydrateSystemUiState() {
        applicationScope.launch {
            combine(
                    sceneInteractor.transitionState
                        .mapNotNull { it as? ObservableTransitionState.Idle }
                        .distinctUntilChanged(),
                    sceneInteractor.isVisible,
                    occlusionInteractor.invisibleDueToOcclusion,
                ) { idleState, isVisible, invisibleDueToOcclusion ->
                    SceneContainerPlugin.SceneContainerPluginState(
                        scene = idleState.currentScene,
                        overlays = idleState.currentOverlays,
                        isVisible = isVisible,
                        invisibleDueToOcclusion = invisibleDueToOcclusion,
                    )
                }
                .map { sceneContainerPluginState ->
                    SceneContainerPlugin.EvaluatorByFlag.map { (flag, evaluator) ->
                            flag to evaluator(sceneContainerPluginState)
                        }
                        .toMap()
                }
                .distinctUntilChanged()
                .collect { flags ->
                    sysUiState.updateFlags(
                        *(flags.entries.map { (key, value) -> key to value }).toTypedArray()
                    )
                }
        }
    }

    private fun hydrateWindowController() {
        applicationScope.launch {
            sceneInteractor.transitionState
                .mapNotNull { transitionState ->
                    (transitionState as? ObservableTransitionState.Idle)?.currentScene
                }
                .distinctUntilChanged()
                .collect { sceneKey ->
                    windowController.setNotificationShadeFocusable(sceneKey != Scenes.Gone)
                }
        }

        applicationScope.launch {
            deviceEntryInteractor.isDeviceEntered.collect { isDeviceEntered ->
                windowController.setKeyguardShowing(!isDeviceEntered)
            }
        }

        applicationScope.launch {
            occlusionInteractor.invisibleDueToOcclusion.collect { invisibleDueToOcclusion ->
                windowController.setKeyguardOccluded(invisibleDueToOcclusion)
            }
        }
    }

    /** Collects and reports signals into the falsing system. */
    private fun collectFalsingSignals() {
        applicationScope.launch {
            deviceEntryInteractor.isDeviceEntered.collect { isLockscreenDismissed ->
                if (isLockscreenDismissed) {
                    falsingCollector.onSuccessfulUnlock()
                }
            }
        }

        applicationScope.launch {
            keyguardInteractor.isDozing.collect { isDozing ->
                falsingCollector.setShowingAod(isDozing)
            }
        }

        applicationScope.launch {
            powerInteractor.detailedWakefulness
                .distinctUntilChangedBy { it.isAwake() }
                .collect { wakefulness ->
                    when {
                        wakefulness.isAwakeFromTouch() -> falsingCollector.onScreenOnFromTouch()
                        wakefulness.isAwake() -> falsingCollector.onScreenTurningOn()
                        wakefulness.isAsleep() -> falsingCollector.onScreenOff()
                    }
                }
        }

        applicationScope.launch {
            sceneInteractor.currentOverlays
                .map { Overlays.Bouncer in it }
                .distinctUntilChanged()
                .collect { switchedToBouncerOverlay ->
                    if (switchedToBouncerOverlay) {
                        falsingCollector.onBouncerShown()
                    } else {
                        falsingCollector.onBouncerHidden()
                    }
                }
        }
    }

    /** Switches to the lockscreen when falsing is detected. */
    private fun respondToFalsingDetections() {
        applicationScope.launch {
            conflatedCallbackFlow {
                    val listener = FalsingBeliefListener { trySend(Unit) }
                    falsingManager.addFalsingBeliefListener(listener)
                    awaitClose { falsingManager.removeFalsingBeliefListener(listener) }
                }
                .collect {
                    val loggingReason = "Falsing detected."
                    switchToScene(targetSceneKey = Scenes.Lockscreen, loggingReason = loggingReason)
                }
        }
    }

    /** Keeps the interaction state of [CentralSurfaces] up-to-date. */
    private fun hydrateInteractionState() {
        applicationScope.launch {
            deviceUnlockedInteractor.deviceUnlockStatus
                .map { !it.isUnlocked }
                .flatMapLatest { isDeviceLocked ->
                    if (isDeviceLocked) {
                        sceneInteractor.transitionState
                            .mapNotNull { it as? ObservableTransitionState.Idle }
                            .map { it.currentScene to it.currentOverlays }
                            .distinctUntilChanged()
                            .map { (currentScene, currentOverlays) ->
                                when {
                                    // When locked, showing the lockscreen scene should be reported
                                    // as "interacting" while showing other scenes should report as
                                    // "not interacting".
                                    //
                                    // This is done here in order to match the legacy
                                    // implementation. The real reason why is lost to lore and myth.
                                    Overlays.NotificationsShade in currentOverlays -> false
                                    Overlays.QuickSettingsShade in currentOverlays -> null
                                    Overlays.Bouncer in currentOverlays -> false
                                    currentScene == Scenes.Lockscreen -> true
                                    currentScene == Scenes.Shade -> false
                                    else -> null
                                }
                            }
                    } else {
                        flowOf(null)
                    }
                }
                .collect { isInteractingOrNull ->
                    isInteractingOrNull?.let { isInteracting ->
                        centralSurfaces?.setInteracting(
                            StatusBarManager.WINDOW_STATUS_BAR,
                            isInteracting,
                        )
                    }
                }
        }
    }

    private fun handleBouncerOverscroll() {
        applicationScope.launch {
            sceneInteractor.transitionState
                // Only consider transitions.
                .filterIsInstance<ObservableTransitionState.Transition>()
                // Only consider user-initiated (e.g. drags) that go from bouncer to lockscreen.
                .filter { transition ->
                    transition.fromContent == Overlays.Bouncer &&
                        transition.toContent == Scenes.Lockscreen &&
                        transition.isInitiatedByUserInput
                }
                .flatMapLatest { it.progress }
                // Figure out the direction of scrolling.
                .map { progress ->
                    when {
                        progress > 0 -> 1
                        progress < 0 -> -1
                        else -> 0
                    }
                }
                .distinctUntilChanged()
                // Only consider negative scrolling, AKA overscroll.
                .filter { it == -1 }
                .collect { faceUnlockInteractor.onSwipeUpOnBouncer() }
        }
    }

    private fun handleKeyguardEnabledness() {
        // Automatically switches scenes when keyguard is enabled or disabled, as needed.
        applicationScope.launch {
            keyguardEnabledInteractor.isKeyguardEnabled
                .sample(
                    combine(
                        deviceUnlockedInteractor.isInLockdown,
                        deviceEntryInteractor.isDeviceEntered,
                        ::Pair,
                    )
                ) { isKeyguardEnabled, (isInLockdown, isDeviceEntered) ->
                    when {
                        !isKeyguardEnabled && !isInLockdown && !isDeviceEntered -> {
                            keyguardEnabledInteractor.setShowKeyguardWhenReenabled(true)
                            Scenes.Gone to "Keyguard became disabled"
                        }
                        isKeyguardEnabled &&
                            keyguardEnabledInteractor.isShowKeyguardWhenReenabled() -> {
                            keyguardEnabledInteractor.setShowKeyguardWhenReenabled(false)
                            Scenes.Lockscreen to "Keyguard became enabled"
                        }
                        else -> null
                    }
                }
                .filterNotNull()
                .collect { (targetScene, loggingReason) ->
                    switchToScene(targetScene, loggingReason)
                }
        }

        // Clears the showKeyguardWhenReenabled if the auth method changes to an insecure one.
        applicationScope.launch {
            authenticationInteractor
                .get()
                .authenticationMethod
                .map { it.isSecure }
                .distinctUntilChanged()
                .collect { isAuthenticationMethodSecure ->
                    if (!isAuthenticationMethodSecure) {
                        keyguardEnabledInteractor.setShowKeyguardWhenReenabled(false)
                    }
                }
        }
    }

    private fun switchToScene(
        targetSceneKey: SceneKey,
        loggingReason: String,
        sceneState: Any? = null,
        freezeAndAnimateToCurrentState: Boolean = false,
    ) {
        sceneInteractor.changeScene(
            toScene = targetSceneKey,
            loggingReason = loggingReason,
            sceneState = sceneState,
            forceSettleToTargetScene = freezeAndAnimateToCurrentState,
        )
    }

    private fun hydrateBackStack() {
        applicationScope.launch {
            sceneInteractor.currentScene.pairwise().collect { (from, to) ->
                sceneBackInteractor.onSceneChange(from = from, to = to)
            }
        }
    }

    private fun notifyKeyguardDismissCancelledCallbacks() {
        applicationScope.launch {
            combine(deviceEntryInteractor.isUnlocked, sceneInteractor.currentOverlays.pairwise()) {
                    isUnlocked,
                    overlayChange ->
                    val difference = overlayChange.previousValue - overlayChange.newValue
                    !isUnlocked &&
                        sceneInteractor.currentScene.value != Scenes.Gone &&
                        Overlays.Bouncer in difference
                }
                .collect { notifyKeyguardDismissCancelled ->
                    if (notifyKeyguardDismissCancelled) {
                        dismissCallbackRegistry.notifyDismissCancelled()
                    }
                }
        }
    }

    /**
     * Keeps the value of [DeviceEntryInteractor.isLockscreenEnabled] fresh.
     *
     * This is needed because that value is sourced from a non-observable data source
     * (`LockPatternUtils`, which doesn't expose a listener or callback for this value). Therefore,
     * every time a transition to the `Lockscreen` scene is started, the value is re-fetched and
     * cached.
     */
    private fun refreshLockscreenEnabled() {
        applicationScope.launch {
            sceneInteractor.transitionState
                .map { it.isTransitioning(to = Scenes.Lockscreen) }
                .distinctUntilChanged()
                .filter { it }
                .collectLatest { deviceEntryInteractor.refreshLockscreenEnabled() }
        }
    }

    /**
     * Wires the scene framework to activity transition animations that originate from anywhere. A
     * subset of these may actually originate from UI inside one of the scenes in the framework.
     *
     * Telling the scene framework about ongoing activity transition animations is critical so the
     * scene framework doesn't make its scene container invisible during a transition.
     *
     * As it turns out, making the scene container view invisible during a transition animation
     * disrupts the animation and causes interaction jank CUJ tracking to ignore reports of the CUJ
     * ending or being canceled.
     */
    private fun hydrateActivityTransitionAnimationState() {
        activityTransitionAnimator.addListener(
            object : ActivityTransitionAnimator.Listener {
                override fun onTransitionAnimationStart() {
                    sceneInteractor.onTransitionAnimationStart()
                }

                override fun onTransitionAnimationEnd() {
                    sceneInteractor.onTransitionAnimationEnd()
                }
            }
        )
    }

    private fun lockWhenDeviceBecomesUntrusted() {
        applicationScope.launch {
            trustInteractor.isTrusted.pairwise().collect { (wasTrusted, isTrusted) ->
                if (wasTrusted && !isTrusted && !deviceEntryInteractor.isDeviceEntered.value) {
                    deviceEntryInteractor.lockNow(
                        "Exited trusted environment while not device not entered"
                    )
                }
            }
        }
    }

    private suspend fun repeatWhen(condition: Flow<Boolean>, block: suspend () -> Unit) {
        condition.distinctUntilChanged().collectLatest { conditionMet ->
            if (conditionMet) {
                block()
            }
        }
    }

    companion object {
        private const val TAG = "SceneContainerStartable"
    }
}
