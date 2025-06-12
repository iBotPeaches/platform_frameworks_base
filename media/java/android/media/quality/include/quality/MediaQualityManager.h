#ifndef ANDROID_MEDIA_QUALITY_MANAGER_H
#define ANDROID_MEDIA_QUALITY_MANAGER_H


namespace android {
namespace media {
namespace quality {

// TODO: implement writeToParcel and readFromParcel

class PictureProfileHandle : public Parcelable {
    public:
        PictureProfileHandle() {}
        status_t writeToParcel(android::Parcel*) const override {
            return 0;
        }
        status_t readFromParcel(const android::Parcel*) override {
            return 0;
        }
        std::string toString() const {
            return "";
        }
};

class SoundProfile : public Parcelable {
    public:
        SoundProfile() {}
        status_t writeToParcel(android::Parcel*) const override {
            return 0;
        }
        status_t readFromParcel(const android::Parcel*) override {
            return 0;
        }
        std::string toString() const {
            return "";
        }
};

class PictureProfile : public Parcelable {
    public:
        PictureProfile() {}
        status_t writeToParcel(android::Parcel*) const override {
            return 0;
        }
        status_t readFromParcel(const android::Parcel*) override {
            return 0;
        }
        std::string toString() const {
            return "";
        }
};

class ActiveProcessingPicture : public Parcelable {
    public:
        ActiveProcessingPicture() {}
        status_t writeToParcel(android::Parcel*) const override {
            return 0;
        }
        status_t readFromParcel(const android::Parcel*) override {
            return 0;
        }
        std::string toString() const {
            return "";
        }
};

class AmbientBacklightEvent : public Parcelable {
    public:
        AmbientBacklightEvent() {}
        status_t writeToParcel(android::Parcel*) const override {
            return 0;
        }
        status_t readFromParcel(const android::Parcel*) override {
            return 0;
        }
        std::string toString() const {
            return "";
        }
};

class AmbientBacklightMetadata : public Parcelable {
    public:
        AmbientBacklightMetadata() {}
        status_t writeToParcel(android::Parcel*) const override {
            return 0;
        }
        status_t readFromParcel(const android::Parcel*) override {
            return 0;
        }
        std::string toString() const {
            return "";
        }
};

class AmbientBacklightSettings : public Parcelable {
    public:
        AmbientBacklightSettings() {}
        status_t writeToParcel(android::Parcel*) const override {
            return 0;
        }
        status_t readFromParcel(const android::Parcel*) override {
            return 0;
        }
        std::string toString() const {
            return "";
        }
};

class ParameterCapability : public Parcelable {
    public:
        ParameterCapability() {}
        status_t writeToParcel(android::Parcel*) const override {
            return 0;
        }
        status_t readFromParcel(const android::Parcel*) override {
            return 0;
        }
        std::string toString() const {
            return "";
        }
};

} // namespace quality
} // namespace media
} // namespace android

#endif
