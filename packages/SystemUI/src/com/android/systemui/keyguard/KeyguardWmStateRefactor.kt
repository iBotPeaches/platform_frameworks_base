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

package com.android.systemui.keyguard

import com.android.systemui.Flags
import com.android.systemui.flags.FlagToken
import com.android.systemui.flags.RefactorFlagUtils

/**
 * Helper for reading or using the keyguard_wm_state_refactor flag state.
 *
 * keyguard_wm_state_refactor works both with and without flexiglass (scene_container), but
 * flexiglass requires keyguard_wm_state_refactor. For this reason, this class will return isEnabled
 * if either keyguard_wm_state_refactor OR scene_container are enabled. This enables us to roll out
 * keyguard_wm_state_refactor independently of scene_container, while also ensuring that
 * scene_container rolling out ahead of keyguard_wm_state_refactor causes code gated by
 * KeyguardWmStateRefactor to be enabled as well.
 */
@Suppress("NOTHING_TO_INLINE")
object KeyguardWmStateRefactor {
    /** The aconfig flag name */
    const val FLAG_NAME = Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR

    /** A token used for dependency declaration */
    val token: FlagToken
        get() = FlagToken(FLAG_NAME, isEnabled)

    @JvmStatic
    inline val isEnabled
        get() = Flags.keyguardWmStateRefactor() || Flags.sceneContainer()

    /**
     * Called to ensure code is only run when the flag is enabled. This protects users from the
     * unintended behaviors caused by accidentally running new logic, while also crashing on an eng
     * build to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun isUnexpectedlyInLegacyMode() =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, FLAG_NAME)

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun assertInLegacyMode() = RefactorFlagUtils.assertInLegacyMode(isEnabled, FLAG_NAME)
}
