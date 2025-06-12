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

package com.android.systemui.statusbar.notification.row

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.android.systemui.res.R
import com.android.wm.shell.shared.animation.Interpolators
import android.graphics.drawable.RippleDrawable
import androidx.core.content.ContextCompat

class AnimatedActionBackgroundDrawable(
    context: Context,
) : RippleDrawable(
    ContextCompat.getColorStateList(
        context,
        R.color.notification_ripple_untinted_color
    ) ?: ColorStateList.valueOf(Color.TRANSPARENT),
    createBaseDrawable(context), null
) {
    companion object {
        private fun createBaseDrawable(context: Context): Drawable {
            return BaseBackgroundDrawable(context)
        }
    }
}

class BaseBackgroundDrawable(
    context: Context,
) : Drawable() {

    private val cornerRadius = context.resources.getDimension(R.dimen.animated_action_button_corner_radius)
    private val outlineStrokeWidth = context.resources.getDimension(R.dimen.animated_action_button_outline_stroke_width)
    private val insetVertical = 8 * context.resources.displayMetrics.density

    private val buttonShape = Path()
    // Color and style
    private val outlineStaticColor = context.getColor(R.color.animated_action_button_stroke_color)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val bgColor =
            context.getColor(
                com.android.internal.R.color.materialColorSurfaceContainerHigh
            )
        color = bgColor
        style = Paint.Style.FILL
    }
    private val outlineGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outlineStaticColor
        style = Paint.Style.STROKE
        strokeWidth = outlineStrokeWidth
    }
    private val outlineSolidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outlineStaticColor
        style = Paint.Style.STROKE
        strokeWidth = outlineStrokeWidth
    }

    private val outlineStartColor =
        context.getColor(
            com.android.internal.R.color.materialColorTertiaryContainer
        )
    private val outlineMiddleColor =
        context.getColor(
            com.android.internal.R.color.materialColorPrimaryFixedDim
        )
    private val outlineEndColor =
        context.getColor(
            com.android.internal.R.color.materialColorPrimary
        )

    // Animation
    private var gradientAnimator: ValueAnimator
    private var rotationAngle = 20f // Start rotation at 20 degrees
    private var fadeAnimator: ValueAnimator? = null
    private var gradientAlpha = 255    // Fading out gradient
    private var solidAlpha = 0         // Fading in solid color

    init {
        gradientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            interpolator = Interpolators.LINEAR
            repeatCount = 0
            addUpdateListener { animator ->
                val animatedValue = animator.animatedValue as Float
                rotationAngle = 20f + animatedValue * 360f // Rotate in a spiral
                invalidateSelf()
            }
            start()
        }
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            startDelay = 1000
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                gradientAlpha = ((1 - progress) * 255).toInt()  // Fade out gradient
                solidAlpha = (progress * 255).toInt()          // Fade in color
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        val boundsF = RectF(bounds)
        boundsF.inset(0f, insetVertical)
        buttonShape.reset()
        buttonShape.addRoundRect(boundsF, cornerRadius, cornerRadius, Path.Direction.CW)

        canvas.save()
        // Draw background
        canvas.clipPath(buttonShape)
        canvas.drawPath(buttonShape, bgPaint)

        // Set up outline gradient
        val gradientShader = LinearGradient(
            boundsF.left, boundsF.top,
            boundsF.right, boundsF.bottom,
            intArrayOf(outlineStartColor, outlineMiddleColor, outlineEndColor),
            null,
            Shader.TileMode.CLAMP
        )
        // Create a rotation matrix for the spiral effect
        val matrix = Matrix()
        matrix.setRotate(rotationAngle, boundsF.centerX(), boundsF.centerY())
        gradientShader.setLocalMatrix(matrix)

        // Apply gradient to outline
        outlineGradientPaint.shader = gradientShader
        outlineGradientPaint.alpha = gradientAlpha
        canvas.drawPath(buttonShape, outlineGradientPaint)
        // Apply solid color to outline
        outlineSolidPaint.alpha = solidAlpha
        canvas.drawPath(buttonShape, outlineSolidPaint)

        canvas.restore()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf() // Redraw when size changes
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        outlineGradientPaint.alpha = alpha
        outlineSolidPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        outlineGradientPaint.colorFilter = colorFilter
        outlineSolidPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
