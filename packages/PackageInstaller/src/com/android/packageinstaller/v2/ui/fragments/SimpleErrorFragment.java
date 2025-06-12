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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_MESSAGE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallStage;
import com.android.packageinstaller.v2.ui.InstallActionListener;

public class SimpleErrorFragment extends DialogFragment {

    private static final String LOG_TAG = SimpleErrorFragment.class.getSimpleName();
    private int mMessageResId;
    private InstallActionListener mInstallActionListener;

    public SimpleErrorFragment() {
        // Required for DialogFragment
    }

    /**
     * Create a new instance of this fragment with necessary data set as fragment arguments
     *
     * @return an instance of the fragment
     */
    public static SimpleErrorFragment newInstance(int messageResId) {
        Bundle args = new Bundle();
        args.putInt(ARGS_MESSAGE, messageResId);

        SimpleErrorFragment fragment = new SimpleErrorFragment();
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
        mMessageResId = requireArguments().getInt(ARGS_MESSAGE);

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" +
            "Dialog message: " + requireContext().getString(mMessageResId));
        return new AlertDialog.Builder(requireContext())
            .setMessage(mMessageResId)
            .setPositiveButton(R.string.ok,
                (dialog, which) ->
                    mInstallActionListener.onNegativeResponse(InstallStage.STAGE_ABORTED))
            .create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        mInstallActionListener.onNegativeResponse(InstallStage.STAGE_ABORTED);
    }
}
