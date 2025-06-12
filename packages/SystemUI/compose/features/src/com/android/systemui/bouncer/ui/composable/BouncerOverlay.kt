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

package com.android.systemui.bouncer.ui.composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.overscroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.bouncer.ui.BouncerDialogFactory
import com.android.systemui.bouncer.ui.viewmodel.BouncerOverlayContentViewModel
import com.android.systemui.bouncer.ui.viewmodel.BouncerUserActionsViewModel
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.Overlay
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

object Bouncer {
    object Elements {
        val Root = ElementKey("BouncerRoot")
        val Background = ElementKey("BouncerBackground")
        val Content = ElementKey("BouncerContent")
    }

    object TestTags {
        const val Root = "bouncer_root"
    }
}

/** The bouncer overlay displays authentication challenges like PIN, password, or pattern. */
@SysUISingleton
class BouncerOverlay
@Inject
constructor(
    private val actionsViewModelFactory: BouncerUserActionsViewModel.Factory,
    private val contentViewModelFactory: BouncerOverlayContentViewModel.Factory,
    private val dialogFactory: BouncerDialogFactory,
) : Overlay {
    override val key = Overlays.Bouncer

    private val actionsViewModel: BouncerUserActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override suspend fun activate(): Nothing {
        actionsViewModel.activate()
    }

    @Composable
    override fun ContentScope.Content(modifier: Modifier) =
        BouncerOverlay(
            viewModel = rememberViewModel("BouncerOverlay") { contentViewModelFactory.create() },
            dialogFactory = dialogFactory,
            modifier = modifier.element(Bouncer.Elements.Root),
        )
}

@Composable
private fun ContentScope.BouncerOverlay(
    viewModel: BouncerOverlayContentViewModel,
    dialogFactory: BouncerDialogFactory,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surface

    DisposableEffect(Unit) { onDispose { viewModel.onUiDestroyed() } }

    Box(modifier) {
        Canvas(Modifier.element(Bouncer.Elements.Background).fillMaxSize()) {
            drawRect(color = backgroundColor)
        }

        // Separate the bouncer content into a reusable composable that doesn't have any
        // ContentScope dependencies
        BouncerContent(
            viewModel,
            dialogFactory,
            Modifier.element(Bouncer.Elements.Content)
                // TODO(b/393516240): Use the same sysuiResTag() as views instead.
                .testTag(Bouncer.Elements.Content.testTag)
                .overscroll(verticalOverscrollEffect)
                .sysuiResTag(Bouncer.TestTags.Root)
                .fillMaxSize(),
        )
    }
}
