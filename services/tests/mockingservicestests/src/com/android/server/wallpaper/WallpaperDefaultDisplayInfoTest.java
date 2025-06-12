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

package com.android.server.wallpaper;

import static android.app.WallpaperManager.ORIENTATION_LANDSCAPE;
import static android.app.WallpaperManager.ORIENTATION_PORTRAIT;
import static android.app.WallpaperManager.ORIENTATION_SQUARE_LANDSCAPE;
import static android.app.WallpaperManager.ORIENTATION_SQUARE_PORTRAIT;
import static android.app.WallpaperManager.ORIENTATION_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.util.SparseArray;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.wallpaper.WallpaperDefaultDisplayInfo.FoldableOrientations;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Set;

/** Unit tests for {@link WallpaperDefaultDisplayInfo}. */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class WallpaperDefaultDisplayInfoTest {
    @Mock
    private WindowManager mWindowManager;

    @Mock
    private Resources mResources;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void defaultDisplayInfo_foldable_shouldHaveExpectedContent() {
        doReturn(new int[]{0}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect innerDisplayBounds = new Rect(0, 0, 2076, 2152);
        Rect outerDisplayBounds = new Rect(0, 0, 1080, 2424);
        WindowMetrics innerDisplayMetrics =
                new WindowMetrics(innerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        WindowMetrics outerDisplayMetrics =
                new WindowMetrics(outerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(innerDisplayMetrics, outerDisplayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        SparseArray<Point> displaySizes = new SparseArray<>();
        displaySizes.put(ORIENTATION_PORTRAIT, new Point(1080, 2424));
        displaySizes.put(ORIENTATION_LANDSCAPE, new Point(2424, 1080));
        displaySizes.put(ORIENTATION_SQUARE_PORTRAIT, new Point(2076, 2152));
        displaySizes.put(ORIENTATION_SQUARE_LANDSCAPE, new Point(2152, 2076));
        assertThat(defaultDisplayInfo.defaultDisplaySizes.contentEquals(displaySizes)).isTrue();
        assertThat(defaultDisplayInfo.isFoldable).isTrue();
        assertThat(defaultDisplayInfo.isLargeScreen).isFalse();
        assertThat(defaultDisplayInfo.foldableOrientations).containsExactly(
                new FoldableOrientations(
                        /* foldedOrientation= */ ORIENTATION_PORTRAIT,
                        /* unfoldedOrientation= */ ORIENTATION_SQUARE_PORTRAIT),
                new FoldableOrientations(
                        /* foldedOrientation= */ ORIENTATION_LANDSCAPE,
                        /* unfoldedOrientation= */ ORIENTATION_SQUARE_LANDSCAPE));
    }

    @Test
    public void defaultDisplayInfo_tablet_shouldHaveExpectedContent() {
        doReturn(new int[]{}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect displayBounds = new Rect(0, 0, 2560, 1600);
        WindowMetrics displayMetrics =
                new WindowMetrics(displayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(displayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        SparseArray<Point> displaySizes = new SparseArray<>();
        displaySizes.put(ORIENTATION_PORTRAIT, new Point(1600, 2560));
        displaySizes.put(ORIENTATION_LANDSCAPE, new Point(2560, 1600));
        assertThat(defaultDisplayInfo.defaultDisplaySizes.contentEquals(displaySizes)).isTrue();
        assertThat(defaultDisplayInfo.isFoldable).isFalse();
        assertThat(defaultDisplayInfo.isLargeScreen).isTrue();
        assertThat(defaultDisplayInfo.foldableOrientations).isEmpty();
    }

    @Test
    public void defaultDisplayInfo_phone_shouldHaveExpectedContent() {
        doReturn(new int[]{}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect displayBounds = new Rect(0, 0, 1280, 2856);
        WindowMetrics displayMetrics =
                new WindowMetrics(displayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 3f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(displayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        SparseArray<Point> displaySizes = new SparseArray<>();
        displaySizes.put(ORIENTATION_PORTRAIT, new Point(1280, 2856));
        displaySizes.put(ORIENTATION_LANDSCAPE, new Point(2856, 1280));
        assertThat(defaultDisplayInfo.defaultDisplaySizes.contentEquals(displaySizes)).isTrue();
        assertThat(defaultDisplayInfo.isFoldable).isFalse();
        assertThat(defaultDisplayInfo.isLargeScreen).isFalse();
        assertThat(defaultDisplayInfo.foldableOrientations).isEmpty();
    }

    @Test
    public void defaultDisplayInfo_equals_sameContent_shouldEqual() {
        doReturn(new int[]{0}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect innerDisplayBounds = new Rect(0, 0, 2076, 2152);
        Rect outerDisplayBounds = new Rect(0, 0, 1080, 2424);
        WindowMetrics innerDisplayMetrics =
                new WindowMetrics(innerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        WindowMetrics outerDisplayMetrics =
                new WindowMetrics(outerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(innerDisplayMetrics, outerDisplayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);
        WallpaperDefaultDisplayInfo otherDefaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        assertThat(defaultDisplayInfo).isEqualTo(otherDefaultDisplayInfo);
    }

    @Test
    public void defaultDisplayInfo_equals_differentBounds_shouldNotEqual() {
        doReturn(new int[]{0}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect innerDisplayBounds = new Rect(0, 0, 2076, 2152);
        Rect outerDisplayBounds = new Rect(0, 0, 1080, 2424);
        WindowMetrics innerDisplayMetrics =
                new WindowMetrics(innerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        WindowMetrics outerDisplayMetrics =
                new WindowMetrics(outerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                // For the first call
                .thenReturn(Set.of(innerDisplayMetrics, outerDisplayMetrics))
                // For the second+ call
                .thenReturn(Set.of(innerDisplayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);
        WallpaperDefaultDisplayInfo otherDefaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        assertThat(defaultDisplayInfo).isNotEqualTo(otherDefaultDisplayInfo);
    }

    @Test
    public void defaultDisplayInfo_hashCode_sameContent_shouldEqual() {
        doReturn(new int[]{0}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect innerDisplayBounds = new Rect(0, 0, 2076, 2152);
        Rect outerDisplayBounds = new Rect(0, 0, 1080, 2424);
        WindowMetrics innerDisplayMetrics =
                new WindowMetrics(innerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        WindowMetrics outerDisplayMetrics =
                new WindowMetrics(outerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(innerDisplayMetrics, outerDisplayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);
        WallpaperDefaultDisplayInfo otherDefaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        assertThat(defaultDisplayInfo.hashCode()).isEqualTo(otherDefaultDisplayInfo.hashCode());
    }

    @Test
    public void defaultDisplayInfo_hashCode_differentBounds_shouldNotEqual() {
        doReturn(new int[]{0}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect innerDisplayBounds = new Rect(0, 0, 2076, 2152);
        Rect outerDisplayBounds = new Rect(0, 0, 1080, 2424);
        WindowMetrics innerDisplayMetrics =
                new WindowMetrics(innerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        WindowMetrics outerDisplayMetrics =
                new WindowMetrics(outerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                // For the first call
                .thenReturn(Set.of(innerDisplayMetrics, outerDisplayMetrics))
                // For the second+ call
                .thenReturn(Set.of(innerDisplayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);
        WallpaperDefaultDisplayInfo otherDefaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        assertThat(defaultDisplayInfo.hashCode()).isNotEqualTo(otherDefaultDisplayInfo.hashCode());
    }

    @Test
    public void getFoldedOrientation_foldable_shouldReturnExpectedOrientation() {
        doReturn(new int[]{0}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect innerDisplayBounds = new Rect(0, 0, 2076, 2152);
        Rect outerDisplayBounds = new Rect(0, 0, 1080, 2424);
        WindowMetrics innerDisplayMetrics =
                new WindowMetrics(innerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        WindowMetrics outerDisplayMetrics =
                new WindowMetrics(outerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(innerDisplayMetrics, outerDisplayMetrics));
        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        assertThat(defaultDisplayInfo.getFoldedOrientation(ORIENTATION_SQUARE_PORTRAIT))
                .isEqualTo(ORIENTATION_PORTRAIT);
        assertThat(defaultDisplayInfo.getFoldedOrientation(ORIENTATION_SQUARE_LANDSCAPE))
                .isEqualTo(ORIENTATION_LANDSCAPE);
        // Use a folded orientation for a folded orientation should return unknown.
        assertThat(defaultDisplayInfo.getFoldedOrientation(ORIENTATION_PORTRAIT))
                .isEqualTo(ORIENTATION_UNKNOWN);
        assertThat(defaultDisplayInfo.getFoldedOrientation(ORIENTATION_LANDSCAPE))
                .isEqualTo(ORIENTATION_UNKNOWN);
    }

    @Test
    public void getUnfoldedOrientation_foldable_shouldReturnExpectedOrientation() {
        doReturn(new int[]{0}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect innerDisplayBounds = new Rect(0, 0, 2076, 2152);
        Rect outerDisplayBounds = new Rect(0, 0, 1080, 2424);
        WindowMetrics innerDisplayMetrics =
                new WindowMetrics(innerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        WindowMetrics outerDisplayMetrics =
                new WindowMetrics(outerDisplayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2.4375f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(innerDisplayMetrics, outerDisplayMetrics));
        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        assertThat(defaultDisplayInfo.getUnfoldedOrientation(ORIENTATION_PORTRAIT))
                .isEqualTo(ORIENTATION_SQUARE_PORTRAIT);
        assertThat(defaultDisplayInfo.getUnfoldedOrientation(ORIENTATION_LANDSCAPE))
                .isEqualTo(ORIENTATION_SQUARE_LANDSCAPE);
        // Use an unfolded orientation for an unfolded orientation should return unknown.
        assertThat(defaultDisplayInfo.getUnfoldedOrientation(ORIENTATION_SQUARE_PORTRAIT))
                .isEqualTo(ORIENTATION_UNKNOWN);
        assertThat(defaultDisplayInfo.getUnfoldedOrientation(ORIENTATION_SQUARE_LANDSCAPE))
                .isEqualTo(ORIENTATION_UNKNOWN);
    }

    @Test
    public void getFoldedOrientation_nonFoldable_shouldReturnUnknown() {
        doReturn(new int[]{}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect displayBounds = new Rect(0, 0, 2560, 1600);
        WindowMetrics displayMetrics =
                new WindowMetrics(displayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(displayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        assertThat(defaultDisplayInfo.getFoldedOrientation(ORIENTATION_SQUARE_PORTRAIT))
                .isEqualTo(ORIENTATION_UNKNOWN);
        assertThat(defaultDisplayInfo.getFoldedOrientation(ORIENTATION_SQUARE_LANDSCAPE))
                .isEqualTo(ORIENTATION_UNKNOWN);
        assertThat(defaultDisplayInfo.getFoldedOrientation(ORIENTATION_PORTRAIT))
                .isEqualTo(ORIENTATION_UNKNOWN);
        assertThat(defaultDisplayInfo.getFoldedOrientation(ORIENTATION_LANDSCAPE))
                .isEqualTo(ORIENTATION_UNKNOWN);
    }

    @Test
    public void getUnFoldedOrientation_nonFoldable_shouldReturnUnknown() {
        doReturn(new int[]{}).when(mResources).getIntArray(eq(R.array.config_foldedDeviceStates));
        Rect displayBounds = new Rect(0, 0, 2560, 1600);
        WindowMetrics displayMetrics =
                new WindowMetrics(displayBounds, new WindowInsets.Builder().build(),
                        /* density= */ 2f);
        when(mWindowManager.getPossibleMaximumWindowMetrics(anyInt()))
                .thenReturn(Set.of(displayMetrics));

        WallpaperDefaultDisplayInfo defaultDisplayInfo = new WallpaperDefaultDisplayInfo(
                mWindowManager, mResources);

        assertThat(defaultDisplayInfo.getUnfoldedOrientation(ORIENTATION_SQUARE_PORTRAIT))
                .isEqualTo(ORIENTATION_UNKNOWN);
        assertThat(defaultDisplayInfo.getUnfoldedOrientation(ORIENTATION_SQUARE_LANDSCAPE))
                .isEqualTo(ORIENTATION_UNKNOWN);
        assertThat(defaultDisplayInfo.getUnfoldedOrientation(ORIENTATION_PORTRAIT))
                .isEqualTo(ORIENTATION_UNKNOWN);
        assertThat(defaultDisplayInfo.getUnfoldedOrientation(ORIENTATION_LANDSCAPE))
                .isEqualTo(ORIENTATION_UNKNOWN);
    }
}
