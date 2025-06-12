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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import androidx.compose.runtime.State as ComposeState
import androidx.compose.runtime.getValue
import com.android.systemui.Flags
import com.android.systemui.KairosActivatable
import com.android.systemui.KairosBuilder
import com.android.systemui.activated
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.flags.FeatureFlagsClassic
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.State as KairosState
import com.android.systemui.kairos.State
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.combine
import com.android.systemui.kairos.flatten
import com.android.systemui.kairos.map
import com.android.systemui.kairos.mapValues
import com.android.systemui.kairos.stateOf
import com.android.systemui.kairosBuilder
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.AirplaneModeInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.MobileViewLogger
import com.android.systemui.statusbar.pipeline.mobile.ui.VerboseMobileViewLogger
import com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView
import com.android.systemui.statusbar.pipeline.shared.ConnectivityConstants
import com.android.systemui.util.composable.kairos.toComposeState
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import javax.inject.Inject
import javax.inject.Provider

/**
 * View model for describing the system's current mobile cellular connections. The result is a list
 * of [MobileIconViewModelKairos]s which describe the individual icons and can be bound to
 * [ModernStatusBarMobileView].
 */
@ExperimentalKairosApi
@SysUISingleton
class MobileIconsViewModelKairos
@Inject
constructor(
    val logger: MobileViewLogger,
    private val verboseLogger: VerboseMobileViewLogger,
    private val interactor: MobileIconsInteractorKairos,
    private val airplaneModeInteractor: AirplaneModeInteractor,
    private val constants: ConnectivityConstants,
    private val flags: FeatureFlagsClassic,
) : KairosBuilder by kairosBuilder() {

    val activeSubscriptionId: State<Int?>
        get() = interactor.activeDataIconInteractor.map { it?.subscriptionId }

    val subscriptionIds: KairosState<List<Int>> =
        interactor.filteredSubscriptions.map { subscriptions ->
            subscriptions.map { it.subscriptionId }
        }

    val icons: Incremental<Int, MobileIconViewModelKairos> = buildIncremental {
        interactor.icons
            .mapValues { (subId, icon) -> buildSpec { commonViewModel(subId, icon) } }
            .applyLatestSpecForKey()
    }

    /** Whether the mobile sub that's displayed first visually is showing its network type icon. */
    val firstMobileSubShowingNetworkTypeIcon: KairosState<Boolean> = buildState {
        combine(subscriptionIds.map { it.lastOrNull() }, icons) { lastId, icons ->
                icons[lastId]?.networkTypeIcon?.map { it != null } ?: stateOf(false)
            }
            .flatten()
    }

    val isStackable: KairosState<Boolean>
        get() = interactor.isStackable

    fun viewModelForSub(
        subId: Int,
        location: StatusBarLocation,
    ): BuildSpec<LocationBasedMobileViewModelKairos> = buildSpec {
        val iconInteractor =
            interactor.icons.sample().getOrElse(subId) { error("Unknown subscription id: $subId") }
        val commonViewModel =
            icons.sample().getOrElse(subId) { error("Unknown subscription id: $subId") }
        LocationBasedMobileViewModelKairos.viewModelForLocation(
            commonViewModel,
            iconInteractor,
            verboseLogger,
            location,
        )
    }

    fun shadeCarrierGroupIcon(subId: Int): BuildSpec<ShadeCarrierGroupMobileIconViewModelKairos> =
        buildSpec {
            val iconInteractor =
                interactor.icons.sample().getOrElse(subId) {
                    error("Unknown subscription id: $subId")
                }
            val commonViewModel =
                icons.sample().getOrElse(subId) { error("Unknown subscription id: $subId") }
            ShadeCarrierGroupMobileIconViewModelKairos(commonViewModel, iconInteractor)
        }

    private fun BuildScope.commonViewModel(subId: Int, iconInteractor: MobileIconInteractorKairos) =
        activated {
            MobileIconViewModelKairos(
                subscriptionId = subId,
                iconInteractor = iconInteractor,
                airplaneModeInteractor = airplaneModeInteractor,
                constants = constants,
                flags = flags,
            )
        }

    @dagger.Module
    object Module {
        @Provides
        @ElementsIntoSet
        fun bindKairosActivatable(
            impl: Provider<MobileIconsViewModelKairos>
        ): Set<@JvmSuppressWildcards KairosActivatable> =
            if (Flags.statusBarMobileIconKairos()) setOf(impl.get()) else emptySet()
    }
}

@ExperimentalKairosApi
class MobileIconsViewModelKairosComposeWrapper(
    icons: ComposeState<Map<Int, MobileIconViewModelKairos>>,
    val logger: MobileViewLogger,
) {
    val icons: Map<Int, MobileIconViewModelKairos> by icons
}

@ExperimentalKairosApi
fun MobileIconsViewModelKairos.composeWrapper(): BuildSpec<MobileIconsViewModelKairosComposeWrapper> = buildSpec {
    MobileIconsViewModelKairosComposeWrapper(
        icons = toComposeState(icons),
        logger = logger,
    )
}
