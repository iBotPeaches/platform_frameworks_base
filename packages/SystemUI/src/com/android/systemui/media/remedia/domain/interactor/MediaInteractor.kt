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

package com.android.systemui.media.remedia.domain.interactor

import com.android.systemui.media.remedia.domain.model.MediaSessionModel

/**
 * Defines interface for classes that can provide business logic in the domain of the media controls
 * element.
 */
interface MediaInteractor {

    /** The list of sessions. Needs to be backed by a compose snapshot state. */
    val sessions: List<MediaSessionModel>

    /** Seek to [to], in milliseconds on the media session with the given [sessionKey]. */
    fun seek(sessionKey: Any, to: Long)

    /** Hide the representation of the media session with the given [sessionKey]. */
    fun hide(sessionKey: Any)

    /** Open media settings. */
    fun openMediaSettings()
}
