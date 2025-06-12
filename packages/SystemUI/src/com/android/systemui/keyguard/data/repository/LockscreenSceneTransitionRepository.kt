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

package com.android.systemui.keyguard.data.repository

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@SysUISingleton
class LockscreenSceneTransitionRepository @Inject constructor() {

    /**
     * This [KeyguardState] will indicate which sub-state within KTF should be navigated to next.
     *
     * This can be either starting a transition to the `Lockscreen` scene or cancelling a transition
     * from the `Lockscreen` scene and returning back to it.
     *
     * A `null` value means that no explicit target state was set and therefore the [DEFAULT_STATE]
     * should be used.
     *
     * Once consumed, this state should be reset to `null`.
     */
    val nextLockscreenTargetState: MutableStateFlow<KeyguardState?> = MutableStateFlow(null)

    companion object {
        val DEFAULT_STATE = KeyguardState.LOCKSCREEN
    }
}
