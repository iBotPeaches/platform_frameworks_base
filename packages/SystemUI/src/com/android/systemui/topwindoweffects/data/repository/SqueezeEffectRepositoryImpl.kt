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

package com.android.systemui.topwindoweffects.data.repository

import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings.Global.POWER_BUTTON_LONG_PRESS
import com.android.internal.R
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.Flags
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@SysUISingleton
class SqueezeEffectRepositoryImpl @Inject constructor(
    @Background private val bgHandler: Handler?,
    @Background private val bgCoroutineContext: CoroutineContext,
    private val globalSettings: GlobalSettings
) : SqueezeEffectRepository {

    override val isSqueezeEffectEnabled: Flow<Boolean> = conflatedCallbackFlow {
        val observer = object : ContentObserver(bgHandler) {
            override fun onChange(selfChange: Boolean) {
                trySendWithFailureLogging(squeezeEffectEnabled, TAG,
                    "updated isSqueezeEffectEnabled")
            }
        }
        trySendWithFailureLogging(squeezeEffectEnabled, TAG, "init isSqueezeEffectEnabled")
        globalSettings.registerContentObserverAsync(POWER_BUTTON_LONG_PRESS, observer)
        awaitClose { globalSettings.unregisterContentObserverAsync(observer) }
    }.flowOn(bgCoroutineContext)

    private val squeezeEffectEnabled
        get() = Flags.enableLppSqueezeEffect() && globalSettings.getInt(
            POWER_BUTTON_LONG_PRESS, R.integer.config_longPressOnPowerBehavior
        ) == 5 // 5 corresponds to launch assistant in config_longPressOnPowerBehavior

    companion object {
        private const val TAG = "SqueezeEffectRepository"
    }
}