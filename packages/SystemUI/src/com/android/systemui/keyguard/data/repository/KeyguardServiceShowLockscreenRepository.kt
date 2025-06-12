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

package com.android.systemui.keyguard.data.repository

import android.os.IRemoteCallback
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

/**
 * Holds an IRemoteCallback along with the current user ID at the time the callback was provided.
 */
data class ShowLockscreenCallback(val userId: Int, val remoteCallback: IRemoteCallback)

/** Maintains state related to KeyguardService requests to show the lockscreen. */
@SysUISingleton
class KeyguardServiceShowLockscreenRepository @Inject constructor() {
    val showLockscreenCallbacks = ArrayList<ShowLockscreenCallback>()

    /**
     * Adds a callback that we'll notify when we show the lockscreen (or affirmatively decide not to
     * show it).
     */
    fun addShowLockscreenCallback(forUser: Int, callback: IRemoteCallback) {
        synchronized(showLockscreenCallbacks) {
            showLockscreenCallbacks.add(ShowLockscreenCallback(forUser, callback))
        }
    }

    companion object {
        private const val TAG = "ShowLockscreenRepository"
    }
}
