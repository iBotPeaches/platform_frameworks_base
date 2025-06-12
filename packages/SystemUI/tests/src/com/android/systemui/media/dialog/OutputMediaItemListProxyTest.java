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

package com.android.systemui.media.dialog;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.media.flags.Flags;
import com.android.settingslib.media.MediaDevice;
import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;
import java.util.stream.Collectors;

@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper
public class OutputMediaItemListProxyTest extends SysuiTestCase {
    private static final String DEVICE_ID_1 = "device_id_1";
    private static final String DEVICE_ID_2 = "device_id_2";
    private static final String DEVICE_ID_3 = "device_id_3";
    private static final String DEVICE_ID_4 = "device_id_4";
    @Mock private MediaDevice mMediaDevice1;
    @Mock private MediaDevice mMediaDevice2;
    @Mock private MediaDevice mMediaDevice3;
    @Mock private MediaDevice mMediaDevice4;

    private MediaItem mMediaItem1;
    private MediaItem mMediaItem2;
    private OutputMediaItemListProxy mOutputMediaItemListProxy;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_FIX_OUTPUT_MEDIA_ITEM_LIST_INDEX_OUT_OF_BOUNDS_EXCEPTION,
                Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING);
    }

    public OutputMediaItemListProxyTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mMediaDevice1.getId()).thenReturn(DEVICE_ID_1);
        when(mMediaDevice2.getId()).thenReturn(DEVICE_ID_2);
        when(mMediaDevice2.isSuggestedDevice()).thenReturn(true);
        when(mMediaDevice3.getId()).thenReturn(DEVICE_ID_3);
        when(mMediaDevice4.getId()).thenReturn(DEVICE_ID_4);
        mMediaItem1 = MediaItem.createDeviceMediaItem(mMediaDevice1);
        mMediaItem2 = MediaItem.createDeviceMediaItem(mMediaDevice2);

        mOutputMediaItemListProxy = new OutputMediaItemListProxy(mContext);
    }

    @EnableFlags(Flags.FLAG_FIX_OUTPUT_MEDIA_ITEM_LIST_INDEX_OUT_OF_BOUNDS_EXCEPTION)
    @Test
    public void updateMediaDevices_shouldUpdateMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        // Create the initial output media item list with mMediaDevice2 and mMediaDevice3.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice2, mMediaDevice3),
                /* selectedDevices */ List.of(mMediaDevice3),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // Check the output media items to be
        //     * a media item with the selected mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the mMediaDevice2
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(mMediaDevice3, null, mMediaDevice2);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isEqualTo(Flags.enableOutputSwitcherDeviceGrouping());

        // Update the output media item list with more media devices.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice4, mMediaDevice1, mMediaDevice3, mMediaDevice2),
                /* selectedDevices */ List.of(mMediaDevice3),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // Check the output media items to be
        //     * a media item with the selected route mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the route mMediaDevice2
        //     * a group divider for speakers and displays
        //     * a media item with the route mMediaDevice4
        //     * a media item with the route mMediaDevice1
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(
                        mMediaDevice3, null, mMediaDevice2, null, mMediaDevice4, mMediaDevice1);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isEqualTo(Flags.enableOutputSwitcherDeviceGrouping());

        // Update the output media item list where mMediaDevice4 is offline and new selected device.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice1, mMediaDevice3, mMediaDevice2),
                /* selectedDevices */ List.of(mMediaDevice1, mMediaDevice3),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        // Check the output media items to be
        //     * a media item with the selected route mMediaDevice3
        //     * a group divider for suggested devices
        //     * a media item with the route mMediaDevice2
        //     * a group divider for speakers and displays
        //     * a media item with the route mMediaDevice1
        assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                .containsExactly(mMediaDevice3, null, mMediaDevice2, null, mMediaDevice1);
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList().get(0).isFirstDeviceInGroup())
                .isEqualTo(Flags.enableOutputSwitcherDeviceGrouping());
    }

    @EnableFlags(Flags.FLAG_FIX_OUTPUT_MEDIA_ITEM_LIST_INDEX_OUT_OF_BOUNDS_EXCEPTION)
    @Test
    public void updateMediaDevices_multipleSelectedDevices_shouldHaveCorrectDeviceOrdering() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        // Create the initial output media item list with mMediaDevice2 and mMediaDevice3.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice2, mMediaDevice4, mMediaDevice3, mMediaDevice1),
                /* selectedDevices */ List.of(mMediaDevice1, mMediaDevice2, mMediaDevice3),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        if (Flags.enableOutputSwitcherDeviceGrouping()) {
            // When the device grouping is enabled, the order of selected devices are preserved:
            //     * a media item with the selected mMediaDevice2
            //     * a media item with the selected mMediaDevice3
            //     * a media item with the selected mMediaDevice1
            //     * a group divider for speakers and displays
            //     * a media item with the mMediaDevice4
            assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                    .containsExactly(
                            mMediaDevice2, mMediaDevice3, mMediaDevice1, null, mMediaDevice4);
            assertThat(
                            mOutputMediaItemListProxy
                                    .getOutputMediaItemList()
                                    .get(0)
                                    .isFirstDeviceInGroup())
                    .isTrue();
        } else {
            // When the device grouping is disabled, the order of selected devices are reverted:
            //     * a media item with the selected mMediaDevice1
            //     * a media item with the selected mMediaDevice3
            //     * a media item with the selected mMediaDevice2
            //     * a group divider for speakers and displays
            //     * a media item with the mMediaDevice4
            assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                    .containsExactly(
                            mMediaDevice1, mMediaDevice3, mMediaDevice2, null, mMediaDevice4);
        }

        // Update the output media item list with a selected device being deselected.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice4, mMediaDevice1, mMediaDevice3, mMediaDevice2),
                /* selectedDevices */ List.of(mMediaDevice2, mMediaDevice3),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        if (Flags.enableOutputSwitcherDeviceGrouping()) {
            // When the device grouping is enabled, the order of selected devices are preserved:
            //     * a media item with the selected mMediaDevice2
            //     * a media item with the selected mMediaDevice3
            //     * a media item with the selected mMediaDevice1
            //     * a group divider for speakers and displays
            //     * a media item with the mMediaDevice4
            assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                    .containsExactly(
                            mMediaDevice2, mMediaDevice3, mMediaDevice1, null, mMediaDevice4);
            assertThat(
                            mOutputMediaItemListProxy
                                    .getOutputMediaItemList()
                                    .get(0)
                                    .isFirstDeviceInGroup())
                    .isTrue();
        } else {
            // When the device grouping is disabled, the order of selected devices are reverted:
            //     * a media item with the selected mMediaDevice1
            //     * a media item with the selected mMediaDevice3
            //     * a media item with the selected mMediaDevice2
            //     * a group divider for speakers and displays
            //     * a media item with the mMediaDevice4
            assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                    .containsExactly(
                            mMediaDevice1, mMediaDevice3, mMediaDevice2, null, mMediaDevice4);
        }

        // Update the output media item list with a selected device is missing.
        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice1, mMediaDevice3, mMediaDevice4),
                /* selectedDevices */ List.of(mMediaDevice3),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);

        if (Flags.enableOutputSwitcherDeviceGrouping()) {
            // When the device grouping is enabled, the order of selected devices are preserved:
            //     * a media item with the selected mMediaDevice3
            //     * a media item with the selected mMediaDevice1
            //     * a group divider for speakers and displays
            //     * a media item with the mMediaDevice4
            assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                    .containsExactly(mMediaDevice3, mMediaDevice1, null, mMediaDevice4);
            assertThat(
                            mOutputMediaItemListProxy
                                    .getOutputMediaItemList()
                                    .get(0)
                                    .isFirstDeviceInGroup())
                    .isTrue();
        } else {
            // When the device grouping is disabled, the order of selected devices are reverted:
            //     * a media item with the selected mMediaDevice1
            //     * a media item with the selected mMediaDevice3
            //     * a group divider for speakers and displays
            //     * a media item with the mMediaDevice4
            assertThat(getMediaDevices(mOutputMediaItemListProxy.getOutputMediaItemList()))
                    .containsExactly(mMediaDevice1, mMediaDevice3, null, mMediaDevice4);
        }
    }

    @DisableFlags(Flags.FLAG_FIX_OUTPUT_MEDIA_ITEM_LIST_INDEX_OUT_OF_BOUNDS_EXCEPTION)
    @Test
    public void clearAndAddAll_shouldUpdateMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        mOutputMediaItemListProxy.clearAndAddAll(List.of(mMediaItem1));
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList()).containsExactly(mMediaItem1);
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();

        mOutputMediaItemListProxy.clearAndAddAll(List.of(mMediaItem2));
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList()).containsExactly(mMediaItem2);
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();
    }

    @EnableFlags(Flags.FLAG_FIX_OUTPUT_MEDIA_ITEM_LIST_INDEX_OUT_OF_BOUNDS_EXCEPTION)
    @Test
    public void clear_flagOn_shouldClearMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice1),
                /* selectedDevices */ List.of(),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();

        mOutputMediaItemListProxy.clear();
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();
    }

    @DisableFlags(Flags.FLAG_FIX_OUTPUT_MEDIA_ITEM_LIST_INDEX_OUT_OF_BOUNDS_EXCEPTION)
    @Test
    public void clear_flagOff_shouldClearMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        mOutputMediaItemListProxy.clearAndAddAll(List.of(mMediaItem1));
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();

        mOutputMediaItemListProxy.clear();
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();
    }

    @EnableFlags(Flags.FLAG_FIX_OUTPUT_MEDIA_ITEM_LIST_INDEX_OUT_OF_BOUNDS_EXCEPTION)
    @Test
    public void removeMutingExpectedDevices_flagOn_shouldClearMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        mOutputMediaItemListProxy.updateMediaDevices(
                /* devices= */ List.of(mMediaDevice1),
                /* selectedDevices */ List.of(),
                /* connectedMediaDevice= */ null,
                /* needToHandleMutingExpectedDevice= */ false);
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();

        mOutputMediaItemListProxy.removeMutingExpectedDevices();
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();
    }

    @DisableFlags(Flags.FLAG_FIX_OUTPUT_MEDIA_ITEM_LIST_INDEX_OUT_OF_BOUNDS_EXCEPTION)
    @Test
    public void removeMutingExpectedDevices_flagOff_shouldClearMediaItemList() {
        assertThat(mOutputMediaItemListProxy.isEmpty()).isTrue();

        mOutputMediaItemListProxy.clearAndAddAll(List.of(mMediaItem1));
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();

        mOutputMediaItemListProxy.removeMutingExpectedDevices();
        assertThat(mOutputMediaItemListProxy.getOutputMediaItemList()).containsExactly(mMediaItem1);
        assertThat(mOutputMediaItemListProxy.isEmpty()).isFalse();
    }

    private List<MediaDevice> getMediaDevices(List<MediaItem> mediaItems) {
        return mediaItems.stream()
                .map(item -> item.getMediaDevice().orElse(null))
                .collect(Collectors.toList());
    }
}
