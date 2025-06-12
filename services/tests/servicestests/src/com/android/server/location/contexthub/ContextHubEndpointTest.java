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

package com.android.server.location.contexthub;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.contexthub.EndpointInfo;
import android.hardware.contexthub.ErrorCode;
import android.hardware.contexthub.HubEndpoint;
import android.hardware.contexthub.HubEndpointInfo;
import android.hardware.contexthub.HubEndpointInfo.HubEndpointIdentifier;
import android.hardware.contexthub.HubMessage;
import android.hardware.contexthub.IContextHubEndpoint;
import android.hardware.contexthub.IContextHubEndpointCallback;
import android.hardware.contexthub.IEndpointCommunication;
import android.hardware.contexthub.Message;
import android.hardware.contexthub.MessageDeliveryStatus;
import android.hardware.contexthub.Reason;
import android.hardware.location.IContextHubTransactionCallback;
import android.hardware.location.NanoAppState;
import android.os.Binder;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

@RunWith(AndroidJUnit4.class)
@Presubmit
public class ContextHubEndpointTest {
    private static final String TAG = "ContextHubEndpointTest";

    private static final int SESSION_ID_RANGE = ContextHubEndpointManager.SERVICE_SESSION_RANGE;
    private static final int MIN_SESSION_ID = 0;
    private static final int MAX_SESSION_ID = MIN_SESSION_ID + SESSION_ID_RANGE - 1;
    private static final int SESSION_ID_FOR_OPEN_REQUEST = MAX_SESSION_ID + 1;
    private static final int INVALID_SESSION_ID_FOR_OPEN_REQUEST = MIN_SESSION_ID + 1;

    private static final String ENDPOINT_NAME = "Example test endpoint";
    private static final int ENDPOINT_ID = 1;
    private static final String ENDPOINT_PACKAGE_NAME = "com.android.server.location.contexthub";

    private static final String TARGET_ENDPOINT_NAME = "Example target endpoint";
    private static final String ENDPOINT_SERVICE_DESCRIPTOR = "serviceDescriptor";
    private static final int TARGET_ENDPOINT_ID = 1;

    private static final int SAMPLE_MESSAGE_TYPE = 1234;
    private static final HubMessage SAMPLE_MESSAGE =
            new HubMessage.Builder(SAMPLE_MESSAGE_TYPE, new byte[] {1, 2, 3, 4, 5})
                    .setResponseRequired(true)
                    .build();
    private static final HubMessage SAMPLE_UNRELIABLE_MESSAGE =
            new HubMessage.Builder(SAMPLE_MESSAGE_TYPE, new byte[] {1, 2, 3, 4, 5})
                    .setResponseRequired(false)
                    .build();

    private ContextHubClientManager mClientManager;
    private ContextHubEndpointManager mEndpointManager;
    private HubInfoRegistry mHubInfoRegistry;
    private ContextHubTransactionManager mTransactionManager;
    private Context mContext;
    @Mock private ScheduledExecutorService mMockTimeoutExecutorService;
    @Mock private IEndpointCommunication mMockEndpointCommunications;
    @Mock private IContextHubWrapper mMockContextHubWrapper;
    @Mock private IContextHubEndpointCallback mMockCallback;
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Before
    public void setUp() throws RemoteException, InstantiationException {
        when(mMockContextHubWrapper.getHubs()).thenReturn(Collections.emptyList());
        when(mMockContextHubWrapper.getEndpoints()).thenReturn(Collections.emptyList());
        when(mMockContextHubWrapper.registerEndpointHub(any(), any()))
                .thenReturn(mMockEndpointCommunications);
        when(mMockEndpointCommunications.requestSessionIdRange(SESSION_ID_RANGE))
                .thenReturn(new int[] {MIN_SESSION_ID, MAX_SESSION_ID});
        when(mMockCallback.asBinder()).thenReturn(new Binder());

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mHubInfoRegistry = new HubInfoRegistry(mContext, mMockContextHubWrapper);
        mClientManager = new ContextHubClientManager(mContext, mMockContextHubWrapper);
        mTransactionManager =
                new ContextHubTransactionManager(
                        mMockContextHubWrapper, mClientManager, new NanoAppStateManager());
        mEndpointManager =
                new ContextHubEndpointManager(
                        mContext,
                        mMockContextHubWrapper,
                        mHubInfoRegistry,
                        mTransactionManager,
                        mMockTimeoutExecutorService);
        mEndpointManager.init();
    }

