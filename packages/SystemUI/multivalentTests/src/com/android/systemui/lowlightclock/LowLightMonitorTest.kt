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
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dream.lowlight.LowLightDreamManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.domain.interactor.displayStateInteractor
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shared.condition.Condition
import com.android.systemui.shared.condition.Monitor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth
import dagger.Lazy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class LowLightMonitorTest : SysuiTestCase() {
    val kosmos = testKosmos().useUnconfinedTestDispatcher()

    @Mock private lateinit var lowLightDreamManagerLazy: Lazy<LowLightDreamManager>

    @Mock private lateinit var lowLightDreamManager: LowLightDreamManager

    private val monitor: Monitor = prepareMonitor()

    @Mock private lateinit var logger: LowLightLogger

    private lateinit var lowLightMonitor: LowLightMonitor

    @Mock private lateinit var lazyConditions: Lazy<Set<Condition>>

    @Mock private lateinit var packageManager: PackageManager

    @Mock private lateinit var dreamComponent: ComponentName

    private val condition = mock<Condition>()

    private val conditionSet = setOf(condition)

    @Captor
    private lateinit var preconditionsSubscriptionCaptor: ArgumentCaptor<Monitor.Subscription>

    private fun prepareMonitor(): Monitor {
        val monitor = mock<Monitor>()
        whenever(monitor.addSubscription(ArgumentMatchers.any())).thenReturn(mock())

        return monitor
    }

    private fun setDisplayOn(screenOn: Boolean) {
        kosmos.displayRepository.setDefaultDisplayOff(!screenOn)
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(lowLightDreamManagerLazy.get()).thenReturn(lowLightDreamManager)
        whenever(lazyConditions.get()).thenReturn(conditionSet)
        lowLightMonitor =
            LowLightMonitor(
                lowLightDreamManagerLazy,
                monitor,
                lazyConditions,
                kosmos.displayStateInteractor,
                logger,
                dreamComponent,
                packageManager,
                kosmos.testScope.backgroundScope,
            )
        whenever(monitor.addSubscription(ArgumentMatchers.any())).thenReturn(mock())
        val subscriptionCaptor = argumentCaptor<Monitor.Subscription>()

        setDisplayOn(false)

        lowLightMonitor.start()
        verify(monitor).addSubscription(subscriptionCaptor.capture())
        clearInvocations(monitor)

        subscriptionCaptor.firstValue.callback.onConditionsChanged(true)
    }

    private fun getConditionCallback(monitor: Monitor): Monitor.Callback {
        val subscriptionCaptor = argumentCaptor<Monitor.Subscription>()
        verify(monitor).addSubscription(subscriptionCaptor.capture())
        return subscriptionCaptor.firstValue.callback
    }

    @Test
    fun testSetAmbientLowLightWhenInLowLight() =
        kosmos.runTest {
            // Turn on screen
            setDisplayOn(true)

            // Set conditions to true
            val callback = getConditionCallback(monitor)
            callback.onConditionsChanged(true)

            // Verify setting low light when condition is true
            Mockito.verify(lowLightDreamManager)
                .setAmbientLightMode(LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT)
        }

    @Test
    fun testExitAmbientLowLightWhenNotInLowLight() =
        kosmos.runTest {
            // Turn on screen
            setDisplayOn(true)

            // Set conditions to true then false
            val callback = getConditionCallback(monitor)
            callback.onConditionsChanged(true)
            clearInvocations(lowLightDreamManager)
            callback.onConditionsChanged(false)

            // Verify ambient light toggles back to light mode regular
            Mockito.verify(lowLightDreamManager)
                .setAmbientLightMode(LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR)
        }

    @Test
    fun testStopMonitorLowLightConditionsWhenScreenTurnsOff() =
        kosmos.runTest {
            val token = mock<Monitor.Subscription.Token>()
            whenever(monitor.addSubscription(ArgumentMatchers.any())).thenReturn(token)

            setDisplayOn(true)

            // Verify removing subscription when screen turns off.
            setDisplayOn(false)
            Mockito.verify(monitor).removeSubscription(token)
        }

    @Test
    fun testSubscribeToLowLightConditionsOnlyOnceWhenScreenTurnsOn() =
        kosmos.runTest {
            val token = mock<Monitor.Subscription.Token>()
            whenever(monitor.addSubscription(ArgumentMatchers.any())).thenReturn(token)

            setDisplayOn(true)
            setDisplayOn(true)
            // Verify subscription is only added once.
            Mockito.verify(monitor, Mockito.times(1)).addSubscription(ArgumentMatchers.any())
        }

    @Test
    fun testSubscribedToExpectedConditions() =
        kosmos.runTest {
            val token = mock<Monitor.Subscription.Token>()
            whenever(monitor.addSubscription(ArgumentMatchers.any())).thenReturn(token)

            setDisplayOn(true)

            val conditions = captureConditions()
            // Verify Monitor is subscribed to the expected conditions
            Truth.assertThat(conditions).isEqualTo(conditionSet)
        }

    @Test
    fun testNotUnsubscribeIfNotSubscribedWhenScreenTurnsOff() =
        kosmos.runTest {
            setDisplayOn(true)
            clearInvocations(monitor)
            setDisplayOn(false)
            runCurrent()
            // Verify doesn't remove subscription since there is none.
            Mockito.verify(monitor).removeSubscription(ArgumentMatchers.any())
        }

    @Test
    fun testSubscribeIfScreenIsOnWhenStarting() =
        kosmos.runTest {
            val monitor = prepareMonitor()

            setDisplayOn(true)

            val targetMonitor =
                LowLightMonitor(
                    lowLightDreamManagerLazy,
                    monitor,
                    lazyConditions,
                    displayStateInteractor,
                    logger,
                    dreamComponent,
                    packageManager,
                    testScope.backgroundScope,
                )

            // start
            targetMonitor.start()

            val callback = getConditionCallback(monitor)
            clearInvocations(monitor)
            callback.onConditionsChanged(true)

            // Verify to add subscription on start and when the screen state is on
            Mockito.verify(monitor).addSubscription(ArgumentMatchers.any())
        }

    @Test
    fun testNoSubscribeIfDreamNotPresent() =
        kosmos.runTest {
            val monitor = prepareMonitor()

            setDisplayOn(true)

            val lowLightMonitor =
                LowLightMonitor(
                    lowLightDreamManagerLazy,
                    monitor,
                    lazyConditions,
                    displayStateInteractor,
                    logger,
                    null,
                    packageManager,
                    testScope,
                )

            // start
            lowLightMonitor.start()

            val callback = getConditionCallback(monitor)
            clearInvocations(monitor)
            callback.onConditionsChanged(true)

            // Verify to add subscription on start and when the screen state is on
            Mockito.verify(monitor, never()).addSubscription(ArgumentMatchers.any())
        }

    private fun captureConditions(): Set<Condition?> {
        Mockito.verify(monitor).addSubscription(preconditionsSubscriptionCaptor.capture())
        return preconditionsSubscriptionCaptor.value.conditions
    }
}
