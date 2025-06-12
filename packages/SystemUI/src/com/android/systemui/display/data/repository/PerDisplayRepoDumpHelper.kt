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

package com.android.systemui.display.data.repository

import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.dump.DumpableFromToString
import javax.inject.Inject

/** Helper class to register PerDisplayRepository in the dump manager in SystemUI. */
@SysUISingleton
class PerDisplayRepoDumpHelper @Inject constructor(private val dumpManager: DumpManager) :
    PerDisplayRepository.InitCallback {
    /**
     * Registers PerDisplayRepository in the dump manager.
     *
     * The repository will be identified by the given debug name.
     */
    override fun onInit(debugName: String, instance: Any) {
        dumpManager.registerNormalDumpable(
            "PerDisplayRepository-$debugName",
            DumpableFromToString(instance),
        )
    }
}
