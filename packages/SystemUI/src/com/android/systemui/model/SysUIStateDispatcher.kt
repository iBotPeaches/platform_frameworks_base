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
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/**
 * Channels changes from several [SysUiState]s to a single callback.
 *
 * There are several [SysUiState]s (one per display). This class allows for listeners to listen to
 * sysui state updates from any of those [SysUiState] instances.
 *
 *                      ┌────────────────────┐
 *                      │ SysUIStateOverride │
 *                      │ displayId=2        │
 *                      └┬───────────────────┘
 *                       │  ▲
 * ┌───────────────┐     │  │  ┌────────────────────┐
 * │ SysUIState    │     │  │  │ SysUIStateOverride │
 * │ displayId=0   │     │  │  │ displayId=1        │
 * └────────────┬──┘     │  │  └┬───────────────────┘
 *              │        │  │   │ ▲
 *              ▼        ▼  │   ▼ │
 *            ┌─────────────┴─────┴─┐
 *            │SysUiStateDispatcher │
 *            └────────┬────────────┘
 *                     │
 *                     ▼
 *             ┌──────────────────┐
 *             │ listeners for    │
 *             │ all displays     │
 *             └──────────────────┘
 */
@SysUISingleton
class SysUIStateDispatcher @Inject constructor() {

    private val listeners = CopyOnWriteArrayList<SysUiState.SysUiStateCallback>()

    /** Called from each [SysUiState] to propagate new state changes. */
    fun dispatchSysUIStateChange(sysUiFlags: Long, displayId: Int) {
        if (displayId != Display.DEFAULT_DISPLAY && !ShadeWindowGoesAround.isEnabled) return
        listeners.forEach { listener ->
            listener.onSystemUiStateChanged(sysUiFlags = sysUiFlags, displayId = displayId)
        }
    }

    /**
     * Registers a listener to listen for system UI state changes.
     *
     * Listeners will have [SysUiState.SysUiStateCallback.onSystemUiStateChanged] called whenever a
     * system UI state changes.
     */
    fun registerListener(listener: SysUiState.SysUiStateCallback) {
        listeners += listener
    }

    fun unregisterListener(listener: SysUiState.SysUiStateCallback) {
        listeners -= listener
    }
}
