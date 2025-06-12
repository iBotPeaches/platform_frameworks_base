/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.android.server.wm.WindowStateAnimator.HAS_DRAWN;
import static com.android.server.wm.WindowStateAnimator.NO_SURFACE;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.PixelFormat;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.WindowInsets;
import android.view.inputmethod.Flags;
import android.view.inputmethod.ImeTracker;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link ImeInsetsSourceProvider} class.
 *
 * <p> Build/Install/Run:
 * atest WmTests:ImeInsetsSourceProviderTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class ImeInsetsSourceProviderTest extends WindowTestsBase {

    private ImeInsetsSourceProvider mImeProvider;

    @Before
    public void setUp() throws Exception {
        mImeProvider = mDisplayContent.getInsetsStateController().getImeSourceProvider();
        mImeProvider.getSource().setVisible(true);
        mWm.mAnimator.ready();
    }

    @Test
    public void testTransparentControlTargetWindowCanShowIme() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindowContainer(ime, null, null);

        final WindowState appWin = newWindowBuilder("app", TYPE_APPLICATION).build();
        final WindowState popup = newWindowBuilder("popup", TYPE_APPLICATION).setParent(
                appWin).build();
        popup.mAttrs.format = PixelFormat.TRANSPARENT;
        mDisplayContent.setImeLayeringTarget(appWin);
        mDisplayContent.updateImeInputAndControlTarget(popup);
        performSurfacePlacementAndWaitForWindowAnimator();

        mImeProvider.scheduleShowImePostLayout(appWin, ImeTracker.Token.empty());
        assertTrue(mImeProvider.isScheduledAndReadyToShowIme());
    }

    /**
     * Checks that scheduling with all the state set and manually triggering the show does succeed.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testScheduleShowIme() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindowContainer(ime, null, null);

        final WindowState target = newWindowBuilder("app", TYPE_APPLICATION).build();
        mDisplayContent.setImeLayeringTarget(target);
        mDisplayContent.updateImeInputAndControlTarget(target);
        performSurfacePlacementAndWaitForWindowAnimator();

        // Schedule (without triggering) after everything is ready.
        mImeProvider.scheduleShowImePostLayout(target, ImeTracker.Token.empty());
        assertTrue(mImeProvider.isScheduledAndReadyToShowIme());
        assertFalse(mImeProvider.isImeShowing());

        // Manually trigger the show.
        mImeProvider.checkAndStartShowImePostLayout();
        // No longer scheduled as it was already shown.
        assertFalse(mImeProvider.isScheduledAndReadyToShowIme());
        assertTrue(mImeProvider.isImeShowing());
    }

    /**
     * Checks that scheduling to show before any state is set does succeed when
     * all the state becomes available.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testScheduleShowIme_noInitialState() {
        final WindowState target = newWindowBuilder("app", TYPE_APPLICATION).build();

        // Schedule before anything is ready.
        mImeProvider.scheduleShowImePostLayout(target, ImeTracker.Token.empty());
        assertFalse(mImeProvider.isScheduledAndReadyToShowIme());
        assertFalse(mImeProvider.isImeShowing());

        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindowContainer(ime, null, null);

        mDisplayContent.setImeLayeringTarget(target);
        mDisplayContent.updateImeInputAndControlTarget(target);
        // Performing surface placement picks up the show scheduled above.
        performSurfacePlacementAndWaitForWindowAnimator();
        // No longer scheduled as it was already shown.
        assertFalse(mImeProvider.isScheduledAndReadyToShowIme());
        assertTrue(mImeProvider.isImeShowing());
    }

    /**
     * Checks that scheduling to show before starting the {@code afterPrepareSurfacesRunnable}
     * from {@link InsetsStateController#notifyPendingInsetsControlChanged}
     * does continue and succeed when the runnable is started.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testScheduleShowIme_delayedAfterPrepareSurfaces() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindowContainer(ime, null, null);

        final WindowState target = newWindowBuilder("app", TYPE_APPLICATION).build();
        mDisplayContent.setImeLayeringTarget(target);
        mDisplayContent.updateImeInputAndControlTarget(target);

        // Schedule before starting the afterPrepareSurfacesRunnable.
        mImeProvider.scheduleShowImePostLayout(target, ImeTracker.Token.empty());
        assertFalse(mImeProvider.isScheduledAndReadyToShowIme());
        assertFalse(mImeProvider.isImeShowing());

        // This tries to pick up the show scheduled above, but must fail as the
        // afterPrepareSurfacesRunnable was not started yet.
        mDisplayContent.applySurfaceChangesTransaction();
        assertFalse(mImeProvider.isScheduledAndReadyToShowIme());
        assertFalse(mImeProvider.isImeShowing());

        // Waiting for the afterPrepareSurfacesRunnable picks up the show scheduled above.
        waitUntilWindowAnimatorIdle();
        // No longer scheduled as it was already shown.
        assertFalse(mImeProvider.isScheduledAndReadyToShowIme());
        assertTrue(mImeProvider.isImeShowing());
    }

    /**
     * Checks that scheduling to show before the surface placement does continue and succeed
     * when the surface placement happens.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testScheduleShowIme_delayedSurfacePlacement() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindowContainer(ime, null, null);

        final WindowState target = newWindowBuilder("app", TYPE_APPLICATION).build();
        mDisplayContent.setImeLayeringTarget(target);
        mDisplayContent.updateImeInputAndControlTarget(target);

        // Schedule before surface placement.
        mImeProvider.scheduleShowImePostLayout(target, ImeTracker.Token.empty());
        assertFalse(mImeProvider.isScheduledAndReadyToShowIme());
        assertFalse(mImeProvider.isImeShowing());

        // Performing surface placement picks up the show scheduled above, and succeeds.
        // This first executes the afterPrepareSurfacesRunnable, and then
        // applySurfaceChangesTransaction. Both of them try to trigger the show,
        // but only the second one can succeed, as it comes after onPostLayout.
        performSurfacePlacementAndWaitForWindowAnimator();
        // No longer scheduled as it was already shown.
        assertFalse(mImeProvider.isScheduledAndReadyToShowIme());
        assertTrue(mImeProvider.isImeShowing());
    }

    @Test
    public void testSetFrozen() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindowContainer(ime, null, null);
        mImeProvider.setServerVisible(true);
        mImeProvider.setClientVisible(true);
        mImeProvider.updateVisibility();
        assertTrue(mImeProvider.getSource().isVisible());

        // Freezing IME states and set the server visible as false.
        mImeProvider.setFrozen(true);
        mImeProvider.setServerVisible(false);
        // Expect the IME insets visible won't be changed.
        assertTrue(mImeProvider.getSource().isVisible());

        // Unfreeze IME states and expect the IME insets became invisible due to pending IME
        // visible state updated.
        mImeProvider.setFrozen(false);
        assertFalse(mImeProvider.getSource().isVisible());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testUpdateControlForTarget_remoteInsetsControlTarget() throws RemoteException {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindowContainer(ime, null, null);
        mImeProvider.setServerVisible(true);
        mImeProvider.setClientVisible(true);
        final WindowState inputTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        final var displayWindowInsetsController = spy(createDisplayWindowInsetsController());
        mDisplayContent.setRemoteInsetsController(displayWindowInsetsController);
        final var controlTarget = mDisplayContent.mRemoteInsetsControlTarget;

        inputTarget.setRequestedVisibleTypes(
                WindowInsets.Type.defaultVisible() | WindowInsets.Type.ime());
        mDisplayContent.setImeInputTarget(inputTarget);
        mDisplayContent.setImeControlTarget(controlTarget);

        assertTrue(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertFalse(controlTarget.isRequestedVisible(WindowInsets.Type.ime()));
        mImeProvider.updateControlForTarget(controlTarget, true /* force */,
                ImeTracker.Token.empty());
        verify(displayWindowInsetsController, times(1)).setImeInputTargetRequestedVisibility(
                eq(true), any());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testUpdateControlForTarget_remoteInsetsControlTargetUnchanged()
            throws RemoteException {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        mImeProvider.setWindowContainer(ime, null, null);
        final WindowState inputTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        final var displayWindowInsetsController = spy(createDisplayWindowInsetsController());
        mDisplayContent.setRemoteInsetsController(displayWindowInsetsController);
        final var controlTarget = mDisplayContent.mRemoteInsetsControlTarget;
        mDisplayContent.setImeInputTarget(inputTarget);
        mDisplayContent.setImeControlTarget(controlTarget);

        // Test for visible
        inputTarget.setRequestedVisibleTypes(WindowInsets.Type.ime());
        controlTarget.updateRequestedVisibleTypes(WindowInsets.Type.ime(), WindowInsets.Type.ime());
        clearInvocations(mDisplayContent);
        assertTrue(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertTrue((controlTarget.isRequestedVisible(WindowInsets.Type.ime())));
        mImeProvider.updateControlForTarget(controlTarget, true /* force */,
                ImeTracker.Token.empty());
        verify(displayWindowInsetsController, never()).setImeInputTargetRequestedVisibility(
                anyBoolean(), any());

        // Test for not visible
        inputTarget.setRequestedVisibleTypes(0);
        controlTarget.updateRequestedVisibleTypes(0 /* visibleTypes */, WindowInsets.Type.ime());
        clearInvocations(mDisplayContent);
        assertFalse(inputTarget.isRequestedVisible(WindowInsets.Type.ime()));
        assertFalse((controlTarget.isRequestedVisible(WindowInsets.Type.ime())));
        mImeProvider.updateControlForTarget(controlTarget, true /* force */,
                ImeTracker.Token.empty());
        verify(displayWindowInsetsController, never()).setImeInputTargetRequestedVisibility(
                anyBoolean(), any());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testOnPostLayout_resetServerVisibilityWhenImeIsNotDrawn() {
        final WindowState ime = newWindowBuilder("ime", TYPE_INPUT_METHOD).build();
        final WindowState inputTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        makeWindowVisibleAndDrawn(ime);
        mImeProvider.setWindowContainer(ime, null, null);
        mImeProvider.setServerVisible(true);
        mImeProvider.setClientVisible(true);
        mImeProvider.updateVisibility();
        mImeProvider.updateControlForTarget(inputTarget, true /* force */, null /* statsToken */);

        // Calling onPostLayout, as the drawn state is initially false.
        mImeProvider.onPostLayout();
        assertTrue(mImeProvider.isSurfaceVisible());

        // Reset window's drawn state
        ime.mWinAnimator.mDrawState = NO_SURFACE;
        mImeProvider.onPostLayout();
        assertFalse(mImeProvider.isServerVisible());
        assertFalse(mImeProvider.isSurfaceVisible());

        // Set it back to drawn
        ime.mWinAnimator.mDrawState = HAS_DRAWN;
        mImeProvider.onPostLayout();
        assertTrue(mImeProvider.isServerVisible());
        assertTrue(mImeProvider.isSurfaceVisible());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testUpdateControlForTarget_differentControlTarget() throws RemoteException {
        final WindowState oldTarget = newWindowBuilder("app", TYPE_APPLICATION).build();
        final WindowState newTarget = newWindowBuilder("newapp", TYPE_APPLICATION).build();

        oldTarget.setRequestedVisibleTypes(
                WindowInsets.Type.defaultVisible() | WindowInsets.Type.ime());
        mDisplayContent.setImeControlTarget(oldTarget);
        mDisplayContent.setImeInputTarget(newTarget);

        // Having a null windowContainer will early return in updateControlForTarget
        mImeProvider.setWindowContainer(null, null, null);

        clearInvocations(mDisplayContent);
        mImeProvider.updateControlForTarget(newTarget, false /* force */, ImeTracker.Token.empty());
        verify(mDisplayContent, never()).getImeInputTarget();
    }
}
