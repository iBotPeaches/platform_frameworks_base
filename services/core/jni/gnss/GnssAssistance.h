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

#ifndef _ANDROID_SERVER_GNSSASSITANCE_H
#define _ANDROID_SERVER_GNSSASSITANCE_H

#include <sys/stat.h>
#pragma once

#ifndef LOG_TAG
#error LOG_TAG must be defined before including this file.
#endif

#include <android/hardware/gnss/gnss_assistance/BnGnssAssistanceInterface.h>
#include <log/log.h>

#include "GnssAssistanceCallback.h"
#include "jni.h"

namespace android::gnss {

using IGnssAssistanceInterface = android::hardware::gnss::gnss_assistance::IGnssAssistanceInterface;
using IGnssAssistanceCallback = android::hardware::gnss::gnss_assistance::IGnssAssistanceCallback;
using BeidouAssistance = android::hardware::gnss::gnss_assistance::GnssAssistance::BeidouAssistance;
using BeidouSatelliteEphemeris = android::hardware::gnss::gnss_assistance::BeidouSatelliteEphemeris;
using GalileoAssistance =
        android::hardware::gnss::gnss_assistance::GnssAssistance::GalileoAssistance;
using GalileoSatelliteEphemeris =
        android::hardware::gnss::gnss_assistance::GalileoSatelliteEphemeris;
using GalileoIonosphericModel = android::hardware::gnss::gnss_assistance::GalileoIonosphericModel;
using GlonassAssistance =
        android::hardware::gnss::gnss_assistance::GnssAssistance::GlonassAssistance;
using GlonassAlmanac = android::hardware::gnss::gnss_assistance::GlonassAlmanac;
using GlonassSatelliteEphemeris =
        android::hardware::gnss::gnss_assistance::GlonassSatelliteEphemeris;
using GnssAssistance = android::hardware::gnss::gnss_assistance::GnssAssistance;
using GnssSignalType = android::hardware::gnss::GnssSignalType;
using GpsAssistance = android::hardware::gnss::gnss_assistance::GnssAssistance::GpsAssistance;
using QzssAssistance = android::hardware::gnss::gnss_assistance::GnssAssistance::QzssAssistance;
using GnssAlmanac = android::hardware::gnss::gnss_assistance::GnssAlmanac;
using GnssSatelliteCorrections =
        android::hardware::gnss::gnss_assistance::GnssAssistance::GnssSatelliteCorrections;
using GpsSatelliteEphemeris = android::hardware::gnss::gnss_assistance::GpsSatelliteEphemeris;
using SatelliteEphemerisTime = android::hardware::gnss::gnss_assistance::SatelliteEphemerisTime;
using UtcModel = android::hardware::gnss::gnss_assistance::UtcModel;
using LeapSecondsModel = android::hardware::gnss::gnss_assistance::LeapSecondsModel;
using KeplerianOrbitModel = android::hardware::gnss::gnss_assistance::KeplerianOrbitModel;
using KlobucharIonosphericModel =
        android::hardware::gnss::gnss_assistance::KlobucharIonosphericModel;
using TimeModel = android::hardware::gnss::gnss_assistance::TimeModel;
using RealTimeIntegrityModel = android::hardware::gnss::gnss_assistance::RealTimeIntegrityModel;
using AuxiliaryInformation = android::hardware::gnss::gnss_assistance::AuxiliaryInformation;

void GnssAssistance_class_init_once(JNIEnv* env, jclass clazz);

class GnssAssistanceInterface {
public:
    GnssAssistanceInterface(const sp<IGnssAssistanceInterface>& iGnssAssistance);
    jboolean injectGnssAssistance(JNIEnv* env, jobject gnssAssistanceObj);
    jboolean setCallback(const sp<IGnssAssistanceCallback>& callback);

private:
    const sp<IGnssAssistanceInterface> mGnssAssistanceInterface;
};

struct GnssAssistanceUtil {
    static void setGlonassAssistance(JNIEnv* env, jobject glonassAssistanceObj,
                                     std::optional<GlonassAssistance>& glonassAssistanceOpt);
    static void setGlonassSatelliteEphemeris(
            JNIEnv* env, jobject glonassSatelliteEphemerisObj,
            std::vector<GlonassSatelliteEphemeris>& glonassSatelliteEphemerisList);
    static void setGlonassAlmanac(JNIEnv* env, jobject glonassAlmanacObj,
                                  std::optional<GlonassAlmanac>& glonassAlmanacOpt);
    static void setBeidouAssistance(JNIEnv* env, jobject beidouAssistanceObj,
                                    std::optional<BeidouAssistance>& beidouAssistanceOpt);
    static void setBeidouSatelliteEphemeris(
            JNIEnv* env, jobject beidouSatelliteEphemerisObj,
            std::vector<BeidouSatelliteEphemeris>& beidouSatelliteEphemerisList);
    static void setGalileoAssistance(JNIEnv* env, jobject galileoAssistanceObj,
                                     std::optional<GalileoAssistance>& galileoAssistanceOpt);
    static void setGalileoSatelliteEphemeris(
            JNIEnv* env, jobject galileoSatelliteEphemerisObj,
            std::vector<GalileoSatelliteEphemeris>& galileoSatelliteEphemerisList);
    static void setGalileoIonosphericModel(
            JNIEnv* env, jobject galileoIonosphericModelObj,
            std::optional<GalileoIonosphericModel>& ionosphericModelOpt);
    static void setGnssAssistance(JNIEnv* env, jobject gnssAssistanceObj,
                                  GnssAssistance& gnssAssistance);
    static void setGpsAssistance(JNIEnv* env, jobject gpsAssistanceObj,
                                 std::optional<GpsAssistance>& gpsAssistanceOpt);
    template <class T>
    static void setGpsOrQzssSatelliteEphemeris(JNIEnv* env, jobject satelliteEphemerisObj,
                                               std::vector<T>& satelliteEphemeris);
    static void setQzssAssistance(JNIEnv* env, jobject qzssAssistanceObj,
                                  std::optional<QzssAssistance>& qzssAssistanceOpt);
    static void setGnssAlmanac(JNIEnv* env, jobject gnssAlmanacObj,
                               std::optional<GnssAlmanac>& gnssAlmanacOpt);
    static void setGnssSignalType(JNIEnv* env, jobject gnssSignalTypeObj,
                                  GnssSignalType& gnssSignalType);
    static void setKeplerianOrbitModel(JNIEnv* env, jobject keplerianOrbitModelObj,
                                       KeplerianOrbitModel& keplerianOrbitModel);
    static void setKlobucharIonosphericModel(
            JNIEnv* env, jobject klobucharIonosphericModelObj,
            std::optional<KlobucharIonosphericModel>& klobucharIonosphericModelOpt);
    static void setTimeModels(JNIEnv* env, jobject timeModelsObj,
                              std::vector<TimeModel>& timeModels);
    static void setLeapSecondsModel(JNIEnv* env, jobject leapSecondsModelObj,
                                    std::optional<LeapSecondsModel>& leapSecondsModelOpt);
    static void setRealTimeIntegrityModels(
            JNIEnv* env, jobject realTimeIntegrityModelsObj,
            std::vector<RealTimeIntegrityModel>& realTimeIntegrityModels);
    static void setSatelliteEphemerisTime(JNIEnv* env, jobject satelliteEphemerisTimeObj,
                                          SatelliteEphemerisTime& satelliteEphemerisTime);
    static void setUtcModel(JNIEnv* env, jobject utcModelObj, std::optional<UtcModel>& utcModelOpt);
    static void setSatelliteCorrections(
            JNIEnv* env, jobject satelliteCorrectionsObj,
            std::vector<GnssSatelliteCorrections>& satelliteCorrections);
    static void setAuxiliaryInformations(JNIEnv* env, jobject auxiliaryInformationListObj,
                                         std::vector<AuxiliaryInformation>& auxiliaryInformations);
};

} // namespace android::gnss

#endif // _ANDROID_SERVER_GNSSASSITANCE__H
