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

package com.android.systemui.qs.tiles.impl.modes.ui

import android.content.res.Resources
import android.widget.Switch
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.ui.model.QSTileDataToStateMapper
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesDndTileModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

class ModesDndTileMapper
@Inject
constructor(@ShadeDisplayAware private val resources: Resources, val theme: Resources.Theme) :
    QSTileDataToStateMapper<ModesDndTileModel> {
    override fun map(config: QSTileConfig, data: ModesDndTileModel): QSTileState =
        QSTileState.build(resources, theme, config.uiConfig) {
            val iconResource =
                if (data.isActivated) R.drawable.qs_dnd_icon_on else R.drawable.qs_dnd_icon_off
            icon =
                Icon.Loaded(
                    resources.getDrawable(iconResource, theme),
                    res = iconResource,
                    contentDescription = null,
                )

            activationState =
                if (data.isActivated) {
                    QSTileState.ActivationState.ACTIVE
                } else {
                    QSTileState.ActivationState.INACTIVE
                }
            label = resources.getString(R.string.quick_settings_dnd_label)
            secondaryLabel = data.extraStatus
            contentDescription = label
            stateDescription = data.extraStatus
            supportedActions =
                setOf(QSTileState.UserAction.CLICK, QSTileState.UserAction.LONG_CLICK)
            expandedAccessibilityClass = Switch::class
        }
}
