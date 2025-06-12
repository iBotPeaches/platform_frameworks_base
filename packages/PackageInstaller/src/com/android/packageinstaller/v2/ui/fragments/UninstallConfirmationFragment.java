/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.packageinstaller.v2.ui.fragments;

import static android.text.format.Formatter.formatFileSize;

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_APP_DATA_SIZE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_IS_ARCHIVE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_MESSAGE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_TITLE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.UninstallUserActionRequired;
import com.android.packageinstaller.v2.ui.UninstallActionListener;

/**
 * Dialog to show while requesting user confirmation for uninstalling an app.
 */
public class UninstallConfirmationFragment extends DialogFragment {

    private static final String LOG_TAG = UninstallConfirmationFragment.class.getSimpleName();
    private UninstallUserActionRequired mDialogData;
    private UninstallActionListener mUninstallActionListener;
    private CheckBox mKeepData;

    public UninstallConfirmationFragment() {
        // Required for DialogFragment
    }

    /**
     * Create a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link UninstallUserActionRequired} object containing data to
     *         display in the dialog
     * @return an instance of the fragment
     */
    public static UninstallConfirmationFragment newInstance(
            @NonNull UninstallUserActionRequired dialogData) {
        Bundle args = new Bundle();
        args.putLong(ARGS_APP_DATA_SIZE, dialogData.getAppDataSize());
        args.putBoolean(ARGS_IS_ARCHIVE, dialogData.isArchive());
        args.putString(ARGS_TITLE, dialogData.getTitle());
        args.putString(ARGS_MESSAGE, dialogData.getMessage());

        UninstallConfirmationFragment fragment = new UninstallConfirmationFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mUninstallActionListener = (UninstallActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setDialogData(requireArguments());

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
            .setTitle(mDialogData.getTitle())
            .setPositiveButton(mDialogData.isArchive() ? R.string.archive : R.string.ok,
                (dialogInt, which) -> mUninstallActionListener.onPositiveResponse(
                    mKeepData != null && mKeepData.isChecked()))
            .setNegativeButton(R.string.cancel,
                (dialogInt, which) -> mUninstallActionListener.onNegativeResponse());

        long appDataSize = mDialogData.getAppDataSize();
        if (appDataSize == 0) {
            builder.setMessage(mDialogData.getMessage());
        } else {
            View dialogView = getLayoutInflater().inflate(R.layout.uninstall_content_view, null);

            ((TextView) dialogView.requireViewById(R.id.message)).setText(mDialogData.getMessage());
            mKeepData = dialogView.requireViewById(R.id.keepData);
            mKeepData.setVisibility(View.VISIBLE);
            mKeepData.setText(getString(R.string.uninstall_keep_data,
                formatFileSize(getContext(), appDataSize)));

            builder.setView(dialogView);
        }
        return builder.create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        mUninstallActionListener.onNegativeResponse();
    }

    private void setDialogData(Bundle args) {
        long appDataSize = args.getLong(ARGS_APP_DATA_SIZE);
        boolean isArchive = args.getBoolean(ARGS_IS_ARCHIVE);
        String title = args.getString(ARGS_TITLE);
        String message = args.getString(ARGS_MESSAGE);

        mDialogData = new UninstallUserActionRequired(title, message, appDataSize, isArchive);
    }
}
