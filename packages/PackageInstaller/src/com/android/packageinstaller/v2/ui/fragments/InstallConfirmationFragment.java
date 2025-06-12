/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.packageinstaller.v2.ui.fragments;

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_ACTION_REASON;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_APP_SNIPPET;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_IS_UPDATING;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_SOURCE_APP;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallUserActionRequired;
import com.android.packageinstaller.v2.model.PackageUtil.AppSnippet;
import com.android.packageinstaller.v2.ui.InstallActionListener;

/**
 * Dialog to show when the requesting user confirmation for installing an app.
 */
public class InstallConfirmationFragment extends DialogFragment {

    public static final String LOG_TAG = InstallConfirmationFragment.class.getSimpleName();
    private InstallUserActionRequired mDialogData;
    @NonNull
    private InstallActionListener mInstallActionListener;
    @NonNull
    private AlertDialog mDialog;

    public InstallConfirmationFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link InstallUserActionRequired} object containing data to display
     *         in the dialog
     * @return an instance of the fragment
     */
    public static InstallConfirmationFragment newInstance(
            @NonNull InstallUserActionRequired dialogData) {
        Bundle args = new Bundle();
        args.putInt(ARGS_ACTION_REASON, dialogData.getActionReason());
        args.putParcelable(ARGS_APP_SNIPPET, dialogData.getAppSnippet());
        args.putBoolean(ARGS_IS_UPDATING, dialogData.isAppUpdating());
        args.putString(ARGS_SOURCE_APP, dialogData.getSourceApp());

        InstallConfirmationFragment fragment = new InstallConfirmationFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mInstallActionListener = (InstallActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setDialogData(requireArguments());

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);
        View dialogView = getLayoutInflater().inflate(R.layout.install_content_view, null);

        int positiveBtnTextRes;
        if (mDialogData.isAppUpdating()) {
            if (mDialogData.getSourceApp() != null) {
                positiveBtnTextRes = R.string.update_anyway;
            } else {
                positiveBtnTextRes = R.string.update;
            }
        } else {
            positiveBtnTextRes = R.string.install;
        }

        mDialog = new AlertDialog.Builder(requireContext())
            .setIcon(mDialogData.getAppIcon())
            .setTitle(mDialogData.getAppLabel())
            .setView(dialogView)
            .setPositiveButton(positiveBtnTextRes,
                (dialogInt, which) -> mInstallActionListener.onPositiveResponse(
                    InstallUserActionRequired.USER_ACTION_REASON_INSTALL_CONFIRMATION))
            .setNegativeButton(R.string.cancel,
                (dialogInt, which) -> mInstallActionListener.onNegativeResponse(
                    mDialogData.getStageCode()))
            .create();

        TextView viewToEnable;
        if (mDialogData.isAppUpdating()) {
            viewToEnable = dialogView.requireViewById(R.id.install_confirm_question_update);
            String sourcePackageName = mDialogData.getSourceApp();
            if (sourcePackageName != null) {
                // Show the update-ownership change message
                viewToEnable.setText(Html.fromHtml(sourcePackageName, Html.FROM_HTML_MODE_LEGACY));
            }
        } else {
            viewToEnable = dialogView.requireViewById(R.id.install_confirm_question);
        }
        viewToEnable.setVisibility(View.VISIBLE);
        viewToEnable.setMovementMethod(new ScrollingMovementMethod());

        return mDialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        mInstallActionListener.onNegativeResponse(mDialogData.getStageCode());
    }

    @Override
    public void onStart() {
        super.onStart();
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        // This prevents tapjacking since an overlay activity started in front of Pia will
        // cause Pia to be paused.
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
    }

    private void setDialogData(Bundle args) {
        int actionReason = args.getInt(ARGS_ACTION_REASON);
        AppSnippet appSnippet = args.getParcelable(ARGS_APP_SNIPPET, AppSnippet.class);
        boolean isUpdating = args.getBoolean(ARGS_IS_UPDATING);
        String sourceApp = args.getString(ARGS_SOURCE_APP);

        mDialogData = new InstallUserActionRequired(actionReason, appSnippet, isUpdating,
                sourceApp);
    }
}
