/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection.coordinator

import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.dump.dumpManager
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.wakefulnessLifecycle
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.shade.domain.interactor.shadeAnimationInteractor
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.statusbar.notification.collection.provider.visualStabilityProvider
import com.android.systemui.statusbar.notification.domain.interactor.seenNotificationsInteractor
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.statusbar.notification.visibilityLocationProvider
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.util.kotlin.JavaAdapter
import org.mockito.kotlin.mock

var Kosmos.visualStabilityCoordinator: VisualStabilityCoordinator by
    Kosmos.Fixture {
        VisualStabilityCoordinator(
            fakeExecutor,
            fakeExecutor,
            dumpManager,
            headsUpNotificationRepository,
            shadeAnimationInteractor,
            JavaAdapter(testScope.backgroundScope),
            seenNotificationsInteractor,
            statusBarStateController,
            visibilityLocationProvider,
            visualStabilityProvider,
            wakefulnessLifecycle,
            communalSceneInteractor,
            shadeInteractor,
            keyguardTransitionInteractor,
            keyguardStateController,
            visualStabilityCoordinatorLogger,
        )
    }

var Kosmos.mockVisualStabilityCoordinator: VisualStabilityCoordinator by Kosmos.Fixture { mock() }
