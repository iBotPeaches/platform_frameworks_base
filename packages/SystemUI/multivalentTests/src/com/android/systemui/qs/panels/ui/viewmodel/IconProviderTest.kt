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

package com.android.systemui.qs.panels.ui.viewmodel

import android.graphics.drawable.TestStubDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import java.util.function.Supplier
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class IconProviderTest : SysuiTestCase() {

    @Test
    fun iconAndSupplier_prefersIcon() {
        val state =
            QSTile.State().apply {
                icon = ResourceIcon.get(R.drawable.android)
                iconSupplier = Supplier { QSTileImpl.DrawableIcon(TestStubDrawable()) }
            }
        val iconProvider = state.toIconProvider()

        assertThat(iconProvider).isEqualTo(IconProvider.ConstantIcon(state.icon))
    }

    @Test
    fun iconOnly_hasIcon() {
        val state = QSTile.State().apply { icon = ResourceIcon.get(R.drawable.android) }
        val iconProvider = state.toIconProvider()

        assertThat(iconProvider).isEqualTo(IconProvider.ConstantIcon(state.icon))
    }

    @Test
    fun supplierOnly_hasIcon() {
        val state =
            QSTile.State().apply {
                iconSupplier = Supplier { ResourceIcon.get(R.drawable.android) }
            }
        val iconProvider = state.toIconProvider()

        assertThat(iconProvider).isEqualTo(IconProvider.IconSupplier(state.iconSupplier))
    }

    @Test
    fun noIconOrSupplier_iconNull() {
        val state = QSTile.State()
        val iconProvider = state.toIconProvider()

        assertThat(iconProvider).isEqualTo(IconProvider.Empty)
    }
}
