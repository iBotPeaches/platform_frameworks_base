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

package com.android.systemui.accessibility.hearingaid

import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.HapClientProfile
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.hearingaid.HearingDevicesInputRoutingController.InputRoutingControlAvailableCallback
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class HearingDevicesInputRoutingControllerTest : SysuiTestCase() {

    private val kosmos = Kosmos()
    private val testScope = kosmos.testScope
    private var hapClientProfile: HapClientProfile = mock()
    private var cachedDevice: CachedBluetoothDevice = mock()
    private var memberCachedDevice: CachedBluetoothDevice = mock()
    private var btDevice: android.bluetooth.BluetoothDevice = mock()
    private var audioManager: AudioManager = mock()
    private lateinit var underTest: HearingDevicesInputRoutingController
    private val testDispatcher = kosmos.testDispatcher

    @Before
    fun setUp() {
        hapClientProfile.stub { on { isProfileReady } doReturn true }
        cachedDevice.stub {
            on { device } doReturn btDevice
            on { profiles } doReturn listOf(hapClientProfile)
        }
        memberCachedDevice.stub {
            on { device } doReturn btDevice
            on { profiles } doReturn listOf(hapClientProfile)
        }

        underTest = HearingDevicesInputRoutingController(mContext, audioManager, testDispatcher)
        underTest.setDevice(cachedDevice)
    }

    @Test
    fun isInputRoutingControlAvailable_validInput_supportHapProfile_returnTrue() {
        testScope.runTest {
            val mockInfoAddress = arrayOf(mockTestAddressInfo(TEST_ADDRESS))
            cachedDevice.stub {
                on { address } doReturn TEST_ADDRESS
                on { profiles } doReturn listOf(hapClientProfile)
            }
            audioManager.stub {
                on { getDevices(AudioManager.GET_DEVICES_INPUTS) } doReturn mockInfoAddress
            }

            var result: Boolean? = null
            underTest.isInputRoutingControlAvailable(
                object : InputRoutingControlAvailableCallback {
                    override fun onResult(available: Boolean) {
                        result = available
                    }
                }
            )

            runCurrent()
            assertThat(result).isTrue()
        }
    }

    @Test
    fun isInputRoutingControlAvailable_notSupportHapProfile_returnFalse() {
        testScope.runTest {
            val mockInfoAddress = arrayOf(mockTestAddressInfo(TEST_ADDRESS))
            cachedDevice.stub {
                on { address } doReturn TEST_ADDRESS
                on { profiles } doReturn emptyList()
            }
            audioManager.stub {
                on { getDevices(AudioManager.GET_DEVICES_INPUTS) } doReturn mockInfoAddress
            }

            var result: Boolean? = null
            underTest.isInputRoutingControlAvailable(
                object : InputRoutingControlAvailableCallback {
                    override fun onResult(available: Boolean) {
                        result = available
                    }
                }
            )

            runCurrent()
            assertThat(result).isFalse()
        }
    }

    @Test
    fun isInputRoutingControlAvailable_validInputMember_supportHapProfile_returnTrue() {
        testScope.runTest {
            val mockInfoAddress2 = arrayOf(mockTestAddressInfo(TEST_ADDRESS_2))
            cachedDevice.stub {
                on { address } doReturn TEST_ADDRESS
                on { profiles } doReturn listOf(hapClientProfile)
                on { memberDevice } doReturn (setOf(memberCachedDevice))
            }
            memberCachedDevice.stub { on { address } doReturn TEST_ADDRESS_2 }
            audioManager.stub {
                on { getDevices(AudioManager.GET_DEVICES_INPUTS) } doReturn mockInfoAddress2
            }

            var result: Boolean? = null
            underTest.isInputRoutingControlAvailable(
                object : InputRoutingControlAvailableCallback {
                    override fun onResult(available: Boolean) {
                        result = available
                    }
                }
            )

            runCurrent()
            assertThat(result).isTrue()
        }
    }

    @Test
    fun isAvailable_notValidInputDevice_returnFalse() {
        testScope.runTest {
            cachedDevice.stub {
                on { address } doReturn TEST_ADDRESS
                on { profiles } doReturn listOf(hapClientProfile)
            }
            audioManager.stub {
                on { getDevices(AudioManager.GET_DEVICES_INPUTS) } doReturn emptyArray()
            }

            var result: Boolean? = null
            underTest.isInputRoutingControlAvailable(
                object : InputRoutingControlAvailableCallback {
                    override fun onResult(available: Boolean) {
                        result = available
                    }
                }
            )

            runCurrent()
            assertThat(result).isFalse()
        }
    }

    @Test
    fun selectInputRouting_builtinMic_setMicrophonePreferredForCallsFalse() {
        underTest.selectInputRouting(
            HearingDevicesInputRoutingController.InputRoutingValue.BUILTIN_MIC.ordinal
        )

        verify(btDevice).isMicrophonePreferredForCalls = false
    }

    private fun mockTestAddressInfo(address: String): AudioDeviceInfo {
        val info: AudioDeviceInfo = mock()
        info.stub {
            on { type } doReturn AudioDeviceInfo.TYPE_BLE_HEADSET
            on { this.address } doReturn address
        }

        return info
    }

    companion object {
        private const val TEST_ADDRESS = "55:66:77:88:99:AA"
        private const val TEST_ADDRESS_2 = "55:66:77:88:99:BB"
    }
}
