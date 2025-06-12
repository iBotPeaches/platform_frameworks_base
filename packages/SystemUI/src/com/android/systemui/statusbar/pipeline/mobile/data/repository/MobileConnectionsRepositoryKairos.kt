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

import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import com.android.settingslib.SignalIcon.MobileIconGroup
import com.android.settingslib.mobile.MobileMappings
import com.android.settingslib.mobile.MobileMappings.Config
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.State
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel

/**
 * Repo for monitoring the complete active subscription info list, to be consumed and filtered based
 * on various policy
 */
@ExperimentalKairosApi
interface MobileConnectionsRepositoryKairos {

    /** All active mobile connections. */
    val mobileConnectionsBySubId: Incremental<Int, MobileConnectionRepositoryKairos>

    /** Observable list of current mobile subscriptions */
    val subscriptions: State<Collection<SubscriptionModel>>

    /**
     * Observable for the subscriptionId of the current mobile data connection. Null if we don't
     * have a valid subscription id
     */
    val activeMobileDataSubscriptionId: State<Int?>

    /** Repo that tracks the current [activeMobileDataSubscriptionId] */
    val activeMobileDataRepository: State<MobileConnectionRepositoryKairos?>

    /**
     * Observable event for when the active data sim switches but the group stays the same. E.g.,
     * CBRS switching would trigger this
     */
    val activeSubChangedInGroupEvent: Events<Unit>

    /** Tracks [SubscriptionManager.getDefaultDataSubscriptionId]. `null` if there is no default. */
    val defaultDataSubId: State<Int?>

    /**
     * True if the default network connection is a mobile-like connection and false otherwise.
     *
     * This is typically shown by having [android.net.NetworkCapabilities.TRANSPORT_CELLULAR], but
     * there are edge cases (like carrier merged wifi) that could also result in the default
     * connection being mobile-like.
     */
    val mobileIsDefault: State<Boolean>

    /**
     * True if the device currently has a carrier merged connection.
     *
     * See [CarrierMergedConnectionRepository] for more info.
     */
    val hasCarrierMergedConnection: State<Boolean>

    /** True if the default network connection is validated and false otherwise. */
    val defaultConnectionIsValidated: State<Boolean>

    /**
     * [Config] is an object that tracks relevant configuration flags for a given subscription ID.
     * In the case of [MobileMappings], it's hard-coded to check the default data subscription's
     * config, so this will apply to every icon that we care about.
     *
     * Relevant bits in the config are things like
     * [CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL]
     *
     * This flow will produce whenever the default data subscription or the carrier config changes.
     */
    val defaultDataSubRatConfig: State<Config>

    /** The icon mapping from network type to [MobileIconGroup] for the default subscription */
    val defaultMobileIconMapping: State<Map<String, MobileIconGroup>>

    /** Fallback [MobileIconGroup] in the case where there is no icon in the mapping */
    val defaultMobileIconGroup: State<MobileIconGroup>

    /**
     * Can the device make emergency calls using the device-based service state? This field is only
     * useful when all known active subscriptions are OOS and not emergency call capable.
     *
     * Specifically, this checks every [ServiceState] of the device, and looks for any that report
     * [ServiceState.isEmergencyOnly].
     *
     * This is an eager flow, and re-evaluates whenever ACTION_SERVICE_STATE is sent for subId = -1.
     */
    val isDeviceEmergencyCallCapable: State<Boolean>

    /**
     * If any active SIM on the device is in
     * [android.telephony.TelephonyManager.SIM_STATE_PIN_REQUIRED] or
     * [android.telephony.TelephonyManager.SIM_STATE_PUK_REQUIRED] or
     * [android.telephony.TelephonyManager.SIM_STATE_PERM_DISABLED]
     */
    val isAnySimSecure: State<Boolean>

    /**
     * Checks if any subscription has [android.telephony.TelephonyManager.getEmergencyCallbackMode]
     * == true
     */
    val isInEcmMode: State<Boolean>
}
