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
package com.android.systemui.unfold.data.repository

import android.os.PowerManager
import android.view.Display
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Repository to get screen timeout updates */
@SysUISingleton
class ScreenTimeoutPolicyRepository
@Inject
constructor(
    private val powerManager: PowerManager,
    @Background private val executor: Executor,
    @Background private val scope: CoroutineScope,
) {

    /** Stores true if there is an active screen timeout */
    val screenTimeoutActive: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    PowerManager.ScreenTimeoutPolicyListener { screenTimeoutPolicy ->
                        trySend(screenTimeoutPolicy == PowerManager.SCREEN_TIMEOUT_ACTIVE)
                    }
                powerManager.addScreenTimeoutPolicyListener(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    listener,
                )
                awaitClose {
                    powerManager.removeScreenTimeoutPolicyListener(
                        Display.DEFAULT_DISPLAY,
                        listener,
                    )
                }
            }
            .stateIn(scope, started = SharingStarted.Eagerly, initialValue = true)
}
