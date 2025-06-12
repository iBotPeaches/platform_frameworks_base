/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection.render;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.systemui.statusbar.notification.collection.EntryAdapter;
import com.android.systemui.statusbar.notification.collection.ListEntry;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.PipelineEntry;

import java.util.List;

/**
 * Helper that determines the group states (parent, summary, children) of a notification. This
 * generally assumes that the notification is attached (aka its parent is not null).
 */
public interface GroupMembershipManager {

    /**
     * @return whether a given entry is the root (GroupEntry or BundleEntry) in a collection which
     * has children
     */
    boolean isGroupRoot(@NonNull EntryAdapter entry);

    /**
     * @return whether a given notification is the summary in a group which has children
     */
    boolean isGroupSummary(@NonNull NotificationEntry entry);

    /**
     * Get the summary of a specified status bar notification. For an isolated notification this
     * returns null, but if called directly on a summary it returns itself.
     */
    @Nullable
    NotificationEntry getGroupSummary(@NonNull NotificationEntry entry);

    /**
     * @return whether a given notification is a child in a group
     */
    boolean isChildInGroup(@NonNull NotificationEntry entry);

    /**
     * @return whether a given notification is a child in a group. The group may be a notification
     * group or a bundle.
     */
    boolean isChildInGroup(@NonNull EntryAdapter entry);

    /**
     * Get the children that are in the summary's group, not including those isolated.
     *
     * @param summary summary of a group
     * @return list of the children
     */
    @Nullable
    List<NotificationEntry> getChildren(@NonNull PipelineEntry summary);
}
