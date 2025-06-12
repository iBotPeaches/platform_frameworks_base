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
package com.android.systemui.model

import android.util.Log
import android.view.Display
import com.android.app.displaylib.PerDisplayInstanceProviderWithTeardown
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.model.SysUiState.SysUiStateCallback
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dalvik.annotation.optimization.NeverCompile
import java.io.PrintWriter
import java.lang.Long.bitCount
import javax.inject.Inject

/** Contains sysUi state flags and notifies registered listeners whenever changes happen. */
interface SysUiState : Dumpable {
    /**
     * Add listener to be notified of changes made to SysUI state.
     *
     * The callback will also be called as part of this function.
     */
    fun addCallback(callback: SysUiStateCallback)

    /** Removes a callback for state changes. */
    fun removeCallback(callback: SysUiStateCallback)

    /** Returns whether a flag is enabled in this state. */
    fun isFlagEnabled(@SystemUiStateFlags flag: Long): Boolean {
        return (flags and flag) != 0L
    }

    /** Returns the current sysui state flags. */
    val flags: Long

    /** Methods to this call can be chained together before calling [commitUpdate]. */
    fun setFlag(@SystemUiStateFlags flag: Long, enabled: Boolean): SysUiState

    /** Call to save all the flags updated from [setFlag]. */
    @Deprecated("Each SysUIState instance is now display specific. Just use commitUpdate()")
    fun commitUpdate(displayId: Int)

    /** Call to save all the flags updated from [setFlag]. */
    fun commitUpdate()

    /** Callback to be notified whenever system UI state flags are changed. */
    fun interface SysUiStateCallback {

        /** To be called when any SysUiStateFlag gets updated for a specific [displayId]. */
        fun onSystemUiStateChanged(@SystemUiStateFlags sysUiFlags: Long, displayId: Int)
    }

    /**
     * Destroys an instance. It shouldn't be used anymore afterwards.
     *
     * This is mainly used to clean up instances associated with displays that are removed.
     */
    fun destroy()

    /** Initializes the state after construction. */
    fun start()

    /** The display ID this instances is associated with */
    val displayId: Int

    companion object {
        const val DEBUG: Boolean = false
    }
}

private const val TAG = "SysUIState"

open class SysUiStateImpl
@AssistedInject
constructor(
    @Assisted override val displayId: Int,
    private val sceneContainerPlugin: SceneContainerPlugin?,
    private val dumpManager: DumpManager,
    private val stateDispatcher: SysUIStateDispatcher,
) : SysUiState {

    private val debugName
        get() = "SysUiStateImpl-ForDisplay=$displayId"

    override fun start() {
        dumpManager.registerNormalDumpable(debugName, this)
    }

    /** Returns the current sysui state flags. */
    @get:SystemUiStateFlags
    @SystemUiStateFlags
    override val flags: Long
        get() = _flags

    private var _flags: Long = 0
    private val stateChange = StateChange()

    /**
     * Add listener to be notified of changes made to SysUI state. The callback will also be called
     * as part of this function.
     *
     * Note that the listener would receive updates for all displays.
     */
    override fun addCallback(callback: SysUiStateCallback) {
        stateDispatcher.registerListener(callback)
        callback.onSystemUiStateChanged(flags, displayId)
    }

    /** Callback will no longer receive events on state change */
    override fun removeCallback(callback: SysUiStateCallback) {
        stateDispatcher.unregisterListener(callback)
    }

    /** Methods to this call can be chained together before calling [.commitUpdate]. */
    override fun setFlag(@SystemUiStateFlags flag: Long, enabled: Boolean): SysUiState {
        if (ShadeWindowGoesAround.isEnabled && bitCount(flag) > 1) {
            error("Flags should be a single bit.")
        }
        val toSet = flagWithOptionalOverrides(flag, enabled, displayId, sceneContainerPlugin)
        stateChange.setFlag(flag, toSet)
        return this
    }

    @Deprecated(
        "Each SysUIState instance is now display specific. Just use commitUpdate.",
        ReplaceWith("commitUpdate()"),
    )
    override fun commitUpdate(displayId: Int) {
        commitUpdate()
    }

    override fun commitUpdate() {
        val newState = stateChange.applyTo(flags)
        notifyAndSetSystemUiStateChanged(newState, flags)
        stateChange.clear()
    }

    /** Notify all those who are registered that the state has changed. */
    private fun notifyAndSetSystemUiStateChanged(newFlags: Long, oldFlags: Long) {
        if (SysUiState.DEBUG) {
            Log.d(TAG, "SysUiState changed for displayId=$displayId: old=$oldFlags new=$newFlags")
        }
        if (newFlags != oldFlags) {
            _flags = newFlags
            stateDispatcher.dispatchSysUIStateChange(newFlags, displayId)
        }
    }

    @NeverCompile
    override fun dump(pw: PrintWriter, args: Array<String>) {
        pw.println("SysUiState state:")
        pw.print("  mSysUiStateFlags=")
        pw.println(flags)
        pw.println("    " + QuickStepContract.getSystemUiStateString(flags))
        pw.print("    backGestureDisabled=")
        pw.println(QuickStepContract.isBackGestureDisabled(flags, false /* forTrackpad */))
        pw.print("    assistantGestureDisabled=")
        pw.println(QuickStepContract.isAssistantGestureDisabled(flags))
        pw.print("    pendingStateChanges=")
        pw.println(stateChange.toString())
    }

    override fun destroy() {
        dumpManager.unregisterDumpable(debugName)
    }

    @AssistedFactory
    interface Factory {
        /** Creates a new instance of [SysUiStateImpl] for a given [displayId]. */
        fun create(displayId: Int): SysUiStateImpl
    }

    companion object {
        private val TAG: String = SysUiState::class.java.simpleName
    }
}

/** Returns the flag value taking into account [SceneContainerPlugin] potential overrides. */
fun flagWithOptionalOverrides(
    flag: Long,
    enabled: Boolean,
    displayId: Int,
    sceneContainerPlugin: SceneContainerPlugin?,
): Boolean {
    var toSet = enabled
    val overrideOrNull = sceneContainerPlugin?.flagValueOverride(flag = flag, displayId = displayId)
    if (overrideOrNull != null && toSet != overrideOrNull) {
        if (SysUiState.DEBUG) {
            Log.d(
                TAG,
                "setFlag for flag $flag and value $toSet overridden to " +
                    "$overrideOrNull by scene container plugin",
            )
        }

        toSet = overrideOrNull
    }
    return toSet
}

/** Creates and destroy instances of [SysUiState] */
@SysUISingleton
class SysUIStateInstanceProvider
@Inject
constructor(
    private val factory: SysUiStateImpl.Factory,
    private val overrideFactory: SysUIStateOverride.Factory,
) : PerDisplayInstanceProviderWithTeardown<SysUiState> {
    override fun createInstance(displayId: Int): SysUiState {
        return if (displayId == Display.DEFAULT_DISPLAY) {
                factory.create(displayId)
            } else {
                overrideFactory.create(displayId)
            }
            .apply { start() }
    }

    override fun destroyInstance(instance: SysUiState) {
        instance.destroy()
    }
}
