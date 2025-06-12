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

package com.android.systemui.ringtone;

import static org.junit.Assert.assertThrows;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RingtonePlayerTest extends SysuiTestCase {

    private static final String TAG = "RingtonePlayerTest";

    @Test
    public void testRingtonePlayerUriUserCheck() {
        // temporarily skipping this test
        Log.i(TAG, "skipping testRingtonePlayerUriUserCheck");
        return;

        // TODO change how IRingtonePlayer is created
//        android.media.IRingtonePlayer irp = mAudioManager.getRingtonePlayer();
//        final AudioAttributes aa = new AudioAttributes.Builder()
//                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();
//        // get a UserId that doesn't belong to mine
//        final int otherUserId = UserHandle.myUserId() == 0 ? 10 : 0;
//        // build a URI that I shouldn't have access to
//        final Uri uri = new Uri.Builder()
//                .scheme("content").authority(otherUserId + "@media")
//                .appendPath("external").appendPath("downloads")
//                .appendPath("bogusPathThatDoesNotMatter.mp3")
//                .build();
//        if (android.media.audio.Flags.ringtoneUserUriCheck()) {
//            assertThrows(SecurityException.class, () ->
//                    irp.play(new Binder(), uri, aa, 1.0f /*volume*/, false /*looping*/)
//            );
//
//            assertThrows(SecurityException.class, () ->
//                    irp.getTitle(uri));
//        }
    }

}
