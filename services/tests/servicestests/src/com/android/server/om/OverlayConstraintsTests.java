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

package com.android.server.om;

import static android.content.Context.DEVICE_ID_DEFAULT;
import static android.content.om.OverlayConstraint.TYPE_DEVICE_ID;
import static android.content.om.OverlayConstraint.TYPE_DISPLAY_ID;
import static android.util.TypedValue.TYPE_STRING;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import static org.testng.Assert.assertThrows;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayConstraint;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.res.Flags;
import android.content.res.Resources;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

@RunWith(JUnitParamsRunner.class)
public class OverlayConstraintsTests {
    private static final String RESOURCE_NAME = "string/module_2_name";
    private static final String RESOURCE_DEFAULT_VALUE = "module_2_name";
    private static final String RESOURCE_OVERLAID_VALUE = "hello";
    private static final long TIMEOUT_MILLIS = 2000L;

    @Rule
    public final VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.createDefault();

    private OverlayManager mOverlayManager;
    private UserHandle mUserHandle;
    private OverlayIdentifier mOverlayIdentifier = null;
    private final String mPackageName = getApplicationContext().getPackageName();

    @Before
    public void setUp() throws Exception {
        final Context context = getApplicationContext();
        mOverlayManager = context.getSystemService(OverlayManager.class);
        mUserHandle = UserHandle.of(UserHandle.myUserId());
    }

    @After
    public void tearDown() throws Exception {
        if (mOverlayIdentifier != null) {
            OverlayManagerTransaction transaction =
                    new OverlayManagerTransaction.Builder()
                            .unregisterFabricatedOverlay(mOverlayIdentifier)
                            .build();
            mOverlayManager.commit(transaction);
            mOverlayIdentifier = null;
            waitForResourceValue(RESOURCE_DEFAULT_VALUE, getApplicationContext());
        }
    }

    @Test
    public void createOverlayConstraint_withInvalidType_fails() {
        assertThrows(IllegalArgumentException.class,
                () -> new OverlayConstraint(500 /* type */, 1 /* value */));
    }

    @Test
    public void createOverlayConstraint_withInvalidValue_fails() {
        assertThrows(IllegalArgumentException.class,
                () -> new OverlayConstraint(TYPE_DEVICE_ID, -1 /* value */));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithNullConstraints_fails() {
        FabricatedOverlay fabricatedOverlay = createFabricatedOverlay();
        assertThrows(NullPointerException.class,
                () -> mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(fabricatedOverlay)
                        .setEnabled(fabricatedOverlay.getIdentifier(), true /* enable */,
                                null /* constraints */)
                        .build()));
    }

