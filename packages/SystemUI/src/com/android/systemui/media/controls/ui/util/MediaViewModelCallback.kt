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

package com.android.systemui.media.controls.ui.util

import androidx.recyclerview.widget.DiffUtil
import com.android.systemui.media.controls.ui.viewmodel.MediaControlViewModel

/** A [DiffUtil.Callback] to calculate difference between old and new media view-model list. */
class MediaViewModelCallback(
    private val old: List<MediaControlViewModel>,
    private val new: List<MediaControlViewModel>,
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        return old.size
    }

    override fun getNewListSize(): Int {
        return new.size
    }

    override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean {
        val oldItem = old[oldIndex]
        val newItem = new[newIndex]
        return oldItem.instanceId == newItem.instanceId
    }

    override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
        val oldItem = old[oldIndex]
        val newItem = new[newIndex]
        return oldItem.updateTime == newItem.updateTime
    }
}
