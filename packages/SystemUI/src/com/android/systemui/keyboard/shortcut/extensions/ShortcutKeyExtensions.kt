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

package com.android.systemui.keyboard.shortcut.extensions

import android.content.Context
import android.view.KeyEvent.META_META_ON
import com.android.systemui.keyboard.shortcut.data.repository.ShortcutHelperKeys
import com.android.systemui.keyboard.shortcut.data.repository.ShortcutHelperKeys.metaModifierIconResId
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutKey
import com.android.systemui.res.R

fun ShortcutKey.toContentDescription(context: Context): String? {
    val forwardSlash = context.getString(R.string.shortcut_helper_key_combinations_forward_slash)
    when (this) {
        is ShortcutKey.Text -> {
            // Special handling for "/" as TalkBack will not read punctuation by
            // default.
            return if (this.value == "/") {
                forwardSlash
            } else {
                this.value
            }
        }

        is ShortcutKey.Icon.ResIdIcon -> {
            val keyLabel =
                if (this.drawableResId == metaModifierIconResId) {
                    ShortcutHelperKeys.modifierLabels[META_META_ON]
                } else {
                    val keyCode =
                        ShortcutHelperKeys.keyIcons.entries
                            .firstOrNull { it.value == this.drawableResId }
                            ?.key
                    ShortcutHelperKeys.specialKeyLabels[keyCode]
                }

            if (keyLabel != null) {
                return keyLabel.invoke(context)
            }
        }

        is ShortcutKey.Icon.DrawableIcon -> {
            // No-Op when shortcutKey is ShortcutKey.Icon.DrawableIcon
        }
    }

    return null
}
