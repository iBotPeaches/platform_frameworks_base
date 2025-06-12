/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_ABORT_REASON;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_ACTIVITY_RESULT_CODE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_ERROR_DIALOG_TYPE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_MESSAGE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_RESULT_INTENT;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallAborted;
import com.android.packageinstaller.v2.ui.InstallActionListener;

public class ParseErrorFragment extends DialogFragment {

    private static final String LOG_TAG = ParseErrorFragment.class.getSimpleName();
    private InstallAborted mDialogData;
    private InstallActionListener mInstallActionListener;

    public ParseErrorFragment() {
        // Required for DialogFragment
    }

    /**
     * Create a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link InstallAborted} object containing data to display in the
     *         dialog
     * @return an instance of the fragment
     */
    public static ParseErrorFragment newInstance(@NonNull InstallAborted dialogData) {
        Bundle args = new Bundle();
        args.putInt(ARGS_ABORT_REASON, dialogData.getAbortReason());
        args.putString(ARGS_MESSAGE, dialogData.getMessage());
        args.putParcelable(ARGS_RESULT_INTENT, dialogData.getResultIntent());
        args.putInt(ARGS_ACTIVITY_RESULT_CODE, dialogData.getActivityResultCode());
        args.putInt(ARGS_ERROR_DIALOG_TYPE, dialogData.getErrorDialogType());

        ParseErrorFragment fragment = new ParseErrorFragment();
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
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setDialogData(requireArguments());

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);
        return new AlertDialog.Builder(requireContext())
            .setMessage(R.string.Parse_error_dlg_text)
            .setPositiveButton(R.string.ok,
                (dialog, which) ->
                    mInstallActionListener.onNegativeResponse(
                        mDialogData.getActivityResultCode(), mDialogData.getResultIntent()))
            .create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        mInstallActionListener.onNegativeResponse(
            mDialogData.getActivityResultCode(), mDialogData.getResultIntent());
    }

    private void setDialogData(Bundle args) {
        int abortReason = args.getInt(ARGS_ABORT_REASON);
        String message = args.getString(ARGS_MESSAGE);
        Intent resultIntent = args.getParcelable(ARGS_RESULT_INTENT, Intent.class);
        int activityResultCode = args.getInt(ARGS_ACTIVITY_RESULT_CODE);
        int errorDialogType = args.getInt(ARGS_ERROR_DIALOG_TYPE);

        mDialogData = new InstallAborted(abortReason, message, resultIntent, activityResultCode,
                errorDialogType);
    }
}
