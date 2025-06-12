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

import android.graphics.Color
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.State
import com.android.systemui.kairos.combine
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.VerboseMobileViewLogger

/**
 * A view model for an individual mobile icon that embeds the notion of a [StatusBarLocation]. This
 * allows the mobile icon to change some view parameters at different locations
 *
 * @param commonImpl for convenience, this class wraps a base interface that can provides all of the
 *   common implementations between locations. See [MobileIconViewModel]
 * @property location the [StatusBarLocation] of this VM.
 * @property verboseLogger an optional logger to log extremely verbose view updates.
 */
@ExperimentalKairosApi
abstract class LocationBasedMobileViewModelKairos(
    val commonImpl: MobileIconViewModelKairosCommon,
    val location: StatusBarLocation,
    val verboseLogger: VerboseMobileViewLogger?,
) : MobileIconViewModelKairosCommon by commonImpl {
    val defaultColor: Int = Color.WHITE

    companion object {
        fun viewModelForLocation(
            commonImpl: MobileIconViewModelKairosCommon,
            interactor: MobileIconInteractorKairos,
            verboseMobileViewLogger: VerboseMobileViewLogger,
            location: StatusBarLocation,
        ): LocationBasedMobileViewModelKairos =
            when (location) {
                StatusBarLocation.HOME ->
                    HomeMobileIconViewModelKairos(commonImpl, verboseMobileViewLogger)
                StatusBarLocation.KEYGUARD -> KeyguardMobileIconViewModelKairos(commonImpl)
                StatusBarLocation.QS -> QsMobileIconViewModelKairos(commonImpl)
                StatusBarLocation.SHADE_CARRIER_GROUP ->
                    ShadeCarrierGroupMobileIconViewModelKairos(commonImpl, interactor)
            }
    }
}

@ExperimentalKairosApi
class HomeMobileIconViewModelKairos(
    commonImpl: MobileIconViewModelKairosCommon,
    verboseMobileViewLogger: VerboseMobileViewLogger,
) :
    MobileIconViewModelKairosCommon,
    LocationBasedMobileViewModelKairos(
        commonImpl,
        location = StatusBarLocation.HOME,
        verboseMobileViewLogger,
    )

@ExperimentalKairosApi
class QsMobileIconViewModelKairos(commonImpl: MobileIconViewModelKairosCommon) :
    MobileIconViewModelKairosCommon,
    LocationBasedMobileViewModelKairos(
        commonImpl,
        location = StatusBarLocation.QS,
        // Only do verbose logging for the Home location.
        verboseLogger = null,
    )

@ExperimentalKairosApi
class ShadeCarrierGroupMobileIconViewModelKairos(
    commonImpl: MobileIconViewModelKairosCommon,
    private val interactor: MobileIconInteractorKairos,
) :
    MobileIconViewModelKairosCommon,
    LocationBasedMobileViewModelKairos(
        commonImpl,
        location = StatusBarLocation.SHADE_CARRIER_GROUP,
        // Only do verbose logging for the Home location.
        verboseLogger = null,
    ) {

    private val isSingleCarrier: State<Boolean>
        get() = interactor.isSingleCarrier

    val carrierName: State<String>
        get() = interactor.carrierName

    override val isVisible: State<Boolean> =
        combine(super.isVisible, isSingleCarrier) { isVisible, isSingleCarrier ->
            !isSingleCarrier && isVisible
        }
}

@ExperimentalKairosApi
class KeyguardMobileIconViewModelKairos(commonImpl: MobileIconViewModelKairosCommon) :
    MobileIconViewModelKairosCommon,
    LocationBasedMobileViewModelKairos(
        commonImpl,
        location = StatusBarLocation.KEYGUARD,
        // Only do verbose logging for the Home location.
        verboseLogger = null,
    )
