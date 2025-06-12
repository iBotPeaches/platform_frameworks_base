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
package com.android.systemui.lowlightclock

import android.content.ComponentName
import android.content.pm.PackageManager
import com.android.dream.lowlight.LowLightDreamManager
import com.android.systemui.biometrics.domain.interactor.DisplayStateInteractor
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.SystemUser
import com.android.systemui.dreams.dagger.DreamModule
import com.android.systemui.lowlightclock.dagger.LowLightModule
import com.android.systemui.shared.condition.Condition
import com.android.systemui.shared.condition.Monitor
import com.android.systemui.util.condition.ConditionalCoreStartable
import com.android.systemui.util.kotlin.BooleanFlowOperators.not
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Tracks environment (low-light or not) in order to correctly show or hide a low-light clock while
 * dreaming.
 */
class LowLightMonitor
@Inject
constructor(
    private val lowLightDreamManager: Lazy<LowLightDreamManager>,
    @param:SystemUser private val conditionsMonitor: Monitor,
    @param:Named(LowLightModule.LOW_LIGHT_PRECONDITIONS)
    private val lowLightConditions: Lazy<Set<Condition>>,
    displayStateInteractor: DisplayStateInteractor,
    private val logger: LowLightLogger,
    @param:Named(DreamModule.LOW_LIGHT_DREAM_SERVICE)
    private val lowLightDreamService: ComponentName?,
    private val packageManager: PackageManager,
    @Background private val scope: CoroutineScope,
) : ConditionalCoreStartable(conditionsMonitor) {
    private val isScreenOn = not(displayStateInteractor.isDefaultDisplayOff).distinctUntilChanged()

    private val isLowLight = conflatedCallbackFlow {
        val token =
            conditionsMonitor.addSubscription(
                Monitor.Subscription.Builder { trySend(it) }
                    .addConditions(lowLightConditions.get())
                    .build()
            )

        awaitClose { conditionsMonitor.removeSubscription(token) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStart() {
        scope.launch {
            if (lowLightDreamService != null) {
                // Note that the dream service is disabled by default. This prevents the dream from
                // appearing in settings on devices that don't have it explicitly excluded (done in
                // the settings overlay). Therefore, the component is enabled if it is to be used
                // here.
                packageManager.setComponentEnabledSetting(
                    lowLightDreamService,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            } else {
                // If there is no low light dream service, do not observe conditions.
                return@launch
            }

            isScreenOn
                .flatMapLatest {
                    if (it) {
                        isLowLight
                    } else {
                        flowOf(false)
                    }
                }
                .distinctUntilChanged()
                .collect {
                    logger.d(TAG, "Low light enabled: $it")
                    lowLightDreamManager
                        .get()
                        .setAmbientLightMode(
                            if (it) LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT
                            else LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR
                        )
                }
        }
    }

    companion object {
        private const val TAG = "LowLightMonitor"
    }
}
