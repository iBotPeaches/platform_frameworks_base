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

package com.android.systemui.keyboard.shortcut.fakes

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock

class FakeLauncherApps {

    private val activityListPerUser: MutableMap<Int, MutableList<LauncherActivityInfo>> =
        mutableMapOf()
    private val callbacks: MutableList<LauncherApps.Callback> = mutableListOf()

    val launcherApps: LauncherApps = mock {
        on { getActivityList(anyOrNull(), any()) }
            .then {
                val userHandle = it.getArgument<UserHandle>(1)

                activityListPerUser.getOrDefault(userHandle.identifier, emptyList())
            }
        on { registerCallback(any(), any()) }
            .then {
                val callback = it.getArgument<LauncherApps.Callback>(0)

                callbacks.add(callback)
            }
        on { unregisterCallback(any()) }
            .then {
                val callback = it.getArgument<LauncherApps.Callback>(0)

                callbacks.remove(callback)
            }
    }

    fun installPackageForUser(packageName: String, className: String, userHandle: UserHandle) {
        val launcherActivityInfo: LauncherActivityInfo = mock {
            on { componentName }
                .thenReturn(ComponentName(/* pkg= */ packageName, /* cls= */ className))
        }

        if (!activityListPerUser.containsKey(userHandle.identifier)) {
            activityListPerUser[userHandle.identifier] = mutableListOf()
        }

        activityListPerUser[userHandle.identifier]!!.add(launcherActivityInfo)

        callbacks.forEach { it.onPackageAdded(/* pkg= */ packageName, userHandle) }
    }

    fun uninstallPackageForUser(packageName: String, className: String, userHandle: UserHandle) {
        activityListPerUser[userHandle.identifier]?.removeIf {
            it.componentName.packageName == packageName && it.componentName.className == className
        }

        callbacks.forEach { it.onPackageRemoved(/* pkg= */ packageName, userHandle) }
    }
}
