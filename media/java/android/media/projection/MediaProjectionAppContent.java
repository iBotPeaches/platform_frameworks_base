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

import android.annotation.FlaggedApi;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Holds information about content an app can share via the MediaProjection APIs.
 * <p>
 * An application requesting a {@link MediaProjection session} can add its own content in the
 * list of available content along with the whole screen or a single application.
 * <p>
 * Each instance of {@link MediaProjectionAppContent} contains an id that is used to identify the
 * content chosen by the user back to the advertising application, thus the meaning of the id is
 * only relevant to that application.
 */
@FlaggedApi(com.android.media.projection.flags.Flags.FLAG_APP_CONTENT_SHARING)
public final class MediaProjectionAppContent implements Parcelable {

    private final Bitmap mThumbnail;
    private final CharSequence mTitle;
    private final int mId;

    /**
     * Constructor to pass a thumbnail, title and id.
     *
     * @param thumbnail The thumbnail representing this content to be shown to the user.
     * @param title     A user visible string representing the title of this content.
     * @param id        An arbitrary int defined by the advertising application to be fed back once
     *                  the user made their choice.
     */
    public MediaProjectionAppContent(@NonNull Bitmap thumbnail, @NonNull CharSequence title,
            int id) {
        mThumbnail = Objects.requireNonNull(thumbnail, "thumbnail can't be null").asShared();
        mTitle = Objects.requireNonNull(title, "title can't be null");
        mId = id;
    }

    /**
     * Returns thumbnail representing this content to be shown to the user.
     *
     * @hide
     */
    @NonNull
    public Bitmap getThumbnail() {
        return mThumbnail;
    }

    /**
     * Returns user visible string representing the title of this content.
     *
     * @hide
     */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /**
     * Returns the arbitrary int defined by the advertising application to be fed back once
     * the user made their choice.
     *
     * @hide
     */
    public int getId() {
        return mId;
    }

    private MediaProjectionAppContent(Parcel in) {
        mThumbnail = in.readParcelable(this.getClass().getClassLoader(), Bitmap.class);
        mTitle = in.readCharSequence();
        mId = in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mThumbnail, flags);
        dest.writeCharSequence(mTitle);
        dest.writeInt(mId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<MediaProjectionAppContent> CREATOR =
            new Creator<>() {
                @NonNull
                @Override
                public MediaProjectionAppContent createFromParcel(@NonNull Parcel in) {
                    return new MediaProjectionAppContent(in);
                }

                @NonNull
                @Override
                public MediaProjectionAppContent[] newArray(int size) {
                    return new MediaProjectionAppContent[size];
                }
            };
}
