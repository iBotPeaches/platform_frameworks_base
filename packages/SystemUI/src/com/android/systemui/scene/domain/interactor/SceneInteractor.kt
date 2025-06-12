/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.scene.domain.interactor

import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardEnabledInteractor
import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.TableRowLogger
import com.android.systemui.scene.data.repository.SceneContainerRepository
import com.android.systemui.scene.domain.resolver.SceneResolver
import com.android.systemui.scene.shared.logger.SceneLogger
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.util.kotlin.pairwise
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Generic business logic and app state accessors for the scene framework.
 *
 * Note that this class should not depend on state or logic of other modules or features. Instead,
 * other feature modules should depend on and call into this class when their parts of the
 * application state change.
 */
@SysUISingleton
class SceneInteractor
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val repository: SceneContainerRepository,
    private val logger: SceneLogger,
    private val sceneFamilyResolvers: Lazy<Map<SceneKey, @JvmSuppressWildcards SceneResolver>>,
    private val deviceUnlockedInteractor: Lazy<DeviceUnlockedInteractor>,
    private val keyguardEnabledInteractor: Lazy<KeyguardEnabledInteractor>,
    private val disabledContentInteractor: DisabledContentInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
) {

    interface OnSceneAboutToChangeListener {

        /**
         * Notifies that the scene is about to change to [toScene].
         *
         * The implementation can choose to consume the [sceneState] to prepare the incoming scene.
         */
        fun onSceneAboutToChange(toScene: SceneKey, sceneState: Any?)
    }

    private val onSceneAboutToChangeListener = mutableSetOf<OnSceneAboutToChangeListener>()

    /**
     * The keys of all scenes and overlays in the container.
     *
     * They will be sorted in z-order such that the last one is the one that should be rendered on
     * top of all previous ones.
     */
    val allContentKeys: List<ContentKey> = repository.allContentKeys

    /**
     * The current scene.
     *
     * Note that during a transition between scenes, more than one scene might be rendered but only
     * one is considered the committed/current scene.
     */
    val currentScene: StateFlow<SceneKey> = repository.currentScene

    /**
     * The current set of overlays to be shown (may be empty).
     *
     * Note that during a transition between overlays, a different set of overlays may be rendered -
     * but only the ones in this set are considered the current overlays.
     */
    val currentOverlays: StateFlow<Set<OverlayKey>> = repository.currentOverlays

    /**
     * The current state of the transition.
     *
     * Consumers should use this state to know:
     * 1. Whether there is an ongoing transition or if the system is at rest.
     * 2. When transitioning, which scenes are being transitioned between.
     * 3. When transitioning, what the progress of the transition is.
     */
    val transitionState: StateFlow<ObservableTransitionState> =
        repository.transitionState
            .onEach { logger.logSceneTransition(it) }
            .stateInTraced(
                name = "transitionState",
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = repository.transitionState.value,
            )

    /**
     * The key of the content that the UI is currently transitioning to or `null` if there is no
     * active transition at the moment.
     *
     * This is a convenience wrapper around [transitionState], meant for flow-challenged consumers
     * like Java code.
     */
    val transitioningTo: StateFlow<ContentKey?> =
        transitionState
            .map { state ->
                when (state) {
                    is ObservableTransitionState.Idle -> null
                    is ObservableTransitionState.Transition -> state.toContent
                }
            }
            .stateInTraced(
                name = "transitioningTo",
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = null,
            )

    /**
     * Whether user input is ongoing for the current transition. For example, if the user is swiping
     * their finger to transition between scenes, this value will be true while their finger is on
     * the screen, then false for the rest of the transition.
     */
    val isTransitionUserInputOngoing: StateFlow<Boolean> =
        transitionState
            .flatMapLatest {
                when (it) {
                    is ObservableTransitionState.Transition -> it.isUserInputOngoing
                    is ObservableTransitionState.Idle -> flowOf(false)
                }
            }
            .stateInTraced(
                name = "isTransitionUserInputOngoing",
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    /** Whether the scene container is visible. */
    val isVisible: StateFlow<Boolean> =
        combine(
                repository.isVisible,
                repository.isRemoteUserInputOngoing,
                repository.activeTransitionAnimationCount,
            ) { isVisible, isRemoteUserInteractionOngoing, activeTransitionAnimationCount ->
                isVisibleInternal(
                    raw = isVisible,
                    isRemoteUserInputOngoing = isRemoteUserInteractionOngoing,
                    activeTransitionAnimationCount = activeTransitionAnimationCount,
                )
            }
            .stateInTraced(
                name = "isVisible",
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = isVisibleInternal(),
            )

    /** Whether there's an ongoing remotely-initiated user interaction. */
    val isRemoteUserInteractionOngoing: StateFlow<Boolean> = repository.isRemoteUserInputOngoing

    /**
     * Whether there's an ongoing user interaction started in the scene container Compose hierarchy.
     */
    val isSceneContainerUserInputOngoing: StateFlow<Boolean> =
        repository.isSceneContainerUserInputOngoing

    /**
     * The amount of transition into or out of the given [content].
     *
     * The value will be `0` if not in this scene or `1` when fully in the given scene.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun transitionProgress(content: ContentKey): Flow<Float> {
        return transitionState.flatMapLatest { transition ->
            when (transition) {
                is ObservableTransitionState.Idle -> {
                    flowOf(
                        if (
                            transition.currentScene == content ||
                                content in transition.currentOverlays
                        ) {
                            1f
                        } else {
                            0f
                        }
                    )
                }
                is ObservableTransitionState.Transition -> {
                    when {
                        transition.toContent == content -> transition.progress
                        transition.fromContent == content -> transition.progress.map { 1f - it }
                        else -> flowOf(0f)
                    }
                }
            }
        }
    }

    fun registerSceneStateProcessor(processor: OnSceneAboutToChangeListener) {
        onSceneAboutToChangeListener.add(processor)
    }

    /**
     * Requests a scene change to the given scene.
     *
     * The change is animated. Therefore, it will be some time before the UI will switch to the
     * desired scene. Once enough of the transition has occurred, the [currentScene] will become
     * [toScene] (unless the transition is canceled by user action or another call to this method).
     *
     * If [forceSettleToTargetScene] is `true` and the target scene is the same as the current
     * scene, any current transition will be canceled and an animation to the target scene will be
     * started.
     *
     * If [Overlays.Bouncer] is showing, we trigger an instant scene change as it will not be user-
     * visible, and trigger a transition to hide the bouncer.
     */
    @JvmOverloads
    fun changeScene(
        toScene: SceneKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
        sceneState: Any? = null,
        forceSettleToTargetScene: Boolean = false,
    ) {
        val currentSceneKey = currentScene.value
        val resolvedScene = sceneFamilyResolvers.get()[toScene]?.resolvedScene?.value ?: toScene
        val bouncerShowing = Overlays.Bouncer in currentOverlays.value

        if (resolvedScene == currentSceneKey && forceSettleToTargetScene) {
            logger.logSceneChangeCancellation(scene = resolvedScene, sceneState = sceneState)
            onSceneAboutToChangeListener.forEach {
                it.onSceneAboutToChange(resolvedScene, sceneState)
            }
            repository.freezeAndAnimateToCurrentState()
        }

        if (
            !validateSceneChange(
                from = currentSceneKey,
                to = resolvedScene,
                loggingReason = loggingReason,
            )
        ) {
            if (bouncerShowing) {
                hideOverlay(
                    Overlays.Bouncer,
                    "Scene change cancelled but hiding bouncer for: ($loggingReason)",
                )
            }
            return
        }

        onSceneAboutToChangeListener.forEach { it.onSceneAboutToChange(resolvedScene, sceneState) }

        logger.logSceneChanged(
            from = currentSceneKey,
            to = resolvedScene,
            sceneState = sceneState,
            reason = loggingReason,
            isInstant = false,
        )

        if (bouncerShowing) {
            repository.snapToScene(resolvedScene)
            hideOverlay(Overlays.Bouncer, "Hiding on changeScene for: ($loggingReason)")
        } else {
            repository.changeScene(resolvedScene, transitionKey)
        }
    }

    /**
     * Requests a scene change to the given scene.
     *
     * The change is instantaneous and not animated; it will be observable in the next frame and
     * there will be no transition animation. If [Overlays.Bouncer] is showing, it will instantly be
     * hidden.
     */
    fun snapToScene(toScene: SceneKey, loggingReason: String) {
        val currentSceneKey = currentScene.value
        val resolvedScene =
            sceneFamilyResolvers.get()[toScene]?.let { familyResolver ->
                if (familyResolver.includesScene(currentSceneKey)) {
                    return
                } else {
                    familyResolver.resolvedScene.value
                }
            } ?: toScene
        if (
            !validateSceneChange(
                from = currentSceneKey,
                to = resolvedScene,
                loggingReason = loggingReason,
            )
        ) {
            return
        }

        logger.logSceneChanged(
            from = currentSceneKey,
            to = resolvedScene,
            sceneState = null,
            reason = loggingReason,
            isInstant = true,
        )

        repository.snapToScene(resolvedScene)
        instantlyHideOverlay(Overlays.Bouncer, "Hiding on snapToScene for: ($loggingReason)")
    }

    /**
     * Request to show [overlay] so that it animates in from [currentScene] and ends up being
     * visible on screen.
     *
     * After this returns, this overlay will be included in [currentOverlays]. This does nothing if
     * [overlay] is already shown.
     *
     * @param overlay The overlay to be shown
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @param transitionKey The transition key for this animated transition
     */
    @JvmOverloads
    fun showOverlay(
        overlay: OverlayKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
    ) {
        if (!validateOverlayChange(to = overlay, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(to = overlay, reason = loggingReason)

        repository.showOverlay(overlay = overlay, transitionKey = transitionKey)
    }

    /**
     * Request to hide [overlay] so that it animates out to [currentScene] and ends up *not* being
     * visible on screen.
     *
     * After this returns, this overlay will not be included in [currentOverlays]. This does nothing
     * if [overlay] is already hidden.
     *
     * @param overlay The overlay to be hidden
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @param transitionKey The transition key for this animated transition
     */
    @JvmOverloads
    fun hideOverlay(
        overlay: OverlayKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
    ) {
        if (!validateOverlayChange(from = overlay, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(from = overlay, reason = loggingReason)

        repository.hideOverlay(overlay = overlay, transitionKey = transitionKey)
    }

    /**
     * Instantly shows [overlay].
     *
     * The change is instantaneous and not animated; it will be observable in the next frame and
     * there will be no transition animation.
     */
    fun instantlyShowOverlay(overlay: OverlayKey, loggingReason: String) {
        if (!validateOverlayChange(to = overlay, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(to = overlay, reason = loggingReason)

        repository.instantlyShowOverlay(overlay)
    }

    /**
     * Instantly hides [overlay].
     *
     * The change is instantaneous and not animated; it will be observable in the next frame and
     * there will be no transition animation.
     */
    fun instantlyHideOverlay(overlay: OverlayKey, loggingReason: String) {
        if (!validateOverlayChange(from = overlay, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(from = overlay, reason = loggingReason)

        repository.instantlyHideOverlay(overlay)
    }

    /**
     * Replace [from] by [to] so that [from] ends up not being visible on screen and [to] ends up
     * being visible.
     *
     * This throws if [from] is not currently shown or if [to] is already shown.
     *
     * @param from The overlay to be hidden, if any
     * @param to The overlay to be shown, if any
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @param transitionKey The transition key for this animated transition
     */
    @JvmOverloads
    fun replaceOverlay(
        from: OverlayKey,
        to: OverlayKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
    ) {
        if (!validateOverlayChange(from = from, to = to, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(from = from, to = to, reason = loggingReason)

        repository.replaceOverlay(from = from, to = to, transitionKey = transitionKey)
    }

    /**
     * Sets the visibility of the container.
     *
     * Please do not call this from outside of the scene framework. If you are trying to force the
     * visibility to visible or invisible, prefer making changes to the existing caller of this
     * method or to upstream state used to calculate [isVisible]; for an example of the latter,
     * please see [onRemoteUserInputStarted] and [onUserInputFinished].
     */
    fun setVisible(isVisible: Boolean, loggingReason: String) {
        val wasVisible = repository.isVisible.value
        if (wasVisible == isVisible) {
            return
        }

        logger.logVisibilityChange(from = wasVisible, to = isVisible, reason = loggingReason)
        return repository.setVisible(isVisible)
    }

    /**
     * Notifies that a scene container user interaction has begun.
     *
     * This is a user interaction that originates within the Composable hierarchy of the scene
     * container.
     */
    fun onSceneContainerUserInputStarted() {
        repository.isSceneContainerUserInputOngoing.value = true
    }

    /**
     * Notifies that a remote user interaction has begun.
     *
     * This is a user interaction that originates outside of the UI of the scene container and
     * possibly outside of the System UI process itself.
     *
     * As an example, consider the dragging that can happen in the launcher that expands the shade.
     * This is a user interaction that begins remotely (as it starts in the launcher process) and is
     * then rerouted by window manager to System UI. While the user interaction definitely continues
     * within the System UI process and code, it also originates remotely.
     */
    fun onRemoteUserInputStarted(loggingReason: String) {
        logger.logRemoteUserInputStarted(loggingReason)
        repository.isRemoteUserInputOngoing.value = true
    }

    /**
     * Notifies that the current user interaction (internally or remotely started, see
     * [onSceneContainerUserInputStarted] and [onRemoteUserInputStarted]) has finished.
     */
    fun onUserInputFinished() {
        logger.logUserInputFinished()
        repository.isSceneContainerUserInputOngoing.value = false
        repository.isRemoteUserInputOngoing.value = false
    }

    /**
     * Binds the given flow so the system remembers it.
     *
     * Note that you must call is with `null` when the UI is done or risk a memory leak.
     */
    fun setTransitionState(transitionState: Flow<ObservableTransitionState>?) {
        repository.setTransitionState(transitionState)
    }

    /**
     * Returns the [concrete scene][Scenes] for [sceneKey] if it is a [scene family][SceneFamilies],
     * otherwise returns a singleton [Flow] containing [sceneKey].
     */
    fun resolveSceneFamily(sceneKey: SceneKey): Flow<SceneKey> = flow {
        emitAll(resolveSceneFamilyOrNull(sceneKey) ?: flowOf(sceneKey))
    }

    /**
     * Returns the [concrete scene][Scenes] for [sceneKey] if it is a [scene family][SceneFamilies],
     * otherwise returns `null`.
     */
    fun resolveSceneFamilyOrNull(sceneKey: SceneKey): StateFlow<SceneKey>? =
        sceneFamilyResolvers.get()[sceneKey]?.resolvedScene

    private fun isVisibleInternal(
        raw: Boolean = repository.isVisible.value,
        isRemoteUserInputOngoing: Boolean = repository.isRemoteUserInputOngoing.value,
        activeTransitionAnimationCount: Int = repository.activeTransitionAnimationCount.value,
    ): Boolean {
        return raw || isRemoteUserInputOngoing || activeTransitionAnimationCount > 0
    }

    /**
     * Validates that the given scene change is allowed.
     *
     * Will throw a runtime exception for illegal states (for example, attempting to change to a
     * scene that's not part of the current scene framework configuration).
     *
     * @param from The current scene being transitioned away from
     * @param to The desired destination scene to transition to
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @return `true` if the scene change is valid; `false` if it shouldn't happen
     */
    private fun validateSceneChange(from: SceneKey, to: SceneKey, loggingReason: String): Boolean {
        check(
            !shadeModeInteractor.isDualShade || (to != Scenes.Shade && to != Scenes.QuickSettings)
        ) {
            "Can't change scene to ${to.debugName} when dual shade is on!"
        }
        check(!shadeModeInteractor.isSplitShade || (to != Scenes.QuickSettings)) {
            "Can't change scene to ${to.debugName} in split shade mode!"
        }

        if (from == to) {
            logger.logSceneChangeRejection(
                from = from,
                to = to,
                originalChangeReason = loggingReason,
                rejectionReason = "${from.debugName} is the same as ${to.debugName}",
            )
            return false
        }

        if (to !in repository.allContentKeys) {
            logger.logSceneChangeRejection(
                from = from,
                to = to,
                originalChangeReason = loggingReason,
                rejectionReason = "${to.debugName} isn't present in allContentKeys",
            )
            return false
        }

        if (disabledContentInteractor.isDisabled(to)) {
            logger.logSceneChangeRejection(
                from = from,
                to = to,
                originalChangeReason = loggingReason,
                rejectionReason = "${to.debugName} is currently disabled",
            )
            return false
        }

        val inMidTransitionFromGone =
            (transitionState.value as? ObservableTransitionState.Transition)?.fromContent ==
                Scenes.Gone
        val isChangeAllowed =
            to != Scenes.Gone ||
                inMidTransitionFromGone ||
                deviceUnlockedInteractor.get().deviceUnlockStatus.value.isUnlocked ||
                !keyguardEnabledInteractor.get().isKeyguardEnabled.value
        check(isChangeAllowed) {
            "Cannot change to the Gone scene while the device is locked and not currently" +
                " transitioning from Gone. Current transition state is ${transitionState.value}." +
                " Logging reason for scene change was: $loggingReason"
        }

        return true
    }

    /**
     * Validates that the given overlay change is allowed.
     *
     * Will throw a runtime exception for illegal states.
     *
     * @param from The overlay to be hidden, if any
     * @param to The overlay to be shown, if any
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @return `true` if the scene change is valid; `false` if it shouldn't happen
     */
    private fun validateOverlayChange(
        from: OverlayKey? = null,
        to: OverlayKey? = null,
        loggingReason: String,
    ): Boolean {
        check(from != null || to != null) {
            "No overlay key provided for requested change." +
                " Current transition state is ${transitionState.value}." +
                " Logging reason for overlay change was: $loggingReason"
        }

        check(
            shadeModeInteractor.isDualShade ||
                (to != Overlays.NotificationsShade && to != Overlays.QuickSettingsShade)
        ) {
            "Can't show overlay ${to?.debugName} when dual shade is off!"
        }

        if (to != null && disabledContentInteractor.isDisabled(to)) {
            logger.logSceneChangeRejection(
                from = from,
                to = to,
                originalChangeReason = loggingReason,
                rejectionReason = "${to.debugName} is currently disabled",
            )
            return false
        }

        return when {
            to != null && from != null && to == from -> {
                logger.logSceneChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "${from.debugName} is the same as ${to.debugName}",
                )
                false
            }

            to != null && to !in repository.allContentKeys -> {
                logger.logSceneChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "${to.debugName} is not in allContentKeys",
                )
                false
            }

            from != null && from !in currentOverlays.value -> {
                logger.logSceneChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "${from.debugName} is not a current overlay",
                )
                false
            }

            to != null && to in currentOverlays.value -> {
                logger.logSceneChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "${to.debugName} is already a current overlay",
                )
                false
            }

            to == Overlays.Bouncer && currentScene.value == Scenes.Gone -> {
                logger.logSceneChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "Cannot show Bouncer over Gone scene",
                )
                false
            }

            else -> true
        }
    }

    /** Returns a flow indicating if the currently visible scene can be resolved from [family]. */
    fun isCurrentSceneInFamily(family: SceneKey): Flow<Boolean> =
        currentScene.map { currentScene -> isSceneInFamily(currentScene, family) }

    /** Returns `true` if [scene] can be resolved from [family]. */
    fun isSceneInFamily(scene: SceneKey, family: SceneKey): Boolean =
        sceneFamilyResolvers.get()[family]?.includesScene(scene) == true

    /**
     * Returns a filtered version of [unfiltered], without action-result entries that would navigate
     * to disabled scenes.
     */
    fun filteredUserActions(
        unfiltered: Flow<Map<UserAction, UserActionResult>>
    ): Flow<Map<UserAction, UserActionResult>> {
        return disabledContentInteractor.filteredUserActions(unfiltered)
    }

    /**
     * Notifies that a transition animation has started.
     *
     * The scene container will remain visible while any transition animation is running within it.
     */
    fun onTransitionAnimationStart() {
        repository.activeTransitionAnimationCount.update { current ->
            (current + 1).also {
                check(it < 10) {
                    "Number of active transition animations is too high. Something must be" +
                        " calling onTransitionAnimationStart too many times!"
                }
            }
        }
    }

    /**
     * Notifies that a transition animation has ended.
     *
     * The scene container will remain visible while any transition animation is running within it.
     */
    fun onTransitionAnimationEnd() {
        decrementActiveTransitionAnimationCount()
    }

    /**
     * Notifies that a transition animation has been canceled.
     *
     * The scene container will remain visible while any transition animation is running within it.
     */
    fun onTransitionAnimationCancelled() {
        decrementActiveTransitionAnimationCount()
    }

    suspend fun hydrateTableLogBuffer(tableLogBuffer: TableLogBuffer) {
        coroutineScope {
            launch {
                currentScene
                    .map { sceneKey -> DiffableSceneKey(key = sceneKey) }
                    .pairwise()
                    .collect { (prev, current) ->
                        tableLogBuffer.logDiffs(prevVal = prev, newVal = current)
                    }
            }

            launch {
                currentOverlays
                    .map { overlayKeys -> DiffableOverlayKeys(keys = overlayKeys) }
                    .pairwise()
                    .collect { (prev, current) ->
                        tableLogBuffer.logDiffs(prevVal = prev, newVal = current)
                    }
            }
        }
    }

    private fun decrementActiveTransitionAnimationCount() {
        repository.activeTransitionAnimationCount.update { current ->
            (current - 1).also {
                check(it >= 0) {
                    "Number of active transition animations is negative. Something must be" +
                        " calling onTransitionAnimationEnd or onTransitionAnimationCancelled too" +
                        " many times!"
                }
            }
        }
    }

    private class DiffableSceneKey(private val key: SceneKey) : Diffable<DiffableSceneKey> {
        override fun logDiffs(prevVal: DiffableSceneKey, row: TableRowLogger) {
            row.logChange(columnName = "currentScene", value = key.debugName)
        }
    }

    private class DiffableOverlayKeys(private val keys: Set<OverlayKey>) :
        Diffable<DiffableOverlayKeys> {
        override fun logDiffs(prevVal: DiffableOverlayKeys, row: TableRowLogger) {
            row.logChange(
                columnName = "currentOverlays",
                value = keys.joinToString { key -> key.debugName },
            )
        }
    }

    /**
     * Based off of the ordering of [allContentKeys], returns the key of the highest z-order content
     * out of [content].
     */
    private fun determineTopmostContent(content: Set<ContentKey>): ContentKey {
        // Assuming allContentKeys is sorted by ascending z-order.
        return checkNotNull(allContentKeys.findLast { it in content }) {
            "Could not find unknown content $content in allContentKeys $allContentKeys"
        }
    }

    /** Optimization for common case where overlays is empty. */
    private fun determineTopmostContent(scene: SceneKey, overlays: Set<OverlayKey>): ContentKey {
        return if (overlays.isEmpty()) {
            scene
        } else {
            determineTopmostContent(overlays)
        }
    }

    /**
     * The current content that has the highest z-order out of all currently shown scenes and
     * overlays.
     *
     * Note that during a transition between content, a different content may have the highest z-
     * order. Only the one provided by this flow is considered the current logical topmost content.
     */
    @Deprecated("Only to be used for compatibility with KeyguardTransitionFramework")
    val topmostContent: StateFlow<ContentKey> =
        combine(currentScene, currentOverlays, ::determineTopmostContent)
            .stateInTraced(
                name = "topmostContent",
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = determineTopmostContent(currentScene.value, currentOverlays.value),
            )
}
