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

package com.android.systemui.topwindoweffects.ui.compose

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.android.systemui.compose.ComposeInitializer
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectViewModel

@SuppressLint("ViewConstructor")
class EffectsWindowRoot(
    context: Context,
    private val onEffectFinished: () -> Unit,
    private val viewModelFactory: SqueezeEffectViewModel.Factory
) : AbstractComposeView(context) {

    override fun onAttachedToWindow() {
        ComposeInitializer.onAttachedToWindow(this)
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ComposeInitializer.onDetachedFromWindow(this)
    }

    @Composable
    override fun Content() {
        SqueezeEffect(
            viewModelFactory = viewModelFactory,
            onEffectFinished = onEffectFinished
        )
    }
}