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

package com.android.systemui.statusbar.notification.collection.coordinator;

import static android.app.NotificationManager.IMPORTANCE_MIN;

import android.app.Notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.statusbar.notification.collection.NotifPipeline;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.PipelineEntry;
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope;
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifComparator;
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifPromoter;
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifSectioner;
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi;
import com.android.systemui.statusbar.notification.promoted.domain.interactor.PromotedNotificationsInteractor;
import com.android.systemui.statusbar.notification.stack.NotificationPriorityBucketKt;
import com.android.systemui.util.kotlin.JavaAdapterKt;

import kotlinx.coroutines.CoroutineScope;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * Handles sectioning for foreground service notifications.
 * Puts non-min colorized foreground service notifications into the FGS section. See
 * {@link NotifCoordinators} for section ordering priority.
 */
@CoordinatorScope
public class ColorizedFgsCoordinator implements Coordinator {
    private static final String TAG = "ColorizedCoordinator";
    private final PromotedNotificationsInteractor mPromotedNotificationsInteractor;
    private final CoroutineScope mMainScope;

    private List<String> mOrderedPromotedNotifKeys = Collections.emptyList();

    @Inject
    public ColorizedFgsCoordinator(
            @Application CoroutineScope mainScope,
            PromotedNotificationsInteractor promotedNotificationsInteractor
    ) {
        mPromotedNotificationsInteractor = promotedNotificationsInteractor;
        mMainScope = mainScope;
    }

    @Override
    public void attach(@NonNull NotifPipeline pipeline) {
        if (PromotedNotificationUi.isEnabled()) {
            pipeline.addPromoter(mPromotedOngoingPromoter);

            JavaAdapterKt.collectFlow(mMainScope,
                    mPromotedNotificationsInteractor.getOrderedChipNotificationKeys(),
                    (List<String> keys) -> {
                        mOrderedPromotedNotifKeys = keys;
                        mNotifSectioner.invalidateList("updated mOrderedPromotedNotifKeys");
                    });
        }
    }

    public NotifSectioner getSectioner() {
        return mNotifSectioner;
    }

    private final NotifPromoter mPromotedOngoingPromoter = new NotifPromoter("PromotedOngoing") {
        @Override
        public boolean shouldPromoteToTopLevel(NotificationEntry child) {
            return isPromotedOngoing(child);
        }
    };

    /**
     * Puts colorized foreground service and call notifications into its own section.
     */
    private final NotifSectioner mNotifSectioner = new NotifSectioner("ColorizedSectioner",
            NotificationPriorityBucketKt.BUCKET_FOREGROUND_SERVICE) {
        @Override
        public boolean isInSection(PipelineEntry entry) {
            NotificationEntry notifEntry = entry.getRepresentativeEntry();
            if (notifEntry == null) {
                return false;
            }
            if (BundleUtil.Companion.isClassified(notifEntry)) {
                return false;
            }
            return isRichOngoing(notifEntry) || isPromotedNotifChip(notifEntry);
        }

        /** get the sort key for any entry in the ongoing section */
        private int getSortKey(@Nullable NotificationEntry entry) {
            if (entry == null) return Integer.MAX_VALUE;
            // Order all promoted notif keys first, using their order in the list
            final int index = mOrderedPromotedNotifKeys.indexOf(entry.getKey());
            if (index >= 0) return index;
            // Next, prioritize promoted ongoing over other notifications
            return isPromotedOngoing(entry) ? Integer.MAX_VALUE - 1 : Integer.MAX_VALUE;
        }

        private final NotifComparator mOngoingComparator = new NotifComparator(
                "OngoingComparator") {
            @Override
            public int compare(@NonNull PipelineEntry o1, @NonNull PipelineEntry o2) {
                return Integer.compare(
                        getSortKey(o1.getRepresentativeEntry()),
                        getSortKey(o2.getRepresentativeEntry())
                );
            }
        };

        @Nullable
        @Override
        public NotifComparator getComparator() {
            if (PromotedNotificationUi.isEnabled()) {
                return mOngoingComparator;
            } else {
                return null;
            }
        }
    };

    /** Determines if the given notification is a colorized or call notification */
    public static boolean isRichOngoing(NotificationEntry entry) {
        return isPromotedOngoing(entry) || isColorizedForegroundService(entry) || isCall(entry);
    }

    private static boolean isColorizedForegroundService(NotificationEntry entry) {
        Notification notification = entry.getSbn().getNotification();
        return notification.isForegroundService()
                && notification.isColorized()
                && entry.getImportance() > IMPORTANCE_MIN;
    }

    private static boolean isPromotedOngoing(NotificationEntry entry) {
        // NOTE: isPromotedOngoing already checks the android.app.ui_rich_ongoing flag.
        return entry != null && entry.getSbn().getNotification().isPromotedOngoing();
    }

    private static boolean isCall(NotificationEntry entry) {
        Notification notification = entry.getSbn().getNotification();
        return entry.getImportance() > IMPORTANCE_MIN
                && notification.isStyle(Notification.CallStyle.class);
    }

    private boolean isPromotedNotifChip(NotificationEntry entry) {
        return PromotedNotificationUi.isEnabled()
                && entry.getImportance() > IMPORTANCE_MIN
                && mOrderedPromotedNotifKeys.contains(entry.getKey());
    }
}
