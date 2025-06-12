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

package android.media;

import static com.android.media.flags.Flags.FLAG_ENABLE_SUGGESTED_DEVICE_API;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Objects;

/**
 * Allows applications to suggest routes to the system UI (for example, in the System UI Output
 * Switcher).
 *
 * @see MediaRouter2#setSuggestedDevice
 * @hide
 */
@FlaggedApi(FLAG_ENABLE_SUGGESTED_DEVICE_API)
public final class SuggestedDeviceInfo implements Parcelable {
    @NonNull
    public static final Creator<SuggestedDeviceInfo> CREATOR =
            new Creator<>() {
                @Override
                public SuggestedDeviceInfo createFromParcel(Parcel in) {
                    return new SuggestedDeviceInfo(in);
                }

                @Override
                public SuggestedDeviceInfo[] newArray(int size) {
                    return new SuggestedDeviceInfo[size];
                }
            };

    @NonNull private final String mDeviceDisplayName;

    @NonNull private final String mRouteId;

    private final int mType;

    @NonNull private final Bundle mExtras;

    private SuggestedDeviceInfo(Builder builder) {
        mDeviceDisplayName = builder.mDeviceDisplayName;
        mRouteId = builder.mRouteId;
        mType = builder.mType;
        mExtras = builder.mExtras;
    }

    private SuggestedDeviceInfo(Parcel in) {
        mDeviceDisplayName = in.readString();
        mRouteId = in.readString();
        mType = in.readInt();
        mExtras = in.readBundle();
    }

    /**
     * Returns the name to be displayed to the user.
     *
     * @return The device display name.
     */
    @NonNull
    public String getDeviceDisplayName() {
        return mDeviceDisplayName;
    }

    /**
     * Returns the route ID associated with the suggestion.
     *
     * @return The route ID.
     */
    @NonNull
    public String getRouteId() {
        return mRouteId;
    }

    /**
     * Returns the device type associated with the suggestion.
     *
     * @return The device type.
     */
    public int getType() {
        return mType;
    }

    /**
     * Returns the extras associated with the suggestion.
     *
     * @return The extras.
     */
    @Nullable
    public Bundle getExtras() {
        return mExtras;
    }

    // SuggestedDeviceInfo Parcelable implementation.
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mDeviceDisplayName);
        dest.writeString(mRouteId);
        dest.writeInt(mType);
        dest.writeBundle(mExtras);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SuggestedDeviceInfo)) {
            return false;
        }
        return Objects.equals(mDeviceDisplayName, ((SuggestedDeviceInfo) obj).mDeviceDisplayName)
                && Objects.equals(mRouteId, ((SuggestedDeviceInfo) obj).mRouteId)
                && mType == ((SuggestedDeviceInfo) obj).mType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDeviceDisplayName, mRouteId, mType);
    }

    @Override
    public String toString() {
        return mDeviceDisplayName + " | " + mRouteId + " | " + mType;
    }

    /** Builder for {@link SuggestedDeviceInfo}. */
    public static final class Builder {
        @NonNull private String mDeviceDisplayName;

        @NonNull private String mRouteId;

        @NonNull private Integer mType;

        private Bundle mExtras = Bundle.EMPTY;

        /**
         * Creates a new SuggestedDeviceInfo. The device display name, route ID, and type must be
         * set. The extras cannot be null, but default to an empty {@link Bundle}.
         *
         * @throws IllegalArgumentException if the builder has a mandatory unset field.
         */
        public SuggestedDeviceInfo build() {
            if (mDeviceDisplayName == null) {
                throw new IllegalArgumentException("Device display name cannot be null");
            }

            if (mRouteId == null) {
                throw new IllegalArgumentException("Route ID cannot be null.");
            }

            if (mType == null) {
                throw new IllegalArgumentException("Device type cannot be null.");
            }

            if (mExtras == null) {
                throw new IllegalArgumentException("Extras cannot be null.");
            }

            return new SuggestedDeviceInfo(this);
        }

        /**
         * Sets the {@link #getDeviceDisplayName() device display name}.
         *
         * @throws IllegalArgumentException if the name is null or empty.
         */
        public Builder setDeviceDisplayName(@NonNull String deviceDisplayName) {
            if (TextUtils.isEmpty(deviceDisplayName)) {
                throw new IllegalArgumentException("Device display name cannot be null");
            }
            mDeviceDisplayName = deviceDisplayName;
            return this;
        }

        /**
         * Sets the {@link #getRouteId() route id}.
         *
         * @throws IllegalArgumentException if the route id is null or empty.
         */
        public Builder setRouteId(@NonNull String routeId) {
            if (TextUtils.isEmpty(routeId)) {
                throw new IllegalArgumentException("Device display name cannot be null");
            }
            mRouteId = routeId;
            return this;
        }

        /** Sets the {@link #getType() device type}. */
        public Builder setType(int type) {
            mType = type;
            return this;
        }

        /**
         * Sets the {@link #getExtras() extras}.
         *
         * <p>The default value is an empty {@link Bundle}.
         *
         * <p>Do not mutate the given {@link Bundle} after passing it to this method. You can use
         * {@link Bundle#deepCopy()} to keep a mutable copy.
         *
         * @throws NullPointerException if the extras are null.
         */
        public Builder setExtras(@NonNull Bundle extras) {
            mExtras = Objects.requireNonNull(extras, "extras must not be null");
            return this;
        }
    }
}
