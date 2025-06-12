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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_ABORT_REASON;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.UninstallAborted;
import com.android.packageinstaller.v2.ui.UninstallActionListener;

/**
 * Dialog to show when an app cannot be uninstalled
 */
public class UninstallErrorFragment extends DialogFragment {

    private static final String LOG_TAG = UninstallErrorFragment.class.getSimpleName();
    private UninstallAborted mDialogData;
    private UninstallActionListener mUninstallActionListener;

    public UninstallErrorFragment() {
        // Required for DialogFragment
    }

    /**
     * Create a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link UninstallAborted} object containing data to display in the
     *         dialog
     * @return an instance of the fragment
     */
    public static UninstallErrorFragment newInstance(UninstallAborted dialogData) {
        Bundle args = new Bundle();
        args.putInt(ARGS_ABORT_REASON, dialogData.getAbortReason());

        UninstallErrorFragment fragment = new UninstallErrorFragment();
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
            .setMessage(mDialogData.getDialogTextResource())
            .setNegativeButton(R.string.ok,
                (dialogInt, which) -> mUninstallActionListener.onNegativeResponse());

        if (mDialogData.getDialogTitleResource() != 0) {
            builder.setTitle(mDialogData.getDialogTitleResource());
        }
        return builder.create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        mUninstallActionListener.onNegativeResponse();
    }

    private void setDialogData(Bundle args) {
        int abortReason = args.getInt(ARGS_ABORT_REASON);
        mDialogData = new UninstallAborted(abortReason);
    }
}
