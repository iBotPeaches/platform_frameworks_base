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

package com.android.systemui.unfold

import android.content.Context
import android.hardware.devicestate.DeviceStateManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.app.tracing.TraceUtils.traceAsync
import com.android.app.tracing.instantForTrack
import com.android.internal.util.LatencyTracker
import com.android.internal.util.LatencyTracker.ACTION_SWITCH_DISPLAY_UNFOLD
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.data.repository.DeviceStateRepository
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.power.shared.model.WakefulnessModel
import com.android.systemui.power.shared.model.WakefulnessState
import com.android.systemui.shared.system.SysUiStatsLog
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.TrackingResult.CORRUPTED
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.TrackingResult.SUCCESS
import com.android.systemui.unfold.DisplaySwitchLatencyTracker.TrackingResult.TIMED_OUT
import com.android.systemui.unfold.dagger.UnfoldSingleThreadBg
import com.android.systemui.unfold.data.repository.ScreenTimeoutPolicyRepository
import com.android.systemui.unfold.data.repository.UnfoldTransitionStatus.TransitionStarted
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import com.android.systemui.util.Compile
import com.android.systemui.util.Utils.isDeviceFoldable
import com.android.systemui.util.animation.data.repository.AnimationStatusRepository
import com.android.systemui.util.kotlin.WithPrev
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.kotlin.race
import com.android.systemui.util.time.SystemClock
import com.android.systemui.util.time.measureTimeMillis
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * [DisplaySwitchLatencyTracker] tracks latency and related fields for display switch of a foldable
 * device. This class populates [DisplaySwitchLatencyEvent] while an ongoing display switch event
 */
