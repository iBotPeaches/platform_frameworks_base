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

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.media.flags.Flags.enableOutputSwitcherRedesign;
import static com.android.systemui.FontStyles.GSF_LABEL_LARGE;
import static com.android.systemui.FontStyles.GSF_TITLE_MEDIUM_EMPHASIZED;
import static com.android.systemui.FontStyles.GSF_TITLE_SMALL;

import android.annotation.NonNull;
import android.app.WallpaperColors;
import android.bluetooth.BluetoothLeBroadcast;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.IconCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Base dialog for media output UI */
public abstract class MediaOutputBaseDialog extends SystemUIDialog
        implements MediaSwitchingController.Callback, Window.Callback {

    private static final String TAG = "MediaOutputDialog";
    private static final String PREF_NAME = "MediaOutputDialog";
    private static final String PREF_IS_LE_BROADCAST_FIRST_LAUNCH = "PrefIsLeBroadcastFirstLaunch";
    private static final boolean DEBUG = true;
    private static final int HANDLE_BROADCAST_FAILED_DELAY = 3000;

    protected final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final LinearLayoutManager mLayoutManager;

    final Context mContext;
    final MediaSwitchingController mMediaSwitchingController;
    final BroadcastSender mBroadcastSender;

    /**
     * Signals whether the dialog should NOT show app-related metadata.
     *
     * <p>A metadata-less dialog hides the title, subtitle, and app icon in the header.
     */
    private final boolean mIncludePlaybackAndAppMetadata;

    @VisibleForTesting
    View mDialogView;
    private TextView mHeaderTitle;
    private TextView mHeaderSubtitle;
    private ImageView mHeaderIcon;
    private ImageView mAppResourceIcon;
    private ImageView mBroadcastIcon;
    private RecyclerView mDevicesRecyclerView;
    private ViewGroup mDeviceListLayout;
    private ViewGroup mQuickAccessShelf;
    private MaterialButton mConnectDeviceButton;
    private LinearLayout mMediaMetadataSectionLayout;
    private Button mDoneButton;
    private ViewGroup mDialogFooter;
    private View mFooterSpacer;
    private Button mStopButton;
    private WallpaperColors mWallpaperColors;
    private boolean mShouldLaunchLeBroadcastDialog;
    private boolean mIsLeBroadcastCallbackRegistered;
    private boolean mDismissing;

    MediaOutputAdapterBase mAdapter;

    protected Executor mExecutor;


    private final BluetoothLeBroadcast.Callback mBroadcastCallback =
            new BluetoothLeBroadcast.Callback() {
                @Override
                public void onBroadcastStarted(int reason, int broadcastId) {
                    if (DEBUG) {
                        Log.d(TAG, "onBroadcastStarted(), reason = " + reason
                                + ", broadcastId = " + broadcastId);
                    }
                    mMainThreadHandler.post(() -> handleLeBroadcastStarted());
                }

                @Override
                public void onBroadcastStartFailed(int reason) {
                    if (DEBUG) {
                        Log.d(TAG, "onBroadcastStartFailed(), reason = " + reason);
                    }
                    mMainThreadHandler.postDelayed(() -> handleLeBroadcastStartFailed(),
                            HANDLE_BROADCAST_FAILED_DELAY);
                }

                @Override
                public void onBroadcastMetadataChanged(int broadcastId,
                        @NonNull BluetoothLeBroadcastMetadata metadata) {
                    if (DEBUG) {
                        Log.d(TAG, "onBroadcastMetadataChanged(), broadcastId = " + broadcastId
                                + ", metadata = " + metadata);
                    }
                    mMainThreadHandler.post(() -> handleLeBroadcastMetadataChanged());
                }

                @Override
                public void onBroadcastStopped(int reason, int broadcastId) {
                    if (DEBUG) {
                        Log.d(TAG, "onBroadcastStopped(), reason = " + reason
                                + ", broadcastId = " + broadcastId);
                    }
                    mMainThreadHandler.post(() -> handleLeBroadcastStopped());
                }

                @Override
                public void onBroadcastStopFailed(int reason) {
                    if (DEBUG) {
                        Log.d(TAG, "onBroadcastStopFailed(), reason = " + reason);
                    }
                    mMainThreadHandler.post(() -> handleLeBroadcastStopFailed());
                }

                @Override
                public void onBroadcastUpdated(int reason, int broadcastId) {
                    if (DEBUG) {
                        Log.d(TAG, "onBroadcastUpdated(), reason = " + reason
                                + ", broadcastId = " + broadcastId);
                    }
                    mMainThreadHandler.post(() -> handleLeBroadcastUpdated());
                }

                @Override
                public void onBroadcastUpdateFailed(int reason, int broadcastId) {
                    if (DEBUG) {
                        Log.d(TAG, "onBroadcastUpdateFailed(), reason = " + reason
                                + ", broadcastId = " + broadcastId);
                    }
                    mMainThreadHandler.post(() -> handleLeBroadcastUpdateFailed());
                }

                @Override
                public void onPlaybackStarted(int reason, int broadcastId) {
                }

                @Override
                public void onPlaybackStopped(int reason, int broadcastId) {
                }
            };

    private class LayoutManagerWrapper extends LinearLayoutManager {
        LayoutManagerWrapper(Context context) {
            super(context);
        }

        @Override
        public void onLayoutCompleted(RecyclerView.State state) {
            super.onLayoutCompleted(state);
            mMediaSwitchingController.setRefreshing(false);
            mMediaSwitchingController.refreshDataSetIfNeeded();
        }
    }

    public MediaOutputBaseDialog(
            Context context,
            BroadcastSender broadcastSender,
            MediaSwitchingController mediaSwitchingController,
            boolean includePlaybackAndAppMetadata) {
        super(context, R.style.Theme_SystemUI_Dialog_Media);

        // Save the context that is wrapped with our theme.
        mContext = getContext();
        mBroadcastSender = broadcastSender;
        mMediaSwitchingController = mediaSwitchingController;
        mLayoutManager = new LayoutManagerWrapper(mContext);
        mExecutor = Executors.newSingleThreadExecutor();
        mIncludePlaybackAndAppMetadata = includePlaybackAndAppMetadata;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDialogView = LayoutInflater.from(mContext).inflate(R.layout.media_output_dialog, null);
        final Window window = getWindow();
        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.gravity = Gravity.CENTER;
        // Config insets to make sure the layout is above the navigation bar
        lp.setFitInsetsTypes(statusBars() | navigationBars());
        lp.setFitInsetsSides(WindowInsets.Side.all());
        lp.setFitInsetsIgnoringVisibility(true);
        window.setAttributes(lp);
        window.setContentView(mDialogView);
        window.setTitle(mContext.getString(R.string.media_output_dialog_accessibility_title));
        window.setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL);

        mHeaderTitle = mDialogView.requireViewById(R.id.header_title);
        mHeaderSubtitle = mDialogView.requireViewById(R.id.header_subtitle);
        mHeaderIcon = mDialogView.requireViewById(R.id.header_icon);
        mQuickAccessShelf = mDialogView.requireViewById(R.id.quick_access_shelf);
        mConnectDeviceButton = mDialogView.requireViewById(R.id.connect_device);
        mDevicesRecyclerView = mDialogView.requireViewById(R.id.list_result);
        mDialogFooter = mDialogView.requireViewById(R.id.dialog_footer);
        mFooterSpacer = mDialogView.requireViewById(R.id.footer_spacer);
        mMediaMetadataSectionLayout = mDialogView.requireViewById(R.id.media_metadata_section);
        mDeviceListLayout = mDialogView.requireViewById(R.id.device_list);
        mDoneButton = mDialogView.requireViewById(R.id.done);
        mStopButton = mDialogView.requireViewById(R.id.stop);
        mAppResourceIcon = mDialogView.requireViewById(R.id.app_source_icon);
        mBroadcastIcon = mDialogView.requireViewById(R.id.broadcast_icon);

        // Init device list
        mLayoutManager.setAutoMeasureEnabled(true);
        mDevicesRecyclerView.setLayoutManager(mLayoutManager);
        mDevicesRecyclerView.setAdapter(mAdapter);
        mDevicesRecyclerView.setHasFixedSize(false);
        // Init bottom buttons
        mDoneButton.setOnClickListener(v -> dismiss());
        mStopButton.setOnClickListener(v -> onStopButtonClick());
        if (mMediaSwitchingController.getAppLaunchIntent() != null) {
            // For a11y purposes only add listener if a section is clickable.
            mMediaMetadataSectionLayout.setOnClickListener(
                    mMediaSwitchingController::tryToLaunchMediaApplication);
        }

        mDismissing = false;

        if (enableOutputSwitcherRedesign()) {
            // Reduce radius of dialog background.
            mDialogView.setBackground(AppCompatResources.getDrawable(mContext,
                    R.drawable.media_output_dialog_background_reduced_radius));
            // Set non-transparent footer background to change it color on scroll.
            mDialogFooter.setBackground(AppCompatResources.getDrawable(mContext,
                    R.drawable.media_output_dialog_footer_background));
            // Right-align the footer buttons.
            LinearLayout.LayoutParams layoutParams =
                    (LinearLayout.LayoutParams) mFooterSpacer.getLayoutParams();
            layoutParams.width = (int) mContext.getResources().getDimension(
                    R.dimen.media_output_dialog_button_gap);
            mFooterSpacer.setLayoutParams(layoutParams);
            layoutParams.weight = 0;
            // Update font family to Google Sans Flex.
            Typeface buttonTypeface = Typeface.create(GSF_LABEL_LARGE, Typeface.NORMAL);
            mDoneButton.setTypeface(buttonTypeface);
            mStopButton.setTypeface(buttonTypeface);
            mHeaderTitle
                    .setTypeface(Typeface.create(GSF_TITLE_MEDIUM_EMPHASIZED, Typeface.NORMAL));
            mHeaderSubtitle
                    .setTypeface(Typeface.create(GSF_TITLE_SMALL, Typeface.NORMAL));
            // Reduce the size of the app icon.
            float appIconSize = mContext.getResources().getDimension(
                    R.dimen.media_output_dialog_app_icon_size);
            float appIconBottomMargin = mContext.getResources().getDimension(
                    R.dimen.media_output_dialog_app_icon_bottom_margin);
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) mAppResourceIcon.getLayoutParams();
            params.bottomMargin = (int) appIconBottomMargin;
            params.width = (int) appIconSize;
            params.height = (int) appIconSize;
            mAppResourceIcon.setLayoutParams(params);
            // Change footer background color on scroll.
            mDevicesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    changeFooterColorForScroll();
                }
            });
        }
    }

    @Override
    public void dismiss() {
        // TODO(287191450): remove this once expensive binder calls are removed from refresh().
        // Due to these binder calls on the UI thread, calling refresh() during dismissal causes
        // significant frame drops for the dismissal animation. Since the dialog is going away
        // anyway, we use this state to turn refresh() into a no-op.
        mDismissing = true;
        super.dismiss();
    }

    @Override
    public void start() {
        mMediaSwitchingController.start(this);
        if (isBroadcastSupported() && !mIsLeBroadcastCallbackRegistered) {
            mMediaSwitchingController.registerLeBroadcastServiceCallback(
                    mExecutor, mBroadcastCallback);
            mIsLeBroadcastCallbackRegistered = true;
        }
    }

    @Override
    public void stop() {
        // unregister broadcast callback should only depend on profile and registered flag
        // rather than remote device or broadcast state
        // otherwise it might have risks of leaking registered callback handle
        if (mMediaSwitchingController.isBroadcastSupported() && mIsLeBroadcastCallbackRegistered) {
            mMediaSwitchingController.unregisterLeBroadcastServiceCallback(mBroadcastCallback);
            mIsLeBroadcastCallbackRegistered = false;
        }
        mMediaSwitchingController.stop();
    }

    @VisibleForTesting
    void refresh() {
        refresh(false);
    }

    void refresh(boolean deviceSetChanged) {
        // TODO(287191450): remove binder calls in this method from the UI thread.
        // If the dialog is going away or is already refreshing, do nothing.
        if (mDismissing || mMediaSwitchingController.isRefreshing()) {
            return;
        }
        mMediaSwitchingController.setRefreshing(true);
        // Update header icon
        final int iconRes = getHeaderIconRes();
        final IconCompat headerIcon = getHeaderIcon();
        final IconCompat appSourceIcon = getAppSourceIcon();
        boolean colorSetUpdated = false;
        if (iconRes != 0) {
            mHeaderIcon.setVisibility(View.VISIBLE);
            mHeaderIcon.setImageResource(iconRes);
        } else if (headerIcon != null) {
            Icon icon = headerIcon.toIcon(mContext);
            if (icon.getType() != Icon.TYPE_BITMAP && icon.getType() != Icon.TYPE_ADAPTIVE_BITMAP) {
                // icon doesn't support getBitmap, use default value for color scheme
                updateButtonBackgroundColorFilter();
                updateDialogBackgroundColor();
            } else {
                Configuration config = mContext.getResources().getConfiguration();
                int currentNightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                boolean isDarkThemeOn = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
                WallpaperColors wallpaperColors = WallpaperColors.fromBitmap(icon.getBitmap());
                colorSetUpdated = !wallpaperColors.equals(mWallpaperColors);
                if (colorSetUpdated) {
                    mMediaSwitchingController.updateCurrentColorScheme(wallpaperColors,
                            isDarkThemeOn);
                    updateButtonBackgroundColorFilter();
                    updateDialogBackgroundColor();
                }
            }
            mHeaderIcon.setVisibility(View.VISIBLE);
            mHeaderIcon.setImageIcon(icon);
        } else {
            updateButtonBackgroundColorFilter();
            updateDialogBackgroundColor();
            mHeaderIcon.setVisibility(View.GONE);
        }

        if (!mIncludePlaybackAndAppMetadata) {
            mAppResourceIcon.setVisibility(View.GONE);
        } else if (appSourceIcon != null) {
            Icon appIcon = appSourceIcon.toIcon(mContext);
            mAppResourceIcon.setColorFilter(
                    mMediaSwitchingController.getColorSchemeLegacy().getColorItemContent());
            mAppResourceIcon.setImageIcon(appIcon);
        } else {
            Drawable appIconDrawable = mMediaSwitchingController.getAppSourceIconFromPackage();
            if (appIconDrawable != null) {
                mAppResourceIcon.setImageDrawable(appIconDrawable);
            } else {
                mAppResourceIcon.setVisibility(View.GONE);
            }
        }

        if (!mIncludePlaybackAndAppMetadata) {
            mHeaderTitle.setVisibility(View.GONE);
            mHeaderSubtitle.setVisibility(View.GONE);
        } else {
            // Update title and subtitle
            mHeaderTitle.setText(getHeaderText());
            final CharSequence subTitle = getHeaderSubtitle();
            if (TextUtils.isEmpty(subTitle)) {
                mHeaderSubtitle.setVisibility(View.GONE);
                mHeaderTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            } else {
                mHeaderSubtitle.setVisibility(View.VISIBLE);
                mHeaderSubtitle.setText(subTitle);
                mHeaderTitle.setGravity(Gravity.NO_GRAVITY);
            }
        }

        if (enableOutputSwitcherRedesign()) {
            if (mMediaSwitchingController.getConnectNewDeviceItem() != null) {
                mQuickAccessShelf.setVisibility(View.VISIBLE);
                mConnectDeviceButton.setVisibility(View.VISIBLE);
                mConnectDeviceButton.setOnClickListener(
                        mMediaSwitchingController::launchBluetoothPairing);
            } else {
                mQuickAccessShelf.setVisibility(View.GONE);
                mConnectDeviceButton.setVisibility(View.GONE);
            }
        }

        // Show when remote media session is available or
        //      when the device supports BT LE audio + media is playing
        mStopButton.setVisibility(getStopButtonVisibility());
        mStopButton.setEnabled(true);
        mStopButton.setText(getStopButtonText());
        mStopButton.setOnClickListener(v -> onStopButtonClick());

        mBroadcastIcon.setVisibility(getBroadcastIconVisibility());
        mBroadcastIcon.setOnClickListener(v -> onBroadcastIconClick());
        if (!mAdapter.isDragging()) {
            int currentActivePosition = mAdapter.getCurrentActivePosition();
            if (!colorSetUpdated && !deviceSetChanged && currentActivePosition >= 0
                    && currentActivePosition < mAdapter.getItemCount()) {
                mAdapter.notifyItemChanged(currentActivePosition);
            } else {
                mAdapter.updateItems();
            }
        } else {
            mMediaSwitchingController.setRefreshing(false);
            mMediaSwitchingController.refreshDataSetIfNeeded();
        }
    }

    private void updateButtonBackgroundColorFilter() {
        if (enableOutputSwitcherRedesign()) {
            mDoneButton.getBackground().setTint(
                    mMediaSwitchingController.getColorScheme().getPrimary());
            mDoneButton.setTextColor(mMediaSwitchingController.getColorScheme().getOnPrimary());
            mStopButton.getBackground().setTint(
                    mMediaSwitchingController.getColorScheme().getOutlineVariant());
            mStopButton.setTextColor(mMediaSwitchingController.getColorScheme().getPrimary());
            mConnectDeviceButton.setTextColor(
                    mMediaSwitchingController.getColorScheme().getOnSurfaceVariant());
            mConnectDeviceButton.setStrokeColor(ColorStateList.valueOf(
                    mMediaSwitchingController.getColorScheme().getOutlineVariant()));
            mConnectDeviceButton.setIconTint(ColorStateList.valueOf(
                    mMediaSwitchingController.getColorScheme().getPrimary()));
        } else {
            ColorFilter buttonColorFilter = new PorterDuffColorFilter(
                    mMediaSwitchingController.getColorSchemeLegacy().getColorButtonBackground(),
                    PorterDuff.Mode.SRC_IN);
            mDoneButton.getBackground().setColorFilter(buttonColorFilter);
            mStopButton.getBackground().setColorFilter(buttonColorFilter);
            mDoneButton.setTextColor(
                    mMediaSwitchingController.getColorSchemeLegacy().getColorPositiveButtonText());
        }
    }

    private void updateDialogBackgroundColor() {
        int backgroundColor = enableOutputSwitcherRedesign()
                ? mMediaSwitchingController.getColorScheme().getSurfaceContainer()
                : mMediaSwitchingController.getColorSchemeLegacy().getColorDialogBackground();
        getDialogView().getBackground().setTint(backgroundColor);
        mDeviceListLayout.setBackgroundColor(backgroundColor);
    }

    private void changeFooterColorForScroll() {
        int totalItemCount = mLayoutManager.getItemCount();
        int lastVisibleItemPosition =
                mLayoutManager.findLastCompletelyVisibleItemPosition();
        boolean hasBottomScroll =
                totalItemCount > 0 && lastVisibleItemPosition != totalItemCount - 1;
        mDialogFooter.getBackground().setTint(
                hasBottomScroll
                        ? mMediaSwitchingController.getColorScheme().getSurfaceContainerHigh()
                        : mMediaSwitchingController.getColorScheme().getSurfaceContainer());
    }

    public void handleLeBroadcastStarted() {
        // Waiting for the onBroadcastMetadataChanged. The UI launchs the broadcast dialog when
        // the metadata is ready.
        mShouldLaunchLeBroadcastDialog = true;
    }

    public void handleLeBroadcastStartFailed() {
        mStopButton.setText(R.string.media_output_broadcast_start_failed);
        mStopButton.setEnabled(false);
        refresh();
    }

    public void handleLeBroadcastMetadataChanged() {
        if (mShouldLaunchLeBroadcastDialog) {
            startLeBroadcastDialog();
            mShouldLaunchLeBroadcastDialog = false;
        }
        refresh();
    }

    public void handleLeBroadcastStopped() {
        mShouldLaunchLeBroadcastDialog = false;
        refresh();
    }

    public void handleLeBroadcastStopFailed() {
        refresh();
    }

    public void handleLeBroadcastUpdated() {
        refresh();
    }

    public void handleLeBroadcastUpdateFailed() {
        refresh();
    }

    protected void startLeBroadcast() {
        mStopButton.setText(R.string.media_output_broadcast_starting);
        mStopButton.setEnabled(false);
        if (!mMediaSwitchingController.startBluetoothLeBroadcast()) {
            // If the system can't execute "broadcast start", then UI shows the error.
            handleLeBroadcastStartFailed();
        }
    }

    protected boolean startLeBroadcastDialogForFirstTime(){
        SharedPreferences sharedPref = mContext.getSharedPreferences(PREF_NAME,
                Context.MODE_PRIVATE);
        if (sharedPref != null
                && sharedPref.getBoolean(PREF_IS_LE_BROADCAST_FIRST_LAUNCH, true)) {
            Log.d(TAG, "PREF_IS_LE_BROADCAST_FIRST_LAUNCH: true");

            mMediaSwitchingController.launchLeBroadcastNotifyDialog(
                    mDialogView,
                    mBroadcastSender,
                    MediaSwitchingController.BroadcastNotifyDialog.ACTION_FIRST_LAUNCH,
                    (d, w) -> {
                        startLeBroadcast();
                    });
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(PREF_IS_LE_BROADCAST_FIRST_LAUNCH, false);
            editor.apply();
            return true;
        }
        return false;
    }

    protected void startLeBroadcastDialog() {
        mMediaSwitchingController.launchMediaOutputBroadcastDialog(mDialogView, mBroadcastSender);
        refresh();
    }

    protected void stopLeBroadcast() {
        mStopButton.setEnabled(false);
        if (!mMediaSwitchingController.stopBluetoothLeBroadcast()) {
            // If the system can't execute "broadcast stop", then UI does refresh.
            mMainThreadHandler.post(() -> refresh());
        }
    }

    abstract IconCompat getAppSourceIcon();

    abstract int getHeaderIconRes();

    abstract IconCompat getHeaderIcon();

    abstract CharSequence getHeaderText();

    abstract CharSequence getHeaderSubtitle();

    abstract int getStopButtonVisibility();

    public CharSequence getStopButtonText() {
        return mContext.getText(R.string.keyboard_key_media_stop);
    }

    public void onStopButtonClick() {
        mMediaSwitchingController.releaseSession();
        dismiss();
    }

    public int getBroadcastIconVisibility() {
        return View.GONE;
    }

    public void onBroadcastIconClick() {
        // Do nothing.
    }

    public boolean isBroadcastSupported() {
        return false;
    }

    @Override
    public void onMediaChanged() {
        mMainThreadHandler.post(() -> refresh());
    }

    @Override
    public void onMediaStoppedOrPaused() {
        if (isShowing()) {
            dismiss();
        }
    }

    @Override
    public void onRouteChanged() {
        mMainThreadHandler.post(() -> refresh());
    }

    @Override
    public void onDeviceListChanged() {
        mMainThreadHandler.post(() -> refresh(true));
    }

    @Override
    public void dismissDialog() {
        mBroadcastSender.closeSystemDialogs();
    }

    View getDialogView() {
        return mDialogView;
    }
}
