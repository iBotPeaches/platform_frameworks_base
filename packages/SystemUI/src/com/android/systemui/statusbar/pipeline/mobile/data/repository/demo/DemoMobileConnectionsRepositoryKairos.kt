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

package com.android.systemui.statusbar.pipeline.mobile.data.repository.demo

import android.content.Context
import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import android.util.Log
import com.android.settingslib.SignalIcon
import com.android.settingslib.mobile.MobileMappings
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.KairosBuilder
import com.android.systemui.activated
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.GroupedEvents
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.State
import com.android.systemui.kairos.TransactionScope
import com.android.systemui.kairos.asIncremental
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.combine
import com.android.systemui.kairos.emptyEvents
import com.android.systemui.kairos.filter
import com.android.systemui.kairos.filterIsInstance
import com.android.systemui.kairos.groupBy
import com.android.systemui.kairos.groupByKey
import com.android.systemui.kairos.map
import com.android.systemui.kairos.mapCheap
import com.android.systemui.kairos.mapNotNull
import com.android.systemui.kairos.mapValues
import com.android.systemui.kairos.mergeLeft
import com.android.systemui.kairos.stateOf
import com.android.systemui.kairosBuilder
import com.android.systemui.log.table.TableLogBufferFactory
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.model.FakeNetworkEventModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.model.FakeNetworkEventModel.Mobile
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.model.FakeNetworkEventModel.MobileDisabled
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.FullMobileConnectionRepository.Factory.Companion.MOBILE_CONNECTION_BUFFER_SIZE
import com.android.systemui.statusbar.pipeline.wifi.data.repository.demo.DemoModeWifiDataSource
import com.android.systemui.statusbar.pipeline.wifi.data.repository.demo.model.FakeWifiEventModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** This repository vends out data based on demo mode commands */
@ExperimentalKairosApi
class DemoMobileConnectionsRepositoryKairos
@AssistedInject
constructor(
    mobileDataSource: DemoModeMobileConnectionDataSourceKairos,
    private val wifiDataSource: DemoModeWifiDataSource,
    context: Context,
    private val logFactory: TableLogBufferFactory,
) : MobileConnectionsRepositoryKairos, KairosBuilder by kairosBuilder() {

    @AssistedFactory
    fun interface Factory {
        fun create(): DemoMobileConnectionsRepositoryKairos
    }

    private val wifiEvents: Events<FakeWifiEventModel?> = buildEvents {
        wifiDataSource.wifiEvents.toEvents()
    }

    private val mobileEventsWithSubId: Events<Pair<Int, FakeNetworkEventModel>> =
        mobileDataSource.mobileEvents.mapNotNull { event ->
            event?.let { (event.subId ?: lastSeenSubId.sample())?.let { it to event } }
        }

    private val mobileEventsBySubId: GroupedEvents<Int, FakeNetworkEventModel> =
        mobileEventsWithSubId.map { mapOf(it) }.groupByKey()

    private val carrierMergedEvents: Events<FakeWifiEventModel.CarrierMerged> =
        wifiEvents.filterIsInstance<FakeWifiEventModel.CarrierMerged>()

    private val wifiEventsBySubId: GroupedEvents<Int, FakeWifiEventModel.CarrierMerged> =
        carrierMergedEvents.groupBy { it.subscriptionId }

    private val lastSeenSubId: State<Int?> = buildState {
        mergeLeft(
                mobileEventsWithSubId.mapCheap { it.first },
                carrierMergedEvents.mapCheap { it.subscriptionId },
            )
            .holdState(null)
    }

    private val activeCarrierMergedSubscription: State<Int?> = buildState {
        mergeLeft(
                carrierMergedEvents.mapCheap { it.subscriptionId },
                wifiEvents
                    .filter {
                        it is FakeWifiEventModel.Wifi || it is FakeWifiEventModel.WifiDisabled
                    }
                    .map { null },
            )
            .holdState(null)
    }

    private val activeMobileSubscriptions: State<Set<Int>> = buildState {
        mobileDataSource.mobileEvents
            .mapNotNull { event ->
                when (event) {
                    null -> null
                    is Mobile -> event.subId?.let { subId -> { subs: Set<Int> -> subs + subId } }
                    is MobileDisabled ->
                        (event.subId ?: maybeGetOnlySubIdForRemoval())?.let { subId ->
                            { subs: Set<Int> -> subs - subId }
                        }
                }
            }
            .foldState(emptySet()) { f, s -> f(s) }
    }

    private val subscriptionIds: State<Set<Int>> =
        combine(activeMobileSubscriptions, activeCarrierMergedSubscription) { mobile, carrierMerged
            ->
            carrierMerged?.let { mobile + carrierMerged } ?: mobile
        }

    private val subscriptionsById: State<Map<Int, SubscriptionModel>> =
        subscriptionIds.map { subs ->
            subs.associateWith { subId ->
                SubscriptionModel(
                    subscriptionId = subId,
                    isOpportunistic = false,
                    carrierName = DEFAULT_CARRIER_NAME,
                    profileClass = PROFILE_CLASS_UNSET,
                )
            }
        }

    override val subscriptions: State<Collection<SubscriptionModel>> =
        subscriptionsById.map { it.values }

    private fun TransactionScope.maybeGetOnlySubIdForRemoval(): Int? {
        val subIds = activeMobileSubscriptions.sample()
        return if (subIds.size == 1) {
            subIds.first()
        } else {
            Log.d(
                TAG,
                "processDisabledMobileState: Unable to infer subscription to " +
                    "disable. Specify subId using '-e slot <subId>'. " +
                    "Known subIds: [${subIds.joinToString(",")}]",
            )
            null
        }
    }

    private val reposBySubId: Incremental<Int, DemoMobileConnectionRepositoryKairos> =
        buildIncremental {
            subscriptionsById
                .asIncremental()
                .mapValues { (id, _) -> buildSpec { newRepo(id) } }
                .applyLatestSpecForKey()
        }

    // TODO(b/261029387): add a command for this value
    override val activeMobileDataSubscriptionId: State<Int> =
        // For now, active is just the first in the list
        subscriptions.map { infos ->
            infos.firstOrNull()?.subscriptionId ?: INVALID_SUBSCRIPTION_ID
        }

    override val activeMobileDataRepository: State<DemoMobileConnectionRepositoryKairos?> =
        combine(activeMobileDataSubscriptionId, reposBySubId) { subId, repoMap -> repoMap[subId] }

    // TODO(b/261029387): consider adding a demo command for this
    override val activeSubChangedInGroupEvent: Events<Unit> = emptyEvents

    /** Demo mode doesn't currently support modifications to the mobile mappings */
    override val defaultDataSubRatConfig: State<MobileMappings.Config> =
        stateOf(MobileMappings.Config.readConfig(context))

    override val defaultMobileIconGroup: State<SignalIcon.MobileIconGroup> =
        stateOf(TelephonyIcons.THREE_G)

    // TODO(b/339023069): demo command for device-based emergency calls state
    override val isDeviceEmergencyCallCapable: State<Boolean> = stateOf(false)

    override val isAnySimSecure: State<Boolean> = stateOf(false)

    override val defaultMobileIconMapping: State<Map<String, SignalIcon.MobileIconGroup>> =
        stateOf(TelephonyIcons.ICON_NAME_TO_ICON)

    /**
     * In order to maintain compatibility with the old demo mode shell command API, reverse the
     * [MobileMappings] lookup from (NetworkType: String -> Icon: MobileIconGroup), so that we can
     * parse the string from the command line into a preferred icon group, and send _a_ valid
     * network type for that icon through the pipeline.
     *
     * Note: collisions don't matter here, because the data source (the command line) only cares
     * about the resulting icon, not the underlying network type.
     */
    private val mobileMappingsReverseLookup: State<Map<SignalIcon.MobileIconGroup, String>> =
        defaultMobileIconMapping.map { networkToIconMap -> networkToIconMap.reverse() }

    private fun <K, V> Map<K, V>.reverse() = entries.associate { (k, v) -> v to k }

    // TODO(b/261029387): add a command for this value
    override val defaultDataSubId: State<Int?> = stateOf(null)

    // TODO(b/261029387): not yet supported
    override val mobileIsDefault: State<Boolean> = stateOf(true)

    // TODO(b/261029387): not yet supported
    override val hasCarrierMergedConnection: State<Boolean> = stateOf(false)

    // TODO(b/261029387): not yet supported
    override val defaultConnectionIsValidated: State<Boolean> = stateOf(true)

    override val isInEcmMode: State<Boolean> = stateOf(false)

    override val mobileConnectionsBySubId: Incremental<Int, DemoMobileConnectionRepositoryKairos>
        get() = reposBySubId

    private fun BuildScope.newRepo(subId: Int) = activated {
        DemoMobileConnectionRepositoryKairos(
            subId = subId,
            tableLogBuffer =
                logFactory.getOrCreate(
                    "DemoMobileConnectionLog[$subId]",
                    MOBILE_CONNECTION_BUFFER_SIZE,
                ),
            mobileEvents = mobileEventsBySubId[subId].filterIsInstance(),
            carrierMergedResetEvents =
                wifiEvents.mapNotNull { it?.takeIf { it !is FakeWifiEventModel.CarrierMerged } },
            wifiEvents = wifiEventsBySubId[subId],
            mobileMappingsReverseLookup = mobileMappingsReverseLookup,
        )
    }

    companion object {
        private const val TAG = "DemoMobileConnectionsRepo"

        private const val DEFAULT_CARRIER_NAME = "demo carrier"
    }
}
