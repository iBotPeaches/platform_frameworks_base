/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.display.data.repository

import com.android.app.displaylib.DisplayRepository as DisplayRepositoryFromLib
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

/**
 * Repository for providing access to display related information and events.
 *
 * This is now just an interface that extends [DisplayRepositoryFromLib] to avoid changing all the
 * imports in sysui using this interface.
 */
interface DisplayRepository : DisplayRepositoryFromLib, DisplaysWithDecorationsRepository

@SysUISingleton
class DisplayRepositoryImpl
@Inject
constructor(
    private val displayRepositoryFromLib: com.android.app.displaylib.DisplayRepository,
    private val displaysWithDecorationsRepositoryImpl: DisplaysWithDecorationsRepository,
) :
    DisplayRepositoryFromLib by displayRepositoryFromLib,
    DisplaysWithDecorationsRepository by displaysWithDecorationsRepositoryImpl,
    DisplayRepository
