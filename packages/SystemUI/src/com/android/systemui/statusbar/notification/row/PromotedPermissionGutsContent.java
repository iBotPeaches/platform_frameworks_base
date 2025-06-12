/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.res.R;

/**
 * This GutsContent shows an explanatory interstitial telling the user they've just revoked this
 * app's permission to post Promoted/Live notifications.
 * If the guts are dismissed without further action, the revocation is committed.
 * If the user hits undo, the permission is not revoked.
 */
public class PromotedPermissionGutsContent extends LinearLayout
        implements NotificationGuts.GutsContent, View.OnClickListener {

    private static final String TAG = "SnoozyPromotedGuts";

    private NotificationGuts mGutsContainer;
    private StatusBarNotification mSbn;

    private TextView mUndoButton;

    private MetricsLogger mMetricsLogger = new MetricsLogger();
    private OnClickListener mDemoteAction;

    public PromotedPermissionGutsContent(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mUndoButton = (TextView) findViewById(R.id.undo);
        mUndoButton.setOnClickListener(this);
        mUndoButton.setContentDescription(
                getContext().getString(R.string.snooze_undo_content_description));

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dispatchConfigurationChanged(getResources().getConfiguration());
    }

    /**
     * Update the content description of the snooze view based on the snooze option and whether the
     * snooze options are expanded or not.
     * For example, this will be something like "Collapsed\u2029Snooze for 1 hour". The paragraph
     * separator is added to introduce a break in speech, to match what TalkBack does by default
     * when you e.g. press on a notification.
     */
    private void updateContentDescription() {
        //
    }


    @Override
    public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        if (super.performAccessibilityActionInternal(action, arguments)) {
            return true;
        }
        if (action == R.id.action_snooze_undo) {
            undoDemote(mUndoButton);
            return true;
        }
        return false;
    }

    /**
     * TODO docs
     * @param sbn
     */
    public void setStatusBarNotification(StatusBarNotification sbn) {
        mSbn = sbn;
        TextView demoteExplanation = (TextView) findViewById(R.id.demote_explain);
        demoteExplanation.setText(mContext.getResources().getString(R.string.demote_explain_text,
                mSbn.getPackageName()));
    }

    @Override
    public void onClick(View v) {
        if (mGutsContainer != null) {
            mGutsContainer.resetFalsingCheck();
        }
        final int id = v.getId();
        if (id == R.id.undo) {
            undoDemote(v);
        }

    }

    private void undoDemote(View v) {
        // Don't commit the demote action, instead log the undo and dismiss the view.
        mGutsContainer.closeControls(v, /* save= */ false);
    }

    @Override
    public int getActualHeight() {
        return getHeight();
    }

    @Override
    public boolean willBeRemoved() {
        return false;
    }

    @Override
    public View getContentView() {
        return this;
    }

    @Override
    public void setGutsParent(NotificationGuts guts) {
        mGutsContainer = guts;
    }

    @Override
    public boolean handleCloseControls(boolean save, boolean force) {
        if (!save) {
            // Undo changes and let the guts handle closing the view
            return false;
        } else {
            // Commit demote action.
            mDemoteAction.onClick(this);
            return false;
        }
    }

    @Override
    public boolean isLeavebehind() {
        return true;
    }

    @Override
    public boolean shouldBeSavedOnClose() {
        return true;
    }

    @Override
    public boolean needsFalsingProtection() {
        return false;
    }

    public void setOnDemoteAction(OnClickListener demoteAction) {
        mDemoteAction = demoteAction;
    }

}
