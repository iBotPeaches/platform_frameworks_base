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

package com.android.server.am;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class BroadcastSkipPolicyTest extends BaseBroadcastQueueTest {
    private static final String TAG = "BroadcastSkipPolicyTest";

    BroadcastSkipPolicy mBroadcastSkipPolicy;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mBroadcastSkipPolicy = new BroadcastSkipPolicy(mAms);

        doReturn(true).when(mIntentFirewall).checkBroadcast(any(Intent.class),
                anyInt(), anyInt(), nullable(String.class), anyInt());

        doReturn(mIPackageManager).when(AppGlobals::getPackageManager);
        doReturn(true).when(mIPackageManager).isPackageAvailable(anyString(), anyInt());

        doReturn(ActivityManager.APP_START_MODE_NORMAL).when(mAms).getAppStartModeLOSP(anyInt(),
                anyString(), anyInt(), anyInt(), eq(true), eq(false), eq(false));

        doReturn(mAppOpsManager).when(mAms).getAppOpsManager();
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).checkOpNoThrow(anyString(),
                anyInt(), anyString(), nullable(String.class));
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).noteOpNoThrow(anyString(),
                anyInt(), anyString(), nullable(String.class), anyString());

        doReturn(mIPermissionManager).when(AppGlobals::getPermissionManager);
        doReturn(PackageManager.PERMISSION_GRANTED).when(mIPermissionManager).checkUidPermission(
                anyInt(), anyString(), anyInt());
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public BroadcastSkipPolicy createBroadcastSkipPolicy() {
        return new BroadcastSkipPolicy(mAms);
    }

    @Test
    public void testShouldSkipMessage_withManifestRcvr_withCompPerm_invokesNoteOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .build();
        final String msg = mBroadcastSkipPolicy.shouldSkipMessage(record,
                makeManifestReceiverWithPermission(PACKAGE_GREEN, CLASS_GREEN,
                        Manifest.permission.PACKAGE_USAGE_STATS));
        assertNull(msg);
        verify(mAppOpsManager).noteOpNoThrow(
                eq(AppOpsManager.permissionToOp(Manifest.permission.PACKAGE_USAGE_STATS)),
                eq(record.callingUid), eq(record.callerPackage), eq(record.callerFeatureId),
                anyString());
        verify(mAppOpsManager, never()).checkOpNoThrow(
                anyString(), anyInt(), anyString(), nullable(String.class));
    }

    @Test
    public void testShouldSkipMessage_withRegRcvr_withCompPerm_invokesNoteOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .build();
        final ProcessRecord receiverApp = makeProcessRecord(makeApplicationInfo(PACKAGE_GREEN));
        final String msg = mBroadcastSkipPolicy.shouldSkipMessage(record,
                makeRegisteredReceiver(receiverApp, 0 /* priority */,
                        Manifest.permission.PACKAGE_USAGE_STATS));
        assertNull(msg);
        verify(mAppOpsManager).noteOpNoThrow(
                eq(AppOpsManager.permissionToOp(Manifest.permission.PACKAGE_USAGE_STATS)),
                eq(record.callingUid), eq(record.callerPackage), eq(record.callerFeatureId),
                anyString());
        verify(mAppOpsManager, never()).checkOpNoThrow(
                anyString(), anyInt(), anyString(), nullable(String.class));
    }

    @Test
    public void testShouldSkipAtEnqueueMessage_withManifestRcvr_withCompPerm_invokesCheckOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .build();
        final String msg = mBroadcastSkipPolicy.shouldSkipAtEnqueueMessage(record,
                makeManifestReceiverWithPermission(PACKAGE_GREEN, CLASS_GREEN,
                        Manifest.permission.PACKAGE_USAGE_STATS));
        assertNull(msg);
        verify(mAppOpsManager).checkOpNoThrow(
                eq(AppOpsManager.permissionToOp(Manifest.permission.PACKAGE_USAGE_STATS)),
                eq(record.callingUid), eq(record.callerPackage), eq(record.callerFeatureId));
        verify(mAppOpsManager, never()).noteOpNoThrow(
                anyString(), anyInt(), anyString(), nullable(String.class), anyString());
    }

    @Test
    public void testShouldSkipAtEnqueueMessage_withRegRcvr_withCompPerm_invokesCheckOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .build();
        final ProcessRecord receiverApp = makeProcessRecord(makeApplicationInfo(PACKAGE_GREEN));
        final String msg = mBroadcastSkipPolicy.shouldSkipAtEnqueueMessage(record,
                makeRegisteredReceiver(receiverApp, 0 /* priority */,
                        Manifest.permission.PACKAGE_USAGE_STATS));
        assertNull(msg);
        verify(mAppOpsManager).checkOpNoThrow(
                eq(AppOpsManager.permissionToOp(Manifest.permission.PACKAGE_USAGE_STATS)),
                eq(record.callingUid), eq(record.callerPackage), eq(record.callerFeatureId));
        verify(mAppOpsManager, never()).noteOpNoThrow(
                anyString(), anyInt(), anyString(), nullable(String.class), anyString());
    }

    @Test
    public void testShouldSkipMessage_withManifestRcvr_withAppOp_invokesNoteOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .setAppOp(AppOpsManager.permissionToOpCode(Manifest.permission.PACKAGE_USAGE_STATS))
                .build();
        final ResolveInfo receiver = makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN);
        final String msg = mBroadcastSkipPolicy.shouldSkipMessage(record, receiver);
        assertNull(msg);
        verify(mAppOpsManager).noteOpNoThrow(
                eq(AppOpsManager.permissionToOp(Manifest.permission.PACKAGE_USAGE_STATS)),
                eq(receiver.activityInfo.applicationInfo.uid),
                eq(receiver.activityInfo.packageName), nullable(String.class), anyString());
        verify(mAppOpsManager, never()).checkOpNoThrow(
                anyString(), anyInt(), anyString(), nullable(String.class));
    }

    @Test
    public void testShouldSkipMessage_withRegRcvr_withAppOp_invokesNoteOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .setAppOp(AppOpsManager.permissionToOpCode(Manifest.permission.PACKAGE_USAGE_STATS))
                .build();
        final ProcessRecord receiverApp = makeProcessRecord(makeApplicationInfo(PACKAGE_GREEN));
        final BroadcastFilter filter = makeRegisteredReceiver(receiverApp, 0 /* priority */,
                null /* requiredPermission */);
        final String msg = mBroadcastSkipPolicy.shouldSkipMessage(record, filter);
        assertNull(msg);
        verify(mAppOpsManager).noteOpNoThrow(
                eq(AppOpsManager.permissionToOp(Manifest.permission.PACKAGE_USAGE_STATS)),
                eq(filter.receiverList.uid),
                eq(filter.packageName), nullable(String.class), anyString());
        verify(mAppOpsManager, never()).checkOpNoThrow(
                anyString(), anyInt(), anyString(), nullable(String.class));
    }

    @Test
    public void testShouldSkipAtEnqueueMessage_withManifestRcvr_withAppOp_invokesCheckOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .setAppOp(AppOpsManager.permissionToOpCode(Manifest.permission.PACKAGE_USAGE_STATS))
                .build();
        final ResolveInfo receiver = makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN);
        final String msg = mBroadcastSkipPolicy.shouldSkipAtEnqueueMessage(record, receiver);
        assertNull(msg);
        verify(mAppOpsManager).checkOpNoThrow(
                eq(AppOpsManager.permissionToOp(Manifest.permission.PACKAGE_USAGE_STATS)),
                eq(receiver.activityInfo.applicationInfo.uid),
                eq(receiver.activityInfo.applicationInfo.packageName), nullable(String.class));
        verify(mAppOpsManager, never()).noteOpNoThrow(
                anyString(), anyInt(), anyString(), nullable(String.class), anyString());
    }

    @Test
    public void testShouldSkipAtEnqueueMessage_withRegRcvr_withAppOp_invokesCheckOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .setAppOp(AppOpsManager.permissionToOpCode(Manifest.permission.PACKAGE_USAGE_STATS))
                .build();
        final ProcessRecord receiverApp = makeProcessRecord(makeApplicationInfo(PACKAGE_GREEN));
        final BroadcastFilter filter = makeRegisteredReceiver(receiverApp, 0 /* priority */,
                null /* requiredPermission */);
        final String msg = mBroadcastSkipPolicy.shouldSkipAtEnqueueMessage(record, filter);
        assertNull(msg);
        verify(mAppOpsManager).checkOpNoThrow(
                eq(AppOpsManager.permissionToOp(Manifest.permission.PACKAGE_USAGE_STATS)),
                eq(filter.receiverList.uid),
                eq(filter.packageName), nullable(String.class));
        verify(mAppOpsManager, never()).noteOpNoThrow(
                anyString(), anyInt(), anyString(), nullable(String.class), anyString());
    }

    @Test
    public void testShouldSkipMessage_withManifestRcvr_withRequiredPerms_invokesNoteOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .setRequiredPermissions(new String[] {Manifest.permission.PACKAGE_USAGE_STATS})
                .build();
        final String msg = mBroadcastSkipPolicy.shouldSkipMessage(record,
                makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN));
        assertNull(msg);
        verify(mPermissionManager).checkPermissionForDataDelivery(
                eq(Manifest.permission.PACKAGE_USAGE_STATS), any(), anyString());
        verify(mPermissionManager, never()).checkPermissionForPreflight(
                eq(Manifest.permission.PACKAGE_USAGE_STATS), any());
    }

    @Test
    public void testShouldSkipMessage_withRegRcvr_withRequiredPerms_invokesNoteOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .setRequiredPermissions(new String[] {Manifest.permission.PACKAGE_USAGE_STATS})
                .build();
        final ProcessRecord receiverApp = makeProcessRecord(makeApplicationInfo(PACKAGE_GREEN));
        final String msg = mBroadcastSkipPolicy.shouldSkipMessage(record,
                makeRegisteredReceiver(receiverApp, 0 /* priority */,
                        null /* requiredPermission */));
        assertNull(msg);
        verify(mPermissionManager).checkPermissionForDataDelivery(
                eq(Manifest.permission.PACKAGE_USAGE_STATS), any(), anyString());
        verify(mPermissionManager, never()).checkPermissionForPreflight(
                eq(Manifest.permission.PACKAGE_USAGE_STATS), any());
    }

    @Test
    public void testShouldSkipAtEnqueueMessage_withManifestRcvr_withRequiredPerms_invokesCheckOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .setRequiredPermissions(new String[] {Manifest.permission.PACKAGE_USAGE_STATS})
                .build();
        final String msg = mBroadcastSkipPolicy.shouldSkipAtEnqueueMessage(record,
                makeManifestReceiver(PACKAGE_GREEN, CLASS_GREEN));
        assertNull(msg);
        verify(mPermissionManager, never()).checkPermissionForDataDelivery(
                eq(Manifest.permission.PACKAGE_USAGE_STATS), any(), anyString());
        verify(mPermissionManager).checkPermissionForPreflight(
                eq(Manifest.permission.PACKAGE_USAGE_STATS), any());
    }

    @Test
    public void testShouldSkipAtEnqueueMessage_withRegRcvr_withRequiredPerms_invokesCheckOp() {
        final BroadcastRecord record = new BroadcastRecordBuilder()
                .setIntent(new Intent(Intent.ACTION_TIME_TICK))
                .setRequiredPermissions(new String[] {Manifest.permission.PACKAGE_USAGE_STATS})
                .build();
        final ProcessRecord receiverApp = makeProcessRecord(makeApplicationInfo(PACKAGE_GREEN));
        final String msg = mBroadcastSkipPolicy.shouldSkipAtEnqueueMessage(record,
                makeRegisteredReceiver(receiverApp, 0 /* priority */,
                        null /* requiredPermission */));
        assertNull(msg);
        verify(mPermissionManager, never()).checkPermissionForDataDelivery(
                eq(Manifest.permission.PACKAGE_USAGE_STATS), any(), anyString());
        verify(mPermissionManager).checkPermissionForPreflight(
                eq(Manifest.permission.PACKAGE_USAGE_STATS), any());
    }

    private ResolveInfo makeManifestReceiverWithPermission(String packageName, String name,
            String permission) {
        final ResolveInfo resolveInfo = makeManifestReceiver(packageName, name);
        resolveInfo.activityInfo.permission = permission;
        return resolveInfo;
    }

    private BroadcastFilter makeRegisteredReceiver(ProcessRecord app, int priority,
            String requiredPermission) {
        final IIntentReceiver receiver = mock(IIntentReceiver.class);
        final ReceiverList receiverList = new ReceiverList(mAms, app, app.getPid(), app.info.uid,
                UserHandle.getUserId(app.info.uid), receiver);
        return makeRegisteredReceiver(receiverList, priority, requiredPermission);
    }
}
