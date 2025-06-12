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
 *
 */

package com.android.systemui.common.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.android.systemui.Flags.doubleTapToSleep
import com.android.systemui.log.TouchHandlingViewLogger
import com.android.systemui.shade.TouchLogger
import kotlinx.coroutines.DisposableHandle

/**
 * View designed to handle long-presses and double taps.
 *
 * The view will not handle any gestures by default. To set it up, set up a listener and, when ready
 * to start consuming gestures, set the gesture's enable function ([setLongPressHandlingEnabled],
 * [setDoublePressHandlingEnabled]) to `true`.
 */
class TouchHandlingView(
    context: Context,
    attrs: AttributeSet?,
    longPressDuration: () -> Long,
    allowedTouchSlop: Int = ViewConfiguration.getTouchSlop(),
    logger: TouchHandlingViewLogger? = null,
) : View(context, attrs) {

    init {
        setupAccessibilityDelegate()
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(context, attrs, { ViewConfiguration.getLongPressTimeout().toLong() })

    interface Listener {
        /** Notifies that a long-press has been detected by the given view. */
        fun onLongPressDetected(view: View, x: Int, y: Int, isA11yAction: Boolean = false)

        /** Notifies that the gesture was too short for a long press, it is actually a click. */
        fun onSingleTapDetected(view: View, x: Int, y: Int) = Unit

        /** Notifies that a double tap has been detected by the given view. */
        fun onDoubleTapDetected(view: View) = Unit
    }

    var listener: Listener? = null

    var accessibilityHintLongPressAction: AccessibilityAction? = null

    private val interactionHandler: TouchHandlingViewInteractionHandler by lazy {
        TouchHandlingViewInteractionHandler(
            context = context,
            postDelayed = { block, timeoutMs ->
                val dispatchToken = Any()

                handler.postDelayed(block, dispatchToken, timeoutMs)

                DisposableHandle { handler.removeCallbacksAndMessages(dispatchToken) }
            },
            isAttachedToWindow = ::isAttachedToWindow,
            onLongPressDetected = { x, y ->
                listener?.onLongPressDetected(view = this, x = x, y = y)
            },
            onSingleTapDetected = { x, y ->
                listener?.onSingleTapDetected(this@TouchHandlingView, x = x, y = y)
            },
            onDoubleTapDetected = {
                if (doubleTapToSleep()) listener?.onDoubleTapDetected(this@TouchHandlingView)
            },
            longPressDuration = longPressDuration,
            allowedTouchSlop = allowedTouchSlop,
            logger = logger,
        )
    }

    var longPressDuration: () -> Long
        get() = interactionHandler.longPressDuration
        set(longPressDuration) {
            interactionHandler.longPressDuration = longPressDuration
        }

    fun setLongPressHandlingEnabled(isEnabled: Boolean) {
        interactionHandler.isLongPressHandlingEnabled = isEnabled
    }

    fun setDoublePressHandlingEnabled(isEnabled: Boolean) {
        interactionHandler.isDoubleTapHandlingEnabled = isEnabled
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return TouchLogger.logDispatchTouch("long_press", event, super.dispatchTouchEvent(event))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return interactionHandler.onTouchEvent(event)
    }

    private fun setupAccessibilityDelegate() {
        accessibilityDelegate =
            object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    v: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(v, info)
                    if (
                        interactionHandler.isLongPressHandlingEnabled &&
                            accessibilityHintLongPressAction != null
                    ) {
                        info.addAction(accessibilityHintLongPressAction)
                    }
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    return if (
                        interactionHandler.isLongPressHandlingEnabled &&
                            action == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK
                    ) {
                        val touchHandlingView = host as? TouchHandlingView
                        if (touchHandlingView != null) {
                            // the coordinates are not available as it is an a11y long press
                            listener?.onLongPressDetected(
                                view = touchHandlingView,
                                x = 0,
                                y = 0,
                                isA11yAction = true,
                            )
                            true
                        } else {
                            false
                        }
                    } else {
                        super.performAccessibilityAction(host, action, args)
                    }
                }
            }
    }
}
