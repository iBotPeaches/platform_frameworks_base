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

import android.view.Display
import com.android.systemui.dump.DumpManager
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * This class is used to provide per-display overrides for only certain flags.
 *
 * While some of the [SystemUiStateFlags] are per display (e.g. shade expansion, dialog visible),
 * some of them are device specific (e.g. whether it's awake or not). A [SysUIStateOverride] is
 * created for each display that is not [Display.DEFAULT_DISPLAY], and if some flags are set on it,
 * they will override whatever the default display state had in those.
 */
class SysUIStateOverride
@AssistedInject
constructor(
    @Assisted override val displayId: Int,
    private val sceneContainerPlugin: SceneContainerPlugin?,
    dumpManager: DumpManager,
    private val defaultDisplayState: SysUiState,
    private val stateDispatcher: SysUIStateDispatcher,
) : SysUiStateImpl(displayId, sceneContainerPlugin, dumpManager, stateDispatcher) {

    private val override = StateChange()
    private var lastSentFlags = defaultDisplayState.flags

    private val defaultFlagsChangedCallback = { _: Long, otherDisplayId: Int ->
        if (otherDisplayId == Display.DEFAULT_DISPLAY) {
            commitUpdate()
        }
    }

    override fun start() {
        super.start()
        stateDispatcher.registerListener(defaultFlagsChangedCallback)
    }

    override fun destroy() {
        super.destroy()
        stateDispatcher.unregisterListener(defaultFlagsChangedCallback)
    }

    override fun commitUpdate() {
        if (flags != lastSentFlags) {
            stateDispatcher.dispatchSysUIStateChange(flags, displayId)
            lastSentFlags = flags
        }
    }

    override val flags: Long
        get() = override.applyTo(defaultDisplayState.flags)

    override fun setFlag(@SystemUiStateFlags flag: Long, enabled: Boolean): SysUiState {
        val toSet = flagWithOptionalOverrides(flag, enabled, displayId, sceneContainerPlugin)
        override.setFlag(flag, toSet)
        return this
    }

    @AssistedFactory
    interface Factory {
        fun create(displayId: Int): SysUIStateOverride
    }
}
