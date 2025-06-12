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
package com.android.systemui.dock

import com.android.systemui.dock.DockManager.AlignmentStateListener

/** A rudimentary fake for DockManager. */
class DockManagerFake : DockManager {
    private val callbacks = mutableSetOf<DockManager.DockEventListener>()
    private val alignmentListeners = mutableSetOf<AlignmentStateListener>()
    private var docked = false

    override fun addListener(callback: DockManager.DockEventListener) {
        callbacks.add(callback)
    }

    override fun removeListener(callback: DockManager.DockEventListener) {
        callbacks.remove(callback)
    }

    override fun addAlignmentStateListener(listener: AlignmentStateListener) {
        alignmentListeners.add(listener)
    }

    override fun removeAlignmentStateListener(listener: AlignmentStateListener) {
        alignmentListeners.remove(listener)
    }

    override fun isDocked(): Boolean {
        return docked
    }

    /** Sets the docked state */
    fun setIsDocked(docked: Boolean) {
        this.docked = docked
    }

    override fun isHidden(): Boolean {
        return false
    }

    /** Notifies callbacks of dock state change */
    fun setDockEvent(event: Int) {
        for (callback in callbacks) {
            callback.onEvent(event)
        }
    }
}