    @Test
    public void testRegisterEndpoint() throws RemoteException {
        IContextHubEndpoint endpoint = registerExampleEndpoint();
        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testReserveSessionId() {
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);

        int sessionId = mEndpointManager.reserveSessionId();
        assertThat(sessionId).isAtLeast(MIN_SESSION_ID);
        assertThat(sessionId).isAtMost(MAX_SESSION_ID);
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE - 1);

        mEndpointManager.returnSessionId(sessionId);
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
    }

    @Test
    public void testInvalidMessageReceivedCallback() throws RemoteException {
        // Send an invalid call to onMessageReceived (no sessions created)
        final int messageType = 1234;
        final int sessionId = 4321;
        final int sequenceNumber = 5678;
        HubMessage message =
                new HubMessage.Builder(messageType, new byte[0]).setResponseRequired(true).build();
        message.setMessageSequenceNumber(sequenceNumber);
        mEndpointManager.onMessageReceived(sessionId, message);

        // Confirm that we can get a delivery status with DESTINATION_NOT_FOUND error
        ArgumentCaptor<MessageDeliveryStatus> statusCaptor =
                ArgumentCaptor.forClass(MessageDeliveryStatus.class);
        verify(mMockEndpointCommunications)
                .sendMessageDeliveryStatusToEndpoint(eq(sessionId), statusCaptor.capture());
        assertThat(statusCaptor.getValue().messageSequenceNumber).isEqualTo(sequenceNumber);
        assertThat(statusCaptor.getValue().errorCode).isEqualTo(ErrorCode.DESTINATION_NOT_FOUND);
    }

    @Test
    public void testHalRestart() throws RemoteException {
        IContextHubEndpoint endpoint = registerExampleEndpoint();

        // Verify that the endpoint is still registered after a HAL restart
        HubEndpointInfo assignedInfo = endpoint.getAssignedHubEndpointInfo();
        HubEndpointIdentifier assignedIdentifier = assignedInfo.getIdentifier();
        mEndpointManager.onHalRestart();
        ArgumentCaptor<EndpointInfo> statusCaptor = ArgumentCaptor.forClass(EndpointInfo.class);
        verify(mMockEndpointCommunications, times(2)).registerEndpoint(statusCaptor.capture());
        assertThat(statusCaptor.getValue().id.id).isEqualTo(assignedIdentifier.getEndpoint());
        assertThat(statusCaptor.getValue().id.hubId).isEqualTo(assignedIdentifier.getHub());

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testHalRestartOnOpenSession() throws RemoteException {
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
        IContextHubEndpoint endpoint = registerExampleEndpoint();

        HubEndpointInfo targetInfo =
                new HubEndpointInfo(
                        TARGET_ENDPOINT_NAME,
                        TARGET_ENDPOINT_ID,
                        ENDPOINT_PACKAGE_NAME,
                        Collections.emptyList());
        int sessionId = endpoint.openSession(targetInfo, /* serviceDescriptor= */ null);
        mEndpointManager.onEndpointSessionOpenComplete(sessionId);
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE - 1);

        mEndpointManager.onHalRestart();

        HubEndpointInfo assignedInfo = endpoint.getAssignedHubEndpointInfo();
        HubEndpointIdentifier assignedIdentifier = assignedInfo.getIdentifier();
        ArgumentCaptor<EndpointInfo> statusCaptor = ArgumentCaptor.forClass(EndpointInfo.class);
        verify(mMockEndpointCommunications, times(2)).registerEndpoint(statusCaptor.capture());
        assertThat(statusCaptor.getValue().id.id).isEqualTo(assignedIdentifier.getEndpoint());
        assertThat(statusCaptor.getValue().id.hubId).isEqualTo(assignedIdentifier.getHub());

        verify(mMockCallback)
                .onSessionClosed(
                        sessionId, ContextHubServiceUtil.toAppHubEndpointReason(Reason.HUB_RESET));

        unregisterExampleEndpoint(endpoint);
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
    }

    @Test
    public void testOpenSessionOnUnregistration() throws RemoteException {
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
        IContextHubEndpoint endpoint = registerExampleEndpoint();

        HubEndpointInfo targetInfo =
                new HubEndpointInfo(
                        TARGET_ENDPOINT_NAME,
                        TARGET_ENDPOINT_ID,
                        ENDPOINT_PACKAGE_NAME,
                        Collections.emptyList());
        int sessionId = endpoint.openSession(targetInfo, /* serviceDescriptor= */ null);
        mEndpointManager.onEndpointSessionOpenComplete(sessionId);
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE - 1);

        unregisterExampleEndpoint(endpoint);
        verify(mMockEndpointCommunications).closeEndpointSession(sessionId, Reason.ENDPOINT_GONE);
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
    }

    @Test
    public void testEndpointSessionOpenRequest() throws RemoteException {
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
        IContextHubEndpoint endpoint = registerExampleEndpoint();

        HubEndpointInfo targetInfo =
                new HubEndpointInfo(
                        TARGET_ENDPOINT_NAME,
                        TARGET_ENDPOINT_ID,
                        ENDPOINT_PACKAGE_NAME,
                        Collections.emptyList());
        mHubInfoRegistry.onEndpointStarted(new HubEndpointInfo[] {targetInfo});
        mEndpointManager.onEndpointSessionOpenRequest(
                SESSION_ID_FOR_OPEN_REQUEST,
                endpoint.getAssignedHubEndpointInfo().getIdentifier(),
                targetInfo.getIdentifier(),
                ENDPOINT_SERVICE_DESCRIPTOR);
        verify(mMockCallback)
                .onSessionOpenRequest(
                        SESSION_ID_FOR_OPEN_REQUEST, targetInfo, ENDPOINT_SERVICE_DESCRIPTOR);

        // Accept
        endpoint.openSessionRequestComplete(SESSION_ID_FOR_OPEN_REQUEST);

        // Even when timeout happens, there should be no effect on this session
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockTimeoutExecutorService)
                .schedule(runnableArgumentCaptor.capture(), anyLong(), any());
        runnableArgumentCaptor.getValue().run();

        verify(mMockEndpointCommunications, times(1))
                .endpointSessionOpenComplete(SESSION_ID_FOR_OPEN_REQUEST);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testEndpointSessionOpenRequestWithInvalidSessionId() throws RemoteException {
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
        IContextHubEndpoint endpoint = registerExampleEndpoint();

        HubEndpointInfo targetInfo =
                new HubEndpointInfo(
                        TARGET_ENDPOINT_NAME,
                        TARGET_ENDPOINT_ID,
                        ENDPOINT_PACKAGE_NAME,
                        Collections.emptyList());
        mHubInfoRegistry.onEndpointStarted(new HubEndpointInfo[] {targetInfo});
        mEndpointManager.onEndpointSessionOpenRequest(
                INVALID_SESSION_ID_FOR_OPEN_REQUEST,
                endpoint.getAssignedHubEndpointInfo().getIdentifier(),
                targetInfo.getIdentifier(),
                ENDPOINT_SERVICE_DESCRIPTOR);
        verify(mMockEndpointCommunications)
                .closeEndpointSession(
                        INVALID_SESSION_ID_FOR_OPEN_REQUEST,
                        Reason.OPEN_ENDPOINT_SESSION_REQUEST_REJECTED);
        verify(mMockCallback, never())
                .onSessionOpenRequest(
                        INVALID_SESSION_ID_FOR_OPEN_REQUEST,
                        targetInfo,
                        ENDPOINT_SERVICE_DESCRIPTOR);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testEndpointSessionOpenRequest_duplicatedSessionId_noopWhenSessionIsActive()
            throws RemoteException {
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
        IContextHubEndpoint endpoint = registerExampleEndpoint();

        HubEndpointInfo targetInfo =
                new HubEndpointInfo(
                        TARGET_ENDPOINT_NAME,
                        TARGET_ENDPOINT_ID,
                        ENDPOINT_PACKAGE_NAME,
                        Collections.emptyList());
        mHubInfoRegistry.onEndpointStarted(new HubEndpointInfo[] {targetInfo});
        mEndpointManager.onEndpointSessionOpenRequest(
                SESSION_ID_FOR_OPEN_REQUEST,
                endpoint.getAssignedHubEndpointInfo().getIdentifier(),
                targetInfo.getIdentifier(),
                ENDPOINT_SERVICE_DESCRIPTOR);
        endpoint.openSessionRequestComplete(SESSION_ID_FOR_OPEN_REQUEST);
        // Now session with id SESSION_ID_FOR_OPEN_REQUEST is active

        // Duplicated session open request
        mEndpointManager.onEndpointSessionOpenRequest(
                SESSION_ID_FOR_OPEN_REQUEST,
                endpoint.getAssignedHubEndpointInfo().getIdentifier(),
                targetInfo.getIdentifier(),
                ENDPOINT_SERVICE_DESCRIPTOR);

        // Client API is only invoked once
        verify(mMockCallback, times(1))
                .onSessionOpenRequest(
                        SESSION_ID_FOR_OPEN_REQUEST, targetInfo, ENDPOINT_SERVICE_DESCRIPTOR);
        // HAL still receives two open complete notifications
        verify(mMockEndpointCommunications, times(2))
                .endpointSessionOpenComplete(SESSION_ID_FOR_OPEN_REQUEST);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testEndpointSessionOpenRequest_rejectAfterTimeout() throws RemoteException {
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
        IContextHubEndpoint endpoint = registerExampleEndpoint();

        HubEndpointInfo targetInfo =
                new HubEndpointInfo(
                        TARGET_ENDPOINT_NAME,
                        TARGET_ENDPOINT_ID,
                        ENDPOINT_PACKAGE_NAME,
                        Collections.emptyList());
        mHubInfoRegistry.onEndpointStarted(new HubEndpointInfo[] {targetInfo});
        mEndpointManager.onEndpointSessionOpenRequest(
                SESSION_ID_FOR_OPEN_REQUEST,
                endpoint.getAssignedHubEndpointInfo().getIdentifier(),
                targetInfo.getIdentifier(),
                ENDPOINT_SERVICE_DESCRIPTOR);

        // Immediately timeout
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockTimeoutExecutorService)
                .schedule(runnableArgumentCaptor.capture(), anyLong(), any());
        runnableArgumentCaptor.getValue().run();

        // Client's callback shouldn't matter after timeout
        try {
            endpoint.openSessionRequestComplete(SESSION_ID_FOR_OPEN_REQUEST);
        } catch (IllegalArgumentException ignore) {
            // This will throw because the session is no longer valid
        }

        // HAL will receive closeEndpointSession with Timeout as reason
        verify(mMockEndpointCommunications, times(1))
                .closeEndpointSession(SESSION_ID_FOR_OPEN_REQUEST, Reason.TIMEOUT);
        // HAL will not receives open complete notifications
        verify(mMockEndpointCommunications, never())
                .endpointSessionOpenComplete(SESSION_ID_FOR_OPEN_REQUEST);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testEndpointSessionOpenRequest_duplicatedSessionId_noopWithinTimeout()
            throws RemoteException {
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);
        IContextHubEndpoint endpoint = registerExampleEndpoint();

        HubEndpointInfo targetInfo =
                new HubEndpointInfo(
                        TARGET_ENDPOINT_NAME,
                        TARGET_ENDPOINT_ID,
                        ENDPOINT_PACKAGE_NAME,
                        Collections.emptyList());
        mHubInfoRegistry.onEndpointStarted(new HubEndpointInfo[] {targetInfo});
        mEndpointManager.onEndpointSessionOpenRequest(
                SESSION_ID_FOR_OPEN_REQUEST,
                endpoint.getAssignedHubEndpointInfo().getIdentifier(),
                targetInfo.getIdentifier(),
                ENDPOINT_SERVICE_DESCRIPTOR);

        // Duplicated session open request
        mEndpointManager.onEndpointSessionOpenRequest(
                SESSION_ID_FOR_OPEN_REQUEST,
                endpoint.getAssignedHubEndpointInfo().getIdentifier(),
                targetInfo.getIdentifier(),
                ENDPOINT_SERVICE_DESCRIPTOR);

        // Finally, endpoint approved the session open request
        endpoint.openSessionRequestComplete(SESSION_ID_FOR_OPEN_REQUEST);

        // Client API is only invoked once
        verify(mMockCallback, times(1))
                .onSessionOpenRequest(
                        SESSION_ID_FOR_OPEN_REQUEST, targetInfo, ENDPOINT_SERVICE_DESCRIPTOR);
        // HAL still receives two open complete notifications
        verify(mMockEndpointCommunications, times(1))
                .endpointSessionOpenComplete(SESSION_ID_FOR_OPEN_REQUEST);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testMessageTransaction() throws RemoteException {
        IContextHubEndpoint endpoint = registerExampleEndpoint();
        testMessageTransactionInternal(endpoint, /* deliverMessageStatus= */ true);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testMessageTransactionCleanupOnUnregistration() throws RemoteException {
        IContextHubEndpoint endpoint = registerExampleEndpoint();
        testMessageTransactionInternal(endpoint, /* deliverMessageStatus= */ false);

        unregisterExampleEndpoint(endpoint);
        assertThat(mTransactionManager.numReliableMessageTransactionPending()).isEqualTo(0);
    }

    @Test
    public void testDuplicateMessageRejected() throws RemoteException {
        IContextHubEndpoint endpoint = registerExampleEndpoint();
        int sessionId = openTestSession(endpoint);

        mEndpointManager.onMessageReceived(sessionId, SAMPLE_MESSAGE);
        ArgumentCaptor<HubMessage> messageCaptor = ArgumentCaptor.forClass(HubMessage.class);
        verify(mMockCallback).onMessageReceived(eq(sessionId), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo(SAMPLE_MESSAGE);

        // Send a duplicate message and confirm it can be rejected
        mEndpointManager.onMessageReceived(sessionId, SAMPLE_MESSAGE);
        ArgumentCaptor<MessageDeliveryStatus> statusCaptor =
                ArgumentCaptor.forClass(MessageDeliveryStatus.class);
        verify(mMockEndpointCommunications)
                .sendMessageDeliveryStatusToEndpoint(eq(sessionId), statusCaptor.capture());
        assertThat(statusCaptor.getValue().messageSequenceNumber)
                .isEqualTo(SAMPLE_MESSAGE.getMessageSequenceNumber());
        assertThat(statusCaptor.getValue().errorCode).isEqualTo(ErrorCode.TRANSIENT_ERROR);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testUnreliableMessage() throws RemoteException {
        IContextHubEndpoint endpoint = registerExampleEndpoint();
        int sessionId = openTestSession(endpoint);

        mEndpointManager.onMessageReceived(sessionId, SAMPLE_UNRELIABLE_MESSAGE);
        ArgumentCaptor<HubMessage> messageCaptor = ArgumentCaptor.forClass(HubMessage.class);
        verify(mMockCallback).onMessageReceived(eq(sessionId), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo(SAMPLE_UNRELIABLE_MESSAGE);

        // Confirm we can send another message
        mEndpointManager.onMessageReceived(sessionId, SAMPLE_UNRELIABLE_MESSAGE);
        verify(mMockCallback, times(2)).onMessageReceived(eq(sessionId), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo(SAMPLE_UNRELIABLE_MESSAGE);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testUnreliableMessageFailureClosesSession() throws RemoteException {
        IContextHubEndpoint endpoint = registerExampleEndpoint();
        int sessionId = openTestSession(endpoint);

        doThrow(new RemoteException("Intended exception in test"))
                .when(mMockCallback)
                .onMessageReceived(anyInt(), any(HubMessage.class));
        mEndpointManager.onMessageReceived(sessionId, SAMPLE_UNRELIABLE_MESSAGE);
        ArgumentCaptor<HubMessage> messageCaptor = ArgumentCaptor.forClass(HubMessage.class);
        verify(mMockCallback).onMessageReceived(eq(sessionId), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo(SAMPLE_UNRELIABLE_MESSAGE);

        verify(mMockEndpointCommunications).closeEndpointSession(sessionId, Reason.UNSPECIFIED);
        verify(mMockCallback).onSessionClosed(sessionId, HubEndpoint.REASON_FAILURE);
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);

        unregisterExampleEndpoint(endpoint);
    }

    @Test
    public void testSendUnreliableMessageFailureClosesSession() throws RemoteException {
        IContextHubEndpoint endpoint = registerExampleEndpoint();
        int sessionId = openTestSession(endpoint);

        doThrow(new RemoteException("Intended exception in test"))
                .when(mMockEndpointCommunications)
                .sendMessageToEndpoint(anyInt(), any(Message.class));
        endpoint.sendMessage(sessionId, SAMPLE_UNRELIABLE_MESSAGE, /* callback= */ null);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockEndpointCommunications)
                .sendMessageToEndpoint(eq(sessionId), messageCaptor.capture());
        Message message = messageCaptor.getValue();
        assertThat(message.type).isEqualTo(SAMPLE_UNRELIABLE_MESSAGE.getMessageType());
        assertThat(message.content).isEqualTo(SAMPLE_UNRELIABLE_MESSAGE.getMessageBody());

        verify(mMockEndpointCommunications).closeEndpointSession(sessionId, Reason.UNSPECIFIED);
        verify(mMockCallback).onSessionClosed(sessionId, HubEndpoint.REASON_FAILURE);
        assertThat(mEndpointManager.getNumAvailableSessions()).isEqualTo(SESSION_ID_RANGE);

        unregisterExampleEndpoint(endpoint);
    }

    /** A helper method to create a session and validates reliable message sending. */
    private void testMessageTransactionInternal(
            IContextHubEndpoint endpoint, boolean deliverMessageStatus) throws RemoteException {
        int sessionId = openTestSession(endpoint);

        IContextHubTransactionCallback callback =
                new IContextHubTransactionCallback.Stub() {
                    @Override
                    public void onQueryResponse(int result, List<NanoAppState> nanoappList) {
                        Log.i(TAG, "Received onQueryResponse callback, result=" + result);
                    }

                    @Override
                    public void onTransactionComplete(int result) {
                        Log.i(TAG, "Received onTransactionComplete callback, result=" + result);
                    }
                };
        endpoint.sendMessage(sessionId, SAMPLE_MESSAGE, callback);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockEndpointCommunications, timeout(1000))
                .sendMessageToEndpoint(eq(sessionId), messageCaptor.capture());
        Message halMessage = messageCaptor.getValue();
        assertThat(halMessage.type).isEqualTo(SAMPLE_MESSAGE.getMessageType());
        assertThat(halMessage.content).isEqualTo(SAMPLE_MESSAGE.getMessageBody());
        assertThat(mTransactionManager.numReliableMessageTransactionPending()).isEqualTo(1);

        if (deliverMessageStatus) {
            mEndpointManager.onMessageDeliveryStatusReceived(
                    sessionId, halMessage.sequenceNumber, ErrorCode.OK);
            assertThat(mTransactionManager.numReliableMessageTransactionPending()).isEqualTo(0);
        }
    }

    private IContextHubEndpoint registerExampleEndpoint() throws RemoteException {
        HubEndpointInfo info =
                new HubEndpointInfo(
                        ENDPOINT_NAME, ENDPOINT_ID, ENDPOINT_PACKAGE_NAME, Collections.emptyList());
        IContextHubEndpoint endpoint =
                mEndpointManager.registerEndpoint(
                        info, mMockCallback, ENDPOINT_PACKAGE_NAME, /* attributionTag= */ null);
        assertThat(endpoint).isNotNull();
        HubEndpointInfo assignedInfo = endpoint.getAssignedHubEndpointInfo();
        assertThat(assignedInfo).isNotNull();
        HubEndpointIdentifier assignedIdentifier = assignedInfo.getIdentifier();
        assertThat(assignedIdentifier).isNotNull();

        // Confirm registerEndpoint was called with the right contents
        ArgumentCaptor<EndpointInfo> statusCaptor = ArgumentCaptor.forClass(EndpointInfo.class);
        verify(mMockEndpointCommunications).registerEndpoint(statusCaptor.capture());
        assertThat(statusCaptor.getValue().id.id).isEqualTo(assignedIdentifier.getEndpoint());
        assertThat(statusCaptor.getValue().id.hubId).isEqualTo(assignedIdentifier.getHub());
        assertThat(mEndpointManager.getNumRegisteredClients()).isEqualTo(1);

        return endpoint;
    }

    private void unregisterExampleEndpoint(IContextHubEndpoint endpoint) throws RemoteException {
        HubEndpointInfo expectedInfo = endpoint.getAssignedHubEndpointInfo();
        endpoint.unregister();
        ArgumentCaptor<EndpointInfo> statusCaptor = ArgumentCaptor.forClass(EndpointInfo.class);
        verify(mMockEndpointCommunications).unregisterEndpoint(statusCaptor.capture());
        assertThat(statusCaptor.getValue().id.id)
                .isEqualTo(expectedInfo.getIdentifier().getEndpoint());
        assertThat(statusCaptor.getValue().id.hubId)
                .isEqualTo(expectedInfo.getIdentifier().getHub());
        assertThat(mEndpointManager.getNumRegisteredClients()).isEqualTo(0);
    }

    private int openTestSession(IContextHubEndpoint endpoint) throws RemoteException {
        HubEndpointInfo targetInfo =
                new HubEndpointInfo(
                        TARGET_ENDPOINT_NAME,
                        TARGET_ENDPOINT_ID,
                        ENDPOINT_PACKAGE_NAME,
                        Collections.emptyList());
        int sessionId = endpoint.openSession(targetInfo, /* serviceDescriptor= */ null);
        mEndpointManager.onEndpointSessionOpenComplete(sessionId);
        return sessionId;
    }
}
