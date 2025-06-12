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

package com.android.systemui.media.remedia.ui.viewmodel

/** Enumerates the known rules for media carousel visibility. */
enum class MediaCarouselVisibility {

    /** The carousel should be shown as long as it has at least one card. */
    WhenNotEmpty,

    /**
     * The carousel should be shown as long as it has at least one card that represents an active
     * media session. In other words: if all cards in the carousel represent _inactive_ sessions,
     * the carousel should _not_ be visible.
     */
    WhenAnyCardIsActive,
}
