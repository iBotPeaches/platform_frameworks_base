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

package com.android.systemui.qs.panels.ui.compose.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.development.ui.compose.BuildNumber
import com.android.systemui.qs.footer.ui.compose.IconButton
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.ui.compose.borderOnFocus

@Composable
fun Toolbar(viewModel: ToolbarViewModel, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        viewModel.userSwitcherViewModel?.let {
            IconButton(
                it,
                useModifierBasedExpandable = true,
                Modifier.sysuiResTag("multi_user_switch"),
            )
        }

        EditModeButton(viewModel.editModeButtonViewModel)

        IconButton(
            viewModel.settingsButtonViewModel,
            useModifierBasedExpandable = true,
            Modifier.sysuiResTag("settings_button_container"),
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BuildNumber(
                viewModelFactory = viewModel.buildNumberViewModelFactory,
                textColor = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier.borderOnFocus(
                            color = MaterialTheme.colorScheme.secondary,
                            cornerSize = CornerSize(1.dp),
                        )
                        .wrapContentSize(),
            )
        }

        IconButton(
            { viewModel.powerButtonViewModel },
            useModifierBasedExpandable = true,
            Modifier.sysuiResTag("pm_lite"),
        )
    }
}
