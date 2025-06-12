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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_APP_SNIPPET;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallInstalling;
import com.android.packageinstaller.v2.model.PackageUtil.AppSnippet;

/**
 * Dialog to show when an install is in progress.
 */
public class InstallInstallingFragment extends DialogFragment {

    private static final String LOG_TAG = InstallInstallingFragment.class.getSimpleName();
    private InstallInstalling mDialogData;
    private AlertDialog mDialog;

    public InstallInstallingFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link InstallInstalling} object containing data to display in the
     *         dialog
     * @return an instance of the fragment
     */
    public static InstallInstallingFragment newInstance(@NonNull InstallInstalling dialogData) {
        Bundle args = new Bundle();
        args.putParcelable(ARGS_APP_SNIPPET, dialogData.getAppSnippet());

        InstallInstallingFragment fragment = new InstallInstallingFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setDialogData(requireArguments());

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);
        View dialogView = getLayoutInflater().inflate(R.layout.install_content_view, null);
        mDialog = new AlertDialog.Builder(requireContext())
            .setTitle(mDialogData.getAppLabel())
            .setIcon(mDialogData.getAppIcon())
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .create();

        dialogView.requireViewById(R.id.installing).setVisibility(View.VISIBLE);
        this.setCancelable(false);

        return mDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        mDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);
    }

    private void setDialogData(Bundle args) {
        AppSnippet appSnippet = args.getParcelable(ARGS_APP_SNIPPET, AppSnippet.class);
        mDialogData = new InstallInstalling(appSnippet);
    }
}
