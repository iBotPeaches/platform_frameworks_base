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

package com.android.systemui.shared.clocks

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import com.android.systemui.plugins.clocks.VPointF
import com.android.systemui.plugins.clocks.VPointF.Companion.times

class DigitTranslateAnimator(private val updateCallback: (VPointF) -> Unit) {
    var currentTranslation = VPointF.ZERO
    var baseTranslation = VPointF.ZERO
    var targetTranslation = VPointF.ZERO

    private val bounceAnimator: ValueAnimator =
        ValueAnimator.ofFloat(1f).apply {
            addUpdateListener { updateCallback(getInterpolatedTranslation(it.animatedFraction)) }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        baseTranslation = currentTranslation
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        baseTranslation = currentTranslation
                    }
                }
            )
        }

    fun animatePosition(
        animate: Boolean = true,
        delay: Long = 0,
        duration: Long,
        interpolator: TimeInterpolator? = null,
        targetTranslation: VPointF,
        onAnimationEnd: Runnable? = null,
    ) {
        this.targetTranslation = targetTranslation
        if (animate) {
            bounceAnimator.cancel()
            bounceAnimator.startDelay = delay
            bounceAnimator.duration = duration
            interpolator?.let { bounceAnimator.interpolator = it }
            if (onAnimationEnd != null) {
                val listener =
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            onAnimationEnd.run()
                            bounceAnimator.removeListener(this)
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            bounceAnimator.removeListener(this)
                        }
                    }
                bounceAnimator.addListener(listener)
            }
            bounceAnimator.start()
        } else {
            // No animation is requested, thus set base and target state to the same state.
            currentTranslation = targetTranslation
            baseTranslation = targetTranslation
            updateCallback(targetTranslation)
        }
    }

    fun getInterpolatedTranslation(progress: Float): VPointF {
        return baseTranslation + progress * (targetTranslation - baseTranslation)
    }
}
