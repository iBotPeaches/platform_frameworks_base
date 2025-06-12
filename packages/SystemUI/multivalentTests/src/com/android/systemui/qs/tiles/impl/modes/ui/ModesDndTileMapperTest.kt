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

import android.app.Flags
import android.graphics.drawable.TestStubDrawable
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesDndTileModel
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_MODES_UI)
class ModesDndTileMapperTest : SysuiTestCase() {
    val config =
        QSTileConfigTestBuilder.build {
            uiConfig =
                QSTileUIConfig.Resource(
                    iconRes = R.drawable.qs_dnd_icon_off,
                    labelRes = R.string.quick_settings_modes_label,
                )
        }

    val underTest =
        ModesDndTileMapper(
            context.orCreateTestableResources
                .apply {
                    addOverride(R.drawable.qs_dnd_icon_on, TestStubDrawable())
                    addOverride(R.drawable.qs_dnd_icon_off, TestStubDrawable())
                }
                .resources,
            context.theme,
        )

    @Test
    fun map_inactiveState() {
        val model = ModesDndTileModel(isActivated = false, extraStatus = null)

        val state = underTest.map(config, model)

        assertThat(state.activationState).isEqualTo(QSTileState.ActivationState.INACTIVE)
        assertThat((state.icon as Icon.Loaded).res).isEqualTo(R.drawable.qs_dnd_icon_off)
        assertThat(state.secondaryLabel).isNull() // Will use default label for activationState
    }

    @Test
    fun map_activeState() {
        val model = ModesDndTileModel(isActivated = true, extraStatus = null)

        val state = underTest.map(config, model)

        assertThat(state.activationState).isEqualTo(QSTileState.ActivationState.ACTIVE)
        assertThat((state.icon as Icon.Loaded).res).isEqualTo(R.drawable.qs_dnd_icon_on)
        assertThat(state.secondaryLabel).isNull() // Will use default label for activationState
    }

    @Test
    fun map_activeStateWithExtraStatus() {
        val model = ModesDndTileModel(isActivated = true, extraStatus = "Until 14:00")

        val state = underTest.map(config, model)

        assertThat(state.activationState).isEqualTo(QSTileState.ActivationState.ACTIVE)
        assertThat((state.icon as Icon.Loaded).res).isEqualTo(R.drawable.qs_dnd_icon_on)
        assertThat(state.secondaryLabel).isEqualTo("Until 14:00")
        assertThat(state.contentDescription).isEqualTo("Do Not Disturb")
        assertThat(state.stateDescription).isEqualTo("Until 14:00")
    }
}
