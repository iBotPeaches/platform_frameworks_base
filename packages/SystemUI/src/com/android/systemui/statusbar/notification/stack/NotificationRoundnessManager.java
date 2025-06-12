/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.stack;

import androidx.annotation.NonNull;

import com.android.systemui.Dumpable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.statusbar.notification.Roundable;
import com.android.systemui.statusbar.notification.SourceType;
import com.android.systemui.statusbar.notification.row.ExpandableView;

import java.io.PrintWriter;
import java.util.HashSet;

import javax.inject.Inject;

/**
 * A class that manages the roundness for notification views
 */
@SysUISingleton
public class NotificationRoundnessManager implements Dumpable {

    private static final String TAG = "NotificationRoundnessManager";
    private static final SourceType DISMISS_ANIMATION = SourceType.from("DismissAnimation");

    private final DumpManager mDumpManager;
    private HashSet<ExpandableView> mAnimatedChildren;
    private boolean mRoundForPulsingViews;
    private boolean mIsClearAllInProgress;

    private ExpandableView mSwipedView = null;
    private Roundable mViewBeforeSwipedView = null;
    private Roundable mViewAfterSwipedView = null;

    @Inject
    NotificationRoundnessManager(DumpManager dumpManager) {
        mDumpManager = dumpManager;
        mDumpManager.registerDumpable(TAG, this);
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        pw.println("roundForPulsingViews=" + mRoundForPulsingViews);
        pw.println("isClearAllInProgress=" + mIsClearAllInProgress);
    }

    public boolean isViewAffectedBySwipe(ExpandableView expandableView) {
        return expandableView != null
                && (expandableView == mSwipedView
                || expandableView == mViewBeforeSwipedView
                || expandableView == mViewAfterSwipedView);
    }

    void setViewsAffectedBySwipe(
            Roundable viewBefore,
            ExpandableView viewSwiped,
            Roundable viewAfter) {
        // This method caches a new set of current View targets and reset the roundness of the old
        // View targets (if any) to 0f.
        HashSet<Roundable> oldViews = new HashSet<>();
        if (mViewBeforeSwipedView != null) oldViews.add(mViewBeforeSwipedView);
        if (mSwipedView != null) oldViews.add(mSwipedView);
        if (mViewAfterSwipedView != null) oldViews.add(mViewAfterSwipedView);

        mViewBeforeSwipedView = viewBefore;
        if (viewBefore != null) {
            oldViews.remove(viewBefore);
        }

        mSwipedView = viewSwiped;
        if (viewSwiped != null) {
            oldViews.remove(viewSwiped);
        }

        mViewAfterSwipedView = viewAfter;
        if (viewAfter != null) {
            oldViews.remove(viewAfter);
        }

        // After setting the current Views, reset the views that are still present in the set.
        for (Roundable oldView : oldViews) {
            oldView.requestRoundnessReset(DISMISS_ANIMATION);
        }
    }

    void setRoundnessForAffectedViews(float roundness) {
        if (mViewBeforeSwipedView != null) {
            mViewBeforeSwipedView.requestBottomRoundness(roundness, DISMISS_ANIMATION);
        }

        if (mSwipedView != null) {
            mSwipedView.requestRoundness(roundness, roundness, DISMISS_ANIMATION);
        }

        if (mViewAfterSwipedView != null) {
            mViewAfterSwipedView.requestTopRoundness(roundness, DISMISS_ANIMATION);
        }
    }

    void setRoundnessForAffectedViews(float roundness, boolean animate) {
        if (mViewBeforeSwipedView != null) {
            mViewBeforeSwipedView.requestBottomRoundness(roundness, DISMISS_ANIMATION, animate);
        }

        if (mSwipedView != null) {
            mSwipedView.requestRoundness(roundness, roundness, DISMISS_ANIMATION, animate);
        }

        if (mViewAfterSwipedView != null) {
            mViewAfterSwipedView.requestTopRoundness(roundness, DISMISS_ANIMATION, animate);
        }
    }

    void setClearAllInProgress(boolean isClearingAll) {
        mIsClearAllInProgress = isClearingAll;
    }

    /**
     * Check if "Clear all" notifications is in progress.
     */
    public boolean isClearAllInProgress() {
        return mIsClearAllInProgress;
    }

    /**
     * Check if we can request the `Pulsing` roundness for notification.
     */
    public boolean shouldRoundNotificationPulsing() {
        return mRoundForPulsingViews;
    }

    public void setAnimatedChildren(HashSet<ExpandableView> animatedChildren) {
        mAnimatedChildren = animatedChildren;
    }

    /**
     * Check if the view should be animated
     * @param view target view
     * @return true, if is in the AnimatedChildren set
     */
    public boolean isAnimatedChild(ExpandableView view) {
        return mAnimatedChildren.contains(view);
    }

    public void setShouldRoundPulsingViews(boolean shouldRoundPulsingViews) {
        mRoundForPulsingViews = shouldRoundPulsingViews;
    }

    public boolean isSwipedViewSet() {
        return mSwipedView != null;
    }
}
