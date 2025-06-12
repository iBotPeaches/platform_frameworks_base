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

package android.app.jank.tests;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;


public class JankTrackerActivity extends Activity {

    private static final int CONTINUE_TEST_DELAY_MS = 4000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.jank_tracker_activity_layout);
    }

    /**
     * In IntegrationTests#jankTrackingResumed_whenActivityBecomesVisibleAgain this activity is
     * placed into the background and then resumed via an intent. The test waits until the
     * `continue_test` string is visible on the screen before validating that Jank tracking has
     * resumed.
     *
     * <p>The 4 second delay allows JankTracker to re-register its callbacks and start receiving
     * JankData before the test proceeds.
     */
    @Override
    protected void onResume() {
        super.onResume();
        getActivityThread().getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                EditText editTextView = findViewById(R.id.edit_text);
                if (editTextView != null) {
                    editTextView.setText(R.string.continue_test);
                }
            }
        }, CONTINUE_TEST_DELAY_MS);
    }
}


