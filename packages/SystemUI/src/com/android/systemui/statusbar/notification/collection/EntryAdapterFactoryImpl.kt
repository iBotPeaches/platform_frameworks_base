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

package com.android.systemui.statusbar.notification.collection

import com.android.internal.logging.MetricsLogger
import com.android.systemui.statusbar.notification.NotificationActivityStarter
import com.android.systemui.statusbar.notification.collection.coordinator.VisualStabilityCoordinator
import com.android.systemui.statusbar.notification.collection.provider.HighPriorityProvider
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier
import com.android.systemui.statusbar.notification.row.NotificationActionClickManager
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider
import javax.inject.Inject

/** Creates an appropriate EntryAdapter for the entry type given */
class EntryAdapterFactoryImpl
@Inject
constructor(
    private val notificationActivityStarter: NotificationActivityStarter,
    private val metricsLogger: MetricsLogger,
    private val peopleNotificationIdentifier: PeopleNotificationIdentifier,
    private val iconStyleProvider: NotificationIconStyleProvider,
    private val visualStabilityCoordinator: VisualStabilityCoordinator,
    private val notificationActionClickManager: NotificationActionClickManager,
    private val highPriorityProvider: HighPriorityProvider,
    private val headsUpManager: HeadsUpManager,
) : EntryAdapterFactory {
    override fun create(entry: PipelineEntry): EntryAdapter {
        return if (entry is NotificationEntry) {
            NotificationEntryAdapter(
                notificationActivityStarter,
                metricsLogger,
                peopleNotificationIdentifier,
                iconStyleProvider,
                visualStabilityCoordinator,
                notificationActionClickManager,
                highPriorityProvider,
                headsUpManager,
                entry,
            )
        } else {
            BundleEntryAdapter(highPriorityProvider, (entry as BundleEntry))
        }
    }
}
