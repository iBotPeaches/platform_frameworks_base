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

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

import com.android.media.flags.Flags;
import com.android.settingslib.media.InputMediaDevice;
import com.android.settingslib.media.MediaDevice;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.res.R;

import java.util.concurrent.Executor;
/**
 * A RecyclerView adapter for the legacy UI media output dialog device list.
 */
public class MediaOutputAdapterLegacy extends MediaOutputAdapterBase {
    private static final String TAG = "MediaOutputAdapterL";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int UNMUTE_DEFAULT_VOLUME = 2;
    @VisibleForTesting static final float DEVICE_DISABLED_ALPHA = 0.5f;
    @VisibleForTesting static final float DEVICE_ACTIVE_ALPHA = 1f;
    private final Executor mMainExecutor;
    private final Executor mBackgroundExecutor;
    View mHolderView;

    public MediaOutputAdapterLegacy(
            MediaSwitchingController controller,
            @Main Executor mainExecutor,
            @Background Executor backgroundExecutor
    ) {
        super(controller);
        mMainExecutor = mainExecutor;
        mBackgroundExecutor = backgroundExecutor;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup,
            int viewType) {

        Context context = viewGroup.getContext();
        mHolderView = LayoutInflater.from(viewGroup.getContext()).inflate(
                MediaItem.getMediaLayoutId(viewType),
                viewGroup, false);

        switch (viewType) {
            case MediaItem.MediaItemType.TYPE_GROUP_DIVIDER:
                return new MediaGroupDividerViewHolderLegacy(mHolderView);
            case MediaItem.MediaItemType.TYPE_PAIR_NEW_DEVICE:
            case MediaItem.MediaItemType.TYPE_DEVICE:
            default:
                return new MediaDeviceViewHolderLegacy(mHolderView, context);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (position >= getItemCount()) {
            if (DEBUG) {
                Log.d(TAG, "Incorrect position: " + position + " list size: "
                        + getItemCount());
            }
            return;
        }
        MediaItem currentMediaItem = mMediaItemList.get(position);
        switch (currentMediaItem.getMediaItemType()) {
            case MediaItem.MediaItemType.TYPE_GROUP_DIVIDER:
                ((MediaGroupDividerViewHolderLegacy) viewHolder).onBind(
                        currentMediaItem.getTitle());
                break;
            case MediaItem.MediaItemType.TYPE_PAIR_NEW_DEVICE:
                ((MediaDeviceViewHolderLegacy) viewHolder).onBindPairNewDevice();
                break;
            case MediaItem.MediaItemType.TYPE_DEVICE:
                ((MediaDeviceViewHolderLegacy) viewHolder).onBindDevice(currentMediaItem, position);
                break;
            default:
                Log.d(TAG, "Incorrect position: " + position);
        }
    }

    public MediaSwitchingController getController() {
        return mController;
    }

    /**
     * ViewHolder for binding device view.
     */
    class MediaDeviceViewHolderLegacy extends MediaDeviceViewHolderBase {

        private static final int ANIM_DURATION = 500;

        final ViewGroup mContainerLayout;
        final FrameLayout mItemLayout;
        final FrameLayout mIconAreaLayout;
        final ViewGroup mTextContent;
        final TextView mTitleText;
        final TextView mSubTitleText;
        final TextView mVolumeValueText;
        final ImageView mTitleIcon;
        final ProgressBar mProgressBar;
        final ImageView mStatusIcon;
        final CheckBox mCheckBox;
        final ViewGroup mEndTouchArea;
        final ImageButton mEndClickIcon;
        @VisibleForTesting
        MediaOutputSeekbar mSeekBar;
        private final float mInactiveRadius;
        private final float mActiveRadius;
        private String mDeviceId;
        private ValueAnimator mCornerAnimator;
        private ValueAnimator mVolumeAnimator;
        private int mLatestUpdateVolume = -1;

        MediaDeviceViewHolderLegacy(View view, Context context) {
            super(view, context);
            mContainerLayout = view.requireViewById(R.id.device_container);
            mItemLayout = view.requireViewById(R.id.item_layout);
            mTextContent = view.requireViewById(R.id.text_content);
            mTitleText = view.requireViewById(R.id.title);
            mSubTitleText = view.requireViewById(R.id.subtitle);
            mTitleIcon = view.requireViewById(R.id.title_icon);
            mProgressBar = view.requireViewById(R.id.volume_indeterminate_progress);
            mSeekBar = view.requireViewById(R.id.volume_seekbar);
            mStatusIcon = view.requireViewById(R.id.media_output_item_status);
            mCheckBox = view.requireViewById(R.id.check_box);
            mEndTouchArea = view.requireViewById(R.id.end_action_area);
            mEndClickIcon = view.requireViewById(R.id.end_area_image_button);
            mVolumeValueText = view.requireViewById(R.id.volume_value);
            mIconAreaLayout = view.requireViewById(R.id.icon_area);
            mInactiveRadius = mContext.getResources().getDimension(
                    R.dimen.media_output_dialog_background_radius);
            mActiveRadius = mContext.getResources().getDimension(
                    R.dimen.media_output_dialog_active_background_radius);
            initAnimator();
        }

        void onBindDevice(MediaItem mediaItem, int position) {
            MediaDevice device = mediaItem.getMediaDevice().get();
            mDeviceId = device.getId();
            mItemLayout.setVisibility(View.VISIBLE);
            mCheckBox.setVisibility(View.GONE);
            mStatusIcon.setVisibility(View.GONE);
            mEndTouchArea.setVisibility(View.GONE);
            mEndClickIcon.setVisibility(View.GONE);
            mContainerLayout.setOnClickListener(null);
            mTitleText.setTextColor(mController.getColorSchemeLegacy().getColorItemContent());
            mSubTitleText.setTextColor(mController.getColorSchemeLegacy().getColorItemContent());
            mVolumeValueText.setTextColor(mController.getColorSchemeLegacy().getColorItemContent());
            mIconAreaLayout.setBackground(null);
            updateIconAreaClickListener(null);
            updateSeekBarProgressColor();
            updateContainerContentA11yImportance(true  /* isImportant */);
            renderItem(mediaItem, position);
        }

        /** Binds a ViewHolder for a "Connect a device" item. */
        void onBindPairNewDevice() {
            mTitleText.setTextColor(mController.getColorSchemeLegacy().getColorItemContent());
            mCheckBox.setVisibility(View.GONE);
            updateTitle(mContext.getText(R.string.media_output_dialog_pairing_new));
            updateItemBackground(ConnectionState.DISCONNECTED);
            final Drawable addDrawable = mContext.getDrawable(R.drawable.ic_add);
            mTitleIcon.setImageDrawable(addDrawable);
            mTitleIcon.setImageTintList(ColorStateList.valueOf(
                    mController.getColorSchemeLegacy().getColorItemContent()));
            mContainerLayout.setOnClickListener(mController::launchBluetoothPairing);
        }

        @Override
        protected void renderDeviceItem(boolean hideGroupItem, MediaDevice device,
                ConnectionState connectionState, boolean restrictVolumeAdjustment,
                GroupStatus groupStatus, OngoingSessionStatus ongoingSessionStatus,
                View.OnClickListener clickListener, boolean deviceDisabled, String subtitle,
                Drawable deviceStatusIcon) {
            if (hideGroupItem) {
                mItemLayout.setVisibility(View.GONE);
                return;
            }
            updateTitle(device.getName());
            updateTitleIcon(device, connectionState, restrictVolumeAdjustment);
            updateSeekBar(device, connectionState, restrictVolumeAdjustment,
                    getDeviceItemContentDescription(device));
            updateEndArea(device, connectionState, groupStatus, ongoingSessionStatus);
            updateLoadingIndicator(connectionState);
            updateFullItemClickListener(clickListener);
            updateContentAlpha(deviceDisabled);
            updateSubtitle(subtitle);
            updateDeviceStatusIcon(deviceStatusIcon, ongoingSessionStatus, connectionState);
            updateItemBackground(connectionState);
        }

        @Override
        protected void renderDeviceGroupItem() {
            String sessionName = mController.getSessionName() == null ? ""
                    : mController.getSessionName().toString();
            updateTitle(sessionName);
            updateUnmutedVolumeIcon(null /* device */);
            updateGroupSeekBar(getGroupItemContentDescription(sessionName));
            updateEndAreaForDeviceGroup();
            updateItemBackground(ConnectionState.CONNECTED);
        }

        void updateTitle(CharSequence title) {
            mTitleText.setText(title);
        }

        void updateSeekBar(@NonNull MediaDevice device, ConnectionState connectionState,
                boolean restrictVolumeAdjustment, String contentDescription) {
            boolean showSeekBar =
                    connectionState == ConnectionState.CONNECTED && !restrictVolumeAdjustment;
            if (!mCornerAnimator.isRunning()) {
                if (showSeekBar) {
                    updateSeekbarProgressBackground();
                }
            }
            mSeekBar.setVisibility(showSeekBar ? View.VISIBLE : View.GONE);
            if (showSeekBar) {
                initSeekbar(device);
                updateContainerContentA11yImportance(false /* isImportant */);
                mSeekBar.setContentDescription(contentDescription);
            } else {
                updateContainerContentA11yImportance(true /* isImportant */);
            }
        }

        void updateGroupSeekBar(String contentDescription) {
            updateSeekbarProgressBackground();
            mSeekBar.setVisibility(View.VISIBLE);
            initGroupSeekbar();
            updateContainerContentA11yImportance(false /* isImportant */);
            mSeekBar.setContentDescription(contentDescription);
        }

        /**
         * Sets the a11y importance for the device container and it's text content. Making the
         * container not important for a11y is required when the seekbar is visible.
         */
        private void updateContainerContentA11yImportance(boolean isImportant) {
            mContainerLayout.setFocusable(isImportant);
            mContainerLayout.setImportantForAccessibility(
                    isImportant ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                            : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mTextContent.setImportantForAccessibility(
                    isImportant ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                            : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        void updateSubtitle(@Nullable String subtitle) {
            if (subtitle == null) {
                mSubTitleText.setVisibility(View.GONE);
            } else {
                mSubTitleText.setText(subtitle);
                mSubTitleText.setVisibility(View.VISIBLE);
            }
        }

        protected void updateLoadingIndicator(ConnectionState connectionState) {
            if (connectionState == ConnectionState.CONNECTING) {
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressBar.getIndeterminateDrawable().setTintList(ColorStateList.valueOf(
                        mController.getColorSchemeLegacy().getColorItemContent()));
            } else {
                mProgressBar.setVisibility(View.GONE);
            }
        }

        protected void updateItemBackground(ConnectionState connectionState) {
            boolean isConnected = connectionState == ConnectionState.CONNECTED;
            boolean isConnecting = connectionState == ConnectionState.CONNECTING;

            // Increase corner radius for a connected state.
            if (!mCornerAnimator.isRunning()) {  // FIXME(b/387576145): This is always True.
                int backgroundDrawableId =
                        isConnected ? R.drawable.media_output_item_background_active
                                : R.drawable.media_output_item_background;
                mItemLayout.setBackground(mContext.getDrawable(backgroundDrawableId).mutate());
            }

            // Connected or connecting state has a darker background.
            int backgroundColor = isConnected || isConnecting
                    ? mController.getColorSchemeLegacy().getColorConnectedItemBackground()
                    : mController.getColorSchemeLegacy().getColorItemBackground();
            mItemLayout.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        }

        protected void updateEndAreaVisibility(boolean showEndTouchArea, boolean isCheckbox) {
            mEndTouchArea.setVisibility(showEndTouchArea ? View.VISIBLE : View.GONE);
            if (showEndTouchArea) {
                mCheckBox.setVisibility(isCheckbox ? View.VISIBLE : View.GONE);
                mEndClickIcon.setVisibility(!isCheckbox ? View.VISIBLE : View.GONE);
            }
        }

        private void updateSeekBarProgressColor() {
            mSeekBar.setProgressTintList(ColorStateList.valueOf(
                    mController.getColorSchemeLegacy().getColorSeekbarProgress()));
            final Drawable contrastDotDrawable =
                    ((LayerDrawable) mSeekBar.getProgressDrawable()).findDrawableByLayerId(
                            R.id.contrast_dot);
            contrastDotDrawable.setTintList(ColorStateList.valueOf(
                    mController.getColorSchemeLegacy().getColorItemContent()));
        }

        void updateSeekbarProgressBackground() {
            final ClipDrawable clipDrawable =
                    (ClipDrawable) ((LayerDrawable) mSeekBar.getProgressDrawable())
                            .findDrawableByLayerId(android.R.id.progress);
            final GradientDrawable progressDrawable =
                    (GradientDrawable) clipDrawable.getDrawable();
            progressDrawable.setCornerRadii(
                    new float[]{0, 0, mActiveRadius,
                            mActiveRadius,
                            mActiveRadius,
                            mActiveRadius, 0, 0});
        }

        private void initializeSeekbarVolume(@Nullable MediaDevice device, int currentVolume) {
            if (!isDragging()) {
                if (mSeekBar.getVolume() != currentVolume && (mLatestUpdateVolume == -1
                        || currentVolume == mLatestUpdateVolume)) {
                    // Update only if volume of device and value of volume bar doesn't match.
                    // Check if response volume match with the latest request, to ignore obsolete
                    // response
                    if (!mVolumeAnimator.isStarted()) {
                        if (currentVolume == 0) {
                            updateMutedVolumeIcon(device);
                        } else {
                            updateUnmutedVolumeIcon(device);
                        }
                        mSeekBar.setVolume(currentVolume);
                        mLatestUpdateVolume = -1;
                    }
                } else if (currentVolume == 0) {
                    mSeekBar.resetVolume();
                    updateMutedVolumeIcon(device);
                }
                if (currentVolume == mLatestUpdateVolume) {
                    mLatestUpdateVolume = -1;
                }
            }
        }

        void initSeekbar(@NonNull MediaDevice device) {
            SeekBarVolumeControl volumeControl = new SeekBarVolumeControl() {
                @Override
                public int getVolume() {
                    return device.getCurrentVolume();
                }
                @Override
                public void setVolume(int volume) {
                    mController.adjustVolume(device, volume);
                }

                @Override
                public void onMute() {
                    mController.logInteractionMuteDevice(device);
                }

                @Override
                public void onUnmute() {
                    mController.logInteractionUnmuteDevice(device);
                }
            };

            if (!mController.isVolumeControlEnabled(device)) {
                disableSeekBar();
            } else {
                enableSeekBar(volumeControl);
            }
            mSeekBar.setMaxVolume(device.getMaxVolume());
            final int currentVolume = device.getCurrentVolume();
            initializeSeekbarVolume(device, currentVolume);

            mSeekBar.setOnSeekBarChangeListener(new MediaSeekBarChangedListener(
                    device, volumeControl) {
                @Override
                public void onStopTrackingTouch(SeekBar seekbar) {
                    super.onStopTrackingTouch(seekbar);
                    mController.logInteractionAdjustVolume(device);
                }
            });
        }

        // Initializes the seekbar for a group of devices.
        void initGroupSeekbar() {
            SeekBarVolumeControl volumeControl = new SeekBarVolumeControl() {
                @Override
                public int getVolume() {
                    return mController.getSessionVolume();
                }

                @Override
                public void setVolume(int volume) {
                    mController.adjustSessionVolume(volume);
                }

                @Override
                public void onMute() {}

                @Override
                public void onUnmute() {}
            };

            if (!mController.isVolumeControlEnabledForSession()) {
                disableSeekBar();
            } else {
                enableSeekBar(volumeControl);
            }
            mSeekBar.setMaxVolume(mController.getSessionVolumeMax());

            final int currentVolume = mController.getSessionVolume();
            initializeSeekbarVolume(null, currentVolume);
            mSeekBar.setOnSeekBarChangeListener(new MediaSeekBarChangedListener(
                    null, volumeControl) {
                @Override
                protected boolean shouldHandleProgressChanged() {
                    return true;
                }
            });
        }

        protected void updateTitleIcon(@NonNull MediaDevice device,
                ConnectionState connectionState, boolean restrictVolumeAdjustment) {
            if (connectionState == ConnectionState.CONNECTED) {
                if (restrictVolumeAdjustment) {
                    // Volume icon without a background that makes it looks like part of a seekbar.
                    updateVolumeIcon(device, false /* isMutedIcon */);
                } else {
                    updateUnmutedVolumeIcon(device);
                }
            } else {
                setUpDeviceIcon(device);
            }
        }

        void updateMutedVolumeIcon(@Nullable MediaDevice device) {
            mIconAreaLayout.setBackground(
                    mContext.getDrawable(R.drawable.media_output_item_background_active));
            updateVolumeIcon(device, true /* isMutedVolumeIcon */);
        }

        void updateUnmutedVolumeIcon(@Nullable MediaDevice device) {
            mIconAreaLayout.setBackground(
                    mContext.getDrawable(R.drawable.media_output_title_icon_area)
            );
            updateVolumeIcon(device, false /* isMutedVolumeIcon */);
        }

        void updateVolumeIcon(@Nullable MediaDevice device, boolean isMutedVolumeIcon) {
            boolean isInputMediaDevice = device instanceof InputMediaDevice;
            int id = getDrawableId(isInputMediaDevice, isMutedVolumeIcon);
            mTitleIcon.setImageDrawable(mContext.getDrawable(id));
            mTitleIcon.setImageTintList(ColorStateList.valueOf(
                    mController.getColorSchemeLegacy().getColorItemContent()));
            mIconAreaLayout.setBackgroundTintList(ColorStateList.valueOf(
                    mController.getColorSchemeLegacy().getColorSeekbarProgress()));
        }

        @VisibleForTesting
        int getDrawableId(boolean isInputDevice, boolean isMutedVolumeIcon) {
            // Returns the microphone icon when the flag is enabled and the device is an input
            // device.
            if (Flags.enableAudioInputDeviceRoutingAndVolumeControl()
                    && isInputDevice) {
                return isMutedVolumeIcon ? R.drawable.ic_mic_off : R.drawable.ic_mic_26dp;
            }
            return isMutedVolumeIcon
                    ? R.drawable.media_output_icon_volume_off
                    : R.drawable.media_output_icon_volume;
        }

        private void updateContentAlpha(boolean deviceDisabled) {
            float alphaValue = deviceDisabled ? DEVICE_DISABLED_ALPHA : DEVICE_ACTIVE_ALPHA;
            mTitleIcon.setAlpha(alphaValue);
            mTitleText.setAlpha(alphaValue);
            mSubTitleText.setAlpha(alphaValue);
            mStatusIcon.setAlpha(alphaValue);
        }

        private void updateDeviceStatusIcon(@Nullable Drawable deviceStatusIcon,
                @Nullable OngoingSessionStatus ongoingSessionStatus,
                ConnectionState connectionState) {
            boolean showOngoingSession =
                    ongoingSessionStatus != null && connectionState == ConnectionState.DISCONNECTED;
            if (deviceStatusIcon == null && !showOngoingSession) {
                mStatusIcon.setVisibility(View.GONE);
            } else {
                if (showOngoingSession) {
                    mStatusIcon.setImageDrawable(
                            mContext.getDrawable(R.drawable.ic_sound_bars_anim));
                } else {
                    mStatusIcon.setImageDrawable(deviceStatusIcon);
                }
                mStatusIcon.setImageTintList(ColorStateList.valueOf(
                        mController.getColorSchemeLegacy().getColorItemContent()));
                if (deviceStatusIcon instanceof AnimatedVectorDrawable) {
                    ((AnimatedVectorDrawable) deviceStatusIcon).start();
                }
                mStatusIcon.setVisibility(View.VISIBLE);
            }
        }


        /** Renders the right side round pill button / checkbox. */
        private void updateEndArea(@NonNull MediaDevice device, ConnectionState connectionState,
                @Nullable GroupStatus groupStatus,
                @Nullable OngoingSessionStatus ongoingSessionStatus) {
            boolean showEndArea = false;
            boolean isCheckbox = false;
            // If both group status and the ongoing session status are present, only the ongoing
            // session controls are displayed. The current layout design doesn't allow both group
            // and ongoing session controls to be rendered simultaneously.
            if (ongoingSessionStatus != null && connectionState == ConnectionState.CONNECTED) {
                showEndArea = true;
                updateEndAreaForOngoingSession(device, ongoingSessionStatus.host());
            } else if (groupStatus != null && shouldShowGroupCheckbox(groupStatus)) {
                showEndArea = true;
                isCheckbox = true;
                updateEndAreaForGroupCheckBox(device, groupStatus);
            }
            updateEndAreaVisibility(showEndArea, isCheckbox);
        }

        private void updateEndAreaForDeviceGroup() {
            updateEndAreaWithIcon(
                    v -> {
                        onExpandGroupButtonClicked();
                    },
                    R.drawable.media_output_item_expand_group,
                    R.string.accessibility_expand_group);
            updateEndAreaVisibility(true /* showEndArea */, false /* isCheckbox */);
        }

        private void updateEndAreaForOngoingSession(@NonNull MediaDevice device, boolean isHost) {
            updateEndAreaWithIcon(
                    v -> mController.tryToLaunchInAppRoutingIntent(device.getId(), v),
                    isHost ? R.drawable.media_output_status_edit_session
                            : R.drawable.ic_sound_bars_anim,
                    R.string.accessibility_open_application);
        }

        private void updateEndAreaWithIcon(View.OnClickListener clickListener,
                @DrawableRes int iconDrawableId,
                @StringRes int accessibilityStringId) {
            updateEndAreaColor(mController.getColorSchemeLegacy().getColorSeekbarProgress());
            mEndClickIcon.setImageTintList(
                    ColorStateList.valueOf(
                            mController.getColorSchemeLegacy().getColorItemContent()));
            mEndClickIcon.setOnClickListener(clickListener);
            Drawable drawable = mContext.getDrawable(iconDrawableId);
            mEndClickIcon.setImageDrawable(drawable);
            if (drawable instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) drawable).start();
            }
            mEndClickIcon.setContentDescription(mContext.getString(accessibilityStringId));
        }

        private void updateEndAreaForGroupCheckBox(@NonNull MediaDevice device,
                @NonNull GroupStatus groupStatus) {
            boolean isEnabled = isGroupCheckboxEnabled(groupStatus);
            updateEndAreaColor(groupStatus.selected()
                    ? mController.getColorSchemeLegacy().getColorSeekbarProgress()
                    : mController.getColorSchemeLegacy().getColorItemBackground());
            mCheckBox.setContentDescription(mContext.getString(
                    groupStatus.selected() ? R.string.accessibility_remove_device_from_group
                            : R.string.accessibility_add_device_to_group));
            mCheckBox.setOnCheckedChangeListener(null);
            mCheckBox.setChecked(groupStatus.selected());
            mCheckBox.setOnCheckedChangeListener(
                    isEnabled ? (buttonView, isChecked) -> onGroupActionTriggered(
                            !groupStatus.selected(), device) : null);
            mCheckBox.setEnabled(isEnabled);
            setCheckBoxColor(mCheckBox, mController.getColorSchemeLegacy().getColorItemContent());
        }

        private void setCheckBoxColor(CheckBox checkBox, int color) {
            checkBox.setForegroundTintList(ColorStateList.valueOf(color));
        }

        private boolean shouldShowGroupCheckbox(@NonNull GroupStatus groupStatus) {
            if (Flags.enableOutputSwitcherDeviceGrouping()) {
                return isGroupCheckboxEnabled(groupStatus);
            }
            return true;
        }

        private boolean isGroupCheckboxEnabled(@NonNull GroupStatus groupStatus) {
            boolean disabled = groupStatus.selected() && !groupStatus.deselectable();
            return !disabled;
        }

        private void updateEndAreaColor(int color) {
            mEndTouchArea.setBackgroundTintList(
                    ColorStateList.valueOf(color));
        }

        private void updateFullItemClickListener(@Nullable View.OnClickListener listener) {
            mContainerLayout.setOnClickListener(listener);
        }

        void updateIconAreaClickListener(@Nullable View.OnClickListener listener) {
            mIconAreaLayout.setOnClickListener(listener);
            if (listener == null) {
                mIconAreaLayout.setClickable(false); // clickable is not removed automatically.
            }
        }

        private void initAnimator() {
            mCornerAnimator = ValueAnimator.ofFloat(mInactiveRadius, mActiveRadius);
            mCornerAnimator.setDuration(ANIM_DURATION);
            mCornerAnimator.setInterpolator(new LinearInterpolator());

            mVolumeAnimator = ValueAnimator.ofInt();
            mVolumeAnimator.addUpdateListener(animation -> {
                int value = (int) animation.getAnimatedValue();
                mSeekBar.setProgress(value);
            });
            mVolumeAnimator.setDuration(ANIM_DURATION);
            mVolumeAnimator.setInterpolator(new LinearInterpolator());
            mVolumeAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mSeekBar.setEnabled(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mSeekBar.setEnabled(true);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mSeekBar.setEnabled(true);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }

        @Override
        protected void disableSeekBar() {
            mSeekBar.setEnabled(false);
            mSeekBar.setOnTouchListener((v, event) -> true);
            updateIconAreaClickListener(null);
        }

        private void enableSeekBar(SeekBarVolumeControl volumeControl) {
            mSeekBar.setEnabled(true);

            mSeekBar.setOnTouchListener((v, event) -> false);
            updateIconAreaClickListener((v) -> {
                if (volumeControl.getVolume() == 0) {
                    volumeControl.onUnmute();
                    mSeekBar.setVolume(UNMUTE_DEFAULT_VOLUME);
                    volumeControl.setVolume(UNMUTE_DEFAULT_VOLUME);
                    updateUnmutedVolumeIcon(null);
                    mIconAreaLayout.setOnTouchListener(((iconV, event) -> false));
                } else {
                    volumeControl.onMute();
                    mSeekBar.resetVolume();
                    volumeControl.setVolume(0);
                    updateMutedVolumeIcon(null);
                    mIconAreaLayout.setOnTouchListener(((iconV, event) -> {
                        mSeekBar.dispatchTouchEvent(event);
                        return false;
                    }));
                }
            });

        }

        protected void setUpDeviceIcon(@NonNull MediaDevice device) {
            mBackgroundExecutor.execute(() -> {
                Icon icon = mController.getDeviceIconCompat(device).toIcon(mContext);
                mMainExecutor.execute(() -> {
                    if (!TextUtils.equals(mDeviceId, device.getId())) {
                        return;
                    }
                    mTitleIcon.setImageIcon(icon);
                    mTitleIcon.setImageTintList(ColorStateList.valueOf(
                            mController.getColorSchemeLegacy().getColorItemContent()));
                });
            });
        }

        interface SeekBarVolumeControl {
            int getVolume();
            void setVolume(int volume);
            void onMute();
            void onUnmute();
        }

        private abstract class MediaSeekBarChangedListener
                implements SeekBar.OnSeekBarChangeListener {
            boolean mStartFromMute = false;
            private MediaDevice mMediaDevice;
            private SeekBarVolumeControl mVolumeControl;

            MediaSeekBarChangedListener(MediaDevice device, SeekBarVolumeControl volumeControl) {
                mMediaDevice = device;
                mVolumeControl = volumeControl;
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!shouldHandleProgressChanged() || !fromUser) {
                    return;
                }

                final String percentageString = mContext.getResources().getString(
                        R.string.media_output_dialog_volume_percentage,
                        mSeekBar.getPercentage());
                mVolumeValueText.setText(percentageString);

                if (mStartFromMute) {
                    updateUnmutedVolumeIcon(mMediaDevice);
                    mStartFromMute = false;
                }

                int seekBarVolume = MediaOutputSeekbar.scaleProgressToVolume(progress);
                if (seekBarVolume != mVolumeControl.getVolume()) {
                    mLatestUpdateVolume = seekBarVolume;
                    mVolumeControl.setVolume(seekBarVolume);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mTitleIcon.setVisibility(View.INVISIBLE);
                mVolumeValueText.setVisibility(View.VISIBLE);
                int currentVolume = MediaOutputSeekbar.scaleProgressToVolume(
                        seekBar.getProgress());
                mStartFromMute = (currentVolume == 0);
                setIsDragging(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int currentVolume = MediaOutputSeekbar.scaleProgressToVolume(
                        seekBar.getProgress());
                if (currentVolume == 0) {
                    seekBar.setProgress(0);
                    updateMutedVolumeIcon(mMediaDevice);
                } else {
                    updateUnmutedVolumeIcon(mMediaDevice);
                }
                mTitleIcon.setVisibility(View.VISIBLE);
                mVolumeValueText.setVisibility(View.GONE);
                setIsDragging(false);
            }
            protected boolean shouldHandleProgressChanged() {
                return mMediaDevice != null;
            }
        };
    }

    class MediaGroupDividerViewHolderLegacy extends RecyclerView.ViewHolder {
        final TextView mTitleText;

        MediaGroupDividerViewHolderLegacy(@NonNull View itemView) {
            super(itemView);
            mTitleText = itemView.requireViewById(R.id.title);
        }

        void onBind(String groupDividerTitle) {
            mTitleText.setTextColor(mController.getColorSchemeLegacy().getColorItemContent());
            mTitleText.setText(groupDividerTitle);
        }
    }
}
