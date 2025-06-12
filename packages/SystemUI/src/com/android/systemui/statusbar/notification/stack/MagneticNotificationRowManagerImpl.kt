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

package com.android.systemui.statusbar.notification.stack

import android.os.VibrationAttributes
import androidx.dynamicanimation.animation.SpringForce
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotificationRowLogger
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.InteractionProperties
import com.google.android.msdl.domain.MSDLPlayer
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import org.jetbrains.annotations.TestOnly

@SysUISingleton
class MagneticNotificationRowManagerImpl
@Inject
constructor(
    private val msdlPlayer: MSDLPlayer,
    private val notificationTargetsHelper: NotificationTargetsHelper,
    private val notificationRoundnessManager: NotificationRoundnessManager,
    private val logger: NotificationRowLogger,
) : MagneticNotificationRowManager {

    var currentState = State.IDLE
        private set

    // Magnetic targets
    var currentMagneticListeners = listOf<MagneticRowListener?>()
        private set

    private var magneticDetachThreshold = Float.POSITIVE_INFINITY
    private var magneticAttachThreshold = 0f

    // Has the roundable target been set for the magnetic view that is being swiped.
    val isSwipedViewRoundableSet: Boolean
        @TestOnly get() = notificationRoundnessManager.isSwipedViewSet

    // Animation spring forces
    private val detachForce =
        SpringForce().setStiffness(DETACH_STIFFNESS).setDampingRatio(DETACH_DAMPING_RATIO)
    private val snapForce =
        SpringForce().setStiffness(SNAP_BACK_STIFFNESS).setDampingRatio(SNAP_BACK_DAMPING_RATIO)
    private val attachForce =
        SpringForce().setStiffness(ATTACH_STIFFNESS).setDampingRatio(ATTACH_DAMPING_RATIO)

    // Multiplier applied to the translation of a row while swiped
    val swipedRowMultiplier =
        MAGNETIC_TRANSLATION_MULTIPLIERS[MAGNETIC_TRANSLATION_MULTIPLIERS.size / 2]

    /**
     * An offset applied to input translation that increases on subsequent re-attachments of a
     * detached magnetic view. This helps keep computations consistent when the drag gesture input
     * and the swiped notification don't share the same origin point after a re-attaching animation.
     */
    private var translationOffset = 0f

    private var dismissVelocity = 0f

    private val detachDirectionEstimator = DirectionEstimator()

    override fun onDensityChange(density: Float) {
        magneticDetachThreshold =
            density * MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
        magneticAttachThreshold =
            density * MagneticNotificationRowManager.MAGNETIC_ATTACH_THRESHOLD_DP
        dismissVelocity = density * DISMISS_VELOCITY
    }

    override fun setMagneticAndRoundableTargets(
        swipingRow: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
    ) {
        if (currentState == State.IDLE) {
            translationOffset = 0f
            detachDirectionEstimator.reset()
            updateMagneticAndRoundableTargets(swipingRow, stackScrollLayout, sectionsManager)
            currentState = State.TARGETS_SET
        } else {
            logger.logMagneticAndRoundableTargetsNotSet(currentState, swipingRow.loggingKey)
        }
    }

    private fun updateMagneticAndRoundableTargets(
        expandableNotificationRow: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
    ) {
        // Update roundable targets
        notificationRoundnessManager.clear()
        val currentRoundableTargets =
            notificationTargetsHelper.findRoundableTargets(
                expandableNotificationRow,
                stackScrollLayout,
                sectionsManager,
            )
        notificationRoundnessManager.setRoundableTargets(currentRoundableTargets)

        // Update magnetic targets
        val newListeners =
            notificationTargetsHelper.findMagneticTargets(
                expandableNotificationRow,
                stackScrollLayout,
                sectionsManager,
                MAGNETIC_TRANSLATION_MULTIPLIERS.size,
            )
        newListeners.forEach {
            if (currentMagneticListeners.contains(it)) {
                it?.cancelMagneticAnimations()
                if (it == currentMagneticListeners.swipedListener()) {
                    it?.cancelTranslationAnimations()
                }
            }
        }
        currentMagneticListeners = newListeners
    }

    override fun setMagneticRowTranslation(
        row: ExpandableNotificationRow,
        translation: Float,
    ): Boolean {
        if (!row.isSwipedTarget()) return false

        val canTargetBeDismissed =
            currentMagneticListeners.swipedListener()?.canRowBeDismissed() ?: false
        val correctedTranslation = translation - translationOffset
        when (currentState) {
            State.IDLE -> {
                logger.logMagneticRowTranslationNotSet(currentState, row.getLoggingKey())
                return false
            }
            State.TARGETS_SET -> {
                detachDirectionEstimator.recordTranslation(correctedTranslation)
                pullTargets(correctedTranslation, canTargetBeDismissed)
                currentState = State.PULLING
            }
            State.PULLING -> {
                detachDirectionEstimator.recordTranslation(correctedTranslation)
                updateRoundness(correctedTranslation)
                if (canTargetBeDismissed) {
                    pullDismissibleRow(correctedTranslation)
                } else {
                    pullTargets(correctedTranslation, canSwipedBeDismissed = false)
                }
            }
            State.DETACHED -> {
                detachDirectionEstimator.recordTranslation(correctedTranslation)
                translateDetachedRow(correctedTranslation)
            }
        }
        return true
    }

    private fun updateRoundness(translation: Float, animate: Boolean = false) {
        val normalizedTranslation = abs(swipedRowMultiplier * translation) / magneticDetachThreshold
        notificationRoundnessManager.setRoundnessForAffectedViews(
            /* roundness */ normalizedTranslation.coerceIn(0f, MAX_PRE_DETACH_ROUNDNESS),
            animate,
        )
    }

    private fun pullDismissibleRow(translation: Float) {
        val crossedThreshold = abs(translation) >= magneticDetachThreshold
        if (crossedThreshold) {
            detachDirectionEstimator.halt()
            snapNeighborsBack()
            currentMagneticListeners.swipedListener()?.let { detach(it, translation) }
            currentState = State.DETACHED
        } else {
            pullTargets(translation, canSwipedBeDismissed = true)
        }
    }

    private fun pullTargets(translation: Float, canSwipedBeDismissed: Boolean) {
        var targetTranslation: Float
        currentMagneticListeners.forEachIndexed { i, listener ->
            listener?.let {
                if (!canSwipedBeDismissed || !it.canRowBeDismissed()) {
                    // Use a reduced translation if the target swiped can't be dismissed or if the
                    // target itself can't be dismissed
                    targetTranslation =
                        MAGNETIC_TRANSLATION_MULTIPLIERS[i] * translation * MAGNETIC_REDUCTION
                } else {
                    targetTranslation = MAGNETIC_TRANSLATION_MULTIPLIERS[i] * translation
                }
                it.setMagneticTranslation(targetTranslation)
            }
        }
        // TODO(b/399633875): Enable pull haptics after we have a clear and polished haptics design
    }

    private fun playPullHaptics(mappedTranslation: Float, canSwipedBeDismissed: Boolean) {
        val normalizedTranslation = abs(mappedTranslation) / magneticDetachThreshold
        val scaleFactor =
            if (canSwipedBeDismissed) {
                WEAK_VIBRATION_SCALE
            } else {
                STRONG_VIBRATION_SCALE
            }
        val vibrationScale = scaleFactor * normalizedTranslation
        msdlPlayer.playToken(
            MSDLToken.DRAG_INDICATOR_CONTINUOUS,
            InteractionProperties.DynamicVibrationScale(
                scale = vibrationScale.pow(VIBRATION_PERCEPTION_EXPONENT),
                vibrationAttributes = VIBRATION_ATTRIBUTES_PIPELINING,
            ),
        )
    }

    private fun snapNeighborsBack(velocity: Float? = null) {
        currentMagneticListeners.forEachIndexed { i, target ->
            target?.let {
                if (i != currentMagneticListeners.size / 2) {
                    val velocityMultiplier = MAGNETIC_TRANSLATION_MULTIPLIERS[i]
                    snapBack(it, velocity?.times(velocityMultiplier))
                }
            }
        }
    }

    private fun detach(listener: MagneticRowListener, toPosition: Float) {
        listener.cancelMagneticAnimations()
        listener.triggerMagneticForce(toPosition, detachForce)
        notificationRoundnessManager.setRoundnessForAffectedViews(
            /* roundness */ 1f,
            /* animate */ true,
        )
        msdlPlayer.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
    }

    private fun snapBack(listener: MagneticRowListener, velocity: Float?) {
        listener.cancelMagneticAnimations()
        listener.triggerMagneticForce(
            endTranslation = 0f,
            snapForce,
            startVelocity = velocity ?: 0f,
        )
    }

    private fun translateDetachedRow(translation: Float) {
        val crossedThreshold = abs(translation) <= magneticAttachThreshold
        if (crossedThreshold) {
            translationOffset += translation
            detachDirectionEstimator.reset()
            updateRoundness(translation = 0f, animate = true)
            currentMagneticListeners.swipedListener()?.let { attach(it) }
            currentState = State.PULLING
        } else {
            val swiped = currentMagneticListeners.swipedListener()
            swiped?.setMagneticTranslation(translation, trackEagerly = false)
        }
    }

    private fun attach(listener: MagneticRowListener) {
        listener.cancelMagneticAnimations()
        listener.triggerMagneticForce(endTranslation = 0f, attachForce)
        msdlPlayer.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
    }

    override fun onMagneticInteractionEnd(row: ExpandableNotificationRow, velocity: Float?) {
        translationOffset = 0f
        detachDirectionEstimator.reset()
        if (row.isSwipedTarget()) {
            when (currentState) {
                State.TARGETS_SET -> currentState = State.IDLE
                State.PULLING -> {
                    snapNeighborsBack(velocity)
                    currentState = State.IDLE
                }
                State.DETACHED -> {
                    // Cancel any detaching animation that may be occurring
                    currentMagneticListeners.swipedListener()?.cancelMagneticAnimations()
                    currentState = State.IDLE
                }
                else -> {}
            }
        } else {
            // A magnetic neighbor may be dismissing. In this case, we need to cancel any snap back
            // magnetic animation to let the external dismiss animation proceed.
            val listener = currentMagneticListeners.find { it == row.magneticRowListener }
            listener?.cancelMagneticAnimations()
        }
    }

    override fun isMagneticRowSwipedDismissible(
        row: ExpandableNotificationRow,
        endVelocity: Float,
    ): Boolean {
        if (!row.isSwipedTarget()) return false
        val isEndVelocityLargeEnough = abs(endVelocity) >= dismissVelocity
        val shouldSnapBack =
            isEndVelocityLargeEnough && detachDirectionEstimator.direction != sign(endVelocity)

        return when (currentState) {
            State.IDLE,
            State.TARGETS_SET,
            State.PULLING -> isEndVelocityLargeEnough
            State.DETACHED -> !shouldSnapBack
        }
    }

    override fun resetRoundness() = notificationRoundnessManager.clear()

    override fun reset() {
        translationOffset = 0f
        detachDirectionEstimator.reset()
        currentMagneticListeners.forEach {
            it?.cancelMagneticAnimations()
            it?.cancelTranslationAnimations()
        }
        currentState = State.IDLE
        currentMagneticListeners = listOf()
        notificationRoundnessManager.clear()
    }

    private fun List<MagneticRowListener?>.swipedListener(): MagneticRowListener? =
        getOrNull(index = size / 2)

    private fun ExpandableNotificationRow.isSwipedTarget(): Boolean =
        magneticRowListener == currentMagneticListeners.swipedListener()

    private fun NotificationRoundnessManager.clear() = setViewsAffectedBySwipe(null, null, null)

    private fun NotificationRoundnessManager.setRoundableTargets(targets: RoundableTargets) =
        setViewsAffectedBySwipe(targets.before, targets.swiped, targets.after)

    /**
     * A class to estimate the direction of a gesture translations with a moving average.
     *
     * The class holds a buffer that stores translations. When requested, the direction of movement
     * is estimated as the sign of the average value from the buffer.
     */
    class DirectionEstimator {

        // A buffer to hold past translations. This is used as a FIFO structure with a fixed size.
        private val translationBuffer = ArrayDeque<Float>()

        /**
         * The estimated direction of the translations. It will be estimated as the average of the
         * values in the [translationBuffer] and set only once when the estimator is halted.
         */
        var direction = 0f
            private set

        private var acceptTranslations = true

        /**
         * Add a new translation to the [translationBuffer] if we are still accepting translations
         * (see [halt]). If the buffer is full, we remove the last value and add the new one to the
         * end.
         */
        fun recordTranslation(translation: Float) {
            if (!acceptTranslations) return

            if (translationBuffer.size == TRANSLATION_BUFFER_SIZE) {
                translationBuffer.removeFirst()
            }
            translationBuffer.addLast(translation)
        }

        /**
         * Halt the operation of the estimator.
         *
         * This stops the estimator from receiving new translations and derives the estimated
         * direction. This is the sign of the average value from the available data in the
         * [translationBuffer].
         */
        fun halt() {
            acceptTranslations = false
            direction = translationBuffer.mean()
        }

        fun reset() {
            translationBuffer.clear()
            acceptTranslations = true
        }

        private fun ArrayDeque<Float>.mean(): Float =
            if (isEmpty()) {
                0f
            } else {
                sign(sum() / translationBuffer.size)
            }

        companion object {
            private const val TRANSLATION_BUFFER_SIZE = 10
        }
    }

    enum class State {
        IDLE,
        TARGETS_SET,
        PULLING,
        DETACHED,
    }

    companion object {
        /**
         * Multipliers applied to the translation of magnetically-coupled views. This list must be
         * symmetric with an odd size, where the center multiplier applies to the view that is
         * currently being swiped. From the center outwards, the multipliers apply to the neighbors
         * of the swiped view.
         */
        private val MAGNETIC_TRANSLATION_MULTIPLIERS = listOf(0.04f, 0.12f, 0.5f, 0.12f, 0.04f)

        const val MAGNETIC_REDUCTION = 0.65f

        /** Spring parameters for physics animators */
        private const val DETACH_STIFFNESS = 800f
        private const val DETACH_DAMPING_RATIO = 0.95f
        private const val SNAP_BACK_STIFFNESS = 550f
        private const val SNAP_BACK_DAMPING_RATIO = 0.6f
        private const val ATTACH_STIFFNESS = 800f
        private const val ATTACH_DAMPING_RATIO = 0.95f

        private const val DISMISS_VELOCITY = 500 // in dp/sec

        // Maximum value of corner roundness that gets applied during the pre-detach dragging
        private const val MAX_PRE_DETACH_ROUNDNESS = 0.8f

        private val VIBRATION_ATTRIBUTES_PIPELINING =
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .setFlags(VibrationAttributes.FLAG_PIPELINED_EFFECT)
                .build()
        private const val VIBRATION_PERCEPTION_EXPONENT = 1 / 0.89f
        private const val WEAK_VIBRATION_SCALE = 0.2f
        private const val STRONG_VIBRATION_SCALE = 0.45f
    }
}
