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

import com.android.systemui.shared.system.QuickStepContract.getSystemUiStateString

/**
 * Represents a set of state changes. A bit can either be set to `true` or `false`.
 *
 * This is used in [SysUIStateDisplaysInteractor] to selectively change bits.
 */
class StateChange {
    private var flagsToSet: Long = 0
    private var flagsToClear: Long = 0

    /**
     * Sets the [state] of the given [bit].
     *
     * @return `this` for chaining purposes
     */
    fun setFlag(bit: Long, state: Boolean): StateChange {
        if (state) {
            flagsToSet = flagsToSet or bit
            flagsToClear = flagsToClear and bit.inv()
        } else {
            flagsToClear = flagsToClear or bit
            flagsToSet = flagsToSet and bit.inv()
        }
        return this
    }

    /**
     * Applies all changed flags to [sysUiState].
     *
     * Note this doesn't call [SysUiState.commitUpdate].
     */
    fun applyTo(sysUiState: SysUiState) {
        iterateBits(flagsToSet or flagsToClear) { bit ->
            val isBitSetInNewState = flagsToSet and bit != 0L
            sysUiState.setFlag(bit, isBitSetInNewState)
        }
    }

    fun applyTo(sysUiState: Long): Long {
        var newState = sysUiState
        newState = newState or flagsToSet
        newState = newState and flagsToClear.inv()
        return newState
    }

    private inline fun iterateBits(flags: Long, action: (bit: Long) -> Unit) {
        var remaining = flags
        while (remaining != 0L) {
            val lowestBit = remaining and -remaining
            action(lowestBit)

            remaining -= lowestBit
        }
    }

    /**
     * Clears all the flags changed in a [sysUiState].
     *
     * Note this doesn't call [SysUiState.commitUpdate].
     */
    fun clearFrom(sysUiState: SysUiState) {
        iterateBits(flagsToSet or flagsToClear) { bit -> sysUiState.setFlag(bit, false) }
    }

    /** Resets all the pending changes. */
    fun clear() {
        flagsToSet = 0
        flagsToClear = 0
    }

    override fun toString(): String {
        return """StateChange(flagsToSet=${getSystemUiStateString(flagsToSet)}, flagsToClear=${
            getSystemUiStateString(
                flagsToClear
            )
        })"""
    }
}
