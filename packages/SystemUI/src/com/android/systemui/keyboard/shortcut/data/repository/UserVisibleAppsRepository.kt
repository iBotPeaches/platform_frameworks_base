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

package com.android.systemui.keyboard.shortcut.data.repository

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.UserHandle
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.UserTracker
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class UserVisibleAppsRepository
@Inject
constructor(
    private val userTracker: UserTracker,
    @Background private val bgExecutor: Executor,
    @Background private val bgHandler: Handler,
    private val launcherApps: LauncherApps,
) {

    val userVisibleApps: Flow<List<LauncherActivityInfo>>
        get() = conflatedCallbackFlow {
            val packageChangeCallback: LauncherApps.Callback =
                object : LauncherApps.Callback() {
                    override fun onPackageAdded(packageName: String, userHandle: UserHandle) {
                        trySendWithFailureLogging(
                            element = retrieveLauncherApps(),
                            loggingTag = TAG,
                            elementDescription = ON_PACKAGE_ADDED,
                        )
                    }

                    override fun onPackageChanged(packageName: String, userHandle: UserHandle) {
                        trySendWithFailureLogging(
                            element = retrieveLauncherApps(),
                            loggingTag = TAG,
                            elementDescription = ON_PACKAGE_CHANGED,
                        )
                    }

                    override fun onPackageRemoved(packageName: String, userHandle: UserHandle) {
                        trySendWithFailureLogging(
                            element = retrieveLauncherApps(),
                            loggingTag = TAG,
                            elementDescription = ON_PACKAGE_REMOVED,
                        )
                    }

                    override fun onPackagesAvailable(
                        packages: Array<out String>,
                        userHandle: UserHandle,
                        replacing: Boolean,
                    ) {
                        trySendWithFailureLogging(
                            element = retrieveLauncherApps(),
                            loggingTag = TAG,
                            elementDescription = ON_PACKAGES_AVAILABLE,
                        )
                    }

                    override fun onPackagesUnavailable(
                        packages: Array<out String>,
                        userHandle: UserHandle,
                        replacing: Boolean,
                    ) {
                        trySendWithFailureLogging(
                            element = retrieveLauncherApps(),
                            loggingTag = TAG,
                            elementDescription = ON_PACKAGES_UNAVAILABLE,
                        )
                    }
                }

            val userChangeCallback =
                object : UserTracker.Callback {
                    override fun onUserChanged(newUser: Int, userContext: Context) {
                        trySendWithFailureLogging(
                            element = retrieveLauncherApps(),
                            loggingTag = TAG,
                            elementDescription = ON_USER_CHANGED,
                        )
                    }
                }

            userTracker.addCallback(userChangeCallback, bgExecutor)
            launcherApps.registerCallback(packageChangeCallback, bgHandler)

            trySendWithFailureLogging(
                element = retrieveLauncherApps(),
                loggingTag = TAG,
                elementDescription = INITIAL_VALUE,
            )

            awaitClose {
                userTracker.removeCallback(userChangeCallback)
                launcherApps.unregisterCallback(packageChangeCallback)
            }
        }

    private fun retrieveLauncherApps(): List<LauncherActivityInfo> {
        return launcherApps.getActivityList(/* packageName= */ null, userTracker.userHandle)
    }

    private companion object {
        const val TAG = "UserVisibleAppsRepository"
        const val ON_PACKAGE_ADDED = "onPackageAdded"
        const val ON_PACKAGE_CHANGED = "onPackageChanged"
        const val ON_PACKAGE_REMOVED = "onPackageRemoved"
        const val ON_PACKAGES_AVAILABLE = "onPackagesAvailable"
        const val ON_PACKAGES_UNAVAILABLE = "onPackagesUnavailable"
        const val ON_USER_CHANGED = "onUserChanged"
        const val INITIAL_VALUE = "InitialValue"
    }
}
