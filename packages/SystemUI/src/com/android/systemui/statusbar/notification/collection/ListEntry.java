/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection;


import android.annotation.UptimeMillisLong;

import androidx.annotation.Nullable;

/**
 * Abstract superclass for top-level entries, i.e. things that can appear in the final notification
 * list shown to users. In practice, this means either GroupEntries or NotificationEntries.
 */
public abstract class ListEntry extends PipelineEntry {
    private final long mCreationTime;

    protected ListEntry(String key, long creationTime) {
        super(key);
        mCreationTime = creationTime;
    }

    /**
     * The SystemClock.elapsedRealtime() when this object was created. In general, this means the
     * moment when NotificationManager notifies our listener about the existence of this entry.
     *
     * This value will not change if the notification is updated, although it will change if the
     * notification is removed and then re-posted. It is also wholly independent from
     * Notification#when.
     */
    @UptimeMillisLong
    public long getCreationTime() {
        return mCreationTime;
    }

    /**
     * Should return the "representative entry" for this ListEntry. For NotificationEntries, its
     * the entry itself. For groups, it should be the summary (but if a summary doesn't exist,
     * this can return null). This method exists to interface with
     * legacy code that expects groups to also be NotificationEntries.
     */
    public abstract @Nullable NotificationEntry getRepresentativeEntry();

    @Nullable public PipelineEntry getParent() {
        return mAttachState.getParent();
    }

    void setParent(@Nullable PipelineEntry parent) {
        mAttachState.setParent(parent);
    }

    @Nullable public PipelineEntry getPreviousParent() {
        return mPreviousAttachState.getParent();
    }
}
