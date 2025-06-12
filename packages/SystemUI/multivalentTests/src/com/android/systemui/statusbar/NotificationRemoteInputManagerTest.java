/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.statusbar;

import static com.android.systemui.log.LogBufferHelperKt.logcatLogBuffer;

import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;
import android.util.Pair;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.shade.domain.interactor.ShadeInteractor;
import com.android.systemui.statusbar.notification.NotifPipelineFlags;
import com.android.systemui.statusbar.notification.RemoteInputControllerLogger;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder;
import com.android.systemui.statusbar.notification.collection.render.NotificationVisibilityProvider;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationTestHelper;
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi;
import com.android.systemui.statusbar.policy.RemoteInputUriController;
import com.android.systemui.util.kotlin.JavaAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper
public class NotificationRemoteInputManagerTest extends SysuiTestCase {

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(NotificationBundleUi.FLAG_NAME);
    }

    private static final String TEST_PACKAGE_NAME = "test";
    private static final int TEST_UID = 0;

    @Mock private NotificationVisibilityProvider mVisibilityProvider;
    @Mock private RemoteInputController.Delegate mDelegate;
    @Mock private NotificationRemoteInputManager.Callback mCallback;
    @Mock private RemoteInputController mController;
    @Mock private SmartReplyController mSmartReplyController;
    @Mock private ExpandableNotificationRow mRow;
    @Mock private StatusBarStateController mStateController;
    @Mock private RemoteInputUriController mRemoteInputUriController;
    @Mock private NotificationClickNotifier mClickNotifier;
    @Mock private NotificationLockscreenUserManager mLockscreenUserManager;
    @Mock private PowerInteractor mPowerInteractor;
    @Mock
    NotificationRemoteInputManager.RemoteInputListener mRemoteInputListener;
    private ActionClickLogger mActionClickLogger;
    @Captor
    ArgumentCaptor<NotificationRemoteInputManager.ClickHandler> mClickHandlerArgumentCaptor;
    private Context mSpyContext;

    private NotificationTestHelper mTestHelper;
    private TestableNotificationRemoteInputManager mRemoteInputManager;
    private NotificationEntry mEntry;

   public NotificationRemoteInputManagerTest(FlagsParameterization flags) {
       super();
       mSetFlagsRule.setFlagsParameterization(flags);
   }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mSpyContext = spy(mContext);
        doNothing().when(mSpyContext).startIntentSender(
                any(), any(), anyInt(), anyInt(), anyInt(), any());


        mTestHelper = new NotificationTestHelper(mSpyContext, mDependency);
        mActionClickLogger = spy(new ActionClickLogger(logcatLogBuffer()));

        mRemoteInputManager = new TestableNotificationRemoteInputManager(mContext,
                mock(NotifPipelineFlags.class),
                mLockscreenUserManager,
                mSmartReplyController,
                mVisibilityProvider,
                mPowerInteractor,
                mStateController,
                mRemoteInputUriController,
                new RemoteInputControllerLogger(logcatLogBuffer()),
                mClickNotifier,
                mActionClickLogger,
                mock(JavaAdapter.class),
                mock(ShadeInteractor.class));
        mRemoteInputManager.setRemoteInputListener(mRemoteInputListener);
        mEntry = new NotificationEntryBuilder()
                .setPkg(TEST_PACKAGE_NAME)
                .setOpPkg(TEST_PACKAGE_NAME)
                .setUid(TEST_UID)
                .setNotification(new Notification())
                .setUser(UserHandle.CURRENT)
                .build();
        mEntry.setRow(mRow);

        mRemoteInputManager.setUpWithPresenterForTest(mCallback, mDelegate, mController);
    }

    @Test
    public void testShouldExtendLifetime_remoteInputActive() {
        when(mController.isRemoteInputActive(mEntry)).thenReturn(true);

        assertTrue(mRemoteInputManager.isRemoteInputActive(mEntry));
    }

    @Test
    public void testShouldExtendLifetime_isSpinning() {
        NotificationRemoteInputManager.FORCE_REMOTE_INPUT_HISTORY = true;
        when(mController.isSpinning(mEntry.getKey())).thenReturn(true);

        assertTrue(mRemoteInputManager.shouldKeepForRemoteInputHistory(mEntry));
    }

    @Test
    public void testShouldExtendLifetime_recentRemoteInput() {
        NotificationRemoteInputManager.FORCE_REMOTE_INPUT_HISTORY = true;
        mEntry.lastRemoteInputSent = SystemClock.elapsedRealtime();

        assertTrue(mRemoteInputManager.shouldKeepForRemoteInputHistory(mEntry));
    }

    @Test
    public void testShouldExtendLifetime_smartReplySending() {
        NotificationRemoteInputManager.FORCE_REMOTE_INPUT_HISTORY = true;
        when(mSmartReplyController.isSendingSmartReply(mEntry.getKey())).thenReturn(true);

        assertTrue(mRemoteInputManager.shouldKeepForSmartReplyHistory(mEntry));
    }

    @Test
    public void testActionClick() throws Exception {
        RemoteViews.RemoteResponse response = mock(RemoteViews.RemoteResponse.class);
        when(response.getLaunchOptions(any())).thenReturn(
                Pair.create(mock(Intent.class), mock(ActivityOptions.class)));
        ExpandableNotificationRow row = getRowWithReplyAction();
        View actionView = ((LinearLayout) row.getPrivateLayout().getExpandedChild().findViewById(
                com.android.internal.R.id.actions)).getChildAt(0);
        Notification n = getNotification(row);
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<NotificationEntry> consumer = notificationEntry -> latch.countDown();
        if (!NotificationBundleUi.isEnabled()) {
            mRemoteInputManager.addActionPressListener(consumer);
        }

        mRemoteInputManager.getRemoteViewsOnClickHandler().onInteraction(
                actionView,
                n.actions[0].actionIntent,
                response);

        verify(mActionClickLogger).logInitialClick(row.getKey(), 0, n.actions[0].actionIntent);
        verify(mClickNotifier).onNotificationActionClick(
                eq(row.getKey()), eq(0), eq(n.actions[0]), any(), eq(false));
        verify(mCallback).handleRemoteViewClick(eq(actionView), eq(n.actions[0].actionIntent),
                eq(false), eq(0), mClickHandlerArgumentCaptor.capture());

        mClickHandlerArgumentCaptor.getValue().handleClick();
        verify(mActionClickLogger).logStartingIntentWithDefaultHandler(
                row.getKey(), n.actions[0].actionIntent, 0);

        verify(mRemoteInputListener).releaseNotificationIfKeptForRemoteInputHistory(row.getKey());
        if (NotificationBundleUi.isEnabled()) {
            verify(row.getEntryAdapter()).onNotificationActionClicked();
        } else {
            latch.await(10, TimeUnit.MILLISECONDS);
        }
    }

    private Notification getNotification(ExpandableNotificationRow row) {
        if (NotificationBundleUi.isEnabled()) {
            return row.getEntryAdapter().getSbn().getNotification();
        } else {
            return row.getEntryLegacy().getSbn().getNotification();
        }
    }

    private ExpandableNotificationRow getRowWithReplyAction() throws Exception {
        PendingIntent pi = PendingIntent.getBroadcast(getContext(), 0, new Intent("Action"),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(mSpyContext, "")
                .setSmallIcon(com.android.systemui.res.R.drawable.ic_person)
                .addAction(new Notification.Action(com.android.systemui.res.R.drawable.ic_person,
                        "reply", pi))
                .build();
        ExpandableNotificationRow row = mTestHelper.createRow(n);
        row.onNotificationUpdated();
        row.getPrivateLayout().setExpandedChild(Notification.Builder.recoverBuilder(mSpyContext, n)
                .createBigContentView().apply(
                        mSpyContext,
                        row.getPrivateLayout(),
                        mRemoteInputManager.getRemoteViewsOnClickHandler()));
        return row;
    }

    private class TestableNotificationRemoteInputManager extends NotificationRemoteInputManager {

        TestableNotificationRemoteInputManager(
                Context context,
                NotifPipelineFlags notifPipelineFlags,
                NotificationLockscreenUserManager lockscreenUserManager,
                SmartReplyController smartReplyController,
                NotificationVisibilityProvider visibilityProvider,
                PowerInteractor powerInteractor,
                StatusBarStateController statusBarStateController,
                RemoteInputUriController remoteInputUriController,
                RemoteInputControllerLogger remoteInputControllerLogger,
                NotificationClickNotifier clickNotifier,
                ActionClickLogger actionClickLogger,
                JavaAdapter javaAdapter,
                ShadeInteractor shadeInteractor) {
            super(
                    context,
                    notifPipelineFlags,
                    lockscreenUserManager,
                    smartReplyController,
                    visibilityProvider,
                    powerInteractor,
                    statusBarStateController,
                    remoteInputUriController,
                    remoteInputControllerLogger,
                    clickNotifier,
                    actionClickLogger,
                    javaAdapter,
                    shadeInteractor);
        }

        public void setUpWithPresenterForTest(Callback callback,
                RemoteInputController.Delegate delegate,
                RemoteInputController controller) {
            super.setUpWithCallback(callback, delegate);
            mRemoteInputController = controller;
        }

    }
}

