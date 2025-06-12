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

package com.android.systemui.statusbar.notification.collection;

import static com.android.systemui.statusbar.notification.stack.NotificationPriorityBucketKt.BUCKET_ALERTING;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.statusbar.notification.collection.listbuilder.NotifSection;
import com.android.systemui.statusbar.notification.stack.PriorityBucket;

/**
 * Class to represent a notification, group, or bundle in the pipeline.
 */
public abstract class PipelineEntry {

    final String mKey;
    final ListAttachState mAttachState = ListAttachState.create();
    final ListAttachState mPreviousAttachState = ListAttachState.create();
    protected int mBucket = BUCKET_ALERTING;

    public PipelineEntry(String key) {
        this.mKey = key;
    }

    /**
     * Key of the representative entry.
     */
    public @NonNull String getKey() {
        return mKey;
    }

    /**
     * @return The representative NotificationEntry:
     *      for NotificationEntry, return itself
     *      for GroupEntry, return the summary NotificationEntry, or null if it does not exist
     *      for BundleEntry, return null
     */
    public abstract @Nullable NotificationEntry getRepresentativeEntry();

    /**
     * @return NotifSection that ShadeListBuilder assigned to this PipelineEntry.
     */
    @Nullable public NotifSection getSection() {
        return mAttachState.getSection();
    }

    /**
     * @return True if this entry was attached in the last pass, else false.
     */
    public boolean wasAttachedInPreviousPass() {
        return getPreviousAttachState().getParent() != null;
    }

    /**
     * @return Index of section assigned to this entry.
     */
    public int getSectionIndex() {
        return mAttachState.getSection() != null ? mAttachState.getSection().getIndex() : -1;
    }

    /**
     * @return Parent PipelineEntry
     */
    public abstract @Nullable PipelineEntry getParent();

    /**
     * @return Current state that ShadeListBuilder assigned to this PipelineEntry.
     */
    final ListAttachState getAttachState() {
        return mAttachState;
    }

    /**
     * @return Previous state that ShadeListBuilder assigned to this PipelineEntry.
     */
    final ListAttachState getPreviousAttachState() {
        return mPreviousAttachState;
    }

    @PriorityBucket
    public int getBucket() {
        return mBucket;
    }

    public void setBucket(@PriorityBucket int bucket) {
        mBucket = bucket;
    }

    /**
     * Stores the current attach state into {@link #getPreviousAttachState()}} and then starts a
     * fresh attach state (all entries will be null/default-initialized).
     */
    void beginNewAttachState() {
        mPreviousAttachState.clone(mAttachState);
        mAttachState.reset();
    }
}
