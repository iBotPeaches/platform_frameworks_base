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

package com.android.systemui.util.settings.repository

import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/** Repository observing values of a [Settings.Secure] for the specified user. */
@SysUISingleton
class SecureSettingsForUserRepository
@Inject
constructor(
    secureSettings: SecureSettings,
    @Background backgroundDispatcher: CoroutineDispatcher,
    @Background backgroundContext: CoroutineContext,
) : SettingsForUserRepository(secureSettings, backgroundDispatcher, backgroundContext)
