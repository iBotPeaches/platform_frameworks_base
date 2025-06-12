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

package com.android.settingslib.widget

import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.DeviceConfig
import android.transition.Fade
import android.transition.Transition
import android.transition.TransitionListenerAdapter
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.banner.R
import java.time.Duration

/** Callback to communicate when a banner message resolution animation is completed. */
fun interface ResolutionCompletedCallback {
    fun onCompleted()
}

internal class ResolutionAnimator(
    private val data: Data,
    private val preferenceViewHolder: PreferenceViewHolder,
) {

    data class Data(
        val resolutionMessage: CharSequence,
        val resolutionCompletedCallback: ResolutionCompletedCallback,
    )

    private val defaultBannerContent: View?
        get() = preferenceViewHolder.findView(R.id.banner_content)
    private val resolvedTextView: TextView?
        get() = preferenceViewHolder.findView(R.id.resolved_banner_text)

    fun startResolutionAnimation() {
        resolvedTextView?.text = data.resolutionMessage
        resolvedTextView?.resolutionDrawable?.reset()

        val transitionSet =
            TransitionSet()
                .setOrdering(TransitionSet.ORDERING_SEQUENTIAL)
                .setInterpolator(linearInterpolator)
                .addTransition(hideIssueContentTransition)
                .addTransition(
                    showResolvedContentTransition
                        .clone()
                        .addListener(
                            object : TransitionListenerAdapter() {
                                override fun onTransitionEnd(transition: Transition) {
                                    super.onTransitionEnd(transition)
                                    startIssueResolvedAnimation()
                                }
                            }
                        )
                )

        preferenceViewHolder.itemView.post {
            TransitionManager.beginDelayedTransition(
                preferenceViewHolder.itemView as ViewGroup,
                transitionSet,
            )

            defaultBannerContent?.visibility = View.INVISIBLE
            resolvedTextView?.visibility = View.VISIBLE
        }

        preferenceViewHolder.itemView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    cancelAnimationsAndFinish()
                }
            }
        )
    }

    private fun startIssueResolvedAnimation() {
        val animatedDrawable = resolvedTextView?.resolutionDrawable

        if (animatedDrawable == null) {
            hideResolvedUiAndFinish()
            return
        }

        animatedDrawable.apply {
            clearAnimationCallbacks()
            registerAnimationCallback(
                object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable) {
                        super.onAnimationEnd(drawable)
                        hideResolvedUiAndFinish()
                    }
                }
            )
            start()
        }
    }

    private fun hideResolvedUiAndFinish() {
        val hideTransition =
            hideResolvedContentTransition
                .clone()
                .setInterpolator(linearInterpolator)
                .addListener(
                    object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: Transition) {
                            super.onTransitionEnd(transition)
                            data.resolutionCompletedCallback.onCompleted()
                        }
                    }
                )
        TransitionManager.beginDelayedTransition(
            preferenceViewHolder.itemView as ViewGroup,
            hideTransition,
        )
        resolvedTextView?.visibility = View.GONE
    }

    private fun cancelAnimationsAndFinish() {
        TransitionManager.endTransitions(preferenceViewHolder.itemView as ViewGroup)

        resolvedTextView?.visibility = View.GONE

        val animatedDrawable = resolvedTextView?.resolutionDrawable
        animatedDrawable?.clearAnimationCallbacks()
        animatedDrawable?.stop()

        data.resolutionCompletedCallback.onCompleted()
    }

    private companion object {
        private val linearInterpolator = LinearInterpolator()

        private val HIDE_ISSUE_CONTENT_TRANSITION_DURATION = Duration.ofMillis(333)
        private val hideIssueContentTransition =
            Fade(Fade.OUT).setDuration(HIDE_ISSUE_CONTENT_TRANSITION_DURATION.toMillis())

        private val SHOW_RESOLVED_CONTENT_TRANSITION_DELAY = Duration.ofMillis(133)
        private val SHOW_RESOLVED_CONTENT_TRANSITION_DURATION = Duration.ofMillis(250)
        private val showResolvedContentTransition =
            Fade(Fade.IN)
                .setStartDelay(SHOW_RESOLVED_CONTENT_TRANSITION_DELAY.toMillis())
                .setDuration(SHOW_RESOLVED_CONTENT_TRANSITION_DURATION.toMillis())

        private val hideResolvedContentTransitionDelay
            get() =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Duration.ofMillis(
                        DeviceConfig.getLong(
                            "settings_ui",
                            "banner_message_pref_hide_resolved_content_delay_millis",
                            400,
                        )
                    )
                } else {
                    Duration.ofMillis(400)
                }

        private val HIDE_RESOLVED_UI_TRANSITION_DURATION = Duration.ofMillis(167)
        private val hideResolvedContentTransition
            get() =
                Fade(Fade.OUT)
                    .setStartDelay(hideResolvedContentTransitionDelay.toMillis())
                    .setDuration(HIDE_RESOLVED_UI_TRANSITION_DURATION.toMillis())

        inline fun <reified T : View> PreferenceViewHolder.findView(id: Int): T? =
            findViewById(id) as? T

        val TextView.resolutionDrawable: AnimatedVectorDrawable?
            get() = compoundDrawables.find { it != null } as? AnimatedVectorDrawable
    }
}
