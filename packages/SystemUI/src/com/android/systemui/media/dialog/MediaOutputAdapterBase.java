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

import static com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_GO_TO_APP;
import static com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_NONE;
import static com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_TRANSFER;
import static com.android.media.flags.Flags.enableOutputSwitcherRedesign;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.media.flags.Flags;
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState;
import com.android.settingslib.media.MediaDevice;
import com.android.systemui.res.R;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A parent RecyclerView adapter for the media output dialog device list. This class doesn't
 * manipulate the layout directly.
 */
public abstract class MediaOutputAdapterBase extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public record OngoingSessionStatus(boolean host) {}

    public record GroupStatus(Boolean selected, Boolean deselectable) {}

    public enum ConnectionState {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
    }

    protected final MediaSwitchingController mController;
    private int mCurrentActivePosition;
    private boolean mIsDragging;
    private static final String TAG = "MediaOutputAdapterBase";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected final List<MediaItem> mMediaItemList = new CopyOnWriteArrayList<>();
    private boolean mShouldGroupSelectedMediaItems = Flags.enableOutputSwitcherDeviceGrouping();

    public MediaOutputAdapterBase(MediaSwitchingController controller) {
        mController = controller;
        mCurrentActivePosition = -1;
        mIsDragging = false;
        setHasStableIds(true);
    }

    boolean isCurrentlyConnected(MediaDevice device) {
        return TextUtils.equals(device.getId(),
                mController.getCurrentConnectedMediaDevice().getId())
                || (mController.getSelectedMediaDevice().size() == 1
                && isDeviceIncluded(mController.getSelectedMediaDevice(), device));
    }

    boolean isDeviceIncluded(List<MediaDevice> deviceList, MediaDevice targetDevice) {
        for (MediaDevice device : deviceList) {
            if (TextUtils.equals(device.getId(), targetDevice.getId())) {
                return true;
            }
        }
        return false;
    }

    boolean isDragging() {
        return mIsDragging;
    }

    void setIsDragging(boolean isDragging) {
        mIsDragging = isDragging;
    }

    int getCurrentActivePosition() {
        return mCurrentActivePosition;
    }

    /** Refreshes the RecyclerView dataset and forces re-render. */
    public void updateItems() {
        mMediaItemList.clear();
        mMediaItemList.addAll(mController.getMediaItemList());
        if (mShouldGroupSelectedMediaItems) {
            if (mController.getSelectedMediaDevice().size() == 1) {
                // Don't group devices if initially there isn't more than one selected.
                mShouldGroupSelectedMediaItems = false;
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        if (position >= mMediaItemList.size()) {
            Log.d(TAG, "Incorrect position for item id: " + position);
            return position;
        }
        MediaItem currentMediaItem = mMediaItemList.get(position);
        return currentMediaItem.getMediaDevice().isPresent()
                ? currentMediaItem.getMediaDevice().get().getId().hashCode()
                : position;
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= mMediaItemList.size()) {
            Log.d(TAG, "Incorrect position for item type: " + position);
            return MediaItem.MediaItemType.TYPE_GROUP_DIVIDER;
        }
        return mMediaItemList.get(position).getMediaItemType();
    }

    @Override
    public int getItemCount() {
        return mMediaItemList.size();
    }

    public abstract class MediaDeviceViewHolderBase extends RecyclerView.ViewHolder {

        Context mContext;

        MediaDeviceViewHolderBase(View view, Context context) {
            super(view);
            mContext = context;
        }

        void renderItem(MediaItem mediaItem, int position) {
            MediaDevice device = mediaItem.getMediaDevice().get();
            boolean isMutingExpectedDeviceExist = mController.hasMutingExpectedDevice();
            final boolean currentlyConnected = isCurrentlyConnected(device);
            boolean isSelected = isDeviceIncluded(mController.getSelectedMediaDevice(), device);
            boolean isDeselectable =
                    isDeviceIncluded(mController.getDeselectableMediaDevice(), device);
            boolean isSelectable = isDeviceIncluded(mController.getSelectableMediaDevice(), device);
            boolean isTransferable =
                    isDeviceIncluded(mController.getTransferableMediaDevices(), device);
            boolean hasRouteListingPreferenceItem = device.hasRouteListingPreferenceItem();

            if (DEBUG) {
                Log.d(
                        TAG,
                        "["
                                + position
                                + "] "
                                + device.getName()
                                + " ["
                                + (isDeselectable ? "deselectable" : "")
                                + "] ["
                                + (isSelected ? "selected" : "")
                                + "] ["
                                + (isSelectable ? "selectable" : "")
                                + "] ["
                                + (isTransferable ? "transferable" : "")
                                + "] ["
                                + (hasRouteListingPreferenceItem ? "hasListingPreference" : "")
                                + "]");
            }

            boolean isDeviceGroup = false;
            boolean hideGroupItem = false;
            GroupStatus groupStatus = null;
            OngoingSessionStatus ongoingSessionStatus = null;
            ConnectionState connectionState = ConnectionState.DISCONNECTED;
            boolean restrictVolumeAdjustment = mController.hasAdjustVolumeUserRestriction();
            String subtitle = null;
            Drawable deviceStatusIcon = null;
            boolean deviceDisabled = false;
            View.OnClickListener clickListener = null;

            if (mCurrentActivePosition == position) {
                mCurrentActivePosition = -1;
            }

            if (mController.isAnyDeviceTransferring()) {
                if (device.getState() == MediaDeviceState.STATE_CONNECTING) {
                    connectionState = ConnectionState.CONNECTING;
                }
            } else {
                // Set different layout for each device
                if (device.isMutingExpectedDevice()
                        && !mController.isCurrentConnectedDeviceRemote()) {
                    connectionState = ConnectionState.CONNECTED;
                    restrictVolumeAdjustment = true;
                    clickListener = v -> onItemClick(v, device);
                } else if (currentlyConnected && isMutingExpectedDeviceExist
                        && !mController.isCurrentConnectedDeviceRemote()) {
                    // mark as disconnected and set special click listener
                    clickListener = v -> cancelMuteAwaitConnection();
                } else if (device.getState() == MediaDeviceState.STATE_GROUPING) {
                    connectionState = ConnectionState.CONNECTING;
                } else if (!enableOutputSwitcherRedesign() && mShouldGroupSelectedMediaItems
                        && hasMultipleSelectedDevices()
                        && isSelected) {
                    if (mediaItem.isFirstDeviceInGroup()) {
                        isDeviceGroup = true;
                    } else {
                        hideGroupItem = true;
                    }
                } else { // A connected or disconnected device.
                    subtitle = device.hasSubtext() ? device.getSubtextString() : null;
                    ongoingSessionStatus = getOngoingSessionStatus(device);
                    groupStatus = getGroupStatus(isSelected, isSelectable, isDeselectable);

                    if (device.getState() == MediaDeviceState.STATE_CONNECTING_FAILED) {
                        deviceStatusIcon = mContext.getDrawable(
                                R.drawable.media_output_status_failed);
                        subtitle = mContext.getString(R.string.media_output_dialog_connect_failed);
                        clickListener = v -> onItemClick(v, device);
                    } else if (currentlyConnected || isSelected) {
                        connectionState = ConnectionState.CONNECTED;
                    } else { // disconnected
                        if (isSelectable) { // groupable device
                            if (!Flags.disableTransferWhenAppsDoNotSupport() || isTransferable
                                    || hasRouteListingPreferenceItem) {
                                clickListener = v -> onItemClick(v, device);
                            }
                        } else {
                            deviceStatusIcon = getDeviceStatusIcon(device);
                            clickListener = getClickListenerBasedOnSelectionBehavior(device);
                        }
                        deviceDisabled = clickListener == null;
                    }
                }
            }

            if (connectionState == ConnectionState.CONNECTED || isDeviceGroup) {
                mCurrentActivePosition = position;
            }

            if (isDeviceGroup) {
                renderDeviceGroupItem();
            } else {
                renderDeviceItem(hideGroupItem, device, connectionState, restrictVolumeAdjustment,
                        groupStatus, ongoingSessionStatus, clickListener, deviceDisabled, subtitle,
                        deviceStatusIcon);
            }
        }

        protected abstract void renderDeviceItem(boolean hideGroupItem, MediaDevice device,
                ConnectionState connectionState, boolean restrictVolumeAdjustment,
                GroupStatus groupStatus, OngoingSessionStatus ongoingSessionStatus,
                View.OnClickListener clickListener, boolean deviceDisabled, String subtitle,
                Drawable deviceStatusIcon);

        protected abstract void renderDeviceGroupItem();

        protected abstract void disableSeekBar();

        private OngoingSessionStatus getOngoingSessionStatus(MediaDevice device) {
            return device.hasOngoingSession() ? new OngoingSessionStatus(
                    device.isHostForOngoingSession()) : null;
        }

        private GroupStatus getGroupStatus(boolean isSelected, boolean isSelectable,
                boolean isDeselectable) {
            // A device should either be selectable or, when the device selected, the list should
            // have other selectable or selected devices.
            boolean selectedWithOtherGroupDevices =
                    isSelected && (hasMultipleSelectedDevices() || hasSelectableDevices());
            if (isSelectable || selectedWithOtherGroupDevices) {
                return new GroupStatus(isSelected, isDeselectable);
            }
            return null;
        }

        private boolean hasMultipleSelectedDevices() {
            return mController.getSelectedMediaDevice().size() > 1;
        }

        private boolean hasSelectableDevices() {
            return !mController.getSelectableMediaDevice().isEmpty();
        }

        @Nullable
        private View.OnClickListener getClickListenerBasedOnSelectionBehavior(
                @NonNull MediaDevice device) {
            return Api34Impl.getClickListenerBasedOnSelectionBehavior(
                    device, mController, v -> onItemClick(v, device));
        }

        @Nullable
        private Drawable getDeviceStatusIcon(MediaDevice device) {
            return Api34Impl.getDeviceStatusIconBasedOnSelectionBehavior(device, mContext);
        }

        protected void onExpandGroupButtonClicked() {
            mShouldGroupSelectedMediaItems = false;
            notifyDataSetChanged();
        }

        protected void onGroupActionTriggered(boolean isChecked, MediaDevice device) {
            disableSeekBar();
            if (isChecked && isDeviceIncluded(mController.getSelectableMediaDevice(), device)) {
                mController.addDeviceToPlayMedia(device);
            } else if (!isChecked && isDeviceIncluded(mController.getDeselectableMediaDevice(),
                    device)) {
                mController.removeDeviceFromPlayMedia(device);
            }
        }

        private void onItemClick(View view, MediaDevice device) {
            if (mController.isCurrentOutputDeviceHasSessionOngoing()) {
                showCustomEndSessionDialog(device);
            } else {
                transferOutput(device);
            }
        }

        private void transferOutput(MediaDevice device) {
            if (mController.isAnyDeviceTransferring()) {
                return;
            }
            if (isCurrentlyConnected(device)) {
                Log.d(TAG, "This device is already connected! : " + device.getName());
                return;
            }
            mController.setTemporaryAllowListExceptionIfNeeded(device);
            mCurrentActivePosition = -1;
            mController.connectDevice(device);
            device.setState(MediaDeviceState.STATE_CONNECTING);
            notifyDataSetChanged();
        }

        @VisibleForTesting
        void showCustomEndSessionDialog(MediaDevice device) {
            MediaSessionReleaseDialog mediaSessionReleaseDialog = new MediaSessionReleaseDialog(
                    mContext, () -> transferOutput(device),
                    mController.getColorSchemeLegacy().getColorButtonBackground(),
                    mController.getColorSchemeLegacy().getColorItemContent());
            mediaSessionReleaseDialog.show();
        }

        private void cancelMuteAwaitConnection() {
            mController.cancelMuteAwaitConnection();
            notifyDataSetChanged();
        }

        protected String getDeviceItemContentDescription(@NonNull MediaDevice device) {
            return mContext.getString(
                    device.getDeviceType() == MediaDevice.MediaDeviceType.TYPE_BLUETOOTH_DEVICE
                            ? R.string.accessibility_bluetooth_name
                            : R.string.accessibility_cast_name, device.getName());
        }

        protected String getGroupItemContentDescription(String sessionName) {
            return mContext.getString(R.string.accessibility_cast_name, sessionName);
        }
    }

    @RequiresApi(34)
    private static class Api34Impl {
        @DoNotInline
        static View.OnClickListener getClickListenerBasedOnSelectionBehavior(
                MediaDevice device,
                MediaSwitchingController controller,
                View.OnClickListener defaultTransferListener) {
            switch (device.getSelectionBehavior()) {
                case SELECTION_BEHAVIOR_NONE:
                    return null;
                case SELECTION_BEHAVIOR_TRANSFER:
                    return defaultTransferListener;
                case SELECTION_BEHAVIOR_GO_TO_APP:
                    return v -> controller.tryToLaunchInAppRoutingIntent(device.getId(), v);
            }
            return defaultTransferListener;
        }

        @DoNotInline
        @Nullable
        static Drawable getDeviceStatusIconBasedOnSelectionBehavior(MediaDevice device,
                Context context) {
            switch (device.getSelectionBehavior()) {
                case SELECTION_BEHAVIOR_NONE:
                    return context.getDrawable(R.drawable.media_output_status_failed);
                case SELECTION_BEHAVIOR_TRANSFER:
                    return null;
                case SELECTION_BEHAVIOR_GO_TO_APP:
                    return context.getDrawable(R.drawable.media_output_status_help);
            }
            return null;
        }
    }
}
