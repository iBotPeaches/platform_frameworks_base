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

@file:OptIn(ExperimentalKairosApi::class)

package com.android.systemui.shade.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.provider.Settings
import android.view.ViewGroup
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.OverlayKey
import com.android.systemui.battery.BatteryMeterViewController
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.TransitionKeys.SlightlyFasterShadeCollapse
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.domain.interactor.PrivacyChipInteractor
import com.android.systemui.shade.domain.interactor.ShadeHeaderClockInteractor
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModelKairos
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/** Models UI state for the shade header. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShadeHeaderViewModel
@AssistedInject
constructor(
    @ShadeDisplayAware context: Context,
    private val activityStarter: ActivityStarter,
    private val sceneInteractor: SceneInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    mobileIconsInteractor: MobileIconsInteractor,
    val mobileIconsViewModel: MobileIconsViewModel,
    private val privacyChipInteractor: PrivacyChipInteractor,
    private val clockInteractor: ShadeHeaderClockInteractor,
    private val tintedIconManagerFactory: TintedIconManager.Factory,
    private val batteryMeterViewControllerFactory: BatteryMeterViewController.Factory,
    val statusBarIconController: StatusBarIconController,
    val kairosNetwork: KairosNetwork,
    val mobileIconsViewModelKairos: dagger.Lazy<MobileIconsViewModelKairos>,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("ShadeHeaderViewModel.hydrator")

    val createTintedIconManager: (ViewGroup, StatusBarLocation) -> TintedIconManager =
        tintedIconManagerFactory::create

    val createBatteryMeterViewController:
        (ViewGroup, StatusBarLocation) -> BatteryMeterViewController =
        batteryMeterViewControllerFactory::create

    val showClock: Boolean by
        hydrator.hydratedStateOf(
            traceName = "showClock",
            initialValue =
                shouldShowClock(
                    isShadeLayoutWide = shadeModeInteractor.isShadeLayoutWide.value,
                    overlays = sceneInteractor.currentOverlays.value,
                ),
            source =
                combine(
                    shadeModeInteractor.isShadeLayoutWide,
                    sceneInteractor.currentOverlays,
                    ::shouldShowClock,
                ),
        )

    val notificationsChipHighlight: HeaderChipHighlight by
        hydrator.hydratedStateOf(
            traceName = "notificationsChipHighlight",
            initialValue = HeaderChipHighlight.None,
            source =
                sceneInteractor.currentOverlays.map { overlays ->
                    when {
                        Overlays.NotificationsShade in overlays -> HeaderChipHighlight.Strong
                        Overlays.QuickSettingsShade in overlays -> HeaderChipHighlight.Weak
                        else -> HeaderChipHighlight.None
                    }
                },
        )

    val quickSettingsChipHighlight: HeaderChipHighlight by
        hydrator.hydratedStateOf(
            traceName = "quickSettingsChipHighlight",
            initialValue = HeaderChipHighlight.None,
            source =
                sceneInteractor.currentOverlays.map { overlays ->
                    when {
                        Overlays.QuickSettingsShade in overlays -> HeaderChipHighlight.Strong
                        Overlays.NotificationsShade in overlays -> HeaderChipHighlight.Weak
                        else -> HeaderChipHighlight.None
                    }
                },
        )

    /** True if there is exactly one mobile connection. */
    val isSingleCarrier: StateFlow<Boolean> = mobileIconsInteractor.isSingleCarrier

    /** The list of subscription Ids for current mobile connections. */
    val mobileSubIds: List<Int> by
        hydrator.hydratedStateOf(
            traceName = "mobileSubIds",
            initialValue = emptyList(),
            source =
                mobileIconsInteractor.filteredSubscriptions.map { list ->
                    list.map { it.subscriptionId }
                },
        )

    /** The list of PrivacyItems to be displayed by the privacy chip. */
    val privacyItems: StateFlow<List<PrivacyItem>> = privacyChipInteractor.privacyItems

    /** Whether or not mic & camera indicators are enabled in the device privacy config. */
    val isMicCameraIndicationEnabled: StateFlow<Boolean> =
        privacyChipInteractor.isMicCameraIndicationEnabled

    /** Whether or not location indicators are enabled in the device privacy config. */
    val isLocationIndicationEnabled: StateFlow<Boolean> =
        privacyChipInteractor.isLocationIndicationEnabled

    /** Whether or not the privacy chip should be visible. */
    val isPrivacyChipVisible: StateFlow<Boolean> = privacyChipInteractor.isChipVisible

    /** Whether or not the privacy chip is enabled in the device privacy config. */
    val isPrivacyChipEnabled: StateFlow<Boolean> = privacyChipInteractor.isChipEnabled

    private val longerPattern = context.getString(R.string.abbrev_wday_month_day_no_year_alarm)
    private val shorterPattern = context.getString(R.string.abbrev_month_day_no_year)

    private val longerDateFormat: Flow<DateFormat> =
        clockInteractor.onTimezoneOrLocaleChanged.mapLatest { getFormatFromPattern(longerPattern) }
    private val shorterDateFormat: Flow<DateFormat> =
        clockInteractor.onTimezoneOrLocaleChanged.mapLatest { getFormatFromPattern(shorterPattern) }

    val longerDateText: String by
        hydrator.hydratedStateOf(
            traceName = "longerDateText",
            initialValue = "",
            source =
                combine(longerDateFormat, clockInteractor.currentTime) { format, time ->
                    format.format(time)
                },
        )

    val shorterDateText: String by
        hydrator.hydratedStateOf(
            traceName = "shorterDateText",
            initialValue = "",
            source =
                combine(shorterDateFormat, clockInteractor.currentTime) { format, time ->
                    format.format(time)
                },
        )

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            awaitCancellation()
        }
    }

    /** Notifies that the privacy chip was clicked. */
    fun onPrivacyChipClicked(privacyChip: OngoingPrivacyChip) {
        privacyChipInteractor.onPrivacyChipClicked(privacyChip)
    }

    /** Notifies that the clock was clicked. */
    fun onClockClicked() {
        clockInteractor.launchClockActivity()
    }

    /** Notifies that the system icons container was clicked. */
    fun onNotificationIconChipClicked() {
        if (!shadeModeInteractor.isDualShade) {
            return
        }
        val loggingReason = "ShadeHeaderViewModel.onNotificationIconChipClicked"
        val currentOverlays = sceneInteractor.currentOverlays.value
        if (Overlays.NotificationsShade in currentOverlays) {
            shadeInteractor.collapseNotificationsShade(
                loggingReason = loggingReason,
                transitionKey = SlightlyFasterShadeCollapse,
            )
        } else {
            shadeInteractor.expandNotificationsShade(loggingReason)
        }
    }

    /** Notifies that the system icons container was clicked. */
    fun onSystemIconChipClicked() {
        val loggingReason = "ShadeHeaderViewModel.onSystemIconChipClicked"
        if (shadeModeInteractor.isDualShade) {
            val currentOverlays = sceneInteractor.currentOverlays.value
            if (Overlays.QuickSettingsShade in currentOverlays) {
                shadeInteractor.collapseQuickSettingsShade(
                    loggingReason = loggingReason,
                    transitionKey = SlightlyFasterShadeCollapse,
                )
            } else {
                shadeInteractor.expandQuickSettingsShade(loggingReason)
            }
        } else {
            shadeInteractor.collapseEitherShade(
                loggingReason = loggingReason,
                transitionKey = SlightlyFasterShadeCollapse,
            )
        }
    }

    /** Notifies that the shadeCarrierGroup was clicked. */
    fun onShadeCarrierGroupClicked() {
        activityStarter.postStartActivityDismissingKeyguard(
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            0,
        )
    }

    /** Represents the background highlight of a header icons chip. */
    sealed interface HeaderChipHighlight {

        fun backgroundColor(colorScheme: ColorScheme): Color

        fun foregroundColor(colorScheme: ColorScheme): Color

        data object None : HeaderChipHighlight {
            override fun backgroundColor(colorScheme: ColorScheme): Color = Color.Unspecified

            override fun foregroundColor(colorScheme: ColorScheme): Color = colorScheme.primary
        }

        data object Weak : HeaderChipHighlight {
            override fun backgroundColor(colorScheme: ColorScheme): Color =
                colorScheme.surface.copy(alpha = 0.1f)

            override fun foregroundColor(colorScheme: ColorScheme): Color = colorScheme.onSurface
        }

        data object Strong : HeaderChipHighlight {
            override fun backgroundColor(colorScheme: ColorScheme): Color =
                colorScheme.primaryContainer

            override fun foregroundColor(colorScheme: ColorScheme): Color =
                colorScheme.onPrimaryContainer
        }
    }

    private fun shouldShowClock(isShadeLayoutWide: Boolean, overlays: Set<OverlayKey>): Boolean {
        // Notifications shade on narrow layout renders its own clock. Hide the header clock.
        return isShadeLayoutWide || Overlays.NotificationsShade !in overlays
    }

    private fun getFormatFromPattern(pattern: String?): DateFormat {
        val format = DateFormat.getInstanceForSkeleton(pattern, Locale.getDefault())
        format.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
        return format
    }

    @AssistedFactory
    interface Factory {
        fun create(): ShadeHeaderViewModel
    }
}
