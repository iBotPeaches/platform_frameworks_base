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

package com.android.systemui.communal.shared.model

/** Models the types of background that can be shown on the hub. */
enum class CommunalBackgroundType(val value: Int, val opaque: Boolean) {
    STATIC(value = 0, opaque = true),
    STATIC_GRADIENT(value = 1, opaque = true),
    ANIMATED(value = 2, opaque = true),
    NONE(value = 3, opaque = false),
    BLUR(value = 4, opaque = false),
    SCRIM(value = 5, opaque = false),
}
