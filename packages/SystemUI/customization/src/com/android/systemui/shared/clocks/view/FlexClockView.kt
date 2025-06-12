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

package com.android.systemui.shared.clocks.view

import android.graphics.Canvas
import android.icu.text.NumberFormat
import android.util.MathUtils.constrainedMap
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.annotation.VisibleForTesting
import androidx.core.view.children
import com.android.app.animation.Interpolators
import com.android.systemui.customization.R
import com.android.systemui.plugins.clocks.ClockAxisStyle
import com.android.systemui.plugins.clocks.ClockLogger
import com.android.systemui.plugins.clocks.VPoint
import com.android.systemui.plugins.clocks.VPointF
import com.android.systemui.plugins.clocks.VPointF.Companion.max
import com.android.systemui.plugins.clocks.VPointF.Companion.times
import com.android.systemui.plugins.clocks.VRectF
import com.android.systemui.shared.clocks.CanvasUtil.translate
import com.android.systemui.shared.clocks.CanvasUtil.use
import com.android.systemui.shared.clocks.ClockContext
import com.android.systemui.shared.clocks.DigitTranslateAnimator
import com.android.systemui.shared.clocks.ViewUtils.measuredSize
import java.util.Locale
import kotlin.collections.filterNotNull
import kotlin.collections.map
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun clamp(value: Float, minVal: Float, maxVal: Float): Float = max(min(value, maxVal), minVal)

class FlexClockView(clockCtx: ClockContext) : ViewGroup(clockCtx.context) {
    protected val logger = ClockLogger(this, clockCtx.messageBuffer, this::class.simpleName!!)
        get() = field ?: ClockLogger.INIT_LOGGER

    @VisibleForTesting
    var isAnimationEnabled = true
        set(value) {
            field = value
            childViews.forEach { view -> view.isAnimationEnabled = value }
        }

    var dozeFraction: Float = 0F
        set(value) {
            field = value
            childViews.forEach { view -> view.dozeFraction = field }
        }

    var isReactiveTouchInteractionEnabled = false
        set(value) {
            field = value
        }

    var _childViews: List<SimpleDigitalClockTextView>? = null
    val childViews: List<SimpleDigitalClockTextView>
        get() {
            return _childViews
                ?: this.children
                    .map { child -> child as? SimpleDigitalClockTextView }
                    .filterNotNull()
                    .toList()
                    .also { _childViews = it }
        }

    private var maxChildSize = VPointF(-1, -1)
    private val lockscreenTranslate = VPointF.ZERO
    private var aodTranslate = VPointF.ZERO

    private var onAnimateDoze: (() -> Unit)? = null
    private var isDozeReadyToAnimate = false

    // Does the current language have mono vertical size when displaying numerals
    private var isMonoVerticalNumericLineSpacing = true

    init {
        setWillNotDraw(false)
        layoutParams =
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        updateLocale(Locale.getDefault())
    }

    var onViewBoundsChanged: ((VRectF) -> Unit)? = null
    private val digitOffsets = mutableMapOf<Int, Float>()