    @Test
    @Parameters(method = "getAllConstraintLists")
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_writesConstraintsIntoOverlayInfo(
            List<OverlayConstraint> constraints) throws Exception {
        enableOverlay(constraints);

        OverlayInfo overlayInfo = mOverlayManager.getOverlayInfo(mOverlayIdentifier, mUserHandle);
        assertNotNull(overlayInfo);
        assertEquals(constraints, overlayInfo.getConstraints());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void disableOverlayWithConstraints_fails() throws Exception {
        FabricatedOverlay fabricatedOverlay = createFabricatedOverlay();
        assertThrows(SecurityException.class,
                () -> mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(fabricatedOverlay)
                        .setEnabled(fabricatedOverlay.getIdentifier(), false /* enable */,
                                List.of(new OverlayConstraint(TYPE_DISPLAY_ID, DEFAULT_DISPLAY)))
                        .build()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithNewConstraints_updatesConstraintsIntoOverlayInfo()
            throws Exception {
        List<OverlayConstraint> constraints1 =
                List.of(new OverlayConstraint(TYPE_DISPLAY_ID, 1 /* value*/));
        enableOverlay(constraints1);

        OverlayInfo overlayInfo1 = mOverlayManager.getOverlayInfo(mOverlayIdentifier, mUserHandle);
        assertNotNull(overlayInfo1);
        assertEquals(constraints1, overlayInfo1.getConstraints());

        List<OverlayConstraint> constraints2 = List.of(
                new OverlayConstraint(TYPE_DISPLAY_ID, 2 /* value */));
        enableOverlay(constraints2);

        OverlayInfo overlayInfo2 = mOverlayManager.getOverlayInfo(mOverlayIdentifier, mUserHandle);
        assertNotNull(overlayInfo2);
        assertEquals(overlayInfo1.overlayName, overlayInfo2.overlayName);
        assertEquals(overlayInfo1.targetPackageName, overlayInfo2.targetPackageName);
        assertEquals(overlayInfo1.packageName, overlayInfo2.packageName);
        assertEquals(constraints2, overlayInfo2.getConstraints());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_fails() throws Exception {
        assertThrows(SecurityException.class, () -> enableOverlay(
                List.of(new OverlayConstraint(TYPE_DISPLAY_ID, DEFAULT_DISPLAY))));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithoutConstraints_appliesOverlayWithoutConstraints()
            throws Exception {
        enableOverlay(Collections.emptyList());

        // Assert than the overlay is applied for both default device context and virtual
        // device context.
        final Context context = getApplicationContext();
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, context);
        VirtualDeviceManager.VirtualDevice virtualDevice =
                mVirtualDeviceRule.createManagedVirtualDevice();
        final Context deviceContext = context.createDeviceContext(virtualDevice.getDeviceId());
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, deviceContext);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_withTypeDeviceId_appliesOverlayWithConstraints()
            throws Exception {
        final int deviceId1 = mVirtualDeviceRule.createManagedVirtualDevice().getDeviceId();
        final int deviceId2 = mVirtualDeviceRule.createManagedVirtualDevice().getDeviceId();
        enableOverlay(List.of(new OverlayConstraint(TYPE_DEVICE_ID, deviceId1),
                new OverlayConstraint(TYPE_DEVICE_ID, deviceId2)));

        // Assert than the overlay is not applied for contexts not associated with the above
        // devices.
        final Context context = getApplicationContext();
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, context);
        final int deviceId3 = mVirtualDeviceRule.createManagedVirtualDevice().getDeviceId();
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, context.createDeviceContext(deviceId3));

        // Assert than the overlay is applied for contexts associated with the above devices.
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, context.createDeviceContext(deviceId1));
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, context.createDeviceContext(deviceId2));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_withTypeDisplayId_appliesOverlayWithConstraints()
            throws Exception {
        final Display display1 =
                mVirtualDeviceRule.createManagedUnownedVirtualDisplay().getDisplay();
        final Display display2 =
                mVirtualDeviceRule.createManagedUnownedVirtualDisplay().getDisplay();
        enableOverlay(List.of(new OverlayConstraint(TYPE_DISPLAY_ID, display1.getDisplayId()),
                new OverlayConstraint(TYPE_DISPLAY_ID, display2.getDisplayId())));

        // Assert than the overlay is not applied for contexts not associated with the above
        // displays.
        final Context context = getApplicationContext();
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, context);
        final Display display3 =
                mVirtualDeviceRule.createManagedUnownedVirtualDisplay().getDisplay();
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, context.createDisplayContext(display3));

