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

package com.android.systemui.statusbar.notification.row.ui.viewmodel

import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import kotlinx.coroutines.CoroutineScope

interface BundleHeaderViewModel {
    val titleText: String
    val numberOfChildren: Int?
    val bundleIcon: Drawable?
    val previewIcons: List<Drawable>

    val state: SceneTransitionLayoutState

    val hasUnreadMessages: Boolean
    val backgroundDrawable: Drawable?

    fun onHeaderClicked(scope: CoroutineScope)
}

class BundleHeaderViewModelImpl : BundleHeaderViewModel {
    override var titleText by mutableStateOf("")
    override var numberOfChildren by mutableStateOf<Int?>(1)
    override var hasUnreadMessages by mutableStateOf(true)
    override var bundleIcon by mutableStateOf<Drawable?>(null)
    override var previewIcons by mutableStateOf(listOf<Drawable>())
    override var backgroundDrawable by mutableStateOf<Drawable?>(null)

    var onExpandClickListener: View.OnClickListener? = null

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override var state: MutableSceneTransitionLayoutState =
        MutableSceneTransitionLayoutState(
            BundleHeader.Scenes.Collapsed,
            MotionScheme.standard(),
            transitions {
                from(BundleHeader.Scenes.Collapsed, to = BundleHeader.Scenes.Expanded) {
                    spec = tween(500)
                    translate(BundleHeader.Elements.PreviewIcon3, x = 32.dp)
                    translate(BundleHeader.Elements.PreviewIcon2, x = 16.dp)
                    fade(BundleHeader.Elements.PreviewIcon1)
                    fade(BundleHeader.Elements.PreviewIcon2)
                    fade(BundleHeader.Elements.PreviewIcon3)
                }
            },
        )

    override fun onHeaderClicked(scope: CoroutineScope) {
        val targetScene =
            when (state.currentScene) {
                BundleHeader.Scenes.Collapsed -> BundleHeader.Scenes.Expanded
                BundleHeader.Scenes.Expanded -> BundleHeader.Scenes.Collapsed
                else -> error("Unknown Scene")
            }
        state.setTargetScene(targetScene, scope)

        onExpandClickListener?.onClick(null)
        hasUnreadMessages = false
    }
}
