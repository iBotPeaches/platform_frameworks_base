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

package com.android.systemui.statusbar.pipeline.mobile.ui

import android.content.Context
import com.android.settingslib.flags.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.pipeline.icons.shared.model.BindableIcon
import com.android.systemui.statusbar.pipeline.icons.shared.model.ModernStatusBarViewCreator
import com.android.systemui.statusbar.pipeline.mobile.ui.binder.StackedMobileIconBinder
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModelImpl
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModelKairos
import com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView
import javax.inject.Inject

@OptIn(ExperimentalKairosApi::class)
@SysUISingleton
class StackedMobileBindableIcon
@Inject
constructor(
    context: Context,
    mobileIconsViewModel: MobileIconsViewModel,
    viewModelFactory: StackedMobileIconViewModelImpl.Factory,
    kairosViewModelFactory: StackedMobileIconViewModelKairos.Factory,
    kairosNetwork: KairosNetwork,
) : BindableIcon {
    override val slot: String =
        context.getString(com.android.internal.R.string.status_bar_stacked_mobile)

    override val initializer = ModernStatusBarViewCreator { context ->
        SingleBindableStatusBarComposeIconView.createView(context).also { view ->
            view.initView(slot) {
                StackedMobileIconBinder.bind(
                    view,
                    mobileIconsViewModel,
                    viewModelFactory,
                    kairosViewModelFactory,
                    kairosNetwork,
                )
            }
        }
    }

    override val shouldBindIcon: Boolean =
        Flags.newStatusBarIcons() && StatusBarRootModernization.isEnabled
}
