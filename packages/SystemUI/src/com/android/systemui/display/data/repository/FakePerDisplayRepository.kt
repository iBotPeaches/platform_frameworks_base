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

// TODO b/401305290 - move to displaylib
class FakePerDisplayRepository<T> : PerDisplayRepository<T> {

    private val instances = mutableMapOf<Int, T>()

    fun add(displayId: Int, instance: T) {
        instances[displayId] = instance
    }

    fun remove(displayId: Int) {
        instances.remove(displayId)
    }

    override fun get(displayId: Int): T? {
        return instances[displayId]
    }

    override val debugName: String
        get() = "FakePerDisplayRepository"
}
