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

package com.android.systemui.communal.domain.interactor

import com.android.systemui.common.domain.interactor.BatteryInteractor
import com.android.systemui.communal.dagger.CommunalModule.Companion.SWIPE_TO_HUB
import com.android.systemui.communal.data.model.FEATURE_AUTO_OPEN
import com.android.systemui.communal.data.model.FEATURE_MANUAL_OPEN
import com.android.systemui.communal.data.model.SuppressionReason
import com.android.systemui.communal.posturing.domain.interactor.PosturingInteractor
import com.android.systemui.communal.shared.model.WhenToStartHub
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dock.DockManager
import com.android.systemui.dock.retrieveIsDocked
import com.android.systemui.util.kotlin.BooleanFlowOperators.allOf
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@SysUISingleton
class CommunalAutoOpenInteractor
@Inject
constructor(
    communalSettingsInteractor: CommunalSettingsInteractor,
    @Background private val backgroundContext: CoroutineContext,
    private val batteryInteractor: BatteryInteractor,
    private val posturingInteractor: PosturingInteractor,
    private val dockManager: DockManager,
    @Named(SWIPE_TO_HUB) private val allowSwipeAlways: Boolean,
) {
    val shouldAutoOpen: Flow<Boolean> =
        communalSettingsInteractor.whenToStartHub
            .flatMapLatestConflated { whenToStartHub ->
                when (whenToStartHub) {
                    WhenToStartHub.WHILE_CHARGING -> batteryInteractor.isDevicePluggedIn
                    WhenToStartHub.WHILE_DOCKED -> {
                        allOf(batteryInteractor.isDevicePluggedIn, dockManager.retrieveIsDocked())
                    }
                    WhenToStartHub.WHILE_CHARGING_AND_POSTURED -> {
                        // Only listen to posturing if applicable to avoid running the posturing
                        // CHRE nanoapp when not needed.
                        batteryInteractor.isDevicePluggedIn.flatMapLatestConflated { isCharging ->
                            if (isCharging) {
                                posturingInteractor.postured
                            } else {
                                flowOf(false)
                            }
                        }
                    }
                    WhenToStartHub.NEVER -> flowOf(false)
                }
            }
            .flowOn(backgroundContext)

    val suppressionReason: Flow<SuppressionReason?> =
        shouldAutoOpen.map { conditionMet ->
            if (conditionMet) {
                null
            } else {
                var suppressedFeatures = FEATURE_AUTO_OPEN
                if (!allowSwipeAlways) {
                    suppressedFeatures = suppressedFeatures or FEATURE_MANUAL_OPEN
                }
                SuppressionReason.ReasonWhenToAutoShow(suppressedFeatures)
            }
        }
}
