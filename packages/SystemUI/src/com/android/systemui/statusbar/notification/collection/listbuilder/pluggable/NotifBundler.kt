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
package com.android.systemui.statusbar.notification.collection.listbuilder.pluggable

import com.android.systemui.statusbar.notification.collection.NotificationEntry

/** Pluggable for bundling notifications according to classification. */
abstract class NotifBundler protected constructor(name: String?) : Pluggable<NotifBundler?>(name) {
    abstract val bundleIds: List<String>

    abstract fun getBundleIdOrNull(entry: NotificationEntry?): String?
}

/** The default, no-op instance of NotifBundler which does not bundle anything. */
object DefaultNotifBundler : NotifBundler("DefaultNotifBundler") {
    override val bundleIds: List<String>
        get() = listOf()

    override fun getBundleIdOrNull(entry: NotificationEntry?): String? {
        return null
    }
}
