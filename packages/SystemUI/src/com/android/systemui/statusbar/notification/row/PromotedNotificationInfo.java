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

package com.android.systemui.statusbar.notification.row;

import android.app.INotificationManager;
import android.app.NotificationChannel;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.notification.AssistantFeedbackController;
import com.android.systemui.statusbar.notification.collection.EntryAdapter;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.promoted.domain.interactor.PackageDemotionInteractor;
import com.android.systemui.statusbar.notification.row.icon.AppIconProvider;
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider;

/**
 * The guts of a notification revealed when performing a long press, specifically
 * for notifications that are shown as promoted. Contains extra controls to allow user to revoke
 * app permissions for sending promoted notifications.
 */
public class PromotedNotificationInfo extends NotificationInfo {
    private static final String TAG = "PromotedNotifInfoGuts";
    private INotificationManager mNotificationManager;
    private PackageDemotionInteractor mPackageDemotionInteractor;
    private NotificationGuts mGutsContainer;

    public PromotedNotificationInfo(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void bindNotification(
            PackageManager pm,
            INotificationManager iNotificationManager,
            AppIconProvider appIconProvider,
            NotificationIconStyleProvider iconStyleProvider,
            OnUserInteractionCallback onUserInteractionCallback,
            ChannelEditorDialogController channelEditorDialogController,
            PackageDemotionInteractor packageDemotionInteractor,
            String pkg,
            NotificationListenerService.Ranking ranking,
            StatusBarNotification sbn,
            NotificationEntry entry,
            EntryAdapter entryAdapter,
            OnSettingsClickListener onSettingsClick,
            OnAppSettingsClickListener onAppSettingsClick,
            OnFeedbackClickListener feedbackClickListener,
            UiEventLogger uiEventLogger,
            boolean isDeviceProvisioned,
            boolean isNonblockable,
            boolean isDismissable,
            boolean wasShownHighPriority,
            AssistantFeedbackController assistantFeedbackController,
            MetricsLogger metricsLogger, OnClickListener onCloseClick) throws RemoteException {
        super.bindNotification(pm, iNotificationManager, appIconProvider, iconStyleProvider,
                onUserInteractionCallback, channelEditorDialogController,
                 packageDemotionInteractor,pkg, ranking, sbn,
                entry, entryAdapter, onSettingsClick, onAppSettingsClick, feedbackClickListener,
                uiEventLogger, isDeviceProvisioned, isDismissable, isNonblockable,
                wasShownHighPriority, assistantFeedbackController, metricsLogger, onCloseClick);

        mNotificationManager = iNotificationManager;

        mPackageDemotionInteractor = packageDemotionInteractor;

        bindDemote(sbn, pkg);
    }

    protected void bindDemote(StatusBarNotification sbn, String packageName) {
        View demoteButton = findViewById(R.id.promoted_demote);
        demoteButton.setOnClickListener(getDemoteClickListener(sbn, packageName));
        demoteButton.setVisibility(demoteButton.hasOnClickListeners() ? VISIBLE : GONE);
    }

    @Override
    public void setGutsParent(NotificationGuts guts) {
        mGutsContainer = guts;
        super.setGutsParent(guts);
    }

    private OnClickListener getDemoteClickListener(StatusBarNotification sbn, String packageName) {
        return ((View v) -> {
            try {
                mNotificationManager.setCanBePromoted(packageName, sbn.getUid(), false, true);
                mPackageDemotionInteractor.onPackageDemoted(packageName, sbn.getUid());
                mGutsContainer.closeControls(v, true);
            } catch (RemoteException e) {
                Log.e(TAG, "Couldn't revoke live update permission", e);
            }
        });
    }
}

