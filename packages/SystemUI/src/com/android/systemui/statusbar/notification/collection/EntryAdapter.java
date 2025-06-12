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

import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender;
import com.android.systemui.statusbar.notification.icon.IconPack;
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;

import kotlinx.coroutines.flow.StateFlow;

/**
 * Adapter interface for UI to get relevant info.
 */
public interface EntryAdapter {

    /**
     * Returns the hash code of the backing entry
     */
    int getBackingHashCode();

    /**
     * Gets the parent of this entry, or null if the entry's view is not attached
     */
    @Nullable PipelineEntry getParent();

    /**
     * Returns whether the entry is attached and appears at the top level of the shade
     */
    boolean isTopLevelEntry();

    /**
     * @return the unique identifier for this entry
     */
    @NonNull String getKey();

    /**
     * Gets the view that this entry is backing.
     */
    @Nullable
    ExpandableNotificationRow getRow();

    /**
     * Whether this entry is the root of its collapsable 'group' - either a BundleEntry or a
     * notification group summary
     */
    boolean isGroupRoot();

    /**
     * @return whether the row can be removed with the 'Clear All' action
     */
    boolean isClearable();

    /**
     * Returns whether the entry is attached to the current shade list
     */
    default boolean isAttached() {
        return getParent() != null;
    }

    /**
     * Returns the target sdk of the package that owns this entry.
     */
    int getTargetSdk();

    /**
     * Returns the summarization for this entry, if there is one
     */
    @Nullable String getSummarization();

    /**
     * Performs any steps needed to set or reset data before an inflation or reinflation.
     */
    default void prepareForInflation() {}

    /**
     * Gets a color that would have sufficient contrast on the given background color.
     */
    int getContrastedColor(Context context, boolean isLowPriority, int backgroundColor);

    /**
     * Whether this entry can peek on screen as a heads up view
     */
    boolean canPeek();

    /**
     * Returns the visible 'time', in milliseconds, of the entry
     */
    long getWhen();

    /**
     * Retrieves the pack of icons associated with this entry
     */
    IconPack getIcons();

    /**
     * Returns whether the content of this entry is sensitive
     */
    StateFlow<Boolean> isSensitive();

    /**
     * Returns whether this row has a background color set by an app
     */
    boolean isColorized();

    /**
     * Returns the SBN that backs this row, if present
     */
    @Nullable
    StatusBarNotification getSbn();

    /**
     * Returns the ranking that backs this row, if present
     */
    @Nullable
    NotificationListenerService.Ranking getRanking();

    void endLifetimeExtension(
            @Nullable NotifLifetimeExtender.OnEndLifetimeExtensionCallback callback,
            @NonNull NotifLifetimeExtender extender);


    void onImportanceChanged();

    /**
     * Use when a change has been made to the underlying object that will both rerank the object
     * in the shade and change something about its appearance to delay the appearance change until
     * the ranking reordering is likely to have settled.
     */
    void markForUserTriggeredMovement();

    /**
     * Determines whether a row is considered 'high priority'.
     *
     * Notifications that are high priority are visible on the lock screen/status bar and in the top
     * section in the shade.
     */
    boolean isHighPriority();

    boolean isMarkedForUserTriggeredMovement();

    void setInlineControlsShown(boolean currentlyVisible);

    boolean isBlockable();

    boolean canDragAndDrop();

    boolean isBubble();

    @Nullable String getStyle();

    int getSectionBucket();

    boolean isAmbient();

    @PeopleNotificationIdentifier.Companion.PeopleNotificationType int getPeopleNotificationType();

    /**
     * Returns whether this row represents promoted ongoing notification.
     */
    boolean isPromotedOngoing();

    default boolean isFullScreenCapable() {
        return false;
    }

    void onDragSuccess();

    /**
     * Process a click on a notification bubble icon
     */
    void onNotificationBubbleIconClicked();

    /**
     * Processes a click on a notification action
     */
    void onNotificationActionClicked();

    NotificationEntry.DismissState getDismissState();

    void onEntryClicked(ExpandableNotificationRow row);

}

