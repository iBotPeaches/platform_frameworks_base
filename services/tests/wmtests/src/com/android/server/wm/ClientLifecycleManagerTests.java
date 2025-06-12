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

package com.android.server.wm;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;

import android.app.IApplicationThread;
import android.app.servertransaction.ActivityLifecycleItem;
import android.app.servertransaction.ClientTransaction;
import android.app.servertransaction.ClientTransactionItem;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Build/Install/Run:
 *  atest WmTests:ClientLifecycleManagerTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class ClientLifecycleManagerTests extends SystemServiceTestsBase {

    @Mock
    private IBinder mClientBinder;
    @Mock
    private IApplicationThread mClient;
    @Mock
    private ClientTransaction mTransaction;
    @Mock
    private ClientTransactionItem mTransactionItem;
    @Mock
    private ActivityLifecycleItem mLifecycleItem;

    private WindowManagerService mWms;
    private ClientLifecycleManager mLifecycleManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mWms = mSystemServicesTestRule.getWindowManagerService();
        mLifecycleManager = spy(new ClientLifecycleManager());
        mLifecycleManager.setWindowManager(mWms);

        doReturn(true).when(mLifecycleItem).isActivityLifecycleItem();
        doReturn(mClientBinder).when(mClient).asBinder();
    }

    @Test
    public void testScheduleTransactionItem() throws RemoteException {
        spyOn(mWms.mWindowPlacerLocked);
        doReturn(true).when(mWms.mWindowPlacerLocked).isTraversalScheduled();

        mLifecycleManager.scheduleTransactionItem(mClient, mTransactionItem);

        // When there is traversal scheduled, add transaction items to pending.
        assertEquals(1, mLifecycleManager.mPendingTransactions.size());
        ClientTransaction transaction = mLifecycleManager.mPendingTransactions.get(mClientBinder);
        assertEquals(1, transaction.getTransactionItems().size());
        assertEquals(mTransactionItem, transaction.getTransactionItems().get(0));
        // TODO(b/324203798): cleanup after remove UnsupportedAppUsage
        assertEquals(1, transaction.getCallbacks().size());
        assertEquals(mTransactionItem, transaction.getCallbacks().get(0));
        assertNull(transaction.getLifecycleStateRequest());
        verify(mLifecycleManager, never()).scheduleTransaction(any());

        // Add new transaction item to the existing pending.
        clearInvocations(mLifecycleManager);
        mLifecycleManager.scheduleTransactionItem(mClient, mLifecycleItem);

        assertEquals(1, mLifecycleManager.mPendingTransactions.size());
        transaction = mLifecycleManager.mPendingTransactions.get(mClientBinder);
        assertEquals(2, transaction.getTransactionItems().size());
        assertEquals(mTransactionItem, transaction.getTransactionItems().get(0));
        assertEquals(mLifecycleItem, transaction.getTransactionItems().get(1));
        // TODO(b/324203798): cleanup after remove UnsupportedAppUsage
        assertEquals(1, transaction.getCallbacks().size());
        assertEquals(mTransactionItem, transaction.getCallbacks().get(0));
        assertEquals(mLifecycleItem, transaction.getLifecycleStateRequest());
        verify(mLifecycleManager, never()).scheduleTransaction(any());
    }

    @Test
    public void testScheduleTransactionItemNow() throws RemoteException {
        mLifecycleManager.scheduleTransactionItemNow(mClient, mTransactionItem);

        // Dispatch immediately.
        assertTrue(mLifecycleManager.mPendingTransactions.isEmpty());
        verify(mLifecycleManager).scheduleTransaction(any());
    }

    @Test
    public void testScheduleTransactionItems() throws RemoteException {
        spyOn(mWms.mWindowPlacerLocked);
        doReturn(true).when(mWms.mWindowPlacerLocked).isTraversalScheduled();

        mLifecycleManager.scheduleTransactionItems(mClient, mTransactionItem, mLifecycleItem);

        assertEquals(1, mLifecycleManager.mPendingTransactions.size());
        final ClientTransaction transaction =
                mLifecycleManager.mPendingTransactions.get(mClientBinder);
        assertEquals(2, transaction.getTransactionItems().size());
        assertEquals(mTransactionItem, transaction.getTransactionItems().get(0));
        assertEquals(mLifecycleItem, transaction.getTransactionItems().get(1));
        // TODO(b/324203798): cleanup after remove UnsupportedAppUsage
        assertEquals(1, transaction.getCallbacks().size());
        assertEquals(mTransactionItem, transaction.getCallbacks().get(0));
        assertEquals(mLifecycleItem, transaction.getLifecycleStateRequest());
        verify(mLifecycleManager, never()).scheduleTransaction(any());
    }

    @Test
    public void testScheduleTransactionItems_shouldDispatchImmediately()
            throws RemoteException {
        spyOn(mWms.mWindowPlacerLocked);
        doReturn(true).when(mWms.mWindowPlacerLocked).isTraversalScheduled();

        mLifecycleManager.scheduleTransactionItems(
                mClient,
                true /* shouldDispatchImmediately */,
                mTransactionItem, mLifecycleItem);

        verify(mLifecycleManager).scheduleTransaction(any());
        assertTrue(mLifecycleManager.mPendingTransactions.isEmpty());
    }

    @Test
    public void testDispatchPendingTransactions() throws RemoteException {
        mLifecycleManager.mPendingTransactions.put(mClientBinder, mTransaction);

        mLifecycleManager.dispatchPendingTransactions();

        assertTrue(mLifecycleManager.mPendingTransactions.isEmpty());
        verify(mTransaction).schedule();
    }

    @Test
    public void testLayoutDeferred() throws RemoteException {
        spyOn(mWms.mWindowPlacerLocked);
        doReturn(false).when(mWms.mWindowPlacerLocked).isInLayout();
        doReturn(false).when(mWms.mWindowPlacerLocked).isTraversalScheduled();
        doReturn(true).when(mWms.mWindowPlacerLocked).isLayoutDeferred();

        // Queue transactions during layout deferred.
        mLifecycleManager.scheduleTransactionItem(mClient, mLifecycleItem);

        verify(mLifecycleManager, never()).scheduleTransaction(any());

        // Continue queueing when there are multi-level defer.
        mLifecycleManager.onLayoutContinued();

        verify(mLifecycleManager, never()).scheduleTransaction(any());

        // Immediately dispatch when layout continue without ongoing/scheduled layout.
        doReturn(false).when(mWms.mWindowPlacerLocked).isLayoutDeferred();

        mLifecycleManager.onLayoutContinued();

        verify(mLifecycleManager).scheduleTransaction(any());
    }

    @EnableFlags(Flags.FLAG_CLEANUP_DISPATCH_PENDING_TRANSACTIONS_REMOTE_EXCEPTION)
    @Test
    public void testOnRemoteException_returnTrueOnSuccess() throws RemoteException {
        final boolean res = mLifecycleManager.scheduleTransactionItemNow(mClient, mTransactionItem);

        assertTrue(res);
    }

    @EnableFlags(Flags.FLAG_CLEANUP_DISPATCH_PENDING_TRANSACTIONS_REMOTE_EXCEPTION)
    @Test
    public void testOnRemoteException_returnFalseOnFailure() throws RemoteException {
        final DeadObjectException e = new DeadObjectException();
        doThrow(e).when(mClient).scheduleTransaction(any());

        // No exception when flag enabled.
        final boolean res = mLifecycleManager.scheduleTransactionItemNow(mClient, mTransactionItem);

        assertFalse(res);
    }

    @EnableFlags(Flags.FLAG_CLEANUP_DISPATCH_PENDING_TRANSACTIONS_REMOTE_EXCEPTION)
    @Test
    public void testOnRemoteException_returnTrueForQueueing() throws RemoteException {
        spyOn(mWms.mWindowPlacerLocked);
        doReturn(true).when(mWms.mWindowPlacerLocked).isLayoutDeferred();
        final DeadObjectException e = new DeadObjectException();
        doThrow(e).when(mClient).scheduleTransaction(any());

        final boolean res = mLifecycleManager.scheduleTransactionItem(mClient, mTransactionItem);

        assertTrue(res);

        doReturn(false).when(mWms.mWindowPlacerLocked).isLayoutDeferred();
        mLifecycleManager.onLayoutContinued();

        verify(mLifecycleManager).scheduleTransaction(any());
    }
}
