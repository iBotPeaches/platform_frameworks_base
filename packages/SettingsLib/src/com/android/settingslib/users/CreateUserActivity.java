/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settingslib.users;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.R;


public class CreateUserActivity extends Activity {
    private static final String TAG = "CreateUserActivity";

    public static final String EXTRA_USER_NAME = "new_user_name";
    public static final String EXTRA_IS_ADMIN = "is_admin";
    public static final String EXTRA_USER_ICON_PATH = "user_icon_path";
    private static final String DIALOG_STATE_KEY = "create_user_dialog_state";
    private static final String EXTRA_CAN_CREATE_ADMIN = "can_create_admin";
    private static final String EXTRA_FILE_AUTHORITY = "file_authority";

    private CreateUserDialogController mCreateUserDialogController;
    @VisibleForTesting
    Dialog mSetupUserDialog;


    /**
     * Creates intent to start CreateUserActivity
     */
    public static @NonNull Intent createIntentForStart(@NonNull Context context,
            boolean canCreateAdminUser, @NonNull String fileAuth) {
        Intent intent = new Intent(context, CreateUserActivity.class);
        intent.putExtra(EXTRA_CAN_CREATE_ADMIN, canCreateAdminUser);
        intent.putExtra(EXTRA_FILE_AUTHORITY, fileAuth);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        mCreateUserDialogController = new CreateUserDialogController(
                intent.getStringExtra(EXTRA_FILE_AUTHORITY));
        setContentView(R.layout.activity_create_new_user);
        if (savedInstanceState != null) {
            mCreateUserDialogController.onRestoreInstanceState(savedInstanceState);
        }
        mSetupUserDialog = createDialog(intent.getBooleanExtra(EXTRA_CAN_CREATE_ADMIN, false));
        mSetupUserDialog.show();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Bundle savedDialogState = savedInstanceState.getBundle(DIALOG_STATE_KEY);
        if (savedDialogState != null && mSetupUserDialog != null) {
            mSetupUserDialog.onRestoreInstanceState(savedDialogState);
        }
    }

    private Dialog createDialog(boolean canCreateAdminUser) {
        return mCreateUserDialogController.createDialog(
                this,
                this::startActivity,
                canCreateAdminUser,
                this::setSuccessResult,
                this::cancel
        );
    }

    @Override
    public boolean onTouchEvent(@Nullable MotionEvent event) {
        cancel();
        return super.onTouchEvent(event);
    }

    @VisibleForTesting
    void setSuccessResult(String userName, Drawable userIcon, String path, Boolean isAdmin) {
        Intent intent = new Intent(this, CreateUserActivity.class);
        intent.putExtra(EXTRA_USER_NAME, userName);
        intent.putExtra(EXTRA_IS_ADMIN, isAdmin);
        intent.putExtra(EXTRA_USER_ICON_PATH, path);

        setResult(RESULT_OK, intent);
        finish();
    }

    @VisibleForTesting
    void cancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (mSetupUserDialog != null && mSetupUserDialog.isShowing()) {
            outState.putBundle(DIALOG_STATE_KEY, mSetupUserDialog.onSaveInstanceState());
        }
        mCreateUserDialogController.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mCreateUserDialogController.onActivityResult(requestCode, resultCode, data);
    }

    private void startActivity(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
        mCreateUserDialogController.startingActivityForResult();
    }
}
