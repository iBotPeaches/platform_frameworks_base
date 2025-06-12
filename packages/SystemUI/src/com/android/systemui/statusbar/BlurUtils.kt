/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.res.Resources
import android.os.Build
import android.os.SystemProperties
import android.os.Trace
import android.os.Trace.TRACE_TAG_APP
import android.util.IndentingPrintWriter
import android.util.Log
import android.util.MathUtils
import android.view.CrossWindowBlurListeners
import android.view.CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED
import android.view.SyncRtSurfaceTransactionApplier
import android.view.ViewRootImpl
import androidx.annotation.VisibleForTesting
import com.android.systemui.Dumpable
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.res.R
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
open class BlurUtils
@Inject
constructor(
    @Main resources: Resources,
    blurConfig: BlurConfig,
    private val crossWindowBlurListeners: CrossWindowBlurListeners,
    dumpManager: DumpManager,
) : Dumpable {
    val minBlurRadius = resources.getDimensionPixelSize(R.dimen.min_window_blur_radius).toFloat()
    val maxBlurRadius =
        if (Flags.notificationShadeBlur()) {
            blurConfig.maxBlurRadiusPx
        } else {
            resources.getDimensionPixelSize(R.dimen.max_window_blur_radius).toFloat()
        }

    private var lastAppliedBlur = 0
    private var lastTargetViewRootImpl: ViewRootImpl? = null
    private var _transactionApplier = SyncRtSurfaceTransactionApplier(null)
    @VisibleForTesting
    open val transactionApplier: SyncRtSurfaceTransactionApplier
        get() = _transactionApplier

    private var earlyWakeupEnabled = false

    /** When this is true, early wakeup flag is not reset on surface flinger when blur drops to 0 */
    private var persistentEarlyWakeupRequired = false

    init {
        dumpManager.registerDumpable(this)
    }

    /** Translates a ratio from 0 to 1 to a blur radius in pixels. */
    fun blurRadiusOfRatio(ratio: Float): Float {
        if (ratio == 0f) {
            return 0f
        }
        return MathUtils.lerp(minBlurRadius, maxBlurRadius, ratio)
    }

    /** Translates a blur radius in pixels to a ratio between 0 to 1. */
    fun ratioOfBlurRadius(blur: Float): Float {
        if (blur == 0f) {
            return 0f
        }
        return MathUtils.map(
            minBlurRadius,
            maxBlurRadius,
            0f /* maxStart */,
            1f /* maxStop */,
            blur,
        )
    }

    /**
     * This method should be called before [applyBlur] so that, if needed, we can set the
     * early-wakeup flag in SurfaceFlinger.
     */
    fun prepareBlur(viewRootImpl: ViewRootImpl?, radius: Int) {
        if (
            viewRootImpl == null ||
                !viewRootImpl.surfaceControl.isValid ||
                !shouldBlur(radius) ||
                earlyWakeupEnabled
        ) {
            return
        }
        updateTransactionApplier(viewRootImpl)
        val builder =
            SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(viewRootImpl.surfaceControl)
        if (lastAppliedBlur == 0 && radius != 0) {
            earlyWakeupStart(builder, "eEarlyWakeup (prepareBlur)")
            transactionApplier.scheduleApply(builder.build())
        }
    }

    /**
     * Applies background blurs to a {@link ViewRootImpl}.
     *
     * @param viewRootImpl The window root.
     * @param radius blur radius in pixels.
     * @param opaque if surface is opaque, regardless or having blurs or no.
     */
    fun applyBlur(viewRootImpl: ViewRootImpl?, radius: Int, opaque: Boolean) {
        if (viewRootImpl == null || !viewRootImpl.surfaceControl.isValid) {
            return
        }
        updateTransactionApplier(viewRootImpl)
        val builder =
            SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(viewRootImpl.surfaceControl)
        if (shouldBlur(radius)) {
            builder.withBackgroundBlur(radius)
            if (!earlyWakeupEnabled && lastAppliedBlur == 0 && radius != 0) {
                earlyWakeupStart(builder, "eEarlyWakeup (applyBlur)")
            }
            if (
                earlyWakeupEnabled &&
                    lastAppliedBlur != 0 &&
                    radius == 0 &&
                    !persistentEarlyWakeupRequired
            ) {
                earlyWakeupEnd(builder, "applyBlur")
            }
            lastAppliedBlur = radius
        }
        builder.withOpaque(opaque)
        transactionApplier.scheduleApply(builder.build())
    }

    private fun updateTransactionApplier(viewRootImpl: ViewRootImpl) {
        if (lastTargetViewRootImpl == viewRootImpl) return
        _transactionApplier = SyncRtSurfaceTransactionApplier(viewRootImpl.view)
        lastTargetViewRootImpl = viewRootImpl
    }

    private fun v(verboseLog: String) {
        if (isLoggable) Log.v(TAG, verboseLog)
    }

    @SuppressLint("MissingPermission")
    private fun earlyWakeupStart(
        builder: SyncRtSurfaceTransactionApplier.SurfaceParams.Builder,
        traceMethodName: String,
    ) {
        v("earlyWakeupStart from $traceMethodName")
        Trace.asyncTraceForTrackBegin(TRACE_TAG_APP, TRACK_NAME, traceMethodName, 0)
        builder.withEarlyWakeupStart()
        earlyWakeupEnabled = true
    }

    @SuppressLint("MissingPermission")
    private fun earlyWakeupEnd(
        builder: SyncRtSurfaceTransactionApplier.SurfaceParams.Builder,
        loggingContext: String,
    ) {
        v("earlyWakeupEnd from $loggingContext")
        builder.withEarlyWakeupEnd()
        Trace.asyncTraceForTrackEnd(TRACE_TAG_APP, TRACK_NAME, 0)
        earlyWakeupEnabled = false
    }

    private fun shouldBlur(radius: Int): Boolean {
        return supportsBlursOnWindows() ||
            ((Flags.notificationShadeBlur() || Flags.bouncerUiRevamp()) &&
                supportsBlursOnWindowsBase() &&
                lastAppliedBlur > 0 &&
                radius == 0)
    }

    /**
     * If this device can render blurs.
     *
     * @return {@code true} when supported.
     * @see android.view.SurfaceControl.Transaction#setBackgroundBlurRadius(SurfaceControl, int)
     */
    open fun supportsBlursOnWindows(): Boolean {
        return supportsBlursOnWindowsBase() && crossWindowBlurListeners.isCrossWindowBlurEnabled
    }

    private fun supportsBlursOnWindowsBase(): Boolean {
        return CROSS_WINDOW_BLUR_SUPPORTED &&
            ActivityManager.isHighEndGfx() &&
            !SystemProperties.getBoolean("persist.sysui.disableBlur", false)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        IndentingPrintWriter(pw, "  ").let {
            it.println("BlurUtils:")
            it.increaseIndent()
            it.println("minBlurRadius: $minBlurRadius")
            it.println("maxBlurRadius: $maxBlurRadius")
            it.println("supportsBlursOnWindows: ${supportsBlursOnWindows()}")
            it.println("CROSS_WINDOW_BLUR_SUPPORTED: $CROSS_WINDOW_BLUR_SUPPORTED")
            it.println("isHighEndGfx: ${ActivityManager.isHighEndGfx()}")
        }
    }

    /**
     * Enables/disables the early wakeup flag on surface flinger. Keeps the early wakeup flag on
     * until it reset by passing false to this method.
     */
    fun setPersistentEarlyWakeup(persistentWakeup: Boolean, viewRootImpl: ViewRootImpl?) {
        persistentEarlyWakeupRequired = persistentWakeup
        if (viewRootImpl == null || !supportsBlursOnWindows()) return

        updateTransactionApplier(viewRootImpl)
        val builder =
            SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(viewRootImpl.surfaceControl)
        if (persistentEarlyWakeupRequired) {
            if (earlyWakeupEnabled) return
            earlyWakeupStart(builder, "setEarlyWakeup")
            transactionApplier.scheduleApply(builder.build())
        } else {
            if (!earlyWakeupEnabled) return
            if (lastAppliedBlur > 0) {
                Log.w(
                    TAG,
                    "resetEarlyWakeup invoked when lastAppliedBlur $lastAppliedBlur is " +
                        "non-zero, this means that the early wakeup signal was reset while blur" +
                        " was still active",
                )
            }
            earlyWakeupEnd(builder, "resetEarlyWakeup")
            transactionApplier.scheduleApply(builder.build())
        }
    }

    companion object {
        const val TRACK_NAME = "BlurUtils"
        private const val TAG = "BlurUtils"
        private val isLoggable = Log.isLoggable(TAG, Log.VERBOSE) || Build.isDebuggable()
    }
}
