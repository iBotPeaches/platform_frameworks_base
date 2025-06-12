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

package android.window

import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.platform.test.annotations.Presubmit
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test to verify [ConfigurationDispatcher]
 *
 * Build/Install/Run:
 * atest FrameworksCoreTests:ConfigurationDispatcherTest
 */
@SmallTest
@Presubmit
@RunWith(Parameterized::class)
class ConfigurationDispatcherTest(private val shouldReportPrivateChanges: Boolean) {

    /**
     * Verifies [ConfigurationDispatcher.shouldReportPrivateChanges].
     */
    @Test
    fun testConfigurationDispatcher() {
        val receiver = TestConfigurationReceiver(shouldReportPrivateChanges)
        val config = Configuration().apply {
            orientation = ORIENTATION_PORTRAIT
        }

        // Verify public config field change
        receiver.windowToken.onConfigurationChangedInner(receiver, config, DEFAULT_DISPLAY, true)

        assertThat(receiver.receivedConfig).isEqualTo(config)

        // Clear the config value
        receiver.receivedConfig.unset()

        // Verify private config field change
        config.windowConfiguration.windowingMode = WINDOWING_MODE_MULTI_WINDOW

        receiver.windowToken.onConfigurationChangedInner(receiver, config, DEFAULT_DISPLAY, true)

        assertThat(receiver.receivedConfig).isEqualTo(
            if (shouldReportPrivateChanges) {
                config
            } else {
                Configuration.EMPTY
            }
        )
    }

    /**
     * Test [android.content.Context] to implement [ConfigurationDispatcher] for testing.
     *
     * @param shouldReportPrivateChanges used to override
     * [ConfigurationDispatcher.shouldReportPrivateChanges] for testing,
     */
    private class TestConfigurationReceiver(
        private val shouldReportPrivateChanges: Boolean
    ) : ContextWrapper(null), ConfigurationDispatcher {
        val windowToken = WindowTokenClient()
        val receivedConfig = Configuration()

        init {
            windowToken.attachContext(this)
        }

        override fun dispatchConfigurationChanged(configuration: Configuration) {
            receivedConfig.setTo(configuration)
        }

        override fun shouldReportPrivateChanges(): Boolean {
            return shouldReportPrivateChanges
        }

        override fun getDisplayId(): Int {
            return DEFAULT_DISPLAY
        }
    }

    companion object {
        @Parameterized.Parameters(name = "shouldReportPrivateChange={0}")
        @JvmStatic
        fun data(): Collection<Any> {
            return listOf(true, false)
        }
    }
}