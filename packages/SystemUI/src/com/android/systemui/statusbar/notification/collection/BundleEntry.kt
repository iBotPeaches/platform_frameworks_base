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
package com.android.systemui.statusbar.notification.collection

import android.app.NotificationChannel
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow

/** Class to represent notifications bundled by classification. */
class BundleEntry(key: String) : PipelineEntry(key) {
    // TODO(b/394483200): move NotificationEntry's implementation to PipelineEntry?
    val isSensitive: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // TODO (b/389839319): implement the row
    val row: ExpandableNotificationRow? = null

    private val _children: MutableList<ListEntry> = ArrayList()
    val children: List<ListEntry> = Collections.unmodifiableList(_children)

    fun addChild(child: ListEntry) {
        _children.add(child)
    }

    fun clearChildren() {
        _children.clear()
    }

    /** @return Null because bundles do not have an associated NotificationEntry. */
    override fun getRepresentativeEntry(): NotificationEntry? {
        return null
    }

    override fun getParent(): PipelineEntry? {
        return null
    }

    override fun wasAttachedInPreviousPass(): Boolean {
        return false
    }

    companion object {
        val ROOT_BUNDLES: List<BundleEntry> =
            listOf(
                BundleEntry(NotificationChannel.PROMOTIONS_ID),
                BundleEntry(NotificationChannel.SOCIAL_MEDIA_ID),
                BundleEntry(NotificationChannel.NEWS_ID),
                BundleEntry(NotificationChannel.RECS_ID),
            )
    }
}
