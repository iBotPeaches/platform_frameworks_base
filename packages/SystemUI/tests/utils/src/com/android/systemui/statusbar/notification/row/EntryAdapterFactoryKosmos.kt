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

import com.android.internal.logging.metricsLogger
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.notification.collection.EntryAdapterFactoryImpl
import com.android.systemui.statusbar.notification.collection.coordinator.mockVisualStabilityCoordinator
import com.android.systemui.statusbar.notification.collection.provider.mockHighPriorityProvider
import com.android.systemui.statusbar.notification.headsup.mockHeadsUpManager
import com.android.systemui.statusbar.notification.mockNotificationActivityStarter
import com.android.systemui.statusbar.notification.people.peopleNotificationIdentifier
import com.android.systemui.statusbar.notification.row.icon.notificationIconStyleProvider

val Kosmos.entryAdapterFactory by
    Kosmos.Fixture {
        EntryAdapterFactoryImpl(
            mockNotificationActivityStarter,
            metricsLogger,
            peopleNotificationIdentifier,
            notificationIconStyleProvider,
            mockVisualStabilityCoordinator,
            mockNotificationActionClickManager,
            mockHighPriorityProvider,
            mockHeadsUpManager,
        )
    }
