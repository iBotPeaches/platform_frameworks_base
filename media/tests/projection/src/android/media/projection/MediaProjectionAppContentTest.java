/*
 * Copyright 2025 The Android Open Source Project
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

package android.media.projection;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaProjectionAppContentTest {

    @Test
    public void testConstructorAndGetters() {
        // Create a mock Bitmap
        Bitmap mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // Create a MediaProjectionAppContent object
        MediaProjectionAppContent content = new MediaProjectionAppContent(mockBitmap, "Test Title",
                123);

        // Verify the values using getters
        assertThat(content.getTitle()).isEqualTo("Test Title");
        assertThat(content.getId()).isEqualTo(123);
        // Compare bitmap configurations and dimensions
        assertThat(content.getThumbnail().getConfig()).isEqualTo(mockBitmap.getConfig());
        assertThat(content.getThumbnail().getWidth()).isEqualTo(mockBitmap.getWidth());
        assertThat(content.getThumbnail().getHeight()).isEqualTo(mockBitmap.getHeight());
    }

    @Test
    public void testParcelable() {
        // Create a mock Bitmap
        Bitmap mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // Create a MediaProjectionAppContent object
        MediaProjectionAppContent content = new MediaProjectionAppContent(mockBitmap, "Test Title",
                123);

        // Parcel and unparcel the object
        Parcel parcel = Parcel.obtain();
        content.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MediaProjectionAppContent unparceledContent =
                MediaProjectionAppContent.CREATOR.createFromParcel(parcel);

        // Verify the values of the unparceled object
        assertThat(unparceledContent.getTitle()).isEqualTo("Test Title");
        assertThat(unparceledContent.getId()).isEqualTo(123);
        // Compare bitmap configurations and dimensions
        assertThat(unparceledContent.getThumbnail().getConfig()).isEqualTo(mockBitmap.getConfig());
        assertThat(unparceledContent.getThumbnail().getWidth()).isEqualTo(mockBitmap.getWidth());
        assertThat(unparceledContent.getThumbnail().getHeight()).isEqualTo(mockBitmap.getHeight());

        parcel.recycle();
    }

    @Test
    public void testCreatorNewArray() {
        // Create a new array using the CREATOR
        MediaProjectionAppContent[] contentArray = MediaProjectionAppContent.CREATOR.newArray(5);

        // Verify that the array is not null and has the correct size
        assertThat(contentArray).isNotNull();
        assertThat(contentArray).hasLength(5);
    }
}
