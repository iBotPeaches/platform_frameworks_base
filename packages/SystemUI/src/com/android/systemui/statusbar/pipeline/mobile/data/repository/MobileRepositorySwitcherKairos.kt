/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.mobile.data.repository

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.android.settingslib.SignalIcon
import com.android.settingslib.mobile.MobileMappings
import com.android.systemui.Flags
import com.android.systemui.KairosActivatable
import com.android.systemui.KairosBuilder
import com.android.systemui.activated
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.demomode.DemoMode
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.State
import com.android.systemui.kairos.flatMap
import com.android.systemui.kairos.map
import com.android.systemui.kairos.switchEvents
import com.android.systemui.kairos.switchIncremental
import com.android.systemui.kairosBuilder
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.DemoMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionsRepositoryKairosImpl
import dagger.Binds
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.channels.awaitClose

/**
 * A provider for the [MobileConnectionsRepository] interface that can choose between the Demo and
 * Prod concrete implementations at runtime. It works by defining a base flow, [activeRepo], which
 * switches based on the latest information from [DemoModeController], and switches every flow in
 * the interface to point to the currently-active provider. This allows us to put the demo mode
 * interface in its own repository, completely separate from the real version, while still using all
 * of the prod implementations for the rest of the pipeline (interactors and onward). Looks
 * something like this:
 * ```
 * RealRepository
 *       │
 *       ├──►RepositorySwitcher──►RealInteractor──►RealViewModel
 *       │
 * DemoRepository
 * ```
 *
 * NOTE: because the UI layer for mobile icons relies on a nested-repository structure, it is likely
 * that we will have to drain the subscription list whenever demo mode changes. Otherwise if a real
 * subscription list [1] is replaced with a demo subscription list [1], the view models will not see
 * a change (due to `distinctUntilChanged`) and will not refresh their data providers to the demo
 * implementation.
 */
@ExperimentalKairosApi
@SysUISingleton
class MobileRepositorySwitcherKairos
@Inject
constructor(
    private val realRepository: MobileConnectionsRepositoryKairosImpl,
    private val demoRepositoryFactory: DemoMobileConnectionsRepositoryKairos.Factory,
    demoModeController: DemoModeController,
) : MobileConnectionsRepositoryKairos, KairosBuilder by kairosBuilder() {

    private val isDemoMode: State<Boolean> = buildState {
        conflatedCallbackFlow {
                val callback =
                    object : DemoMode {
                        override fun dispatchDemoCommand(command: String?, args: Bundle?) {
                            // Nothing, we just care about on/off
                        }

                        override fun onDemoModeStarted() {
                            trySend(true)
                        }

                        override fun onDemoModeFinished() {
                            trySend(false)
                        }
                    }

                demoModeController.addCallback(callback)
                awaitClose { demoModeController.removeCallback(callback) }
            }
            .toState(demoModeController.isInDemoMode)
    }

    // Convenient definition flow for the currently active repo (based on demo mode or not)
    @VisibleForTesting
    val activeRepo: State<MobileConnectionsRepositoryKairos> = buildState {
        isDemoMode.mapLatestBuild { demoMode ->
            if (demoMode) {
                activated { demoRepositoryFactory.create() }
            } else {
                realRepository
            }
        }
    }

    override val mobileConnectionsBySubId: Incremental<Int, MobileConnectionRepositoryKairos> =
        activeRepo.map { it.mobileConnectionsBySubId }.switchIncremental()

    override val subscriptions: State<Collection<SubscriptionModel>> =
        activeRepo.flatMap { it.subscriptions }

    override val activeMobileDataSubscriptionId: State<Int?> =
        activeRepo.flatMap { it.activeMobileDataSubscriptionId }

    override val activeMobileDataRepository: State<MobileConnectionRepositoryKairos?> =
        activeRepo.flatMap { it.activeMobileDataRepository }

    override val activeSubChangedInGroupEvent: Events<Unit> =
        activeRepo.map { it.activeSubChangedInGroupEvent }.switchEvents()

    override val defaultDataSubRatConfig: State<MobileMappings.Config> =
        activeRepo.flatMap { it.defaultDataSubRatConfig }

    override val defaultMobileIconMapping: State<Map<String, SignalIcon.MobileIconGroup>> =
        activeRepo.flatMap { it.defaultMobileIconMapping }

    override val defaultMobileIconGroup: State<SignalIcon.MobileIconGroup> =
        activeRepo.flatMap { it.defaultMobileIconGroup }

    override val isDeviceEmergencyCallCapable: State<Boolean> =
        activeRepo.flatMap { it.isDeviceEmergencyCallCapable }

    override val isAnySimSecure: State<Boolean> = activeRepo.flatMap { it.isAnySimSecure }

    override val defaultDataSubId: State<Int?> = activeRepo.flatMap { it.defaultDataSubId }

    override val mobileIsDefault: State<Boolean> = activeRepo.flatMap { it.mobileIsDefault }

    override val hasCarrierMergedConnection: State<Boolean> =
        activeRepo.flatMap { it.hasCarrierMergedConnection }

    override val defaultConnectionIsValidated: State<Boolean> =
        activeRepo.flatMap { it.defaultConnectionIsValidated }

    override val isInEcmMode: State<Boolean> = activeRepo.flatMap { it.isInEcmMode }

    @dagger.Module
    interface Module {
        @Binds fun bindImpl(impl: MobileRepositorySwitcherKairos): MobileConnectionsRepositoryKairos

        companion object {
            @Provides
            @ElementsIntoSet
            fun kairosActivatable(
                impl: Provider<MobileRepositorySwitcherKairos>
            ): Set<@JvmSuppressWildcards KairosActivatable> =
                if (Flags.statusBarMobileIconKairos()) setOf(impl.get()) else emptySet()
        }
    }
}
