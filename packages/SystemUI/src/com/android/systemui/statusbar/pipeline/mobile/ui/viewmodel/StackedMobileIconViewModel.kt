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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel.DualSim
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

interface StackedMobileIconViewModel {
    val dualSim: DualSim?
    val networkTypeIcon: Icon.Resource?
    val isIconVisible: Boolean

    data class DualSim(
        val primary: SignalIconModel.Cellular,
        val secondary: SignalIconModel.Cellular,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class StackedMobileIconViewModelImpl
@AssistedInject
constructor(mobileIconsViewModel: MobileIconsViewModel) :
    ExclusiveActivatable(), StackedMobileIconViewModel {
    private val hydrator = Hydrator("StackedMobileIconViewModel")

    private val isStackable: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isStackable",
            source = mobileIconsViewModel.isStackable,
            initialValue = false,
        )

    private val iconViewModelFlow: Flow<List<MobileIconViewModelCommon>> =
        combine(
            mobileIconsViewModel.mobileSubViewModels,
            mobileIconsViewModel.activeMobileDataSubscriptionId,
        ) { viewModels, activeSubId ->
            // Sort to get the active subscription first, if it's set
            viewModels.sortedByDescending { it.subscriptionId == activeSubId }
        }

    override val dualSim: DualSim? by
        hydrator.hydratedStateOf(
            traceName = "dualSim",
            source =
                iconViewModelFlow.flatMapLatest { viewModels ->
                    combine(viewModels.map { it.icon }) { icons ->
                        icons
                            .toList()
                            .filterIsInstance<SignalIconModel.Cellular>()
                            .takeIf { it.size == 2 }
                            ?.let { DualSim(it[0], it[1]) }
                    }
                },
            initialValue = null,
        )

    override val networkTypeIcon: Icon.Resource? by
        hydrator.hydratedStateOf(
            traceName = "networkTypeIcon",
            source =
                iconViewModelFlow.flatMapLatest { viewModels ->
                    viewModels.firstOrNull()?.networkTypeIcon ?: flowOf(null)
                },
            initialValue = null,
        )

    override val isIconVisible: Boolean by derivedStateOf { isStackable && dualSim != null }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(): StackedMobileIconViewModelImpl
    }
}
