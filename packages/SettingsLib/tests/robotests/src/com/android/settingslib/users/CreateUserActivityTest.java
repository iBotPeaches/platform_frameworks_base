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

import static com.android.settingslib.users.CreateUserActivity.EXTRA_IS_ADMIN;
import static com.android.settingslib.users.CreateUserActivity.EXTRA_USER_ICON_PATH;
import static com.android.settingslib.users.CreateUserActivity.EXTRA_USER_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MotionEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class CreateUserActivityTest {

    private static final String TEST_USER_NAME = "test_user";
    private static final String TEST_USER_ICON_PATH = "/test_path";
    private static final boolean TEST_IS_ADMIN = true;

    private Context mContext;
    private CreateUserActivity mCreateUserActivity;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mCreateUserActivity = Robolectric.buildActivity(CreateUserActivity.class).setup().get();
    }

    @Test
    public void startActivity_startsActivityForResult() {
        Intent activityIntent = CreateUserActivity.createIntentForStart(mContext, true, "");
        mCreateUserActivity.startActivity(activityIntent, null);

        assertThat(shadowOf(mCreateUserActivity).getNextStartedActivityForResult().intent)
                .isEqualTo(activityIntent);
    }

    @Test
    public void onTouchEvent_finishesActivityAndCancelsResult() {
        mCreateUserActivity.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0,
                0));

        assertThat(mCreateUserActivity.isFinishing()).isTrue();
        assertThat(shadowOf(mCreateUserActivity).getResultCode())
                .isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void setSuccessResult_finishesActivityAndSetsSuccessResult() {
        Drawable mockDrawable = mock(Drawable.class);

        mCreateUserActivity.setSuccessResult(TEST_USER_NAME, mockDrawable, TEST_USER_ICON_PATH,
                TEST_IS_ADMIN);

        assertThat(mCreateUserActivity.isFinishing()).isTrue();
        assertThat(shadowOf(mCreateUserActivity).getResultCode()).isEqualTo(Activity.RESULT_OK);

        Intent resultIntent = shadowOf(mCreateUserActivity).getResultIntent();
        assertThat(resultIntent.getStringExtra(EXTRA_USER_NAME)).isEqualTo(TEST_USER_NAME);
        assertThat(resultIntent.getBooleanExtra(EXTRA_IS_ADMIN, false)).isEqualTo(TEST_IS_ADMIN);
        assertThat(resultIntent.getStringExtra(EXTRA_USER_ICON_PATH))
                .isEqualTo(TEST_USER_ICON_PATH);
    }

    @Test
    public void cancel_finishesActivityAndSetsCancelResult() {
        mCreateUserActivity.cancel();

        assertThat(mCreateUserActivity.isFinishing()).isTrue();
        assertThat(shadowOf(mCreateUserActivity).getResultCode())
                .isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void onSaveInstanceState_savesDialogState() {
        Bundle outState = new Bundle();
        mCreateUserActivity.onSaveInstanceState(outState);

        CreateUserActivity restoredActivity =
                Robolectric.buildActivity(CreateUserActivity.class).setup(outState).get();

        assertThat(restoredActivity.mSetupUserDialog).isNotNull();
        assertThat(restoredActivity.mSetupUserDialog.isShowing()).isTrue();
    }
}
