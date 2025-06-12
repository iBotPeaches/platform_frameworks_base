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

package com.android.systemui.media.dialog;

import static android.media.RouteListingPreference.Item.SUBTEXT_AD_ROUTING_DISALLOWED;
import static android.media.RouteListingPreference.Item.SUBTEXT_CUSTOM;
import static android.media.RouteListingPreference.Item.SUBTEXT_SUBSCRIPTION_REQUIRED;

import static com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_GO_TO_APP;
import static com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_NONE;
import static com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_TRANSFER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.drawable.Icon;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.testing.TestableLooper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import androidx.core.graphics.drawable.IconCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.media.flags.Flags;
import com.android.settingslib.media.LocalMediaManager;
import com.android.settingslib.media.MediaDevice;
import com.android.settingslib.utils.ThreadUtils;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.res.R;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@SmallTest
@DisableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_REDESIGN)
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class MediaOutputAdapterLegacyTest extends SysuiTestCase {

    private static final String TEST_DEVICE_NAME_1 = "test_device_name_1";
    private static final String TEST_DEVICE_NAME_2 = "test_device_name_2";
    private static final String TEST_DEVICE_ID_1 = "test_device_id_1";
    private static final String TEST_DEVICE_ID_2 = "test_device_id_2";
    private static final String TEST_SESSION_NAME = "test_session_name";
    private static final String TEST_CUSTOM_SUBTEXT = "custom subtext";

    private static final int TEST_MAX_VOLUME = 20;
    private static final int TEST_CURRENT_VOLUME = 10;

    // Mock
    private MediaSwitchingController mMediaSwitchingController =
            mock(MediaSwitchingController.class);
    private MediaOutputDialog mMediaOutputDialog = mock(MediaOutputDialog.class);
    private MediaDevice mMediaDevice1 = mock(MediaDevice.class);
    private MediaDevice mMediaDevice2 = mock(MediaDevice.class);
    private Icon mIcon = mock(Icon.class);
    private IconCompat mIconCompat = mock(IconCompat.class);
    private View mDialogLaunchView = mock(View.class);

    @Captor
    private ArgumentCaptor<SeekBar.OnSeekBarChangeListener> mOnSeekBarChangeListenerCaptor;
    private MediaOutputAdapterLegacy mMediaOutputAdapter;
    private MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy mViewHolder;
    private List<MediaDevice> mMediaDevices = new ArrayList<>();
    private List<MediaItem> mMediaItems = new ArrayList<>();
    MediaOutputSeekbar mSpyMediaOutputSeekbar;
    Executor mMainExecutor = mContext.getMainExecutor();
    ListeningExecutorService mBackgroundExecutor = ThreadUtils.getBackgroundExecutor();

    @Before
    public void setUp() {
        when(mMediaSwitchingController.getMediaItemList()).thenReturn(mMediaItems);
        when(mMediaSwitchingController.hasAdjustVolumeUserRestriction()).thenReturn(false);
        when(mMediaSwitchingController.isAnyDeviceTransferring()).thenReturn(false);
        when(mMediaSwitchingController.getDeviceIconCompat(mMediaDevice1)).thenReturn(mIconCompat);
        when(mMediaSwitchingController.getDeviceIconCompat(mMediaDevice2)).thenReturn(mIconCompat);
        when(mMediaSwitchingController.getCurrentConnectedMediaDevice()).thenReturn(mMediaDevice1);
        when(mMediaSwitchingController.isActiveRemoteDevice(mMediaDevice1)).thenReturn(true);
        when(mMediaSwitchingController.getSessionVolumeMax()).thenReturn(TEST_MAX_VOLUME);
        when(mMediaSwitchingController.getSessionVolume()).thenReturn(TEST_CURRENT_VOLUME);
        when(mMediaSwitchingController.getSessionName()).thenReturn(TEST_SESSION_NAME);
        when(mMediaSwitchingController.getColorSchemeLegacy()).thenReturn(
                mock(MediaOutputColorSchemeLegacy.class));
        when(mIconCompat.toIcon(mContext)).thenReturn(mIcon);
        when(mMediaDevice1.getName()).thenReturn(TEST_DEVICE_NAME_1);
        when(mMediaDevice1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(mMediaDevice2.getName()).thenReturn(TEST_DEVICE_NAME_2);
        when(mMediaDevice2.getId()).thenReturn(TEST_DEVICE_ID_2);
        when(mMediaDevice1.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_CONNECTED);
        when(mMediaDevice2.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED);
        mMediaDevices.add(mMediaDevice1);
        mMediaDevices.add(mMediaDevice2);
        mMediaItems.add(MediaItem.createDeviceMediaItem(mMediaDevice1, true));
        mMediaItems.add(MediaItem.createDeviceMediaItem(mMediaDevice2, false));

        mMediaOutputAdapter = new MediaOutputAdapterLegacy(mMediaSwitchingController, mMainExecutor,
                mBackgroundExecutor);
        mMediaOutputAdapter.updateItems();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mSpyMediaOutputSeekbar = spy(mViewHolder.mSeekBar);
    }

    @Test
    public void getItemCount_returnsMediaItemSize() {
        assertThat(mMediaOutputAdapter.getItemCount()).isEqualTo(mMediaItems.size());
    }

    @Test
    public void getItemId_validPosition_returnCorrespondingId() {
        assertThat(mMediaOutputAdapter.getItemId(0)).isEqualTo(mMediaItems.get(
                0).getMediaDevice().get().getId().hashCode());
    }

    @Test
    public void getItemId_invalidPosition_returnPosition() {
        int invalidPosition = mMediaItems.size() + 1;
        assertThat(mMediaOutputAdapter.getItemId(invalidPosition)).isEqualTo(invalidPosition);
    }

    @Test
    public void onBindViewHolder_bindPairNew_verifyView() {
        mMediaOutputAdapter = new MediaOutputAdapterLegacy(mMediaSwitchingController, mMainExecutor,
                mBackgroundExecutor);
        mMediaOutputAdapter.updateItems();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaItems.add(MediaItem.createPairNewDeviceMediaItem());
        mMediaItems.add(MediaItem.createPairNewDeviceMediaItem());
        mMediaOutputAdapter.updateItems();
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 2);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(
                mContext.getText(R.string.media_output_dialog_pairing_new).toString());
    }

    @DisableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void onBindViewHolder_bindGroup_withSessionName_verifyView() {
        when(mMediaSwitchingController.getSelectedMediaDevice())
                .thenReturn(
                        mMediaItems.stream()
                                .map((item) -> item.getMediaDevice().get())
                                .collect(Collectors.toList()));
        when(mMediaSwitchingController.getSessionName()).thenReturn(TEST_SESSION_NAME);
        mMediaOutputAdapter = new MediaOutputAdapterLegacy(mMediaSwitchingController, mMainExecutor,
                mBackgroundExecutor);
        mMediaOutputAdapter.updateItems();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.getItemCount();
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @DisableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void onBindViewHolder_bindGroup_noSessionName_verifyView() {
        when(mMediaSwitchingController.getSelectedMediaDevice())
                .thenReturn(
                        mMediaItems.stream()
                                .map((item) -> item.getMediaDevice().get())
                                .collect(Collectors.toList()));
        when(mMediaSwitchingController.getSessionName()).thenReturn(null);
        mMediaOutputAdapter = new MediaOutputAdapterLegacy(mMediaSwitchingController, mMainExecutor,
                mBackgroundExecutor);
        mMediaOutputAdapter.updateItems();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.getItemCount();
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_bindConnectedDevice_verifyView() {
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_bindNonRemoteConnectedDevice_verifyView() {
        when(mMediaSwitchingController.isActiveRemoteDevice(mMediaDevice1)).thenReturn(false);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @DisableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void onBindViewHolder_bindConnectedRemoteDevice_verifyView() {
        when(mMediaSwitchingController.getSelectedMediaDevice())
                .thenReturn(ImmutableList.of(mMediaDevice1));
        when(mMediaSwitchingController.getSelectableMediaDevice())
                .thenReturn(ImmutableList.of(mMediaDevice2));
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(true);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_bindConnectedRemoteDevice_verifyContentDescriptionNotNull() {
        when(mMediaSwitchingController.getSelectableMediaDevice())
                .thenReturn(ImmutableList.of(mMediaDevice2));
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(true);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getContentDescription()).isNotNull();
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isFalse();
        assertThat(mViewHolder.mContainerLayout.getImportantForAccessibility()).isEqualTo(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        assertThat(mViewHolder.mTextContent.getImportantForAccessibility()).isEqualTo(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    @Test
    public void onBindViewHolder_bindSingleConnectedRemoteDevice_verifyView() {
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(ImmutableList.of());
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(true);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_bindConnectedRemoteDeviceWithOnGoingSession_verifyView() {
        when(mMediaDevice1.hasOngoingSession()).thenReturn(true);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(ImmutableList.of());
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(true);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mStatusIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_bindConnectedRemoteDeviceWithHostOnGoingSession_verifyView() {
        when(mMediaDevice1.hasOngoingSession()).thenReturn(true);
        when(mMediaDevice1.isHostForOngoingSession()).thenReturn(true);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(ImmutableList.of());
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(true);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mStatusIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_bindConnectedDeviceWithMutingExpectedDeviceExist_verifyView() {
        when(mMediaSwitchingController.hasMutingExpectedDevice()).thenReturn(true);
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
    }

    @Test
    public void onBindViewHolder_isMutingExpectedDevice_verifyView() {
        when(mMediaDevice1.isMutingExpectedDevice()).thenReturn(true);
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(false);
        when(mMediaSwitchingController.isActiveRemoteDevice(mMediaDevice1)).thenReturn(false);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
    }

    @Test
    public void onBindViewHolder_initSeekbar_setsVolume() {
        when(mMediaDevice1.getMaxVolume()).thenReturn(TEST_MAX_VOLUME);
        when(mMediaDevice1.getCurrentVolume()).thenReturn(TEST_CURRENT_VOLUME);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mSeekBar.getVolume()).isEqualTo(TEST_CURRENT_VOLUME);
    }

    @Test
    public void onBindViewHolder_initSeekbarWithUnmutedVolume_displaysMuteIcon() {
        when(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).thenReturn(true);
        when(mMediaDevice1.getMaxVolume()).thenReturn(TEST_MAX_VOLUME);
        when(mMediaDevice1.getCurrentVolume()).thenReturn(TEST_CURRENT_VOLUME);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mIconAreaLayout.getVisibility()).isEqualTo(View.VISIBLE);

        mViewHolder.mIconAreaLayout.performClick();
        verify(mMediaSwitchingController).adjustVolume(mMediaDevice1, 0);
        verify(mMediaSwitchingController).logInteractionMuteDevice(mMediaDevice1);
    }

    @Test
    public void onBindViewHolder_initSeekbarWithMutedVolume_displaysUnmuteIcon() {
        when(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).thenReturn(true);
        when(mMediaDevice1.getMaxVolume()).thenReturn(TEST_MAX_VOLUME);
        when(mMediaDevice1.getCurrentVolume()).thenReturn(0); // muted.
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mIconAreaLayout.getVisibility()).isEqualTo(View.VISIBLE);

        mViewHolder.mIconAreaLayout.performClick();
        // Default unmute volume is 2.
        verify(mMediaSwitchingController).adjustVolume(mMediaDevice1, 2);
        verify(mMediaSwitchingController).logInteractionUnmuteDevice(mMediaDevice1);
    }

    @Test
    public void onBindViewHolder_dragSeekbar_setsVolume() {
        mOnSeekBarChangeListenerCaptor = ArgumentCaptor.forClass(
                SeekBar.OnSeekBarChangeListener.class);
        MediaOutputSeekbar mSpySeekbar = spy(mViewHolder.mSeekBar);
        mViewHolder.mSeekBar = mSpySeekbar;
        when(mMediaDevice1.getMaxVolume()).thenReturn(TEST_MAX_VOLUME);
        when(mMediaDevice1.getCurrentVolume()).thenReturn(TEST_MAX_VOLUME);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        verify(mViewHolder.mSeekBar).setOnSeekBarChangeListener(
                mOnSeekBarChangeListenerCaptor.capture());

        mOnSeekBarChangeListenerCaptor.getValue().onStopTrackingTouch(mViewHolder.mSeekBar);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        verify(mMediaSwitchingController).logInteractionAdjustVolume(mMediaDevice1);
    }

    @DisableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void onBindViewHolder_bindSelectableDevice_verifyView() {
        List<MediaDevice> selectableDevices = new ArrayList<>();
        selectableDevices.add(mMediaDevice2);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(selectableDevices);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mCheckBox.isChecked()).isFalse();
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();

        mViewHolder.mCheckBox.performClick();
        verify(mMediaSwitchingController).addDeviceToPlayMedia(mMediaDevice2);
    }

    @DisableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void onBindViewHolder_bindDeselectableDevice_verifyView() {
        when(mMediaSwitchingController.getSelectedMediaDevice()).thenReturn(
                List.of(mMediaDevice1, mMediaDevice2));
        when(mMediaSwitchingController.getDeselectableMediaDevice()).thenReturn(
                List.of(mMediaDevice1, mMediaDevice2));
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mCheckBox.isChecked()).isTrue();
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.GONE);

        mViewHolder.mCheckBox.performClick();
        verify(mMediaSwitchingController).removeDeviceFromPlayMedia(mMediaDevice2);
    }

    @DisableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void onBindViewHolder_changingSelectedValue_doesntTriggerChangeListener() {
        List<MediaDevice> selectableDevices = List.of(mMediaDevice2);
        List<MediaDevice> selectedDevices = new ArrayList<>();
        selectedDevices.add(mMediaDevice1);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(selectableDevices);
        when(mMediaSwitchingController.getSelectedMediaDevice()).thenReturn(selectedDevices);

        // mMediaDevice2 is selected
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);
        assertThat(mViewHolder.mCheckBox.isChecked()).isFalse();

        // changing the selected state programmatically (not a user click)
        selectedDevices.add(mMediaDevice2);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);
        assertThat(mViewHolder.mCheckBox.isChecked()).isTrue();

        // The onCheckedChangeListener is not invoked
        verify(mMediaSwitchingController, never()).addDeviceToPlayMedia(mMediaDevice2);
        verify(mMediaSwitchingController, never()).removeDeviceFromPlayMedia(mMediaDevice2);
    }

    @Test
    public void onBindViewHolder_bindNonActiveConnectedDevice_verifyView() {
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
    }

    @Test
    public void onBindViewHolder_bindDisconnectedBluetoothDevice_verifyView() {
        when(mMediaDevice2.getDeviceType()).thenReturn(
                MediaDevice.MediaDeviceType.TYPE_BLUETOOTH_DEVICE);
        when(mMediaDevice2.isConnected()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(
                TEST_DEVICE_NAME_2);
    }

    @Test
    public void onBindViewHolder_bindFailedStateDevice_verifyView() {
        when(mMediaDevice2.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();
        assertThat(mViewHolder.mContainerLayout.getImportantForAccessibility()).isEqualTo(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        assertThat(mViewHolder.mTextContent.getImportantForAccessibility()).isEqualTo(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mSubTitleText.getText().toString()).isEqualTo(
                mContext.getText(R.string.media_output_dialog_connect_failed).toString());
    }

    @Test
    public void subStatusSupported_onBindViewHolder_bindHostDeviceWithOngoingSession_verifyView() {
        when(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).thenReturn(true);
        when(mMediaDevice1.isHostForOngoingSession()).thenReturn(true);
        when(mMediaDevice1.hasSubtext()).thenReturn(true);
        when(mMediaDevice1.getSubtext()).thenReturn(SUBTEXT_CUSTOM);
        when(mMediaDevice1.getSubtextString()).thenReturn(TEST_CUSTOM_SUBTEXT);
        when(mMediaDevice1.hasOngoingSession()).thenReturn(true);
        when(mMediaDevice1.getSelectionBehavior()).thenReturn(SELECTION_BEHAVIOR_GO_TO_APP);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mStatusIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mSubTitleText.getText().toString()).isEqualTo(TEST_CUSTOM_SUBTEXT);
        assertThat(mViewHolder.mContainerLayout.hasOnClickListeners()).isFalse();
    }

    @Test
    public void subStatusSupported_onBindViewHolder_bindDeviceRequirePremium_verifyView() {
        String deviceStatus = (String) mContext.getText(
                com.android.settingslib.R.string.media_output_status_require_premium);
        when(mMediaDevice2.hasSubtext()).thenReturn(true);
        when(mMediaDevice2.getSubtext()).thenReturn(SUBTEXT_SUBSCRIPTION_REQUIRED);
        when(mMediaDevice2.getSubtextString()).thenReturn(deviceStatus);
        when(mMediaDevice2.getSelectionBehavior()).thenReturn(SELECTION_BEHAVIOR_GO_TO_APP);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mStatusIcon.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mSubTitleText.getText().toString()).isEqualTo(deviceStatus);
        assertThat(mViewHolder.mContainerLayout.hasOnClickListeners()).isTrue();
    }

    @Test
    public void subStatusSupported_onBindViewHolder_bindDeviceWithAdPlaying_verifyView() {
        String deviceStatus = (String) mContext.getText(
                com.android.settingslib.R.string.media_output_status_try_after_ad);
        when(mMediaDevice2.hasSubtext()).thenReturn(true);
        when(mMediaDevice2.getSubtext()).thenReturn(SUBTEXT_AD_ROUTING_DISALLOWED);
        when(mMediaDevice2.getSubtextString()).thenReturn(deviceStatus);
        when(mMediaDevice2.getSelectionBehavior()).thenReturn(SELECTION_BEHAVIOR_NONE);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mStatusIcon.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mSubTitleText.getText().toString()).isEqualTo(deviceStatus);
        assertThat(mViewHolder.mContainerLayout.hasOnClickListeners()).isFalse();
    }

    @Test
    public void subStatusSupported_onBindViewHolder_bindDeviceWithOngoingSession_verifyView() {
        when(mMediaDevice1.hasSubtext()).thenReturn(true);
        when(mMediaDevice1.getSubtext()).thenReturn(SUBTEXT_CUSTOM);
        when(mMediaDevice1.getSubtextString()).thenReturn(TEST_CUSTOM_SUBTEXT);
        when(mMediaDevice1.hasOngoingSession()).thenReturn(true);
        when(mMediaDevice1.getSelectionBehavior()).thenReturn(SELECTION_BEHAVIOR_GO_TO_APP);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mStatusIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mSubTitleText.getText().toString()).isEqualTo(TEST_CUSTOM_SUBTEXT);
        assertThat(mViewHolder.mContainerLayout.hasOnClickListeners()).isFalse();
    }

    @Test
    public void onBindViewHolder_inTransferring_bindTransferringDevice_verifyView() {
        when(mMediaSwitchingController.isAnyDeviceTransferring()).thenReturn(true);
        when(mMediaDevice1.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_CONNECTING);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_bindGroupingDevice_verifyView() {
        when(mMediaSwitchingController.isAnyDeviceTransferring()).thenReturn(false);
        when(mMediaDevice1.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_GROUPING);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mSubTitleText.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_inTransferring_bindNonTransferringDevice_verifyView() {
        when(mMediaSwitchingController.isAnyDeviceTransferring()).thenReturn(true);
        when(mMediaDevice2.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_CONNECTING);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mProgressBar.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
    }

    @Test
    public void onItemClick_clickPairNew_verifyLaunchBluetoothPairing() {
        mMediaOutputAdapter = new MediaOutputAdapterLegacy(mMediaSwitchingController, mMainExecutor,
                mBackgroundExecutor);
        mMediaOutputAdapter.updateItems();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaItems.add(MediaItem.createPairNewDeviceMediaItem());
        mMediaOutputAdapter.updateItems();
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 2);
        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).launchBluetoothPairing(mViewHolder.mContainerLayout);
    }

    @Test
    public void onItemClick_clickDevice_verifyConnectDevice() {
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(false);
        assertThat(mMediaDevice2.getState()).isEqualTo(
                LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED);
        when(mMediaDevice2.getSelectionBehavior()).thenReturn(SELECTION_BEHAVIOR_TRANSFER);
        mMediaOutputAdapter = new MediaOutputAdapterLegacy(mMediaSwitchingController, mMainExecutor,
                mBackgroundExecutor);
        mMediaOutputAdapter.updateItems();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.getItemCount();
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);
        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).connectDevice(mMediaDevice2);
    }

    @Test
    public void onItemClick_clickDeviceWithSessionOngoing_verifyShowsDialog() {
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(true);
        assertThat(mMediaDevice2.getState()).isEqualTo(
                LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED);
        when(mMediaDevice2.getSelectionBehavior()).thenReturn(SELECTION_BEHAVIOR_TRANSFER);
        mMediaOutputAdapter = new MediaOutputAdapterLegacy(mMediaSwitchingController,
                mContext.getMainExecutor(), ThreadUtils.getBackgroundExecutor());
        mMediaOutputAdapter.updateItems();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy spyMediaDeviceViewHolder = spy(
                mViewHolder);
        mMediaOutputAdapter.getItemCount();

        mMediaOutputAdapter.onBindViewHolder(spyMediaDeviceViewHolder, 0);
        mMediaOutputAdapter.onBindViewHolder(spyMediaDeviceViewHolder, 1);
        spyMediaDeviceViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController, never()).connectDevice(mMediaDevice2);
        verify(spyMediaDeviceViewHolder).showCustomEndSessionDialog(mMediaDevice2);
    }

    @Test
    public void onItemClick_selectionBehaviorGoToApp_sendsLaunchIntent() {
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(true);
        when(mMediaDevice2.getState()).thenReturn(
                LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED);
        when(mMediaDevice2.getSelectionBehavior()).thenReturn(SELECTION_BEHAVIOR_GO_TO_APP);
        mMediaOutputAdapter = new MediaOutputAdapterLegacy(mMediaSwitchingController, mMainExecutor,
                mBackgroundExecutor);
        mMediaOutputAdapter.updateItems();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy spyMediaDeviceViewHolder = spy(
                mViewHolder);

        mMediaOutputAdapter.onBindViewHolder(spyMediaDeviceViewHolder, 1);
        spyMediaDeviceViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).tryToLaunchInAppRoutingIntent(TEST_DEVICE_ID_2,
                mViewHolder.mContainerLayout);
    }

    @Test
    public void onItemClick_clicksWithMutingExpectedDeviceExist_cancelsMuteAwaitConnection() {
        when(mMediaSwitchingController.isAnyDeviceTransferring()).thenReturn(false);
        when(mMediaSwitchingController.hasMutingExpectedDevice()).thenReturn(true);
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(false);
        when(mMediaDevice1.isMutingExpectedDevice()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).cancelMuteAwaitConnection();
    }

    @Test
    public void onGroupActionTriggered_clicksEndAreaOfSelectableDevice_triggerGrouping() {
        List<MediaDevice> selectableDevices = new ArrayList<>();
        selectableDevices.add(mMediaDevice2);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(selectableDevices);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        mViewHolder.mCheckBox.performClick();

        verify(mMediaSwitchingController).addDeviceToPlayMedia(mMediaDevice2);
    }

    @DisableFlags(Flags.FLAG_DISABLE_TRANSFER_WHEN_APPS_DO_NOT_SUPPORT)
    @Test
    public void clickFullItemOfSelectableDevice_flagOff_hasListingPreference_verifyConnectDevice() {
        List<MediaDevice> mediaDevices = new ArrayList<>();
        mediaDevices.add(mMediaDevice2);
        when(mMediaDevice2.hasRouteListingPreferenceItem()).thenReturn(true);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(mediaDevices);
        when(mMediaSwitchingController.getTransferableMediaDevices()).thenReturn(List.of());
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mTitleText.getAlpha())
                .isEqualTo(MediaOutputAdapterLegacy.DEVICE_ACTIVE_ALPHA);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();

        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).connectDevice(mMediaDevice2);
    }

    @EnableFlags(Flags.FLAG_DISABLE_TRANSFER_WHEN_APPS_DO_NOT_SUPPORT)
    @Test
    public void clickFullItemOfSelectableDevice_flagOn_hasListingPreference_verifyConnectDevice() {
        List<MediaDevice> mediaDevices = new ArrayList<>();
        mediaDevices.add(mMediaDevice2);
        when(mMediaDevice2.hasRouteListingPreferenceItem()).thenReturn(true);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(mediaDevices);
        when(mMediaSwitchingController.getTransferableMediaDevices()).thenReturn(List.of());
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mTitleText.getAlpha())
                .isEqualTo(MediaOutputAdapterLegacy.DEVICE_ACTIVE_ALPHA);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();

        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).connectDevice(mMediaDevice2);
    }

    @DisableFlags(Flags.FLAG_DISABLE_TRANSFER_WHEN_APPS_DO_NOT_SUPPORT)
    @Test
    public void clickFullItemOfSelectableDevice_flagOff_isTransferable_verifyConnectDevice() {
        List<MediaDevice> mediaDevices = new ArrayList<>();
        mediaDevices.add(mMediaDevice2);
        when(mMediaDevice2.hasRouteListingPreferenceItem()).thenReturn(false);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(mediaDevices);
        when(mMediaSwitchingController.getTransferableMediaDevices()).thenReturn(mediaDevices);
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mTitleText.getAlpha())
                .isEqualTo(MediaOutputAdapterLegacy.DEVICE_ACTIVE_ALPHA);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();

        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).connectDevice(mMediaDevice2);
    }

    @EnableFlags(Flags.FLAG_DISABLE_TRANSFER_WHEN_APPS_DO_NOT_SUPPORT)
    @Test
    public void clickFullItemOfSelectableDevice_flagOn_isTransferable_verifyConnectDevice() {
        List<MediaDevice> mediaDevices = new ArrayList<>();
        mediaDevices.add(mMediaDevice2);
        when(mMediaDevice2.hasRouteListingPreferenceItem()).thenReturn(false);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(mediaDevices);
        when(mMediaSwitchingController.getTransferableMediaDevices()).thenReturn(mediaDevices);
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mTitleText.getAlpha())
                .isEqualTo(MediaOutputAdapterLegacy.DEVICE_ACTIVE_ALPHA);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();

        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).connectDevice(mMediaDevice2);
    }

    @DisableFlags(Flags.FLAG_DISABLE_TRANSFER_WHEN_APPS_DO_NOT_SUPPORT)
    @Test
    public void clickFullItemOfSelectableDevice_flagOff_notTransferable_verifyConnectDevice() {
        List<MediaDevice> mediaDevices = new ArrayList<>();
        mediaDevices.add(mMediaDevice2);
        when(mMediaDevice2.hasRouteListingPreferenceItem()).thenReturn(false);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(mediaDevices);
        when(mMediaSwitchingController.getTransferableMediaDevices()).thenReturn(List.of());
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mTitleText.getAlpha())
                .isEqualTo(MediaOutputAdapterLegacy.DEVICE_ACTIVE_ALPHA);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();

        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController).connectDevice(mMediaDevice2);
    }

    @EnableFlags(Flags.FLAG_DISABLE_TRANSFER_WHEN_APPS_DO_NOT_SUPPORT)
    @Test
    public void clickFullItemOfSelectableDevice_flagOn_notTransferable_verifyNotConnectDevice() {
        List<MediaDevice> mediaDevices = new ArrayList<>();
        mediaDevices.add(mMediaDevice2);
        when(mMediaDevice2.hasRouteListingPreferenceItem()).thenReturn(false);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(mediaDevices);
        when(mMediaSwitchingController.getTransferableMediaDevices()).thenReturn(List.of());
        when(mMediaSwitchingController.isCurrentOutputDeviceHasSessionOngoing()).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
        assertThat(mViewHolder.mTitleText.getAlpha())
                .isEqualTo(MediaOutputAdapterLegacy.DEVICE_DISABLED_ALPHA);
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isTrue();

        mViewHolder.mContainerLayout.performClick();

        verify(mMediaSwitchingController, never()).connectDevice(any(MediaDevice.class));
    }

    @Test
    public void onGroupActionTriggered_clickSelectedRemoteDevice_triggerUngrouping() {
        when(mMediaSwitchingController.getSelectableMediaDevice())
                .thenReturn(ImmutableList.of(mMediaDevice2));
        when(mMediaSwitchingController.getDeselectableMediaDevice())
                .thenReturn(ImmutableList.of(mMediaDevice1));
        when(mMediaSwitchingController.getSelectedMediaDevice())
                .thenReturn(ImmutableList.of(mMediaDevice1));
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(true);
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(new LinearLayout(mContext), 0);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        mViewHolder.mCheckBox.performClick();

        verify(mMediaSwitchingController).removeDeviceFromPlayMedia(mMediaDevice1);
    }

    @DisableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void onBindViewHolder_hasVolumeAdjustmentRestriction_verifySeekbarDisabled() {
        when(mMediaSwitchingController.getSelectedMediaDevice()).thenReturn(
                List.of(mMediaDevice1, mMediaDevice2));
        when(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).thenReturn(true);
        when(mMediaSwitchingController.hasAdjustVolumeUserRestriction()).thenReturn(true);
        mMediaOutputAdapter.updateItems();

        // Connected and selected device
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);

        // Selected device
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);
        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_volumeControlChangeToEnabled_enableSeekbarAgain() {
        when(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).thenReturn(false);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);
        assertThat(mViewHolder.mSeekBar.isEnabled()).isFalse();

        when(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).thenReturn(true);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.isEnabled()).isTrue();
    }

    @Test
    public void updateItems_controllerItemsUpdated_notUpdatesInAdapterUntilUpdateItems() {
        mMediaOutputAdapter.updateItems();
        List<MediaItem> updatedList = new ArrayList<>();
        updatedList.add(MediaItem.createPairNewDeviceMediaItem());
        when(mMediaSwitchingController.getMediaItemList()).thenReturn(updatedList);
        assertThat(mMediaOutputAdapter.getItemCount()).isEqualTo(mMediaItems.size());

        mMediaOutputAdapter.updateItems();

        assertThat(mMediaOutputAdapter.getItemCount()).isEqualTo(updatedList.size());
    }

    @DisableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void getDrawableId_FlagDisabled_InputDeviceMutedIcon() {
        assertThat(
                mViewHolder.getDrawableId(true /* isInputDevice */, true /* isMutedVolumeIcon */))
                .isEqualTo(R.drawable.media_output_icon_volume_off);
    }

    @DisableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void getDrawableId_FlagDisabled_OutputDeviceMutedIcon() {
        assertThat(
                mViewHolder.getDrawableId(false /* isInputDevice */, true /* isMutedVolumeIcon */))
                .isEqualTo(R.drawable.media_output_icon_volume_off);
    }

    @DisableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void getDrawableId_FlagDisabled_InputDeviceUnmutedIcon() {
        assertThat(
                mViewHolder.getDrawableId(true /* isInputDevice */, false /* isMutedVolumeIcon */))
                .isEqualTo(R.drawable.media_output_icon_volume);
    }

    @DisableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void getDrawableId_FlagDisabled_OutputDeviceUnmutedIcon() {
        assertThat(
                mViewHolder.getDrawableId(false /* isInputDevice */, false /* isMutedVolumeIcon */))
                .isEqualTo(R.drawable.media_output_icon_volume);
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void getDrawableId_FlagEnabled_InputDeviceMutedIcon() {
        assertThat(
                mViewHolder.getDrawableId(true /* isInputDevice */, true /* isMutedVolumeIcon */))
                .isEqualTo(R.drawable.ic_mic_off);
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void getDrawableId_FlagEnabled_OutputDeviceMutedIcon() {
        assertThat(
                mViewHolder.getDrawableId(false /* isInputDevice */, true /* isMutedVolumeIcon */))
                .isEqualTo(R.drawable.media_output_icon_volume_off);
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void getDrawableId_FlagEnabled_InputDeviceUnmutedIcon() {
        assertThat(
                mViewHolder.getDrawableId(true /* isInputDevice */, false /* isMutedVolumeIcon */))
                .isEqualTo(R.drawable.ic_mic_26dp);
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    public void getDrawableId_FlagEnabled_OutputDeviceUnmutedIcon() {
        assertThat(
                mViewHolder.getDrawableId(false /* isInputDevice */, false /* isMutedVolumeIcon */))
                .isEqualTo(R.drawable.media_output_icon_volume);
    }

    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void multipleSelectedDevices_verifySessionView() {
        initializeSession();

        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(
                        new LinearLayout(mContext), MediaItem.MediaItemType.TYPE_DEVICE);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mSeekBar.getContentDescription()).isNotNull();
        assertThat(mViewHolder.mContainerLayout.isFocusable()).isFalse();
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_SESSION_NAME);
        assertThat(mViewHolder.mSeekBar.getVolume()).isEqualTo(TEST_CURRENT_VOLUME);
    }

    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void multipleSelectedDevices_verifyCollapsedView() {
        initializeSession();

        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(
                        new LinearLayout(mContext), MediaItem.MediaItemType.TYPE_DEVICE);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mItemLayout.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.GONE);
    }

    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void multipleSelectedDevices_expandIconClicked_verifyInitialView() {
        initializeSession();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(
                        new LinearLayout(mContext), MediaItem.MediaItemType.TYPE_DEVICE);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        mViewHolder.mEndClickIcon.performClick();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(
                        new LinearLayout(mContext), MediaItem.MediaItemType.TYPE_DEVICE);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
    }

    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void multipleSelectedDevices_expandIconClicked_verifyCollapsedView() {
        initializeSession();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(
                        new LinearLayout(mContext), MediaItem.MediaItemType.TYPE_DEVICE);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        mViewHolder.mEndClickIcon.performClick();
        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(
                        new LinearLayout(mContext), MediaItem.MediaItemType.TYPE_DEVICE);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 1);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_2);
    }

    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_DEVICE_GROUPING)
    @Test
    public void deviceCanNotBeDeselected_verifyView() {
        List<MediaDevice> selectedDevices = new ArrayList<>();
        selectedDevices.add(mMediaDevice1);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(selectedDevices);
        when(mMediaSwitchingController.getSelectedMediaDevice()).thenReturn(selectedDevices);
        when(mMediaSwitchingController.getDeselectableMediaDevice()).thenReturn(new ArrayList<>());

        mViewHolder = (MediaOutputAdapterLegacy.MediaDeviceViewHolderLegacy) mMediaOutputAdapter
                .onCreateViewHolder(
                        new LinearLayout(mContext), MediaItem.MediaItemType.TYPE_DEVICE);
        mMediaOutputAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mSeekBar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mCheckBox.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mEndTouchArea.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mEndClickIcon.getVisibility()).isEqualTo(View.GONE);
        assertThat(mViewHolder.mTitleText.getText().toString()).isEqualTo(TEST_DEVICE_NAME_1);
    }

    private void initializeSession() {
        List<MediaDevice> selectedDevices = new ArrayList<>();
        selectedDevices.add(mMediaDevice1);
        selectedDevices.add(mMediaDevice2);
        when(mMediaSwitchingController.getSelectableMediaDevice()).thenReturn(selectedDevices);
        when(mMediaSwitchingController.getSelectedMediaDevice()).thenReturn(selectedDevices);
        when(mMediaSwitchingController.getDeselectableMediaDevice()).thenReturn(selectedDevices);

        mMediaOutputAdapter.updateItems();
    }
}