    protected fun calculateSize(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        shouldMeasureChildren: Boolean,
    ): VPointF {
        maxChildSize = VPointF(-1, -1)
        childViews.forEach { textView ->
            if (shouldMeasureChildren) {
                textView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            }
            maxChildSize = max(maxChildSize, textView.measuredSize)
        }
        aodTranslate = VPointF.ZERO
        // TODO(b/364680879): Cleanup
        /*
        aodTranslate = VPointF(
            maxChildSize.x * AOD_HORIZONTAL_TRANSLATE_RATIO,
            maxChildSize.y * AOD_VERTICAL_TRANSLATE_RATIO
        )
        */

        val xScale = if (childViews.size < 4) 1f else 2f
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)
        return (maxChildSize + aodTranslate.abs()) * VPointF(xScale, 2f) + VPointF(0f, yBuffer)
    }

    override fun onViewAdded(child: View?) {
        if (child == null) return
        logger.onViewAdded(child)
        super.onViewAdded(child)
        (child as? SimpleDigitalClockTextView)?.let {
            it.digitTranslateAnimator = DigitTranslateAnimator { invalidate() }
        }
        child.setWillNotDraw(true)
        _childViews = null
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        _childViews = null
    }

    fun refreshTime() {
        logger.refreshTime()
        childViews.forEach { textView -> textView.refreshText() }
    }

    override fun setVisibility(visibility: Int) {
        logger.setVisibility(visibility)
        super.setVisibility(visibility)
    }

    override fun setAlpha(alpha: Float) {
        logger.setAlpha(alpha)
        super.setAlpha(alpha)
    }

    override fun invalidate() {
        logger.invalidate()
        super.invalidate()
    }

    override fun requestLayout() {
        logger.requestLayout()
        super.requestLayout()
    }

    fun updateMeasuredSize() =
        updateMeasuredSize(
            measuredWidthAndState,
            measuredHeightAndState,
            shouldMeasureChildren = false,
        )

    private fun updateMeasuredSize(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        shouldMeasureChildren: Boolean,
    ) {
        val size = calculateSize(widthMeasureSpec, heightMeasureSpec, shouldMeasureChildren)
        setMeasuredDimension(size.x.roundToInt(), size.y.roundToInt())
    }

    fun updateLocation() {
        val layoutBounds = this.layoutBounds ?: return
        val bounds = VRectF.fromCenter(layoutBounds.center, this.measuredSize)
        setFrame(
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.right.roundToInt(),
            bounds.bottom.roundToInt(),
        )
        updateChildFrames(isLayout = false)
        onViewBoundsChanged?.let { it(bounds) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        logger.onMeasure(widthMeasureSpec, heightMeasureSpec)
        updateMeasuredSize(widthMeasureSpec, heightMeasureSpec, shouldMeasureChildren = true)

        isDozeReadyToAnimate = true
        onAnimateDoze?.invoke()
        onAnimateDoze = null
    }

    private var layoutBounds = VRectF.ZERO

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        logger.onLayout(changed, left, top, right, bottom)
        layoutBounds = VRectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        updateChildFrames(isLayout = true)
    }

    private fun updateChildFrames(isLayout: Boolean) {
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)
        childViews.forEach { child ->
            var offset =
                maxChildSize.run {
                    when (child.id) {
                        R.id.HOUR_FIRST_DIGIT -> VPointF.ZERO
                        R.id.HOUR_SECOND_DIGIT -> VPointF(x, 0f)
                        R.id.HOUR_DIGIT_PAIR -> VPointF.ZERO
                        // Add a small vertical buffer for second line views
                        R.id.MINUTE_DIGIT_PAIR -> VPointF(0f, y + yBuffer)
                        R.id.MINUTE_FIRST_DIGIT -> VPointF(0f, y + yBuffer)
                        R.id.MINUTE_SECOND_DIGIT -> VPointF(x, y + yBuffer)
                        else -> VPointF.ZERO
                    }
                }

            val childSize = child.measuredSize
            offset += aodTranslate.abs()

            // Horizontal offset to center each view in the available space
            val midX = if (childViews.size < 4) measuredWidth / 2f else measuredWidth / 4f
            offset += VPointF(midX - childSize.x / 2f, 0f)

            val setPos = if (isLayout) child::layout else child::setLeftTopRightBottom
            setPos(
                offset.x.roundToInt(),
                offset.y.roundToInt(),
                (offset.x + childSize.x).roundToInt(),
                (offset.y + childSize.y).roundToInt(),
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        logger.onDraw()
        childViews.forEach { child ->
            canvas.use { canvas ->
                canvas.translate(digitOffsets.getOrDefault(child.id, 0f), 0f)
                canvas.translate(child.left.toFloat(), child.top.toFloat())
                child.draw(canvas)
            }
        }
    }

    fun onLocaleChanged(locale: Locale) {
        updateLocale(locale)
        requestLayout()
    }

    fun updateColor(color: Int) {
        childViews.forEach { view -> view.updateColor(color) }
        invalidate()
    }

    fun updateAxes(axes: ClockAxisStyle, isAnimated: Boolean) {
        childViews.forEach { view -> view.updateAxes(axes, isAnimated) }
        requestLayout()
    }

    fun onFontSettingChanged(fontSizePx: Float) {
        childViews.forEach { view -> view.applyTextSize(fontSizePx) }
    }

    fun animateDoze(isDozing: Boolean, isAnimated: Boolean) {
        fun executeDozeAnimation() {
            childViews.forEach { view -> view.animateDoze(isDozing, isAnimated) }
            if (maxChildSize.x < 0 || maxChildSize.y < 0) {
                measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            }
            childViews.forEach { textView ->
                textView.digitTranslateAnimator?.let {
                    if (!isDozing) {
                        it.animatePosition(
                            animate = isAnimated && isAnimationEnabled,
                            interpolator = Interpolators.EMPHASIZED,
                            duration = AOD_TRANSITION_DURATION,
                            targetTranslation =
                                updateDirectionalTargetTranslate(id, lockscreenTranslate),
                        )
                    } else {
                        it.animatePosition(
                            animate = isAnimated && isAnimationEnabled,
                            interpolator = Interpolators.EMPHASIZED,
                            duration = AOD_TRANSITION_DURATION,
                            onAnimationEnd = null,
                            targetTranslation = updateDirectionalTargetTranslate(id, aodTranslate),
                        )
                    }
                }
            }
        }

        if (isDozeReadyToAnimate) executeDozeAnimation()
        else onAnimateDoze = { executeDozeAnimation() }
    }

    fun animateCharge() {
        childViews.forEach { view -> view.animateCharge() }
        childViews.forEach { textView ->
            textView.digitTranslateAnimator?.let {
                it.animatePosition(
                    animate = isAnimationEnabled,
                    interpolator = Interpolators.EMPHASIZED,
                    duration = CHARGING_TRANSITION_DURATION,
                    onAnimationEnd = {
                        it.animatePosition(
                            animate = isAnimationEnabled,
                            interpolator = Interpolators.EMPHASIZED,
                            duration = CHARGING_TRANSITION_DURATION,
                            targetTranslation =
                                updateDirectionalTargetTranslate(
                                    textView.id,
                                    if (dozeFraction == 1F) aodTranslate else lockscreenTranslate,
                                ),
                        )
                    },
                    targetTranslation =
                        updateDirectionalTargetTranslate(
                            textView.id,
                            if (dozeFraction == 1F) lockscreenTranslate else aodTranslate,
                        ),
                )
            }
        }
    }

    fun animateFidget(x: Float, y: Float) {
        val touchPt = VPointF(x, y)
        val ints = intArrayOf(0, 0)
        childViews
            .sortedBy { view ->
                view.getLocationInWindow(ints)
                val loc = VPoint(ints[0], ints[1])
                val center = loc + view.measuredSize / 2f
                (center - touchPt).length()
            }
            .forEachIndexed { i, view ->
                view.animateFidget(FIDGET_DELAYS[min(i, FIDGET_DELAYS.size - 1)])
            }
    }

    private fun updateLocale(locale: Locale) {
        isMonoVerticalNumericLineSpacing =
            !NON_MONO_VERTICAL_NUMERIC_LINE_SPACING_LANGUAGES.any {
                val newLocaleNumberFormat =
                    NumberFormat.getInstance(locale).format(FORMAT_NUMBER.toLong())
                val nonMonoVerticalNumericLineSpaceNumberFormat =
                    NumberFormat.getInstance(Locale.forLanguageTag(it))
                        .format(FORMAT_NUMBER.toLong())
                newLocaleNumberFormat == nonMonoVerticalNumericLineSpaceNumberFormat
            }
    }

    /**
     * Offsets the textViews of the clock for the step clock animation.
     *
     * The animation makes the textViews of the clock move at different speeds, when the clock is
     * moving horizontally.
     *
     * @param clockStartLeft the [getLeft] position of the clock, before it started moving.
     * @param clockMoveDirection the direction in which it is moving. A positive number means right,
     *   and negative means left.
     * @param moveFraction fraction of the clock movement. 0 means it is at the beginning, and 1
     *   means it finished moving.
     */
    fun offsetGlyphsForStepClockAnimation(
        clockStartLeft: Int,
        clockMoveDirection: Int,
        moveFraction: Float,
    ) {
        // TODO(b/393577936): The step animation isn't correct with the two pairs approach
        val isMovingToCenter = if (isLayoutRtl) clockMoveDirection < 0 else clockMoveDirection > 0
        // The sign of moveAmountDeltaForDigit is already set here
        // we can interpret (left - clockStartLeft) as (destinationPosition - originPosition)
        // so we no longer need to multiply direct sign to moveAmountDeltaForDigit
        val currentMoveAmount = left - clockStartLeft
        var index = 0
        childViews.forEach { child ->
            val digitFraction =
                getDigitFraction(
                    digit = index++,
                    isMovingToCenter = isMovingToCenter,
                    fraction = moveFraction,
                )
            // left here is the final left position after the animation is done
            val moveAmountForDigit = currentMoveAmount * digitFraction
            var moveAmountDeltaForDigit = moveAmountForDigit - currentMoveAmount
            if (isMovingToCenter && moveAmountForDigit < 0) moveAmountDeltaForDigit *= -1
            digitOffsets[child.id] = moveAmountDeltaForDigit
            invalidate()
        }
    }

    private val moveToCenterDelays: List<Int>
        get() = if (isLayoutRtl) MOVE_LEFT_DELAYS else MOVE_RIGHT_DELAYS

    private val moveToSideDelays: List<Int>
        get() = if (isLayoutRtl) MOVE_RIGHT_DELAYS else MOVE_LEFT_DELAYS

    private fun getDigitFraction(digit: Int, isMovingToCenter: Boolean, fraction: Float): Float {
        // The delay for the digit, in terms of fraction.
        // (i.e. the digit should not move during 0.0 - 0.1).
        val delays = if (isMovingToCenter) moveToCenterDelays else moveToSideDelays
        val digitInitialDelay = delays[digit] * MOVE_DIGIT_STEP
        return MOVE_INTERPOLATOR.getInterpolation(
            constrainedMap(
                /* rangeMin= */ 0.0f,
                /* rangeMax= */ 1.0f,
                /* valueMin= */ digitInitialDelay,
                /* valueMax= */ digitInitialDelay + availableAnimationTime(childViews.size),
                /* value= */ fraction,
            )
        )
    }

    companion object {
        val AOD_TRANSITION_DURATION = 750L
        val CHARGING_TRANSITION_DURATION = 300L

        val AOD_HORIZONTAL_TRANSLATE_RATIO = -0.15F
        val AOD_VERTICAL_TRANSLATE_RATIO = 0.075F

        val FIDGET_DELAYS = listOf(0L, 75L, 150L, 225L)

        // Delays. Each digit's animation should have a slight delay, so we get a nice
        // "stepping" effect. When moving right, the second digit of the hour should move first.
        // When moving left, the first digit of the hour should move first. The lists encode
        // the delay for each digit (hour[0], hour[1], minute[0], minute[1]), to be multiplied
        // by delayMultiplier.
        private val MOVE_LEFT_DELAYS = listOf(0, 1, 2, 3)
        private val MOVE_RIGHT_DELAYS = listOf(1, 0, 3, 2)

        // How much delay to apply to each subsequent digit. This is measured in terms of "fraction"
        // (i.e. a value of 0.1 would cause a digit to wait until fraction had hit 0.1, or 0.2 etc
        // before moving).
        //
        // The current specs dictate that each digit should have a 33ms gap between them. The
        // overall time is 1s right now.
        private const val MOVE_DIGIT_STEP = 0.033f

        // Constants for the animation
        private val MOVE_INTERPOLATOR = Interpolators.EMPHASIZED

        private const val FORMAT_NUMBER = 1234567890

        // Total available transition time for each digit, taking into account the step. If step is
        // 0.1, then digit 0 would animate over 0.0 - 0.7, making availableTime 0.7.
        private fun availableAnimationTime(numDigits: Int): Float {
            return 1.0f - MOVE_DIGIT_STEP * (numDigits.toFloat() - 1)
        }

        // Add language tags below that do not have vertically mono spaced numerals
        private val NON_MONO_VERTICAL_NUMERIC_LINE_SPACING_LANGUAGES =
            setOf(
                "my" // Burmese
            )

        // Use the sign of targetTranslation to control the direction of digit translation
        fun updateDirectionalTargetTranslate(id: Int, targetTranslation: VPointF): VPointF {
            return targetTranslation *
                when (id) {
                    R.id.HOUR_FIRST_DIGIT -> VPointF(-1, -1)
                    R.id.HOUR_SECOND_DIGIT -> VPointF(1, -1)
                    R.id.MINUTE_FIRST_DIGIT -> VPointF(-1, 1)
                    R.id.MINUTE_SECOND_DIGIT -> VPointF(1, 1)
                    R.id.HOUR_DIGIT_PAIR -> VPointF(-1, -1)
                    R.id.MINUTE_DIGIT_PAIR -> VPointF(-1, 1)
                    else -> VPointF(1, 1)
                }
        }
    }
}