@SysUISingleton
class DisplaySwitchLatencyTracker
@Inject
constructor(
    private val context: Context,
    private val deviceStateRepository: DeviceStateRepository,
    private val powerInteractor: PowerInteractor,
    private val screenTimeoutPolicyRepository: ScreenTimeoutPolicyRepository,
    private val unfoldTransitionInteractor: UnfoldTransitionInteractor,
    private val animationStatusRepository: AnimationStatusRepository,
    private val keyguardInteractor: KeyguardInteractor,
    @UnfoldSingleThreadBg private val singleThreadBgExecutor: Executor,
    @Application private val applicationScope: CoroutineScope,
    private val displaySwitchLatencyLogger: DisplaySwitchLatencyLogger,
    private val systemClock: SystemClock,
    private val deviceStateManager: DeviceStateManager,
    private val latencyTracker: LatencyTracker,
) : CoreStartable {

    private val backgroundDispatcher = singleThreadBgExecutor.asCoroutineDispatcher()
    private val isAodEnabled: Boolean
        get() = keyguardInteractor.isAodAvailable.value

    private val displaySwitchStarted =
        deviceStateRepository.state.pairwise().filter {
            // Start tracking only when the foldable device is
            // folding(UNFOLDED/HALF_FOLDED -> FOLDED) or unfolding(FOLDED -> HALF_FOLD/UNFOLDED)
            foldableDeviceState ->
            foldableDeviceState.previousValue == DeviceState.FOLDED ||
                foldableDeviceState.newValue == DeviceState.FOLDED
        }

    private var startOrEndEvent: Flow<Any> = merge(displaySwitchStarted, anyEndEventFlow())

    private var isCoolingDown = false

    override fun start() {
        if (!isDeviceFoldable(context.resources, deviceStateManager)) {
            return
        }
        applicationScope.launch(context = backgroundDispatcher) {
            displaySwitchStarted.collectLatest { (previousState, newState) ->
                if (isCoolingDown) return@collectLatest
                if (previousState == DeviceState.FOLDED) {
                    latencyTracker.onActionStart(ACTION_SWITCH_DISPLAY_UNFOLD)
                    instantForTrack(TAG) { "unfold latency tracking started" }
                }
                val event = DisplaySwitchLatencyEvent().withBeforeFields(previousState.toStatsInt())
                try {
                    withTimeout(SCREEN_EVENT_TIMEOUT) {
                        val displaySwitchTimeMs =
                            measureTimeMillis(systemClock) {
                                traceAsync(TAG, "displaySwitch") {
                                    waitForDisplaySwitch(newState.toStatsInt())
                                }
                            }
                        if (previousState == DeviceState.FOLDED) {
                            latencyTracker.onActionEnd(ACTION_SWITCH_DISPLAY_UNFOLD)
                        }
                        logDisplaySwitchEvent(event, newState, displaySwitchTimeMs)
                    }
                } catch (e: TimeoutCancellationException) {
                    instantForTrack(TAG) { "tracking timed out" }
                    latencyTracker.onActionCancel(ACTION_SWITCH_DISPLAY_UNFOLD)
                    logDisplaySwitchEvent(
                        event = event,
                        toFoldableDeviceState = newState,
                        displaySwitchTimeMs = SCREEN_EVENT_TIMEOUT.inWholeMilliseconds,
                        trackingResult = TIMED_OUT,
                    )
                } catch (e: CancellationException) {
                    instantForTrack(TAG) { "new state interrupted, entering cool down" }
                    latencyTracker.onActionCancel(ACTION_SWITCH_DISPLAY_UNFOLD)
                    startCoolDown(event)
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun startCoolDown(event: DisplaySwitchLatencyEvent) {
        if (isCoolingDown) return
        isCoolingDown = true
        applicationScope.launch(context = backgroundDispatcher) {
            val startTime = systemClock.elapsedRealtime()
            var lastState: DeviceState? = null
            try {
                startOrEndEvent.timeout(COOL_DOWN_DURATION).collect {
                    if (it is WithPrev<*, *>) {
                        lastState = it.newValue as? DeviceState
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val totalCooldownTime = systemClock.elapsedRealtime() - startTime
                logDisplaySwitchEvent(
                    event = event,
                    toFoldableDeviceState = lastState ?: DeviceState.UNKNOWN,
                    displaySwitchTimeMs = totalCooldownTime,
                    trackingResult = CORRUPTED,
                )
                instantForTrack(TAG) { "cool down finished, lasted $totalCooldownTime ms" }
                isCoolingDown = false
            }
        }
    }

    private fun logDisplaySwitchEvent(
        event: DisplaySwitchLatencyEvent,
        toFoldableDeviceState: DeviceState,
        displaySwitchTimeMs: Long,
        trackingResult: TrackingResult = SUCCESS,
    ) {
        displaySwitchLatencyLogger.log(
            event.withAfterFields(
                toFoldableDeviceState,
                displaySwitchTimeMs,
                getCurrentState(),
                trackingResult,
            )
        )
    }

    private fun DeviceState.toStatsInt(): Int =
        when (this) {
            DeviceState.FOLDED -> FOLDABLE_DEVICE_STATE_CLOSED
            DeviceState.HALF_FOLDED -> FOLDABLE_DEVICE_STATE_HALF_OPEN
            DeviceState.UNFOLDED -> FOLDABLE_DEVICE_STATE_OPEN
            DeviceState.CONCURRENT_DISPLAY -> FOLDABLE_DEVICE_STATE_FLIPPED
            else -> FOLDABLE_DEVICE_STATE_UNKNOWN
        }

    private fun TrackingResult.toStatsInt(): Int =
        when (this) {
            SUCCESS -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__SUCCESS
            CORRUPTED -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__CORRUPTED
            TIMED_OUT -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__TIMED_OUT
        }

    private suspend fun waitForDisplaySwitch(toFoldableDeviceState: Int) {
        val isTransitionEnabled =
            unfoldTransitionInteractor.isAvailable &&
                animationStatusRepository.areAnimationsEnabled().first()
        if (shouldWaitForTransitionStart(toFoldableDeviceState, isTransitionEnabled)) {
            traceAsync(TAG, "waitForTransitionStart()") {
                unfoldTransitionInteractor.waitForTransitionStart()
            }
        } else {
            race({ waitForScreenTurnedOn() }, { waitForGoToSleepWithScreenOff() })
        }
    }

    private fun anyEndEventFlow(): Flow<Any> {
        val unfoldStatus =
            unfoldTransitionInteractor.unfoldTransitionStatus.filter { it is TransitionStarted }
        // dropping first emission as we're only interested in new emissions, not current state
        val screenOn =
            powerInteractor.screenPowerState.drop(1).filter { it == ScreenPowerState.SCREEN_ON }
        val goToSleep =
            powerInteractor.detailedWakefulness.drop(1).filter { sleepWithScreenOff(it) }
        return merge(screenOn, goToSleep, unfoldStatus)
    }

    private fun shouldWaitForTransitionStart(
        toFoldableDeviceState: Int,
        isTransitionEnabled: Boolean,
    ): Boolean = (toFoldableDeviceState != FOLDABLE_DEVICE_STATE_CLOSED && isTransitionEnabled)

    private suspend fun waitForScreenTurnedOn() {
        traceAsync(TAG, "waitForScreenTurnedOn()") {
            // dropping first as it's stateFlow and will always emit latest value but we're
            // only interested in new states
            powerInteractor.screenPowerState
                .drop(1)
                .filter { it == ScreenPowerState.SCREEN_ON }
                .first()
        }
    }

    private suspend fun waitForGoToSleepWithScreenOff() {
        traceAsync(TAG, "waitForGoToSleepWithScreenOff()") {
            powerInteractor.detailedWakefulness.filter { sleepWithScreenOff(it) }.first()
        }
    }

    private fun sleepWithScreenOff(model: WakefulnessModel) =
        model.internalWakefulnessState == WakefulnessState.ASLEEP && !isAodEnabled

    private fun getCurrentState(): Int =
        when {
            isStateAod() -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__AOD
            isStateScreenOff() -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__SCREEN_OFF
            else -> SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TO_STATE__UNKNOWN
        }

    private fun isStateAod(): Boolean = (isAsleepDueToFold() && isAodEnabled)

    private fun isStateScreenOff(): Boolean = (isAsleepDueToFold() && !isAodEnabled)

    private fun isAsleepDueToFold(): Boolean {
        val lastWakefulnessEvent = powerInteractor.detailedWakefulness.value

        return (lastWakefulnessEvent.isAsleep() &&
            (lastWakefulnessEvent.lastSleepReason == WakeSleepReason.FOLD))
    }

    private inline fun log(msg: () -> String) {
        if (DEBUG) Log.d(TAG, msg())
    }

    private fun DisplaySwitchLatencyEvent.withBeforeFields(
        fromFoldableDeviceState: Int
    ): DisplaySwitchLatencyEvent {
        log { "fromFoldableDeviceState=$fromFoldableDeviceState" }
        instantForTrack(TAG) { "fromFoldableDeviceState=$fromFoldableDeviceState" }

        val screenTimeoutActive = screenTimeoutPolicyRepository.screenTimeoutActive.value
        val screenWakelockStatus =
            if (screenTimeoutActive) {
                NO_SCREEN_WAKELOCKS
            } else {
                HAS_SCREEN_WAKELOCKS
            }

        return copy(
            fromFoldableDeviceState = fromFoldableDeviceState,
            screenWakelockStatus = screenWakelockStatus
        )
    }

    private fun DisplaySwitchLatencyEvent.withAfterFields(
        toFoldableDeviceState: DeviceState,
        displaySwitchTimeMs: Long,
        toState: Int,
        trackingResult: TrackingResult,
    ): DisplaySwitchLatencyEvent {
        log {
            "trackingResult=$trackingResult, " +
                "toFoldableDeviceState=$toFoldableDeviceState, " +
                "toState=$toState, " +
                "latencyMs=$displaySwitchTimeMs"
        }
        instantForTrack(TAG) { "toFoldableDeviceState=$toFoldableDeviceState, toState=$toState" }

        return copy(
            toFoldableDeviceState = toFoldableDeviceState.toStatsInt(),
            latencyMs = displaySwitchTimeMs.toInt(),
            toState = toState,
            trackingResult = trackingResult.toStatsInt(),
        )
    }

    /**
     * Stores values corresponding to all respective [DisplaySwitchLatencyTrackedField] in a single
     * event of display switch for foldable devices.
     *
     * Once the data is captured in this data class and appropriate to log, it is logged through
     * [DisplaySwitchLatencyLogger]
     */
    data class DisplaySwitchLatencyEvent(
        val latencyMs: Int = VALUE_UNKNOWN,
        val fromFoldableDeviceState: Int = FOLDABLE_DEVICE_STATE_UNKNOWN,
        val fromState: Int = SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__UNKNOWN,
        val fromFocusedAppUid: Int = VALUE_UNKNOWN,
        val fromPipAppUid: Int = VALUE_UNKNOWN,
        val fromVisibleAppsUid: Set<Int> = setOf(),
        val fromDensityDpi: Int = VALUE_UNKNOWN,
        val toFoldableDeviceState: Int = FOLDABLE_DEVICE_STATE_UNKNOWN,
        val toState: Int = SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_STATE__UNKNOWN,
        val toFocusedAppUid: Int = VALUE_UNKNOWN,
        val toPipAppUid: Int = VALUE_UNKNOWN,
        val toVisibleAppsUid: Set<Int> = setOf(),
        val toDensityDpi: Int = VALUE_UNKNOWN,
        val notificationCount: Int = VALUE_UNKNOWN,
        val externalDisplayCount: Int = VALUE_UNKNOWN,
        val throttlingLevel: Int =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__THROTTLING_LEVEL__NONE,
        val vskinTemperatureC: Int = VALUE_UNKNOWN,
        val hallSensorToFirstHingeAngleChangeMs: Int = VALUE_UNKNOWN,
        val hallSensorToDeviceStateChangeMs: Int = VALUE_UNKNOWN,
        val onScreenTurningOnToOnDrawnMs: Int = VALUE_UNKNOWN,
        val onDrawnToOnScreenTurnedOnMs: Int = VALUE_UNKNOWN,
        val trackingResult: Int =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__TRACKING_RESULT__UNKNOWN_RESULT,
        val screenWakelockStatus: Int =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_UNKNOWN,
    )

    enum class TrackingResult {
        SUCCESS,
        CORRUPTED,
        TIMED_OUT,
    }

    companion object {
        private const val VALUE_UNKNOWN = -1
        private const val TAG = "DisplaySwitchLatency"
        private val DEBUG = Compile.IS_DEBUG && Log.isLoggable(TAG, Log.VERBOSE)
        @VisibleForTesting val SCREEN_EVENT_TIMEOUT = 15.seconds
        @VisibleForTesting val COOL_DOWN_DURATION = 2.seconds

        private const val FOLDABLE_DEVICE_STATE_UNKNOWN =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_UNKNOWN
        const val FOLDABLE_DEVICE_STATE_CLOSED =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_CLOSED
        const val FOLDABLE_DEVICE_STATE_HALF_OPEN =
            SysUiStatsLog
                .DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_HALF_OPENED
        private const val FOLDABLE_DEVICE_STATE_OPEN =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_OPENED
        private const val FOLDABLE_DEVICE_STATE_FLIPPED =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__FROM_FOLDABLE_DEVICE_STATE__STATE_FLIPPED

        private const val HAS_SCREEN_WAKELOCKS =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_HAS_SCREEN_WAKELOCKS
        private const val NO_SCREEN_WAKELOCKS =
            SysUiStatsLog.DISPLAY_SWITCH_LATENCY_TRACKED__SCREEN_WAKELOCK_STATUS__SCREEN_WAKELOCK_STATUS_NO_WAKELOCKS
    }
}
