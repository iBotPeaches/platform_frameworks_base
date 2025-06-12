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

import androidx.compose.runtime.mutableStateOf
import com.android.systemui.KairosBuilder
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.State

@ExperimentalKairosApi
fun <T> KairosBuilder.hydratedComposeStateOf(
    source: State<T>,
    initialValue: T,
): androidx.compose.runtime.State<T> =
    mutableStateOf(initialValue).also { state ->
        onActivated { source.observe { state.value = it } }
    }
