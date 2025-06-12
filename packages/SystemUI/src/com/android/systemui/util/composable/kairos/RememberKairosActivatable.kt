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

package com.android.systemui.util.composable.kairos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.android.systemui.KairosActivatable
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork

@ExperimentalKairosApi
@Composable
fun <T : KairosActivatable> rememberKairosActivatable(
    kairosNetwork: KairosNetwork,
    key: Any = Unit,
    factory: () -> T,
): T {
    val instance = remember(key, factory) { factory() }
    LaunchedEffect(instance, kairosNetwork) {
        kairosNetwork.activateSpec { instance.run { activate() } }
    }
    return instance
}
