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

package com.android.systemui.statusbar.notification.row

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.util.ListenerSet
import java.util.function.Consumer
import javax.inject.Inject

/**
 * Pipeline components can register consumers here to be informed when a notification action is
 * clicked
 */
@SysUISingleton
class NotificationActionClickManager @Inject constructor() {
    private val actionClickListeners = ListenerSet<Consumer<NotificationEntry>>()

    fun addActionClickListener(listener: Consumer<NotificationEntry>) {
        actionClickListeners.addIfAbsent(listener)
    }

    fun removeActionClickListener(listener: Consumer<NotificationEntry>) {
        actionClickListeners.remove(listener)
    }

    fun onNotificationActionClicked(entry: NotificationEntry) {
        for (listener in actionClickListeners) {
            listener.accept(entry)
        }
    }
}
