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

package com.android.systemui.statusbar.pipeline.mobile.ui

import com.android.systemui.Dumpable
import com.android.systemui.KairosActivatable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.awaitClose
import com.android.systemui.kairos.combine
import com.android.systemui.kairos.launchEffect
import com.android.systemui.shade.carrier.ShadeCarrierGroupController
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModelKairos
import java.io.PrintWriter
import javax.inject.Inject

/**
 * This class is intended to provide a context to collect on the
 * [MobileIconsInteractorKairos.filteredSubscriptions] data source and supply a state flow that can
 * control [StatusBarIconController] to keep the old UI in sync with the new data source.
 *
 * It also provides a mechanism to create a top-level view model for each IconManager to know about
 * the list of available mobile lines of service for which we want to show icons.
 */
@ExperimentalKairosApi
@SysUISingleton
class MobileUiAdapterKairos
@Inject
constructor(
    private val iconController: StatusBarIconController,
    val mobileIconsViewModel: MobileIconsViewModelKairos,
    private val logger: MobileViewLogger,
    dumpManager: DumpManager,
) : KairosActivatable, Dumpable {

    init {
        dumpManager.registerNormalDumpable(this)
    }

    private var isCollecting: Boolean = false
    private var lastValue: List<Int>? = null

    private var shadeCarrierGroupController: ShadeCarrierGroupController? = null

    override fun BuildScope.activate() {
        launchEffect {
            isCollecting = true
            awaitClose { isCollecting = false }
        }
        // Start notifying the icon controller of subscriptions
        combine(mobileIconsViewModel.subscriptionIds, mobileIconsViewModel.isStackable) { a, b ->
                Pair(a, b)
            }
            .observe { (subIds, isStackable) ->
                logger.logUiAdapterSubIdsSentToIconController(subIds, isStackable)
                lastValue = subIds
                if (isStackable) {
                    // Passing an empty list to remove pre-existing mobile icons.
                    // StackedMobileBindableIcon will show the stacked icon instead.
                    iconController.setNewMobileIconSubIds(emptyList())
                } else {
                    iconController.setNewMobileIconSubIds(subIds)
                }
                shadeCarrierGroupController?.updateModernMobileIcons(subIds)
            }
    }

    /** Set the [ShadeCarrierGroupController] to notify of subscription updates */
    fun setShadeCarrierGroupController(controller: ShadeCarrierGroupController) {
        shadeCarrierGroupController = controller
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("isCollecting=$isCollecting")
        pw.println("Last values sent to icon controller: $lastValue")
    }
}
