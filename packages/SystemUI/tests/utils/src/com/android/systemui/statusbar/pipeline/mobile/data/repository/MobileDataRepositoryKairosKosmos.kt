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

import android.content.applicationContext
import android.telephony.SubscriptionManager
import android.telephony.telephonyManager
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.demoModeController
import com.android.systemui.dump.dumpManager
import com.android.systemui.kairos.ActivatedKairosFixture
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.MutableEvents
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.kairos
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.log.table.tableLogBufferFactory
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.mobile.data.MobileInputLogger
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.DemoMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.DemoModeMobileConnectionDataSourceKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.demo.model.FakeNetworkEventModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionsRepositoryKairosImpl
import com.android.systemui.statusbar.pipeline.mobile.util.FakeSubscriptionManagerProxy
import com.android.systemui.statusbar.pipeline.mobile.util.SubscriptionManagerProxy
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.wifi.data.repository.demo.DemoModeWifiDataSource
import com.android.systemui.statusbar.pipeline.wifi.data.repository.wifiRepository
import com.android.systemui.util.mockito.mockFixture
import org.mockito.kotlin.mock

@ExperimentalKairosApi
var Kosmos.mobileConnectionsRepositoryKairos: MobileConnectionsRepositoryKairos by Fixture {
    mobileRepositorySwitcherKairos
}

@ExperimentalKairosApi
val Kosmos.fakeMobileConnectionsRepositoryKairos by ActivatedKairosFixture {
    FakeMobileConnectionsRepositoryKairos(kairos, logcatTableLogBuffer(this), mobileMappingsProxy)
}

@ExperimentalKairosApi
val Kosmos.demoMobileConnectionsRepositoryKairos by ActivatedKairosFixture {
    DemoMobileConnectionsRepositoryKairos(
        mobileDataSource = demoModeMobileConnectionDataSourceKairos,
        wifiDataSource = wifiDataSource,
        context = applicationContext,
        logFactory = tableLogBufferFactory,
    )
}

@ExperimentalKairosApi
val Kosmos.demoModeMobileConnectionDataSourceKairos:
    DemoModeMobileConnectionDataSourceKairos by Fixture {
    FakeDemoModeMobileConnectionDataSourceKairos(kairos)
}

val Kosmos.wifiDataSource: DemoModeWifiDataSource by mockFixture()

@ExperimentalKairosApi
class FakeDemoModeMobileConnectionDataSourceKairos(kairos: KairosNetwork) :
    DemoModeMobileConnectionDataSourceKairos {
    override val mobileEvents = MutableEvents<FakeNetworkEventModel?>(kairos)
}

@ExperimentalKairosApi
val DemoModeMobileConnectionDataSourceKairos.fake
    get() = this as FakeDemoModeMobileConnectionDataSourceKairos

@ExperimentalKairosApi
val Kosmos.mobileRepositorySwitcherKairos:
    MobileRepositorySwitcherKairos by ActivatedKairosFixture {
    MobileRepositorySwitcherKairos(
        realRepository = mobileConnectionsRepositoryKairosImpl,
        demoRepositoryFactory = demoMobileConnectionsRepositoryKairosFactory,
        demoModeController = demoModeController,
    )
}

@ExperimentalKairosApi
val Kosmos.demoMobileConnectionsRepositoryKairosFactory:
    DemoMobileConnectionsRepositoryKairos.Factory by Fixture {
    DemoMobileConnectionsRepositoryKairos.Factory {
        DemoMobileConnectionsRepositoryKairos(
            mobileDataSource = demoModeMobileConnectionDataSourceKairos,
            wifiDataSource = wifiDataSource,
            context = applicationContext,
            logFactory = tableLogBufferFactory,
        )
    }
}

@ExperimentalKairosApi
val Kosmos.mobileConnectionsRepositoryKairosImpl:
    MobileConnectionsRepositoryKairosImpl by ActivatedKairosFixture {
    MobileConnectionsRepositoryKairosImpl(
        connectivityRepository = connectivityRepository,
        subscriptionManager = subscriptionManager,
        subscriptionManagerProxy = subscriptionManagerProxy,
        telephonyManager = telephonyManager,
        logger = mobileInputLogger,
        tableLogger = summaryLogger,
        mobileMappingsProxy = mobileMappingsProxy,
        broadcastDispatcher = broadcastDispatcher,
        context = applicationContext,
        bgDispatcher = testDispatcher,
        mainDispatcher = testDispatcher,
        airplaneModeRepository = airplaneModeRepository,
        wifiRepository = wifiRepository,
        keyguardUpdateMonitor = keyguardUpdateMonitor,
        dumpManager = dumpManager,
        mobileRepoFactory = { mobileConnectionRepositoryKairosFactory },
    )
}

val Kosmos.subscriptionManager: SubscriptionManager by mockFixture()
val Kosmos.mobileInputLogger: MobileInputLogger by mockFixture()
val Kosmos.summaryLogger: TableLogBuffer by Fixture { logcatTableLogBuffer(this, "summaryLogger") }

@ExperimentalKairosApi
val Kosmos.mobileConnectionRepositoryKairosFactory by Fixture {
    MobileConnectionsRepositoryKairosImpl.ConnectionRepoFactory { subId ->
        buildSpec { FakeMobileConnectionRepositoryKairos(subId, kairos, mock()) }
    }
}

val Kosmos.subscriptionManagerProxy: SubscriptionManagerProxy by Fixture {
    FakeSubscriptionManagerProxy()
}