        // Assert than the overlay is applied for contexts associated with the above displays.
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, context.createDisplayContext(display1));
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, context.createDisplayContext(display2));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_withTypesDisplayIdAndDeviceId_appliesOverlayWithConstraints()
            throws Exception {
        final Display display1 =
                mVirtualDeviceRule.createManagedUnownedVirtualDisplay().getDisplay();
        final int deviceId1 = mVirtualDeviceRule.createManagedVirtualDevice().getDeviceId();
        enableOverlay(List.of(new OverlayConstraint(TYPE_DISPLAY_ID, display1.getDisplayId()),
                new OverlayConstraint(TYPE_DEVICE_ID, deviceId1)));

        // Assert than the overlay is not applied for contexts not associated with the above
        // display or device.
        final Context context = getApplicationContext();
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, context);
        final Display display2 =
                mVirtualDeviceRule.createManagedUnownedVirtualDisplay().getDisplay();
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, context.createDisplayContext(display2));
        final int deviceId2 = mVirtualDeviceRule.createManagedVirtualDevice().getDeviceId();
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, context.createDeviceContext(deviceId2));

        // Assert than the overlay is applied for contexts associated with the above display or
        // device.
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, context.createDisplayContext(display1));
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, context.createDeviceContext(deviceId1));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_withTypeDisplayId_appliesForActivityOnDisplay()
            throws Exception {
        final Display display =
                mVirtualDeviceRule.createManagedUnownedVirtualDisplay(
                                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder())
                        .getDisplay();
        final Activity activityOnDefaultDisplay = mVirtualDeviceRule.startActivityOnDisplaySync(
                DEFAULT_DISPLAY, Activity.class);
        final Activity activityOnVirtualDisplay = mVirtualDeviceRule.startActivityOnDisplaySync(
                display.getDisplayId(), Activity.class);

        enableOverlay(List.of(new OverlayConstraint(TYPE_DISPLAY_ID, display.getDisplayId())));

        // Assert than the overlay is not applied for any existing activity on the default display.
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, activityOnDefaultDisplay);
        // Assert than the overlay is applied for any existing activity on the virtual display.
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, activityOnVirtualDisplay);

        // Assert than the overlay is not applied for any new activity on the default display.
        final Activity newActivityOnDefaultDisplay = mVirtualDeviceRule.startActivityOnDisplaySync(
                DEFAULT_DISPLAY, Activity.class);
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, newActivityOnDefaultDisplay);
        // Assert than the overlay is applied for any new activity on the virtual display.
        final Activity newActivityOnVirtualDisplay = mVirtualDeviceRule.startActivityOnDisplaySync(
                display.getDisplayId(), Activity.class);
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, newActivityOnVirtualDisplay);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_withTypeDeviceId_appliesForActivityOnDevice()
            throws Exception {
        final VirtualDeviceManager.VirtualDevice device =
                mVirtualDeviceRule.createManagedVirtualDevice();
        final Display display =
                mVirtualDeviceRule.createManagedVirtualDisplay(device,
                                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder())
                        .getDisplay();
        final Activity activityOnDefaultDevice = mVirtualDeviceRule.startActivityOnDisplaySync(
                DEFAULT_DISPLAY, Activity.class);
        final Activity activityOnVirtualDevice = mVirtualDeviceRule.startActivityOnDisplaySync(
                display.getDisplayId(), Activity.class);

        enableOverlay(List.of(new OverlayConstraint(TYPE_DEVICE_ID, device.getDeviceId())));

        // Assert than the overlay is not applied for any existing activity on the default device.
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, activityOnDefaultDevice);
        // Assert than the overlay is applied for any existing activity on the virtual device.
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, activityOnVirtualDevice);

        // Assert than the overlay is not applied for any new activity on the default device.
        final Activity newActivityOnDefaultDevice = mVirtualDeviceRule.startActivityOnDisplaySync(
                DEFAULT_DISPLAY, Activity.class);
        ensureResourceValueStaysAt(RESOURCE_DEFAULT_VALUE, newActivityOnDefaultDevice);
        // Assert than the overlay is applied for any new activity on the virtual device.
        final Activity newActivityOnVirtualDevice = mVirtualDeviceRule.startActivityOnDisplaySync(
                display.getDisplayId(), Activity.class);
        waitForResourceValue(RESOURCE_OVERLAID_VALUE, newActivityOnVirtualDevice);
    }

    private FabricatedOverlay createFabricatedOverlay() {
        FabricatedOverlay fabricatedOverlay = new FabricatedOverlay.Builder(
                mPackageName, "testOverlay" /* name */, mPackageName)
                .build();
        fabricatedOverlay.setResourceValue(RESOURCE_NAME, TYPE_STRING, RESOURCE_OVERLAID_VALUE,
                null /* configuration */);
        return fabricatedOverlay;
    }

    private void enableOverlay(List<OverlayConstraint> constraints) {
        FabricatedOverlay fabricatedOverlay = createFabricatedOverlay();
        OverlayManagerTransaction transaction =
                new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(fabricatedOverlay)
                        .setEnabled(fabricatedOverlay.getIdentifier(), true /* enable */,
                                constraints)
                        .build();
        mOverlayManager.commit(transaction);
        mOverlayIdentifier = fabricatedOverlay.getIdentifier();
    }

    private static void waitForResourceValue(final String expectedValue, Context context)
            throws TimeoutException {
        final long endTime = System.currentTimeMillis() + TIMEOUT_MILLIS;
        final Resources resources = context.getResources();
        final int resourceId = getResourceId(context);
        String resourceValue = null;
        while (System.currentTimeMillis() < endTime) {
            resourceValue = resources.getString(resourceId);
            if (Objects.equals(resourceValue, expectedValue)) {
                return;
            }
        }
        throw new TimeoutException("Timed out waiting for '" + RESOURCE_NAME + "' value to equal '"
                + expectedValue + "': current value is '" + resourceValue + "'");
    }

    private static void ensureResourceValueStaysAt(final String expectedValue, Context context) {
        final long endTime = System.currentTimeMillis() + TIMEOUT_MILLIS;
        final Resources resources = context.getResources();
        final int resourceId = getResourceId(context);
        String resourceValue;
        while (System.currentTimeMillis() < endTime) {
            resourceValue = resources.getString(resourceId);
            assertEquals(expectedValue, resourceValue);
        }
    }

    private static int getResourceId(Context context) {
        return context.getResources().getIdentifier(RESOURCE_NAME, "", context.getPackageName());
    }

    private static List<OverlayConstraint>[] getAllConstraintLists() {
        return new List[]{
                Collections.emptyList(),
                List.of(new OverlayConstraint(TYPE_DISPLAY_ID, DEFAULT_DISPLAY)),
                List.of(new OverlayConstraint(TYPE_DEVICE_ID, DEVICE_ID_DEFAULT)),
                List.of(new OverlayConstraint(TYPE_DEVICE_ID, DEVICE_ID_DEFAULT),
                        new OverlayConstraint(TYPE_DEVICE_ID, DEVICE_ID_DEFAULT))
        };
    }
}
