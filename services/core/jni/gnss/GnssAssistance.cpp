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

// Define LOG_TAG before <log/log.h> to overwrite the default value.

#define LOG_TAG "GnssAssistanceJni"

#include "GnssAssistance.h"

#include <utils/String16.h>

#include "GnssAssistanceCallback.h"
#include "Utils.h"

namespace android::gnss {

using GnssConstellationType = android::hardware::gnss::GnssConstellationType;
using GnssCorrectionComponent = android::hardware::gnss::gnss_assistance::GnssCorrectionComponent;
using GnssInterval =
        android::hardware::gnss::gnss_assistance::GnssCorrectionComponent::GnssInterval;
using GnssSatelliteAlmanac =
        android::hardware::gnss::gnss_assistance::GnssAlmanac::GnssSatelliteAlmanac;
using IonosphericCorrection = android::hardware::gnss::gnss_assistance::IonosphericCorrection;
using PseudorangeCorrection =
        android::hardware::gnss::gnss_assistance::GnssCorrectionComponent::PseudorangeCorrection;
using GalileoSatelliteClockModel = android::hardware::gnss::gnss_assistance::
        GalileoSatelliteEphemeris::GalileoSatelliteClockModel;
using GalileoSvHealth =
        android::hardware::gnss::gnss_assistance::GalileoSatelliteEphemeris::GalileoSvHealth;
using GlonassSatelliteAlmanac =
        android::hardware::gnss::gnss_assistance::GlonassAlmanac::GlonassSatelliteAlmanac;
using GlonassSatelliteClockModel = android::hardware::gnss::gnss_assistance::
        GlonassSatelliteEphemeris::GlonassSatelliteClockModel;
using GlonassSatelliteOrbitModel = android::hardware::gnss::gnss_assistance::
        GlonassSatelliteEphemeris::GlonassSatelliteOrbitModel;
using GnssSignalType = hardware::gnss::GnssSignalType;
using GnssConstellationType = hardware::gnss::GnssConstellationType;
using BeidouB1CSatelliteOrbitType =
        android::hardware::gnss::gnss_assistance::AuxiliaryInformation::BeidouB1CSatelliteOrbitType;
using QzssSatelliteEphemeris = android::hardware::gnss::gnss_assistance::QzssSatelliteEphemeris;

// Implementation of GnssAssistance (AIDL HAL)

namespace {
jmethodID method_gnssAssistanceGetGpsAssistance;
jmethodID method_gnssAssistanceGetGlonassAssistance;
jmethodID method_gnssAssistanceGetGalileoAssistance;
jmethodID method_gnssAssistanceGetBeidouAssistance;
jmethodID method_gnssAssistanceGetQzssAssistance;

jmethodID method_listSize;
jmethodID method_listGet;

jmethodID method_gnssAlmanacGetIssueDateMillis;
jmethodID method_gnssAlmanacGetIoda;
jmethodID method_gnssAlmanacGetWeekNumber;
jmethodID method_gnssAlmanacGetToaSeconds;
jmethodID method_gnssAlmanacGetSatelliteAlmanacs;
jmethodID method_gnssAlmanacIsCompleteAlmanacProvided;
jmethodID method_satelliteAlmanacGetSvid;
jmethodID method_satelliteAlmanacGetSvHealth;
jmethodID method_satelliteAlmanacGetAf0;
jmethodID method_satelliteAlmanacGetAf1;
jmethodID method_satelliteAlmanacGetEccentricity;
jmethodID method_satelliteAlmanacGetInclination;
jmethodID method_satelliteAlmanacGetM0;
jmethodID method_satelliteAlmanacGetOmega;
jmethodID method_satelliteAlmanacGetOmega0;
jmethodID method_satelliteAlmanacGetOmegaDot;
jmethodID method_satelliteAlmanacGetRootA;

jmethodID method_satelliteEphemerisTimeGetIode;
jmethodID method_satelliteEphemerisTimeGetToeSeconds;
jmethodID method_satelliteEphemerisTimeGetWeekNumber;

jmethodID method_keplerianOrbitModelGetDeltaN;
jmethodID method_keplerianOrbitModelGetEccentricity;
jmethodID method_keplerianOrbitModelGetI0;
jmethodID method_keplerianOrbitModelGetIDot;
jmethodID method_keplerianOrbitModelGetM0;
jmethodID method_keplerianOrbitModelGetOmega;
jmethodID method_keplerianOrbitModelGetOmega0;
jmethodID method_keplerianOrbitModelGetOmegaDot;
jmethodID method_keplerianOrbitModelGetRootA;
jmethodID method_keplerianOrbitModelGetSecondOrderHarmonicPerturbation;
jmethodID method_secondOrderHarmonicPerturbationGetCic;
jmethodID method_secondOrderHarmonicPerturbationGetCis;
jmethodID method_secondOrderHarmonicPerturbationGetCrc;
jmethodID method_secondOrderHarmonicPerturbationGetCrs;
jmethodID method_secondOrderHarmonicPerturbationGetCuc;
jmethodID method_secondOrderHarmonicPerturbationGetCus;

jmethodID method_klobucharIonosphericModelGetAlpha0;
jmethodID method_klobucharIonosphericModelGetAlpha1;
jmethodID method_klobucharIonosphericModelGetAlpha2;
jmethodID method_klobucharIonosphericModelGetAlpha3;
jmethodID method_klobucharIonosphericModelGetBeta0;
jmethodID method_klobucharIonosphericModelGetBeta1;
jmethodID method_klobucharIonosphericModelGetBeta2;
jmethodID method_klobucharIonosphericModelGetBeta3;

jmethodID method_utcModelGetA0;
jmethodID method_utcModelGetA1;
jmethodID method_utcModelGetTimeOfWeek;
jmethodID method_utcModelGetWeekNumber;

jmethodID method_leapSecondsModelGetDayNumberLeapSecondsFuture;
jmethodID method_leapSecondsModelGetLeapSeconds;
jmethodID method_leapSecondsModelGetLeapSecondsFuture;
jmethodID method_leapSecondsModelGetWeekNumberLeapSecondsFuture;

jmethodID method_timeModelsGetTimeOfWeek;
jmethodID method_timeModelsGetToGnss;
jmethodID method_timeModelsGetWeekNumber;
jmethodID method_timeModelsGetA0;
jmethodID method_timeModelsGetA1;

jmethodID method_realTimeIntegrityModelGetBadSvid;
jmethodID method_realTimeIntegrityModelGetBadSignalTypes;
jmethodID method_realTimeIntegrityModelGetStartDateSeconds;
jmethodID method_realTimeIntegrityModelGetEndDateSeconds;
jmethodID method_realTimeIntegrityModelGetPublishDateSeconds;
jmethodID method_realTimeIntegrityModelGetAdvisoryNumber;
jmethodID method_realTimeIntegrityModelGetAdvisoryType;

jmethodID method_gnssSignalTypeGetConstellationType;
jmethodID method_gnssSignalTypeGetCarrierFrequencyHz;
jmethodID method_gnssSignalTypeGetCodeType;

jmethodID method_auxiliaryInformationGetSvid;
jmethodID method_auxiliaryInformationGetAvailableSignalTypes;
jmethodID method_auxiliaryInformationGetFrequencyChannelNumber;
jmethodID method_auxiliaryInformationGetSatType;

jmethodID method_satelliteCorrectionGetSvid;
jmethodID method_satelliteCorrectionGetIonosphericCorrections;
jmethodID method_ionosphericCorrectionGetCarrierFrequencyHz;
jmethodID method_ionosphericCorrectionGetIonosphericCorrection;
jmethodID method_gnssCorrectionComponentGetPseudorangeCorrection;
jmethodID method_gnssCorrectionComponentGetSourceKey;
jmethodID method_gnssCorrectionComponentGetValidityInterval;
jmethodID method_pseudorangeCorrectionGetCorrectionMeters;
jmethodID method_pseudorangeCorrectionGetCorrectionUncertaintyMeters;
jmethodID method_pseudorangeCorrectionGetCorrectionRateMetersPerSecond;
jmethodID method_gnssIntervalGetStartMillisSinceGpsEpoch;
jmethodID method_gnssIntervalGetEndMillisSinceGpsEpoch;

jmethodID method_gpsAssistanceGetAlmanac;
jmethodID method_gpsAssistanceGetIonosphericModel;
jmethodID method_gpsAssistanceGetUtcModel;
jmethodID method_gpsAssistanceGetLeapSecondsModel;
jmethodID method_gpsAssistanceGetTimeModels;
jmethodID method_gpsAssistanceGetSatelliteEphemeris;
jmethodID method_gpsAssistanceGetRealTimeIntegrityModels;
jmethodID method_gpsAssistanceGetSatelliteCorrections;
jmethodID method_gpsAssistanceGetAuxiliaryInformation;
jmethodID method_gpsSatelliteEphemerisGetSvid;
jmethodID method_gpsSatelliteEphemerisGetGpsL2Params;
jmethodID method_gpsSatelliteEphemerisGetSatelliteClockModel;
jmethodID method_gpsSatelliteEphemerisGetSatelliteOrbitModel;
jmethodID method_gpsSatelliteEphemerisGetSatelliteHealth;
jmethodID method_gpsSatelliteEphemerisGetSatelliteEphemerisTime;
jmethodID method_gpsL2ParamsGetL2Code;
jmethodID method_gpsL2ParamsGetL2Flag;
jmethodID method_gpsSatelliteClockModelGetAf0;
jmethodID method_gpsSatelliteClockModelGetAf1;
jmethodID method_gpsSatelliteClockModelGetAf2;
jmethodID method_gpsSatelliteClockModelGetTgd;
jmethodID method_gpsSatelliteClockModelGetIodc;
jmethodID method_gpsSatelliteClockModelGetTimeOfClockSeconds;
jmethodID method_gpsSatelliteHealthGetFitInt;
jmethodID method_gpsSatelliteHealthGetSvAccur;
jmethodID method_gpsSatelliteHealthGetSvHealth;

jmethodID method_beidouAssistanceGetAlmanac;
jmethodID method_beidouAssistanceGetIonosphericModel;
jmethodID method_beidouAssistanceGetUtcModel;
jmethodID method_beidouAssistanceGetLeapSecondsModel;
jmethodID method_beidouAssistanceGetTimeModels;
jmethodID method_beidouAssistanceGetSatelliteEphemeris;
jmethodID method_beidouAssistanceGetSatelliteCorrections;
jmethodID method_beidouAssistanceGetRealTimeIntegrityModels;
jmethodID method_beidouAssistanceGetAuxiliaryInformation;
jmethodID method_beidouSatelliteEphemerisGetSvid;
jmethodID method_beidouSatelliteEphemerisGetSatelliteClockModel;
jmethodID method_beidouSatelliteEphemerisGetSatelliteOrbitModel;
jmethodID method_beidouSatelliteEphemerisGetSatelliteHealth;
jmethodID method_beidouSatelliteEphemerisGetSatelliteEphemerisTime;
jmethodID method_beidouSatelliteClockModelGetAf0;
jmethodID method_beidouSatelliteClockModelGetAf1;
jmethodID method_beidouSatelliteClockModelGetAf2;
jmethodID method_beidouSatelliteClockModelGetAodc;
jmethodID method_beidouSatelliteClockModelGetTgd1;
jmethodID method_beidouSatelliteClockModelGetTgd2;
jmethodID method_beidouSatelliteClockModelGetTimeOfClockSeconds;
jmethodID method_beidouSatelliteHealthGetSatH1;
jmethodID method_beidouSatelliteHealthGetSvAccur;
jmethodID method_beidouSatelliteEphemerisTimeGetAode;
jmethodID method_beidouSatelliteEphemerisTimeGetBeidouWeekNumber;
jmethodID method_beidouSatelliteEphemerisTimeGetToeSeconds;

jmethodID method_galileoAssistanceGetAlmanac;
jmethodID method_galileoAssistanceGetIonosphericModel;
jmethodID method_galileoAssistanceGetUtcModel;
jmethodID method_galileoAssistanceGetLeapSecondsModel;
jmethodID method_galileoAssistanceGetTimeModels;
jmethodID method_galileoAssistanceGetSatelliteEphemeris;
jmethodID method_galileoAssistanceGetSatelliteCorrections;
jmethodID method_galileoAssistanceGetRealTimeIntegrityModels;
jmethodID method_galileoAssistanceGetAuxiliaryInformation;
jmethodID method_galileoSatelliteEphemerisGetSvid;
jmethodID method_galileoSatelliteEphemerisGetSatelliteClockModels;
jmethodID method_galileoSatelliteEphemerisGetSatelliteOrbitModel;
jmethodID method_galileoSatelliteEphemerisGetSatelliteHealth;
jmethodID method_galileoSatelliteEphemerisGetSatelliteEphemerisTime;
jmethodID method_galileoSatelliteClockModelGetAf0;
jmethodID method_galileoSatelliteClockModelGetAf1;
jmethodID method_galileoSatelliteClockModelGetAf2;
jmethodID method_galileoSatelliteClockModelGetBgdSeconds;
jmethodID method_galileoSatelliteClockModelGetSatelliteClockType;
jmethodID method_galileoSatelliteClockModelGetSisaMeters;
jmethodID method_galileoSatelliteClockModelGetTimeOfClockSeconds;
jmethodID method_galileoSvHealthGetDataValidityStatusE1b;
jmethodID method_galileoSvHealthGetDataValidityStatusE5a;
jmethodID method_galileoSvHealthGetDataValidityStatusE5b;
jmethodID method_galileoSvHealthGetSignalHealthStatusE1b;
jmethodID method_galileoSvHealthGetSignalHealthStatusE5a;
jmethodID method_galileoSvHealthGetSignalHealthStatusE5b;
jmethodID method_galileoIonosphericModelGetAi0;
jmethodID method_galileoIonosphericModelGetAi1;
jmethodID method_galileoIonosphericModelGetAi2;

jmethodID method_glonassAssistanceGetAlmanac;
jmethodID method_glonassAssistanceGetUtcModel;
jmethodID method_glonassAssistanceGetTimeModels;
jmethodID method_glonassAssistanceGetSatelliteEphemeris;
jmethodID method_glonassAssistanceGetSatelliteCorrections;
jmethodID method_glonassAssistanceGetRealTimeIntegrityModels;
jmethodID method_glonassAssistanceGetAuxiliaryInformation;
jmethodID method_glonassAlmanacGetIssueDateMillis;
jmethodID method_glonassAlmanacGetSatelliteAlmanacs;
jmethodID method_glonassSatelliteAlmanacGetDeltaI;
jmethodID method_glonassSatelliteAlmanacGetDeltaT;
jmethodID method_glonassSatelliteAlmanacGetDeltaTDot;
jmethodID method_glonassSatelliteAlmanacGetEccentricity;
jmethodID method_glonassSatelliteAlmanacGetFrequencyChannelNumber;
jmethodID method_glonassSatelliteAlmanacGetLambda;
jmethodID method_glonassSatelliteAlmanacGetOmega;
jmethodID method_glonassSatelliteAlmanacGetSlotNumber;
jmethodID method_glonassSatelliteAlmanacGetHealthState;
jmethodID method_glonassSatelliteAlmanacGetTLambda;
jmethodID method_glonassSatelliteAlmanacGetTau;
jmethodID method_glonassSatelliteAlmanacGetIsGlonassM;
jmethodID method_glonassSatelliteAlmanacGetCalendarDayNumber;
jmethodID method_glonassSatelliteEphemerisGetAgeInDays;
jmethodID method_glonassSatelliteEphemerisGetSatelliteClockModel;
jmethodID method_glonassSatelliteEphemerisGetSatelliteOrbitModel;
jmethodID method_glonassSatelliteEphemerisGetHealthState;
jmethodID method_glonassSatelliteEphemerisGetSlotNumber;
jmethodID method_glonassSatelliteEphemerisGetFrameTimeSeconds;
jmethodID method_glonassSatelliteEphemerisGetUpdateIntervalMinutes;
jmethodID method_glonassSatelliteEphemerisGetIsGlonassM;
jmethodID method_glonassSatelliteEphemerisGetIsUpdateIntervalOdd;

jmethodID method_glonassSatelliteOrbitModelGetX;
jmethodID method_glonassSatelliteOrbitModelGetY;
jmethodID method_glonassSatelliteOrbitModelGetZ;
jmethodID method_glonassSatelliteOrbitModelGetXAccel;
jmethodID method_glonassSatelliteOrbitModelGetYAccel;
jmethodID method_glonassSatelliteOrbitModelGetZAccel;
jmethodID method_glonassSatelliteOrbitModelGetXDot;
jmethodID method_glonassSatelliteOrbitModelGetYDot;
jmethodID method_glonassSatelliteOrbitModelGetZDot;
jmethodID method_glonassSatelliteClockModelGetClockBias;
jmethodID method_glonassSatelliteClockModelGetFrequencyBias;
jmethodID method_glonassSatelliteClockModelGetFrequencyChannelNumber;
jmethodID method_glonassSatelliteClockModelGetTimeOfClockSeconds;

jmethodID method_qzssAssistanceGetAlmanac;
jmethodID method_qzssAssistanceGetIonosphericModel;
jmethodID method_qzssAssistanceGetUtcModel;
jmethodID method_qzssAssistanceGetLeapSecondsModel;
jmethodID method_qzssAssistanceGetTimeModels;
jmethodID method_qzssAssistanceGetSatelliteEphemeris;
jmethodID method_qzssAssistanceGetSatelliteCorrections;
jmethodID method_qzssAssistanceGetAuxiliaryInformation;
jmethodID method_qzssAssistanceGetRealTimeIntegrityModels;
jmethodID method_qzssSatelliteEphemerisGetSvid;
jmethodID method_qzssSatelliteEphemerisGetGpsL2Params;
jmethodID method_qzssSatelliteEphemerisGetSatelliteClockModel;
jmethodID method_qzssSatelliteEphemerisGetSatelliteOrbitModel;
jmethodID method_qzssSatelliteEphemerisGetSatelliteHealth;
jmethodID method_qzssSatelliteEphemerisGetSatelliteEphemerisTime;
jmethodID method_qzssSatelliteClockModelGetAf0;
jmethodID method_qzssSatelliteClockModelGetAf1;
jmethodID method_qzssSatelliteClockModelGetAf2;
jmethodID method_qzssSatelliteClockModelGetAodc;
jmethodID method_qzssSatelliteClockModelGetTgd1;
jmethodID method_qzssSatelliteClockModelGetTgd2;
jmethodID method_qzssSatelliteClockModelGetTimeOfClockSeconds;
} // namespace

void GnssAssistance_class_init_once(JNIEnv* env, jclass clazz) {
    // Get the methods of GnssAssistance class.
    jclass gnssAssistanceClass = env->FindClass("android/location/GnssAssistance");

    method_gnssAssistanceGetGpsAssistance =
            env->GetMethodID(gnssAssistanceClass, "getGpsAssistance",
                             "()Landroid/location/GpsAssistance;");
    method_gnssAssistanceGetGlonassAssistance =
            env->GetMethodID(gnssAssistanceClass, "getGlonassAssistance",
                             "()Landroid/location/GlonassAssistance;");
    method_gnssAssistanceGetGalileoAssistance =
            env->GetMethodID(gnssAssistanceClass, "getGalileoAssistance",
                             "()Landroid/location/GalileoAssistance;");
    method_gnssAssistanceGetBeidouAssistance =
            env->GetMethodID(gnssAssistanceClass, "getBeidouAssistance",
                             "()Landroid/location/BeidouAssistance;");
    method_gnssAssistanceGetQzssAssistance =
            env->GetMethodID(gnssAssistanceClass, "getQzssAssistance",
                             "()Landroid/location/QzssAssistance;");

    // Get the methods of List class.
    jclass listClass = env->FindClass("java/util/List");

    method_listSize = env->GetMethodID(listClass, "size", "()I");
    method_listGet = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

    // Get the methods of GnssAlmanac class.
    jclass gnssAlmanacClass = env->FindClass("android/location/GnssAlmanac");

    method_gnssAlmanacGetIssueDateMillis =
            env->GetMethodID(gnssAlmanacClass, "getIssueDateMillis", "()J");
    method_gnssAlmanacGetIoda = env->GetMethodID(gnssAlmanacClass, "getIoda", "()I");
    method_gnssAlmanacGetWeekNumber = env->GetMethodID(gnssAlmanacClass, "getWeekNumber", "()I");
    method_gnssAlmanacGetToaSeconds = env->GetMethodID(gnssAlmanacClass, "getToaSeconds", "()I");
    method_gnssAlmanacGetSatelliteAlmanacs =
            env->GetMethodID(gnssAlmanacClass, "getGnssSatelliteAlmanacs", "()Ljava/util/List;");
    method_gnssAlmanacIsCompleteAlmanacProvided =
            env->GetMethodID(gnssAlmanacClass, "isCompleteAlmanacProvided", "()Z");

    // Get the methods of SatelliteAlmanac class.
    jclass satelliteAlmanacClass =
            env->FindClass("android/location/GnssAlmanac$GnssSatelliteAlmanac");

    method_satelliteAlmanacGetSvid = env->GetMethodID(satelliteAlmanacClass, "getSvid", "()I");
    method_satelliteAlmanacGetSvHealth =
            env->GetMethodID(satelliteAlmanacClass, "getSvHealth", "()I");
    method_satelliteAlmanacGetAf0 = env->GetMethodID(satelliteAlmanacClass, "getAf0", "()D");
    method_satelliteAlmanacGetAf1 = env->GetMethodID(satelliteAlmanacClass, "getAf1", "()D");
    method_satelliteAlmanacGetEccentricity =
            env->GetMethodID(satelliteAlmanacClass, "getEccentricity", "()D");
    method_satelliteAlmanacGetInclination =
            env->GetMethodID(satelliteAlmanacClass, "getInclination", "()D");
    method_satelliteAlmanacGetM0 = env->GetMethodID(satelliteAlmanacClass, "getM0", "()D");
    method_satelliteAlmanacGetOmega = env->GetMethodID(satelliteAlmanacClass, "getOmega", "()D");
    method_satelliteAlmanacGetOmega0 = env->GetMethodID(satelliteAlmanacClass, "getOmega0", "()D");
    method_satelliteAlmanacGetOmegaDot =
            env->GetMethodID(satelliteAlmanacClass, "getOmegaDot", "()D");
    method_satelliteAlmanacGetRootA = env->GetMethodID(satelliteAlmanacClass, "getRootA", "()D");

    // Get the mothods of SatelliteEphemerisTime class.
    jclass satelliteEphemerisTimeClass = env->FindClass("android/location/SatelliteEphemerisTime");

    method_satelliteEphemerisTimeGetIode =
            env->GetMethodID(satelliteEphemerisTimeClass, "getIode", "()I");
    method_satelliteEphemerisTimeGetToeSeconds =
            env->GetMethodID(satelliteEphemerisTimeClass, "getToeSeconds", "()I");
    method_satelliteEphemerisTimeGetWeekNumber =
            env->GetMethodID(satelliteEphemerisTimeClass, "getWeekNumber", "()I");

    // Get the mothods of KeplerianOrbitModel class.
    jclass keplerianOrbitModelClass = env->FindClass("android/location/KeplerianOrbitModel");

    method_keplerianOrbitModelGetDeltaN =
            env->GetMethodID(keplerianOrbitModelClass, "getDeltaN", "()D");
    method_keplerianOrbitModelGetEccentricity =
            env->GetMethodID(keplerianOrbitModelClass, "getEccentricity", "()D");
    method_keplerianOrbitModelGetI0 = env->GetMethodID(keplerianOrbitModelClass, "getI0", "()D");
    method_keplerianOrbitModelGetIDot =
            env->GetMethodID(keplerianOrbitModelClass, "getIDot", "()D");
    method_keplerianOrbitModelGetM0 = env->GetMethodID(keplerianOrbitModelClass, "getM0", "()D");
    method_keplerianOrbitModelGetOmega =
            env->GetMethodID(keplerianOrbitModelClass, "getOmega", "()D");
    method_keplerianOrbitModelGetOmega0 =
            env->GetMethodID(keplerianOrbitModelClass, "getOmega0", "()D");
    method_keplerianOrbitModelGetOmegaDot =
            env->GetMethodID(keplerianOrbitModelClass, "getOmegaDot", "()D");
    method_keplerianOrbitModelGetRootA =
            env->GetMethodID(keplerianOrbitModelClass, "getRootA", "()D");
    method_keplerianOrbitModelGetSecondOrderHarmonicPerturbation =
            env->GetMethodID(keplerianOrbitModelClass, "getSecondOrderHarmonicPerturbation",
                             "()Landroid/location/"
                             "KeplerianOrbitModel$SecondOrderHarmonicPerturbation;");

    // Get the methods of SecondOrderHarmonicPerturbation class.
    jclass secondOrderHarmonicPerturbationClass =
            env->FindClass("android/location/KeplerianOrbitModel$SecondOrderHarmonicPerturbation");

    method_secondOrderHarmonicPerturbationGetCic =
            env->GetMethodID(secondOrderHarmonicPerturbationClass, "getCic", "()D");
    method_secondOrderHarmonicPerturbationGetCis =
            env->GetMethodID(secondOrderHarmonicPerturbationClass, "getCis", "()D");
    method_secondOrderHarmonicPerturbationGetCrc =
            env->GetMethodID(secondOrderHarmonicPerturbationClass, "getCrc", "()D");
    method_secondOrderHarmonicPerturbationGetCrs =
            env->GetMethodID(secondOrderHarmonicPerturbationClass, "getCrs", "()D");
    method_secondOrderHarmonicPerturbationGetCuc =
            env->GetMethodID(secondOrderHarmonicPerturbationClass, "getCuc", "()D");
    method_secondOrderHarmonicPerturbationGetCus =
            env->GetMethodID(secondOrderHarmonicPerturbationClass, "getCus", "()D");

    // Get the methods of KlobucharIonosphericModel class.
    jclass klobucharIonosphericModelClass =
            env->FindClass("android/location/KlobucharIonosphericModel");

    method_klobucharIonosphericModelGetAlpha0 =
            env->GetMethodID(klobucharIonosphericModelClass, "getAlpha0", "()D");
    method_klobucharIonosphericModelGetAlpha1 =
            env->GetMethodID(klobucharIonosphericModelClass, "getAlpha1", "()D");
    method_klobucharIonosphericModelGetAlpha2 =
            env->GetMethodID(klobucharIonosphericModelClass, "getAlpha2", "()D");
    method_klobucharIonosphericModelGetAlpha3 =
            env->GetMethodID(klobucharIonosphericModelClass, "getAlpha3", "()D");
    method_klobucharIonosphericModelGetBeta0 =
            env->GetMethodID(klobucharIonosphericModelClass, "getBeta0", "()D");
    method_klobucharIonosphericModelGetBeta1 =
            env->GetMethodID(klobucharIonosphericModelClass, "getBeta1", "()D");
    method_klobucharIonosphericModelGetBeta2 =
            env->GetMethodID(klobucharIonosphericModelClass, "getBeta2", "()D");
    method_klobucharIonosphericModelGetBeta3 =
            env->GetMethodID(klobucharIonosphericModelClass, "getBeta3", "()D");

    // Get the methods of UtcModel class.
    jclass utcModelClass = env->FindClass("android/location/UtcModel");

    method_utcModelGetA0 = env->GetMethodID(utcModelClass, "getA0", "()D");
    method_utcModelGetA1 = env->GetMethodID(utcModelClass, "getA1", "()D");
    method_utcModelGetTimeOfWeek = env->GetMethodID(utcModelClass, "getTimeOfWeek", "()I");
    method_utcModelGetWeekNumber = env->GetMethodID(utcModelClass, "getWeekNumber", "()I");

    // Get the methods of LeapSecondsModel class.
    jclass leapSecondsModelClass = env->FindClass("android/location/LeapSecondsModel");

    method_leapSecondsModelGetDayNumberLeapSecondsFuture =
            env->GetMethodID(leapSecondsModelClass, "getDayNumberLeapSecondsFuture", "()I");
    method_leapSecondsModelGetLeapSeconds =
            env->GetMethodID(leapSecondsModelClass, "getLeapSeconds", "()I");
    method_leapSecondsModelGetLeapSecondsFuture =
            env->GetMethodID(leapSecondsModelClass, "getLeapSecondsFuture", "()I");
    method_leapSecondsModelGetWeekNumberLeapSecondsFuture =
            env->GetMethodID(leapSecondsModelClass, "getWeekNumberLeapSecondsFuture", "()I");

    // Get the methods of TimeModel class.
    jclass timeModelsClass = env->FindClass("android/location/TimeModel");

    method_timeModelsGetTimeOfWeek = env->GetMethodID(timeModelsClass, "getTimeOfWeek", "()I");
    method_timeModelsGetToGnss = env->GetMethodID(timeModelsClass, "getToGnss", "()I");
    method_timeModelsGetWeekNumber = env->GetMethodID(timeModelsClass, "getWeekNumber", "()I");
    method_timeModelsGetA0 = env->GetMethodID(timeModelsClass, "getA0", "()D");
    method_timeModelsGetA1 = env->GetMethodID(timeModelsClass, "getA1", "()D");

    // Get the methods of AuxiliaryInformation class.
    jclass auxiliaryInformationClass = env->FindClass("android/location/AuxiliaryInformation");

    method_auxiliaryInformationGetSvid =
            env->GetMethodID(auxiliaryInformationClass, "getSvid", "()I");
    method_auxiliaryInformationGetAvailableSignalTypes =
            env->GetMethodID(auxiliaryInformationClass, "getAvailableSignalTypes",
                             "()Ljava/util/List;");
    method_auxiliaryInformationGetFrequencyChannelNumber =
            env->GetMethodID(auxiliaryInformationClass, "getFrequencyChannelNumber", "()I");
    method_auxiliaryInformationGetSatType =
            env->GetMethodID(auxiliaryInformationClass, "getSatType", "()I");

    // Get the methods of RealTimeIntegrityModel
    jclass realTimeIntegrityModelClass = env->FindClass("android/location/RealTimeIntegrityModel");

    method_realTimeIntegrityModelGetBadSvid =
            env->GetMethodID(realTimeIntegrityModelClass, "getBadSvid", "()I");
    method_realTimeIntegrityModelGetBadSignalTypes =
            env->GetMethodID(realTimeIntegrityModelClass, "getBadSignalTypes",
                             "()Ljava/util/List;");
    method_realTimeIntegrityModelGetStartDateSeconds =
            env->GetMethodID(realTimeIntegrityModelClass, "getStartDateSeconds", "()J");
    method_realTimeIntegrityModelGetEndDateSeconds =
            env->GetMethodID(realTimeIntegrityModelClass, "getEndDateSeconds", "()J");
    method_realTimeIntegrityModelGetPublishDateSeconds =
            env->GetMethodID(realTimeIntegrityModelClass, "getPublishDateSeconds", "()J");
    method_realTimeIntegrityModelGetAdvisoryNumber =
            env->GetMethodID(realTimeIntegrityModelClass, "getAdvisoryNumber",
                             "()Ljava/lang/String;");
    method_realTimeIntegrityModelGetAdvisoryType =
            env->GetMethodID(realTimeIntegrityModelClass, "getAdvisoryType",
                             "()Ljava/lang/String;");

    // Get the methods of GnssSignalType class.
    jclass gnssSignalTypeClass = env->FindClass("android/location/GnssSignalType");

    method_gnssSignalTypeGetConstellationType =
            env->GetMethodID(gnssSignalTypeClass, "getConstellationType", "()I");
    method_gnssSignalTypeGetCarrierFrequencyHz =
            env->GetMethodID(gnssSignalTypeClass, "getCarrierFrequencyHz", "()D");
    method_gnssSignalTypeGetCodeType =
            env->GetMethodID(gnssSignalTypeClass, "getCodeType", "()Ljava/lang/String;");

    // Get the methods of SatelliteCorrection class.
    jclass satelliteCorrectionClass =
            env->FindClass("android/location/GnssAssistance$GnssSatelliteCorrections");

    method_satelliteCorrectionGetSvid =
            env->GetMethodID(satelliteCorrectionClass, "getSvid", "()I");
    method_satelliteCorrectionGetIonosphericCorrections =
            env->GetMethodID(satelliteCorrectionClass, "getIonosphericCorrections",
                             "()Ljava/util/List;");

    // Get the methods of IonosphericCorrection class.
    jclass ionosphericCorrectionClass = env->FindClass("android/location/IonosphericCorrection");

    method_ionosphericCorrectionGetCarrierFrequencyHz =
            env->GetMethodID(ionosphericCorrectionClass, "getCarrierFrequencyHz", "()J");
    method_ionosphericCorrectionGetIonosphericCorrection =
            env->GetMethodID(ionosphericCorrectionClass, "getIonosphericCorrection",
                             "()Landroid/location/GnssCorrectionComponent;");

    // Get the methods of GnssCorrectionComponent class.
    jclass gnssCorrectionComponentClass =
            env->FindClass("android/location/GnssCorrectionComponent");

    method_gnssCorrectionComponentGetPseudorangeCorrection =
            env->GetMethodID(gnssCorrectionComponentClass, "getPseudorangeCorrection",
                             "()Landroid/location/GnssCorrectionComponent$PseudorangeCorrection;");
    method_gnssCorrectionComponentGetSourceKey =
            env->GetMethodID(gnssCorrectionComponentClass, "getSourceKey", "()Ljava/lang/String;");
    method_gnssCorrectionComponentGetValidityInterval =
            env->GetMethodID(gnssCorrectionComponentClass, "getValidityInterval",
                             "()Landroid/location/GnssCorrectionComponent$GnssInterval;");

    // Get the methods of PseudorangeCorrection class.
    jclass pseudorangeCorrectionClass =
            env->FindClass("android/location/GnssCorrectionComponent$PseudorangeCorrection");

    method_pseudorangeCorrectionGetCorrectionMeters =
            env->GetMethodID(pseudorangeCorrectionClass, "getCorrectionMeters", "()D");
    method_pseudorangeCorrectionGetCorrectionRateMetersPerSecond =
            env->GetMethodID(pseudorangeCorrectionClass, "getCorrectionRateMetersPerSecond", "()D");
    method_pseudorangeCorrectionGetCorrectionUncertaintyMeters =
            env->GetMethodID(pseudorangeCorrectionClass, "getCorrectionUncertaintyMeters", "()D");

    // Get the methods of GnssInterval class.
    jclass gnssIntervalClass =
            env->FindClass("android/location/GnssCorrectionComponent$GnssInterval");

    method_gnssIntervalGetStartMillisSinceGpsEpoch =
            env->GetMethodID(gnssIntervalClass, "getStartMillisSinceGpsEpoch", "()J");
    method_gnssIntervalGetEndMillisSinceGpsEpoch =
            env->GetMethodID(gnssIntervalClass, "getEndMillisSinceGpsEpoch", "()J");

    // Get the methods of GpsAssistance class.
    jclass gpsAssistanceClass = env->FindClass("android/location/GpsAssistance");

    method_gpsAssistanceGetAlmanac =
            env->GetMethodID(gpsAssistanceClass, "getAlmanac", "()Landroid/location/GnssAlmanac;");
    method_gpsAssistanceGetIonosphericModel =
            env->GetMethodID(gpsAssistanceClass, "getIonosphericModel",
                             "()Landroid/location/KlobucharIonosphericModel;");
    method_gpsAssistanceGetUtcModel =
            env->GetMethodID(gpsAssistanceClass, "getUtcModel", "()Landroid/location/UtcModel;");
    method_gpsAssistanceGetLeapSecondsModel =
            env->GetMethodID(gpsAssistanceClass, "getLeapSecondsModel",
                             "()Landroid/location/LeapSecondsModel;");
    method_gpsAssistanceGetTimeModels =
            env->GetMethodID(gpsAssistanceClass, "getTimeModels", "()Ljava/util/List;");
    method_gpsAssistanceGetSatelliteEphemeris =
            env->GetMethodID(gpsAssistanceClass, "getSatelliteEphemeris", "()Ljava/util/List;");
    method_gpsAssistanceGetRealTimeIntegrityModels =
            env->GetMethodID(gpsAssistanceClass, "getRealTimeIntegrityModels",
                             "()Ljava/util/List;");
    method_gpsAssistanceGetSatelliteCorrections =
            env->GetMethodID(gpsAssistanceClass, "getSatelliteCorrections", "()Ljava/util/List;");
    method_gpsAssistanceGetAuxiliaryInformation =
            env->GetMethodID(gpsAssistanceClass, "getAuxiliaryInformation", "()Ljava/util/List;");

    // Get the methods of GpsSatelliteEphemeris class.
    jclass gpsSatelliteEphemerisClass = env->FindClass("android/location/GpsSatelliteEphemeris");

    method_gpsSatelliteEphemerisGetSvid =
            env->GetMethodID(gpsSatelliteEphemerisClass, "getSvid", "()I");
    method_gpsSatelliteEphemerisGetGpsL2Params =
            env->GetMethodID(gpsSatelliteEphemerisClass, "getGpsL2Params",
                             "()Landroid/location/GpsSatelliteEphemeris$GpsL2Params;");
    method_gpsSatelliteEphemerisGetSatelliteClockModel =
            env->GetMethodID(gpsSatelliteEphemerisClass, "getSatelliteClockModel",
                             "()Landroid/location/GpsSatelliteEphemeris$GpsSatelliteClockModel;");
    method_gpsSatelliteEphemerisGetSatelliteOrbitModel =
            env->GetMethodID(gpsSatelliteEphemerisClass, "getSatelliteOrbitModel",
                             "()Landroid/location/KeplerianOrbitModel;");
    method_gpsSatelliteEphemerisGetSatelliteHealth =
            env->GetMethodID(gpsSatelliteEphemerisClass, "getSatelliteHealth",
                             "()Landroid/location/GpsSatelliteEphemeris$GpsSatelliteHealth;");
    method_gpsSatelliteEphemerisGetSatelliteEphemerisTime =
            env->GetMethodID(gpsSatelliteEphemerisClass, "getSatelliteEphemerisTime",
                             "()Landroid/location/SatelliteEphemerisTime;");

    // Get the methods of GpsL2Params class.
    jclass gpsL2ParamsClass = env->FindClass("android/location/GpsSatelliteEphemeris$GpsL2Params");
    method_gpsL2ParamsGetL2Code = env->GetMethodID(gpsL2ParamsClass, "getL2Code", "()I");
    method_gpsL2ParamsGetL2Flag = env->GetMethodID(gpsL2ParamsClass, "getL2Flag", "()I");

    // Get the methods of GpsSatelliteClockModel class.
    jclass gpsSatelliteClockModelClass =
            env->FindClass("android/location/GpsSatelliteEphemeris$GpsSatelliteClockModel");
    method_gpsSatelliteClockModelGetAf0 =
            env->GetMethodID(gpsSatelliteClockModelClass, "getAf0", "()D");
    method_gpsSatelliteClockModelGetAf1 =
            env->GetMethodID(gpsSatelliteClockModelClass, "getAf1", "()D");
    method_gpsSatelliteClockModelGetAf2 =
            env->GetMethodID(gpsSatelliteClockModelClass, "getAf2", "()D");
    method_gpsSatelliteClockModelGetTgd =
            env->GetMethodID(gpsSatelliteClockModelClass, "getTgd", "()D");
    method_gpsSatelliteClockModelGetIodc =
            env->GetMethodID(gpsSatelliteClockModelClass, "getIodc", "()I");
    method_gpsSatelliteClockModelGetTimeOfClockSeconds =
            env->GetMethodID(gpsSatelliteClockModelClass, "getTimeOfClockSeconds", "()J");

    // Get the methods of GpsSatelliteHealth class.
    jclass gpsSatelliteHealthClass =
            env->FindClass("android/location/GpsSatelliteEphemeris$GpsSatelliteHealth");
    method_gpsSatelliteHealthGetFitInt =
            env->GetMethodID(gpsSatelliteHealthClass, "getFitInt", "()D");
    method_gpsSatelliteHealthGetSvAccur =
            env->GetMethodID(gpsSatelliteHealthClass, "getSvAccur", "()D");
    method_gpsSatelliteHealthGetSvHealth =
            env->GetMethodID(gpsSatelliteHealthClass, "getSvHealth", "()I");

    // Get the methods of BeidouAssistance class.
    jclass beidouAssistanceClass = env->FindClass("android/location/BeidouAssistance");
    method_beidouAssistanceGetAlmanac = env->GetMethodID(beidouAssistanceClass, "getAlmanac",
                                                         "()Landroid/location/GnssAlmanac;");
    method_beidouAssistanceGetIonosphericModel =
            env->GetMethodID(beidouAssistanceClass, "getIonosphericModel",
                             "()Landroid/location/KlobucharIonosphericModel;");
    method_beidouAssistanceGetUtcModel =
            env->GetMethodID(beidouAssistanceClass, "getUtcModel", "()Landroid/location/UtcModel;");
    method_beidouAssistanceGetLeapSecondsModel =
            env->GetMethodID(beidouAssistanceClass, "getLeapSecondsModel",
                             "()Landroid/location/LeapSecondsModel;");
    method_beidouAssistanceGetTimeModels =
            env->GetMethodID(beidouAssistanceClass, "getTimeModels", "()Ljava/util/List;");
    method_beidouAssistanceGetSatelliteEphemeris =
            env->GetMethodID(beidouAssistanceClass, "getSatelliteEphemeris", "()Ljava/util/List;");
    method_beidouAssistanceGetSatelliteCorrections =
            env->GetMethodID(beidouAssistanceClass, "getSatelliteCorrections",
                             "()Ljava/util/List;");
    method_beidouAssistanceGetRealTimeIntegrityModels =
            env->GetMethodID(beidouAssistanceClass, "getRealTimeIntegrityModels",
                             "()Ljava/util/List;");
    method_beidouAssistanceGetAuxiliaryInformation =
            env->GetMethodID(beidouAssistanceClass, "getAuxiliaryInformation",
                             "()Ljava/util/List;");

    // Get the methods of BeidouSatelliteEphemeris class.
    jclass beidouSatelliteEphemerisClass =
            env->FindClass("android/location/BeidouSatelliteEphemeris");
    method_beidouSatelliteEphemerisGetSvid =
            env->GetMethodID(beidouSatelliteEphemerisClass, "getSvid", "()I");
    method_beidouSatelliteEphemerisGetSatelliteClockModel =
            env->GetMethodID(beidouSatelliteEphemerisClass, "getSatelliteClockModel",
                             "()Landroid/location/"
                             "BeidouSatelliteEphemeris$BeidouSatelliteClockModel;");
    method_beidouSatelliteEphemerisGetSatelliteOrbitModel =
            env->GetMethodID(beidouSatelliteEphemerisClass, "getSatelliteOrbitModel",
                             "()Landroid/location/KeplerianOrbitModel;");
    method_beidouSatelliteEphemerisGetSatelliteHealth =
            env->GetMethodID(beidouSatelliteEphemerisClass, "getSatelliteHealth",
                             "()Landroid/location/BeidouSatelliteEphemeris$BeidouSatelliteHealth;");
    method_beidouSatelliteEphemerisGetSatelliteEphemerisTime =
            env->GetMethodID(beidouSatelliteEphemerisClass, "getSatelliteEphemerisTime",
                             "()Landroid/location/"
                             "BeidouSatelliteEphemeris$BeidouSatelliteEphemerisTime;");

    // Get the methods of BeidouSatelliteClockModel
    jclass beidouSatelliteClockModelClass =
            env->FindClass("android/location/BeidouSatelliteEphemeris$BeidouSatelliteClockModel");
    method_beidouSatelliteClockModelGetAf0 =
            env->GetMethodID(beidouSatelliteClockModelClass, "getAf0", "()D");
    method_beidouSatelliteClockModelGetAf1 =
            env->GetMethodID(beidouSatelliteClockModelClass, "getAf1", "()D");
    method_beidouSatelliteClockModelGetAf2 =
            env->GetMethodID(beidouSatelliteClockModelClass, "getAf2", "()D");
    method_beidouSatelliteClockModelGetAodc =
            env->GetMethodID(beidouSatelliteClockModelClass, "getAodc", "()I");
    method_beidouSatelliteClockModelGetTgd1 =
            env->GetMethodID(beidouSatelliteClockModelClass, "getTgd1", "()D");
    method_beidouSatelliteClockModelGetTgd2 =
            env->GetMethodID(beidouSatelliteClockModelClass, "getTgd2", "()D");
    method_beidouSatelliteClockModelGetTimeOfClockSeconds =
            env->GetMethodID(beidouSatelliteClockModelClass, "getTimeOfClockSeconds", "()J");

    // Get the methods of BeidouSatelliteHealth
    jclass beidouSatelliteHealthClass =
            env->FindClass("android/location/BeidouSatelliteEphemeris$BeidouSatelliteHealth");
    method_beidouSatelliteHealthGetSatH1 =
            env->GetMethodID(beidouSatelliteHealthClass, "getSatH1", "()I");
    method_beidouSatelliteHealthGetSvAccur =
            env->GetMethodID(beidouSatelliteHealthClass, "getSvAccur", "()D");

    // Get the methods of BeidouSatelliteEphemerisTime
    jclass beidouSatelliteEphemerisTimeClass = env->FindClass(
            "android/location/BeidouSatelliteEphemeris$BeidouSatelliteEphemerisTime");
    method_beidouSatelliteEphemerisTimeGetAode =
            env->GetMethodID(beidouSatelliteEphemerisTimeClass, "getAode", "()I");
    method_beidouSatelliteEphemerisTimeGetBeidouWeekNumber =
            env->GetMethodID(beidouSatelliteEphemerisTimeClass, "getBeidouWeekNumber", "()I");
    method_beidouSatelliteEphemerisTimeGetToeSeconds =
            env->GetMethodID(beidouSatelliteEphemerisTimeClass, "getToeSeconds", "()I");

    // Get the methods of GalileoAssistance class.
    jclass galileoAssistanceClass = env->FindClass("android/location/GalileoAssistance");
    method_galileoAssistanceGetAlmanac = env->GetMethodID(galileoAssistanceClass, "getAlmanac",
                                                          "()Landroid/location/GnssAlmanac;");
    method_galileoAssistanceGetIonosphericModel =
            env->GetMethodID(galileoAssistanceClass, "getIonosphericModel",
                             "()Landroid/location/GalileoIonosphericModel;");
    method_galileoAssistanceGetUtcModel = env->GetMethodID(galileoAssistanceClass, "getUtcModel",
                                                           "()Landroid/location/UtcModel;");
    method_galileoAssistanceGetLeapSecondsModel =
            env->GetMethodID(galileoAssistanceClass, "getLeapSecondsModel",
                             "()Landroid/location/LeapSecondsModel;");
    method_galileoAssistanceGetTimeModels =
            env->GetMethodID(galileoAssistanceClass, "getTimeModels", "()Ljava/util/List;");
    method_galileoAssistanceGetSatelliteEphemeris =
            env->GetMethodID(galileoAssistanceClass, "getSatelliteEphemeris", "()Ljava/util/List;");
    method_galileoAssistanceGetSatelliteCorrections =
            env->GetMethodID(galileoAssistanceClass, "getSatelliteCorrections",
                             "()Ljava/util/List;");
    method_galileoAssistanceGetRealTimeIntegrityModels =
            env->GetMethodID(galileoAssistanceClass, "getRealTimeIntegrityModels",
                             "()Ljava/util/List;");
    method_galileoAssistanceGetAuxiliaryInformation =
            env->GetMethodID(galileoAssistanceClass, "getAuxiliaryInformation",
                             "()Ljava/util/List;");

    // Get the methods of GalileoSatelliteEphemeris class
    jclass galileoSatelliteEphemerisClass =
            env->FindClass("android/location/GalileoSatelliteEphemeris");
    method_galileoSatelliteEphemerisGetSatelliteClockModels =
            env->GetMethodID(galileoSatelliteEphemerisClass, "getSatelliteClockModels",
                             "()Ljava/util/List;");
    method_galileoSatelliteEphemerisGetSvid =
            env->GetMethodID(galileoSatelliteEphemerisClass, "getSvid", "()I");
    method_galileoSatelliteEphemerisGetSatelliteEphemerisTime =
            env->GetMethodID(galileoSatelliteEphemerisClass, "getSatelliteEphemerisTime",
                             "()Landroid/location/SatelliteEphemerisTime;");
    method_galileoSatelliteEphemerisGetSatelliteHealth =
            env->GetMethodID(galileoSatelliteEphemerisClass, "getSatelliteHealth",
                             "()Landroid/location/GalileoSatelliteEphemeris$GalileoSvHealth;");
    method_galileoSatelliteEphemerisGetSatelliteOrbitModel =
            env->GetMethodID(galileoSatelliteEphemerisClass, "getSatelliteOrbitModel",
                             "()Landroid/location/KeplerianOrbitModel;");

    // Get the methods of GalileoSatelliteClockModel class.
    jclass galileoSatelliteClockModelClass =
            env->FindClass("android/location/GalileoSatelliteEphemeris$GalileoSatelliteClockModel");
    method_galileoSatelliteClockModelGetAf0 =
            env->GetMethodID(galileoSatelliteClockModelClass, "getAf0", "()D");
    method_galileoSatelliteClockModelGetAf1 =
            env->GetMethodID(galileoSatelliteClockModelClass, "getAf1", "()D");
    method_galileoSatelliteClockModelGetAf2 =
            env->GetMethodID(galileoSatelliteClockModelClass, "getAf2", "()D");
    method_galileoSatelliteClockModelGetBgdSeconds =
            env->GetMethodID(galileoSatelliteClockModelClass, "getBgdSeconds", "()D");
    method_galileoSatelliteClockModelGetSatelliteClockType =
            env->GetMethodID(galileoSatelliteClockModelClass, "getSatelliteClockType", "()I");
    method_galileoSatelliteClockModelGetSisaMeters =
            env->GetMethodID(galileoSatelliteClockModelClass, "getSisaMeters", "()D");
    method_galileoSatelliteClockModelGetTimeOfClockSeconds =
            env->GetMethodID(galileoSatelliteClockModelClass, "getTimeOfClockSeconds", "()J");

    // Get the methods of GalileoSvHealth class.
    jclass galileoSvHealthClass =
            env->FindClass("android/location/GalileoSatelliteEphemeris$GalileoSvHealth");
    method_galileoSvHealthGetDataValidityStatusE1b =
            env->GetMethodID(galileoSvHealthClass, "getDataValidityStatusE1b", "()I");
    method_galileoSvHealthGetDataValidityStatusE5a =
            env->GetMethodID(galileoSvHealthClass, "getDataValidityStatusE5a", "()I");
    method_galileoSvHealthGetDataValidityStatusE5b =
            env->GetMethodID(galileoSvHealthClass, "getDataValidityStatusE5b", "()I");
    method_galileoSvHealthGetSignalHealthStatusE1b =
            env->GetMethodID(galileoSvHealthClass, "getSignalHealthStatusE1b", "()I");
    method_galileoSvHealthGetSignalHealthStatusE5a =
            env->GetMethodID(galileoSvHealthClass, "getSignalHealthStatusE5a", "()I");
    method_galileoSvHealthGetSignalHealthStatusE5b =
            env->GetMethodID(galileoSvHealthClass, "getSignalHealthStatusE5b", "()I");

    // Get the methods of GalileoIonosphericModel class.
    jclass galileoIonosphericModelClass =
            env->FindClass("android/location/GalileoIonosphericModel");
    method_galileoIonosphericModelGetAi0 =
            env->GetMethodID(galileoIonosphericModelClass, "getAi0", "()D");
    method_galileoIonosphericModelGetAi1 =
            env->GetMethodID(galileoIonosphericModelClass, "getAi1", "()D");
    method_galileoIonosphericModelGetAi2 =
            env->GetMethodID(galileoIonosphericModelClass, "getAi2", "()D");

    // Get the methods of GlonassAssistance class.
    jclass glonassAssistanceClass = env->FindClass("android/location/GlonassAssistance");
    method_glonassAssistanceGetAlmanac = env->GetMethodID(glonassAssistanceClass, "getAlmanac",
                                                          "()Landroid/location/GlonassAlmanac;");
    method_glonassAssistanceGetUtcModel = env->GetMethodID(glonassAssistanceClass, "getUtcModel",
                                                           "()Landroid/location/UtcModel;");
    method_glonassAssistanceGetTimeModels =
            env->GetMethodID(glonassAssistanceClass, "getTimeModels", "()Ljava/util/List;");
    method_glonassAssistanceGetSatelliteEphemeris =
            env->GetMethodID(glonassAssistanceClass, "getSatelliteEphemeris", "()Ljava/util/List;");
    method_glonassAssistanceGetSatelliteCorrections =
            env->GetMethodID(glonassAssistanceClass, "getSatelliteCorrections",
                             "()Ljava/util/List;");
    method_glonassAssistanceGetRealTimeIntegrityModels =
            env->GetMethodID(glonassAssistanceClass, "getRealTimeIntegrityModels",
                             "()Ljava/util/List;");
    method_glonassAssistanceGetAuxiliaryInformation =
            env->GetMethodID(glonassAssistanceClass, "getAuxiliaryInformation",
                             "()Ljava/util/List;");

    // Get the methods of GlonassAlmanac class.
    jclass glonassAlmanacClass = env->FindClass("android/location/GlonassAlmanac");
    method_glonassAlmanacGetIssueDateMillis =
            env->GetMethodID(glonassAlmanacClass, "getIssueDateMillis", "()J");
    method_glonassAlmanacGetSatelliteAlmanacs =
            env->GetMethodID(glonassAlmanacClass, "getSatelliteAlmanacs", "()Ljava/util/List;");

    // Get the methods of GlonassSatelliteAlmanac class
    jclass glonassSatelliteAlmanacClass =
            env->FindClass("android/location/GlonassAlmanac$GlonassSatelliteAlmanac");
    method_glonassSatelliteAlmanacGetDeltaI =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getDeltaI", "()D");
    method_glonassSatelliteAlmanacGetDeltaT =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getDeltaT", "()D");
    method_glonassSatelliteAlmanacGetDeltaTDot =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getDeltaTDot", "()D");
    method_glonassSatelliteAlmanacGetEccentricity =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getEccentricity", "()D");
    method_glonassSatelliteAlmanacGetFrequencyChannelNumber =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getFrequencyChannelNumber", "()I");
    method_glonassSatelliteAlmanacGetLambda =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getLambda", "()D");
    method_glonassSatelliteAlmanacGetOmega =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getOmega", "()D");
    method_glonassSatelliteAlmanacGetSlotNumber =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getSlotNumber", "()I");
    method_glonassSatelliteAlmanacGetHealthState =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getHealthState", "()I");
    method_glonassSatelliteAlmanacGetTLambda =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getTLambda", "()D");
    method_glonassSatelliteAlmanacGetTau =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getTau", "()D");
    method_glonassSatelliteAlmanacGetCalendarDayNumber =
            env->GetMethodID(glonassSatelliteAlmanacClass, "getCalendarDayNumber", "()I");
    method_glonassSatelliteAlmanacGetIsGlonassM =
            env->GetMethodID(glonassSatelliteAlmanacClass, "isGlonassM", "()Z");

    // Get the methods of GlonassSatelliteEphemeris
    jclass glonassSatelliteEphemerisClass =
            env->FindClass("android/location/GlonassSatelliteEphemeris");
    method_glonassSatelliteEphemerisGetAgeInDays =
            env->GetMethodID(glonassSatelliteEphemerisClass, "getAgeInDays", "()I");
    method_glonassSatelliteEphemerisGetFrameTimeSeconds =
            env->GetMethodID(glonassSatelliteEphemerisClass, "getFrameTimeSeconds", "()D");
    method_glonassSatelliteEphemerisGetHealthState =
            env->GetMethodID(glonassSatelliteEphemerisClass, "getHealthState", "()I");
    method_glonassSatelliteEphemerisGetSlotNumber =
            env->GetMethodID(glonassSatelliteEphemerisClass, "getSlotNumber", "()I");
    method_glonassSatelliteEphemerisGetSatelliteClockModel =
            env->GetMethodID(glonassSatelliteEphemerisClass, "getSatelliteClockModel",
                             "()Landroid/location/"
                             "GlonassSatelliteEphemeris$GlonassSatelliteClockModel;");
    method_glonassSatelliteEphemerisGetSatelliteOrbitModel =
            env->GetMethodID(glonassSatelliteEphemerisClass, "getSatelliteOrbitModel",
                             "()Landroid/location/"
                             "GlonassSatelliteEphemeris$GlonassSatelliteOrbitModel;");
    method_glonassSatelliteEphemerisGetUpdateIntervalMinutes =
            env->GetMethodID(glonassSatelliteEphemerisClass, "getUpdateIntervalMinutes", "()I");
    method_glonassSatelliteEphemerisGetIsGlonassM =
            env->GetMethodID(glonassSatelliteEphemerisClass, "isGlonassM", "()Z");
    method_glonassSatelliteEphemerisGetIsUpdateIntervalOdd =
            env->GetMethodID(glonassSatelliteEphemerisClass, "isUpdateIntervalOdd", "()Z");

    // Get the methods of GlonassSatelliteOrbitModel
    jclass glonassSatelliteOrbitModelClass =
            env->FindClass("android/location/GlonassSatelliteEphemeris$GlonassSatelliteOrbitModel");
    method_glonassSatelliteOrbitModelGetX =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getX", "()D");
    method_glonassSatelliteOrbitModelGetXAccel =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getXAccel", "()D");
    method_glonassSatelliteOrbitModelGetXDot =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getXDot", "()D");
    method_glonassSatelliteOrbitModelGetY =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getY", "()D");
    method_glonassSatelliteOrbitModelGetYAccel =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getYAccel", "()D");
    method_glonassSatelliteOrbitModelGetYDot =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getYDot", "()D");
    method_glonassSatelliteOrbitModelGetZ =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getZ", "()D");
    method_glonassSatelliteOrbitModelGetZAccel =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getZAccel", "()D");
    method_glonassSatelliteOrbitModelGetZDot =
            env->GetMethodID(glonassSatelliteOrbitModelClass, "getZDot", "()D");

    // Get the methods of GlonassSatelliteClockModel
    jclass glonassSatelliteClockModelClass =
            env->FindClass("android/location/GlonassSatelliteEphemeris$GlonassSatelliteClockModel");
    method_glonassSatelliteClockModelGetClockBias =
            env->GetMethodID(glonassSatelliteClockModelClass, "getClockBias", "()D");
    method_glonassSatelliteClockModelGetFrequencyBias =
            env->GetMethodID(glonassSatelliteClockModelClass, "getFrequencyBias", "()D");
    method_glonassSatelliteClockModelGetFrequencyChannelNumber =
            env->GetMethodID(glonassSatelliteClockModelClass, "getFrequencyChannelNumber", "()I");
    method_glonassSatelliteClockModelGetTimeOfClockSeconds =
            env->GetMethodID(glonassSatelliteClockModelClass, "getTimeOfClockSeconds", "()J");

    // Get the methods of QzssAssistance class.
    jclass qzssAssistanceClass = env->FindClass("android/location/QzssAssistance");
    method_qzssAssistanceGetAlmanac =
            env->GetMethodID(qzssAssistanceClass, "getAlmanac", "()Landroid/location/GnssAlmanac;");
    method_qzssAssistanceGetIonosphericModel =
            env->GetMethodID(qzssAssistanceClass, "getIonosphericModel",
                             "()Landroid/location/KlobucharIonosphericModel;");
    method_qzssAssistanceGetUtcModel =
            env->GetMethodID(qzssAssistanceClass, "getUtcModel", "()Landroid/location/UtcModel;");
    method_qzssAssistanceGetLeapSecondsModel =
            env->GetMethodID(qzssAssistanceClass, "getLeapSecondsModel",
                             "()Landroid/location/LeapSecondsModel;");
    method_qzssAssistanceGetTimeModels =
            env->GetMethodID(qzssAssistanceClass, "getTimeModels", "()Ljava/util/List;");
    method_qzssAssistanceGetSatelliteEphemeris =
            env->GetMethodID(qzssAssistanceClass, "getSatelliteEphemeris", "()Ljava/util/List;");
    method_qzssAssistanceGetSatelliteCorrections =
            env->GetMethodID(qzssAssistanceClass, "getSatelliteCorrections", "()Ljava/util/List;");
    method_qzssAssistanceGetAuxiliaryInformation =
            env->GetMethodID(qzssAssistanceClass, "getAuxiliaryInformation", "()Ljava/util/List;");

    // Get the methods of QzssSatelliteEphemeris class.
    jclass qzssSatelliteEphemerisClass = env->FindClass("android/location/QzssSatelliteEphemeris");
    method_qzssSatelliteEphemerisGetSvid =
            env->GetMethodID(qzssSatelliteEphemerisClass, "getSvid", "()I");
    method_qzssSatelliteEphemerisGetGpsL2Params =
            env->GetMethodID(qzssSatelliteEphemerisClass, "getGpsL2Params",
                             "()Landroid/location/GpsSatelliteEphemeris$GpsL2Params;");
    method_qzssSatelliteEphemerisGetSatelliteEphemerisTime =
            env->GetMethodID(qzssSatelliteEphemerisClass, "getSatelliteEphemerisTime",
                             "()Landroid/location/SatelliteEphemerisTime;");
    method_qzssSatelliteEphemerisGetSatelliteHealth =
            env->GetMethodID(qzssSatelliteEphemerisClass, "getSatelliteHealth",
                             "()Landroid/location/GpsSatelliteEphemeris$GpsSatelliteHealth;");
    method_qzssSatelliteEphemerisGetSatelliteOrbitModel =
            env->GetMethodID(qzssSatelliteEphemerisClass, "getSatelliteOrbitModel",
                             "()Landroid/location/KeplerianOrbitModel;");
}

GnssAssistanceInterface::GnssAssistanceInterface(
        const sp<IGnssAssistanceInterface>& iGnssAssistance)
      : mGnssAssistanceInterface(iGnssAssistance) {
    assert(mGnssAssistanceInterface != nullptr);
}

jboolean GnssAssistanceInterface::injectGnssAssistance(JNIEnv* env, jobject gnssAssistanceObj) {
    GnssAssistance gnssAssistance;
    GnssAssistanceUtil::setGnssAssistance(env, gnssAssistanceObj, gnssAssistance);
    auto status = mGnssAssistanceInterface->injectGnssAssistance(gnssAssistance);
    return checkAidlStatus(status, "IGnssAssistanceInterface injectGnssAssistance() failed.");
}

jboolean GnssAssistanceInterface::setCallback(const sp<IGnssAssistanceCallback>& callback) {
    auto status = mGnssAssistanceInterface->setCallback(callback);
    return checkAidlStatus(status, "IGnssAssistanceInterface setCallback() failed.");
}

void GnssAssistanceUtil::setGnssAssistance(JNIEnv* env, jobject gnssAssistanceObj,
                                           GnssAssistance& gnssAssistance) {
    jobject gpsAssistanceObj =
            env->CallObjectMethod(gnssAssistanceObj, method_gnssAssistanceGetGpsAssistance);
    jobject glonassAssistanceObj =
            env->CallObjectMethod(gnssAssistanceObj, method_gnssAssistanceGetGlonassAssistance);
    jobject qzssAssistanceObj =
            env->CallObjectMethod(gnssAssistanceObj, method_gnssAssistanceGetQzssAssistance);
    jobject galileoAssistanceObj =
            env->CallObjectMethod(gnssAssistanceObj, method_gnssAssistanceGetGalileoAssistance);
    jobject beidouAssistanceObj =
            env->CallObjectMethod(gnssAssistanceObj, method_gnssAssistanceGetBeidouAssistance);

    GnssAssistanceUtil::setGpsAssistance(env, gpsAssistanceObj, gnssAssistance.gpsAssistance);
    GnssAssistanceUtil::setGlonassAssistance(env, glonassAssistanceObj,
                                             gnssAssistance.glonassAssistance);
    GnssAssistanceUtil::setQzssAssistance(env, qzssAssistanceObj, gnssAssistance.qzssAssistance);
    GnssAssistanceUtil::setGalileoAssistance(env, galileoAssistanceObj,
                                             gnssAssistance.galileoAssistance);
    GnssAssistanceUtil::setBeidouAssistance(env, beidouAssistanceObj,
                                            gnssAssistance.beidouAssistance);
    env->DeleteLocalRef(gpsAssistanceObj);
    env->DeleteLocalRef(glonassAssistanceObj);
    env->DeleteLocalRef(qzssAssistanceObj);
    env->DeleteLocalRef(galileoAssistanceObj);
    env->DeleteLocalRef(beidouAssistanceObj);
}

void GnssAssistanceUtil::setQzssAssistance(JNIEnv* env, jobject qzssAssistanceObj,
                                           std::optional<QzssAssistance>& qzssAssistanceOpt) {
    QzssAssistance qzssAssistance;
    jobject qzssAlmanacObj =
            env->CallObjectMethod(qzssAssistanceObj, method_qzssAssistanceGetAlmanac);
    jobject qzssIonosphericModelObj =
            env->CallObjectMethod(qzssAssistanceObj, method_qzssAssistanceGetIonosphericModel);
    jobject qzssUtcModelObj =
            env->CallObjectMethod(qzssAssistanceObj, method_qzssAssistanceGetUtcModel);
    jobject qzssLeapSecondsModelObj =
            env->CallObjectMethod(qzssAssistanceObj, method_qzssAssistanceGetLeapSecondsModel);
    jobject qzssTimeModelsObj =
            env->CallObjectMethod(qzssAssistanceObj, method_qzssAssistanceGetTimeModels);
    jobject qzssSatelliteEphemerisObj =
            env->CallObjectMethod(qzssAssistanceObj, method_qzssAssistanceGetSatelliteEphemeris);
    jobject qzssSatelliteCorrectionsObj =
            env->CallObjectMethod(qzssAssistanceObj, method_qzssAssistanceGetSatelliteCorrections);
    jobject qzssAuxiliaryInformationObj =
            env->CallObjectMethod(qzssAssistanceObj, method_qzssAssistanceGetAuxiliaryInformation);
    setGnssAlmanac(env, qzssAlmanacObj, qzssAssistance.almanac);
    setKlobucharIonosphericModel(env, qzssIonosphericModelObj, qzssAssistance.ionosphericModel);
    setUtcModel(env, qzssUtcModelObj, qzssAssistance.utcModel);
    setLeapSecondsModel(env, qzssLeapSecondsModelObj, qzssAssistance.leapSecondsModel);
    setTimeModels(env, qzssTimeModelsObj, qzssAssistance.timeModels);
    setGpsOrQzssSatelliteEphemeris<QzssSatelliteEphemeris>(env, qzssSatelliteEphemerisObj,
                                                           qzssAssistance.satelliteEphemeris);
    setSatelliteCorrections(env, qzssSatelliteCorrectionsObj, qzssAssistance.satelliteCorrections);
    setAuxiliaryInformations(env, qzssAuxiliaryInformationObj,
                             qzssAssistance.auxiliaryInformations);
    qzssAssistanceOpt = qzssAssistance;
    env->DeleteLocalRef(qzssAlmanacObj);
    env->DeleteLocalRef(qzssIonosphericModelObj);
    env->DeleteLocalRef(qzssUtcModelObj);
    env->DeleteLocalRef(qzssLeapSecondsModelObj);
    env->DeleteLocalRef(qzssTimeModelsObj);
    env->DeleteLocalRef(qzssSatelliteEphemerisObj);
    env->DeleteLocalRef(qzssSatelliteCorrectionsObj);
    env->DeleteLocalRef(qzssAuxiliaryInformationObj);
}

void GnssAssistanceUtil::setGlonassAssistance(
        JNIEnv* env, jobject glonassAssistanceObj,
        std::optional<GlonassAssistance>& glonassAssistanceOpt) {
    GlonassAssistance glonassAssistance;
    jobject glonassAlmanacObj =
            env->CallObjectMethod(glonassAssistanceObj, method_glonassAssistanceGetAlmanac);
    jobject utcModelObj =
            env->CallObjectMethod(glonassAssistanceObj, method_glonassAssistanceGetUtcModel);
    jobject timeModelsObj =
            env->CallObjectMethod(glonassAssistanceObj, method_glonassAssistanceGetTimeModels);
    jobject satelliteEphemerisObj =
            env->CallObjectMethod(glonassAssistanceObj,
                                  method_glonassAssistanceGetSatelliteEphemeris);
    jobject satelliteCorrectionsObj =
            env->CallObjectMethod(glonassAssistanceObj,
                                  method_glonassAssistanceGetSatelliteCorrections);
    jobject realTimeIntegrityModelsObj =
            env->CallObjectMethod(glonassAssistanceObj,
                                  method_glonassAssistanceGetRealTimeIntegrityModels);
    jobject auxiliaryInformationObj =
            env->CallObjectMethod(glonassAssistanceObj,
                                  method_glonassAssistanceGetAuxiliaryInformation);
    setGlonassAlmanac(env, glonassAlmanacObj, glonassAssistance.almanac);
    setUtcModel(env, utcModelObj, glonassAssistance.utcModel);
    setTimeModels(env, timeModelsObj, glonassAssistance.timeModels);
    setGlonassSatelliteEphemeris(env, satelliteEphemerisObj, glonassAssistance.satelliteEphemeris);
    setSatelliteCorrections(env, satelliteCorrectionsObj, glonassAssistance.satelliteCorrections);
    setRealTimeIntegrityModels(env, realTimeIntegrityModelsObj,
                               glonassAssistance.realTimeIntegrityModels);
    setAuxiliaryInformations(env, auxiliaryInformationObj, glonassAssistance.auxiliaryInformations);
    glonassAssistanceOpt = glonassAssistance;
    env->DeleteLocalRef(glonassAlmanacObj);
    env->DeleteLocalRef(utcModelObj);
    env->DeleteLocalRef(timeModelsObj);
    env->DeleteLocalRef(satelliteEphemerisObj);
    env->DeleteLocalRef(satelliteCorrectionsObj);
    env->DeleteLocalRef(realTimeIntegrityModelsObj);
    env->DeleteLocalRef(auxiliaryInformationObj);
}

void GnssAssistanceUtil::setGlonassAlmanac(JNIEnv* env, jobject glonassAlmanacObj,
                                           std::optional<GlonassAlmanac>& glonassAlmanacOpt) {
    if (glonassAlmanacObj == nullptr) return;
    GlonassAlmanac glonassAlmanac;
    jlong issueDateMillis =
            env->CallLongMethod(glonassAlmanacObj, method_glonassAlmanacGetIssueDateMillis);
    glonassAlmanac.issueDateMs = issueDateMillis;
    jobject satelliteAlmanacsObj =
            env->CallObjectMethod(glonassAlmanacObj, method_glonassAlmanacGetSatelliteAlmanacs);
    if (satelliteAlmanacsObj == nullptr) return;
    auto len = env->CallIntMethod(satelliteAlmanacsObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        jobject glonassSatelliteAlmanacObj =
                env->CallObjectMethod(satelliteAlmanacsObj, method_listGet, i);
        if (glonassSatelliteAlmanacObj == nullptr) continue;
        GlonassSatelliteAlmanac glonassSatelliteAlmanac;
        jdouble deltaI = env->CallDoubleMethod(glonassSatelliteAlmanacObj,
                                               method_glonassSatelliteAlmanacGetDeltaI);
        glonassSatelliteAlmanac.deltaI = deltaI;
        jdouble deltaT = env->CallDoubleMethod(glonassSatelliteAlmanacObj,
                                               method_glonassSatelliteAlmanacGetDeltaT);
        glonassSatelliteAlmanac.deltaT = deltaT;
        jdouble deltaTDot = env->CallDoubleMethod(glonassSatelliteAlmanacObj,
                                                  method_glonassSatelliteAlmanacGetDeltaTDot);
        glonassSatelliteAlmanac.deltaTDot = deltaTDot;
        jdouble eccentricity = env->CallDoubleMethod(glonassSatelliteAlmanacObj,
                                                     method_glonassSatelliteAlmanacGetEccentricity);
        glonassSatelliteAlmanac.eccentricity = eccentricity;
        jint frequencyChannelNumber =
                env->CallIntMethod(glonassSatelliteAlmanacObj,
                                   method_glonassSatelliteAlmanacGetFrequencyChannelNumber);
        glonassSatelliteAlmanac.frequencyChannelNumber =
                static_cast<int32_t>(frequencyChannelNumber);
        jdouble lambda = env->CallDoubleMethod(glonassSatelliteAlmanacObj,
                                               method_glonassSatelliteAlmanacGetLambda);
        glonassSatelliteAlmanac.lambda = lambda;
        jdouble omega = env->CallDoubleMethod(glonassSatelliteAlmanacObj,
                                              method_glonassSatelliteAlmanacGetOmega);
        glonassSatelliteAlmanac.omega = omega;
        jint slotNumber = env->CallIntMethod(glonassSatelliteAlmanacObj,
                                             method_glonassSatelliteAlmanacGetSlotNumber);
        glonassSatelliteAlmanac.slotNumber = static_cast<int32_t>(slotNumber);
        jint healthState = env->CallIntMethod(glonassSatelliteAlmanacObj,
                                              method_glonassSatelliteAlmanacGetHealthState);
        glonassSatelliteAlmanac.svHealth = static_cast<int32_t>(healthState);
        jdouble tLambda = env->CallDoubleMethod(glonassSatelliteAlmanacObj,
                                                method_glonassSatelliteAlmanacGetTLambda);
        glonassSatelliteAlmanac.tLambda = tLambda;
        jdouble tau = env->CallDoubleMethod(glonassSatelliteAlmanacObj,
                                            method_glonassSatelliteAlmanacGetTau);
        glonassSatelliteAlmanac.tau = tau;
        jboolean isGlonassM = env->CallBooleanMethod(glonassSatelliteAlmanacObj,
                                                     method_glonassSatelliteAlmanacGetIsGlonassM);
        glonassSatelliteAlmanac.isGlonassM = isGlonassM;
        jint calendarDayNumber =
                env->CallIntMethod(glonassSatelliteAlmanacObj,
                                   method_glonassSatelliteAlmanacGetCalendarDayNumber);
        glonassSatelliteAlmanac.calendarDayNumber = static_cast<int32_t>(calendarDayNumber);
        env->DeleteLocalRef(glonassSatelliteAlmanacObj);
        glonassAlmanac.satelliteAlmanacs.push_back(glonassSatelliteAlmanac);
        glonassAlmanacOpt = glonassAlmanac;
    }
    env->DeleteLocalRef(satelliteAlmanacsObj);
}

void GnssAssistanceUtil::setGlonassSatelliteEphemeris(
        JNIEnv* env, jobject glonassSatelliteEphemerisListObj,
        std::vector<GlonassSatelliteEphemeris>& glonassSatelliteEphemerisList) {
    if (glonassSatelliteEphemerisListObj == nullptr) return;
    auto len = env->CallIntMethod(glonassSatelliteEphemerisListObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        jobject glonassSatelliteEphemerisObj =
                env->CallObjectMethod(glonassSatelliteEphemerisListObj, method_listGet, i);
        if (glonassSatelliteEphemerisObj == nullptr) continue;
        GlonassSatelliteEphemeris glonassSatelliteEphemeris;
        jdouble ageInDays = env->CallDoubleMethod(glonassSatelliteEphemerisObj,
                                                  method_glonassSatelliteEphemerisGetAgeInDays);
        glonassSatelliteEphemeris.ageInDays = ageInDays;

        // Set the GlonassSatelliteClockModel.
        jobject glonassSatelliteClockModelObj =
                env->CallObjectMethod(glonassSatelliteEphemerisObj,
                                      method_glonassSatelliteEphemerisGetSatelliteClockModel);
        GlonassSatelliteClockModel glonassSatelliteClockModel;
        jdouble clockBias = env->CallDoubleMethod(glonassSatelliteClockModelObj,
                                                  method_glonassSatelliteClockModelGetClockBias);
        glonassSatelliteClockModel.clockBias = clockBias;
        jdouble frequencyBias =
                env->CallDoubleMethod(glonassSatelliteClockModelObj,
                                      method_glonassSatelliteClockModelGetFrequencyBias);
        glonassSatelliteClockModel.frequencyBias = frequencyBias;
        jint frequencyChannelNumber =
                env->CallIntMethod(glonassSatelliteClockModelObj,
                                   method_glonassSatelliteClockModelGetFrequencyChannelNumber);
        glonassSatelliteClockModel.frequencyChannelNumber =
                static_cast<int32_t>(frequencyChannelNumber);
        jdouble timeOfClockSeconds =
                env->CallDoubleMethod(glonassSatelliteClockModelObj,
                                      method_glonassSatelliteClockModelGetTimeOfClockSeconds);
        glonassSatelliteClockModel.timeOfClockSeconds = timeOfClockSeconds;
        glonassSatelliteEphemeris.satelliteClockModel = glonassSatelliteClockModel;
        env->DeleteLocalRef(glonassSatelliteClockModelObj);

        // Set the GlonassSatelliteOrbitModel.
        jobject glonassSatelliteOrbitModelObj =
                env->CallObjectMethod(glonassSatelliteEphemerisObj,
                                      method_glonassSatelliteEphemerisGetSatelliteOrbitModel);
        GlonassSatelliteOrbitModel glonassSatelliteOrbitModel;
        jdouble x = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                          method_glonassSatelliteOrbitModelGetX);
        glonassSatelliteOrbitModel.x = x;
        jdouble y = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                          method_glonassSatelliteOrbitModelGetY);
        glonassSatelliteOrbitModel.y = y;
        jdouble z = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                          method_glonassSatelliteOrbitModelGetZ);
        glonassSatelliteOrbitModel.z = z;
        jdouble xAccel = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                               method_glonassSatelliteOrbitModelGetXAccel);
        glonassSatelliteOrbitModel.xAccel = xAccel;
        jdouble yAccel = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                               method_glonassSatelliteOrbitModelGetYAccel);
        glonassSatelliteOrbitModel.yAccel = yAccel;
        jdouble zAccel = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                               method_glonassSatelliteOrbitModelGetZAccel);
        glonassSatelliteOrbitModel.zAccel = zAccel;
        jdouble xDot = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                             method_glonassSatelliteOrbitModelGetXDot);
        glonassSatelliteOrbitModel.xDot = xDot;
        jdouble yDot = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                             method_glonassSatelliteOrbitModelGetYDot);
        glonassSatelliteOrbitModel.yDot = yDot;
        jdouble zDot = env->CallDoubleMethod(glonassSatelliteOrbitModelObj,
                                             method_glonassSatelliteOrbitModelGetZDot);
        glonassSatelliteOrbitModel.zDot = zDot;
        glonassSatelliteEphemeris.satelliteOrbitModel = glonassSatelliteOrbitModel;
        env->DeleteLocalRef(glonassSatelliteOrbitModelObj);

        jint healthState = env->CallIntMethod(glonassSatelliteEphemerisObj,
                                              method_glonassSatelliteEphemerisGetHealthState);
        glonassSatelliteEphemeris.svHealth = static_cast<int32_t>(healthState);
        jint slotNumber = env->CallIntMethod(glonassSatelliteEphemerisObj,
                                             method_glonassSatelliteEphemerisGetSlotNumber);
        glonassSatelliteEphemeris.slotNumber = static_cast<int32_t>(slotNumber);
        jdouble frameTimeSeconds =
                env->CallDoubleMethod(glonassSatelliteEphemerisObj,
                                      method_glonassSatelliteEphemerisGetFrameTimeSeconds);
        glonassSatelliteEphemeris.frameTimeSeconds = frameTimeSeconds;
        jint updateIntervalMinutes =
                env->CallIntMethod(glonassSatelliteEphemerisObj,
                                   method_glonassSatelliteEphemerisGetUpdateIntervalMinutes);
        glonassSatelliteEphemeris.updateIntervalMinutes =
                static_cast<int32_t>(updateIntervalMinutes);
        jboolean isGlonassM = env->CallBooleanMethod(glonassSatelliteEphemerisObj,
                                                     method_glonassSatelliteEphemerisGetIsGlonassM);
        glonassSatelliteEphemeris.isGlonassM = isGlonassM;
        jboolean isUpdateIntervalOdd =
                env->CallBooleanMethod(glonassSatelliteEphemerisObj,
                                       method_glonassSatelliteEphemerisGetIsUpdateIntervalOdd);
        glonassSatelliteEphemeris.isOddUpdateInterval = isUpdateIntervalOdd;
        glonassSatelliteEphemerisList.push_back(glonassSatelliteEphemeris);
        env->DeleteLocalRef(glonassSatelliteEphemerisObj);
    }
}

void GnssAssistanceUtil::setGalileoAssistance(
        JNIEnv* env, jobject galileoAssistanceObj,
        std::optional<GalileoAssistance>& galileoAssistanceOpt) {
    GalileoAssistance galileoAssistance;
    jobject galileoAlmanacObj =
            env->CallObjectMethod(galileoAssistanceObj, method_galileoAssistanceGetAlmanac);
    jobject ionosphericModelObj =
            env->CallObjectMethod(galileoAssistanceObj,
                                  method_galileoAssistanceGetIonosphericModel);
    jobject utcModelObj =
            env->CallObjectMethod(galileoAssistanceObj, method_galileoAssistanceGetUtcModel);
    jobject leapSecondsModelObj =
            env->CallObjectMethod(galileoAssistanceObj,
                                  method_galileoAssistanceGetLeapSecondsModel);
    jobject timeModelsObj =
            env->CallObjectMethod(galileoAssistanceObj, method_galileoAssistanceGetTimeModels);
    jobject satelliteEphemerisObj =
            env->CallObjectMethod(galileoAssistanceObj,
                                  method_galileoAssistanceGetSatelliteEphemeris);
    jobject realTimeIntegrityModelsObj =
            env->CallObjectMethod(galileoAssistanceObj,
                                  method_galileoAssistanceGetRealTimeIntegrityModels);
    jobject satelliteCorrectionsObj =
            env->CallObjectMethod(galileoAssistanceObj,
                                  method_galileoAssistanceGetSatelliteCorrections);
    jobject auxiliaryInformationsObj =
            env->CallObjectMethod(galileoAssistanceObj,
                                  method_galileoAssistanceGetAuxiliaryInformation);
    setGnssAlmanac(env, galileoAlmanacObj, galileoAssistance.almanac);
    setGalileoIonosphericModel(env, ionosphericModelObj, galileoAssistance.ionosphericModel);
    setUtcModel(env, utcModelObj, galileoAssistance.utcModel);
    setLeapSecondsModel(env, leapSecondsModelObj, galileoAssistance.leapSecondsModel);
    setTimeModels(env, timeModelsObj, galileoAssistance.timeModels);
    setGalileoSatelliteEphemeris(env, satelliteEphemerisObj, galileoAssistance.satelliteEphemeris);
    setRealTimeIntegrityModels(env, realTimeIntegrityModelsObj,
                               galileoAssistance.realTimeIntegrityModels);
    setSatelliteCorrections(env, satelliteCorrectionsObj, galileoAssistance.satelliteCorrections);
    setAuxiliaryInformations(env, auxiliaryInformationsObj,
                             galileoAssistance.auxiliaryInformations);
    galileoAssistanceOpt = galileoAssistance;
    env->DeleteLocalRef(galileoAlmanacObj);
    env->DeleteLocalRef(ionosphericModelObj);
    env->DeleteLocalRef(utcModelObj);
    env->DeleteLocalRef(leapSecondsModelObj);
    env->DeleteLocalRef(timeModelsObj);
    env->DeleteLocalRef(satelliteEphemerisObj);
    env->DeleteLocalRef(realTimeIntegrityModelsObj);
    env->DeleteLocalRef(satelliteCorrectionsObj);
    env->DeleteLocalRef(auxiliaryInformationsObj);
}

void GnssAssistanceUtil::setGalileoIonosphericModel(
        JNIEnv* env, jobject galileoIonosphericModelObj,
        std::optional<GalileoIonosphericModel>& ionosphericModelOpt) {
    if (galileoIonosphericModelObj == nullptr) return;
    GalileoIonosphericModel ionosphericModel;
    jdouble ai0 =
            env->CallDoubleMethod(galileoIonosphericModelObj, method_galileoIonosphericModelGetAi0);
    ionosphericModel.ai0 = ai0;
    jdouble ai1 =
            env->CallDoubleMethod(galileoIonosphericModelObj, method_galileoIonosphericModelGetAi1);
    ionosphericModel.ai1 = ai1;
    jdouble ai2 =
            env->CallDoubleMethod(galileoIonosphericModelObj, method_galileoIonosphericModelGetAi2);
    ionosphericModel.ai2 = ai2;
    ionosphericModelOpt = ionosphericModel;
}

void GnssAssistanceUtil::setGalileoSatelliteEphemeris(
        JNIEnv* env, jobject galileoSatelliteEphemerisListObj,
        std::vector<GalileoSatelliteEphemeris>& galileoSatelliteEphemerisList) {
    if (galileoSatelliteEphemerisListObj == nullptr) return;
    auto len = env->CallIntMethod(galileoSatelliteEphemerisListObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        jobject galileoSatelliteEphemerisObj =
                env->CallObjectMethod(galileoSatelliteEphemerisListObj, method_listGet, i);
        GalileoSatelliteEphemeris galileoSatelliteEphemeris;
        GalileoSvHealth galileoSvHealth;
        // Set the svid of the satellite.
        jint svid = env->CallLongMethod(galileoSatelliteEphemerisObj,
                                        method_galileoSatelliteEphemerisGetSvid);
        galileoSatelliteEphemeris.svid = svid;

        // Set the satellite clock models.
        jobject galileoSatelliteClockModelListObj =
                env->CallObjectMethod(galileoSatelliteEphemerisObj,
                                      method_galileoSatelliteEphemerisGetSatelliteClockModels);
        auto size = env->CallIntMethod(galileoSatelliteClockModelListObj, method_listSize);
        for (uint16_t j = 0; j < size; ++j) {
            jobject galileoSatelliteClockModelObj =
                    env->CallObjectMethod(galileoSatelliteClockModelListObj, method_listGet, j);
            if (galileoSatelliteClockModelObj == nullptr) continue;
            GalileoSatelliteClockModel galileoSatelliteClockModel;
            jdouble af0 = env->CallDoubleMethod(galileoSatelliteClockModelObj,
                                                method_galileoSatelliteClockModelGetAf0);
            galileoSatelliteClockModel.af0 = af0;
            jdouble af1 = env->CallDoubleMethod(galileoSatelliteClockModelObj,
                                                method_galileoSatelliteClockModelGetAf1);
            galileoSatelliteClockModel.af1 = af1;
            jdouble af2 = env->CallDoubleMethod(galileoSatelliteClockModelObj,
                                                method_galileoSatelliteClockModelGetAf2);
            galileoSatelliteClockModel.af2 = af2;
            jdouble bgdSeconds =
                    env->CallDoubleMethod(galileoSatelliteClockModelObj,
                                          method_galileoSatelliteClockModelGetBgdSeconds);
            galileoSatelliteClockModel.bgdSeconds = bgdSeconds;
            jint satelliteClockType =
                    env->CallIntMethod(galileoSatelliteClockModelObj,
                                       method_galileoSatelliteClockModelGetSatelliteClockType);
            galileoSatelliteClockModel.satelliteClockType =
                    static_cast<GalileoSatelliteClockModel::SatelliteClockType>(satelliteClockType);
            jdouble sisaMeters =
                    env->CallDoubleMethod(galileoSatelliteClockModelObj,
                                          method_galileoSatelliteClockModelGetSisaMeters);
            galileoSatelliteClockModel.sisaMeters = sisaMeters;
            jdouble timeOfClockSeconds =
                    env->CallDoubleMethod(galileoSatelliteClockModelObj,
                                          method_galileoSatelliteClockModelGetTimeOfClockSeconds);
            galileoSatelliteClockModel.timeOfClockSeconds = timeOfClockSeconds;
            galileoSatelliteEphemeris.satelliteClockModel.push_back(galileoSatelliteClockModel);
            env->DeleteLocalRef(galileoSatelliteClockModelObj);
        }
        env->DeleteLocalRef(galileoSatelliteClockModelListObj);

        // Set the satelliteOrbitModel of the satellite.
        jobject satelliteOrbitModelObj =
                env->CallObjectMethod(galileoSatelliteEphemerisObj,
                                      method_galileoSatelliteEphemerisGetSatelliteOrbitModel);
        GnssAssistanceUtil::setKeplerianOrbitModel(env, satelliteOrbitModelObj,
                                                   galileoSatelliteEphemeris.satelliteOrbitModel);
        env->DeleteLocalRef(satelliteOrbitModelObj);

        // Set the satellite health of the satellite clock model.
        jobject galileoSvHealthObj =
                env->CallObjectMethod(galileoSatelliteEphemerisObj,
                                      method_galileoSatelliteEphemerisGetSatelliteHealth);
        jint dataValidityStatusE1b =
                env->CallIntMethod(galileoSvHealthObj,
                                   method_galileoSvHealthGetDataValidityStatusE1b);
        galileoSvHealth.dataValidityStatusE1b =
                static_cast<GalileoSvHealth::GalileoHealthDataVaidityType>(dataValidityStatusE1b);
        jint dataValidityStatusE5a =
                env->CallIntMethod(galileoSvHealthObj,
                                   method_galileoSvHealthGetDataValidityStatusE5a);
        galileoSvHealth.dataValidityStatusE5a =
                static_cast<GalileoSvHealth::GalileoHealthDataVaidityType>(dataValidityStatusE5a);
        jint dataValidityStatusE5b =
                env->CallIntMethod(galileoSvHealthObj,
                                   method_galileoSvHealthGetDataValidityStatusE5b);
        galileoSvHealth.dataValidityStatusE5b =
                static_cast<GalileoSvHealth::GalileoHealthDataVaidityType>(dataValidityStatusE5b);
        jint signalHealthStatusE1b =
                env->CallIntMethod(galileoSvHealthObj,
                                   method_galileoSvHealthGetSignalHealthStatusE1b);
        galileoSvHealth.signalHealthStatusE1b =
                static_cast<GalileoSvHealth::GalileoHealthStatusType>(signalHealthStatusE1b);
        jint signalHealthStatusE5a =
                env->CallIntMethod(galileoSvHealthObj,
                                   method_galileoSvHealthGetSignalHealthStatusE5a);
        galileoSvHealth.signalHealthStatusE5a =
                static_cast<GalileoSvHealth::GalileoHealthStatusType>(signalHealthStatusE5a);
        jint signalHealthStatusE5b =
                env->CallIntMethod(galileoSvHealthObj,
                                   method_galileoSvHealthGetSignalHealthStatusE5b);
        galileoSvHealth.signalHealthStatusE5b =
                static_cast<GalileoSvHealth::GalileoHealthStatusType>(signalHealthStatusE5b);
        galileoSatelliteEphemeris.svHealth = galileoSvHealth;
        env->DeleteLocalRef(galileoSvHealthObj);

        // Set the satelliteEphemerisTime of the satellite.
        jobject satelliteEphemerisTimeObj =
                env->CallObjectMethod(galileoSatelliteEphemerisObj,
                                      method_galileoSatelliteEphemerisGetSatelliteEphemerisTime);
        GnssAssistanceUtil::setSatelliteEphemerisTime(env, satelliteEphemerisTimeObj,
                                                      galileoSatelliteEphemeris
                                                              .satelliteEphemerisTime);
        env->DeleteLocalRef(satelliteEphemerisTimeObj);

        galileoSatelliteEphemerisList.push_back(galileoSatelliteEphemeris);
        env->DeleteLocalRef(galileoSatelliteEphemerisObj);
    }
}

void GnssAssistanceUtil::setBeidouAssistance(JNIEnv* env, jobject beidouAssistanceObj,
                                             std::optional<BeidouAssistance>& beidouAssistanceOpt) {
    BeidouAssistance beidouAssistance;
    jobject beidouAlmanacObj =
            env->CallObjectMethod(beidouAssistanceObj, method_beidouAssistanceGetAlmanac);
    jobject ionosphericModelObj =
            env->CallObjectMethod(beidouAssistanceObj, method_beidouAssistanceGetIonosphericModel);
    jobject utcModelObj =
            env->CallObjectMethod(beidouAssistanceObj, method_beidouAssistanceGetUtcModel);
    jobject leapSecondsModelObj =
            env->CallObjectMethod(beidouAssistanceObj, method_beidouAssistanceGetLeapSecondsModel);
    jobject timeModelsObj =
            env->CallObjectMethod(beidouAssistanceObj, method_beidouAssistanceGetTimeModels);
    jobject satelliteEphemerisObj =
            env->CallObjectMethod(beidouAssistanceObj,
                                  method_beidouAssistanceGetSatelliteEphemeris);
    jobject realTimeIntegrityModelsObj =
            env->CallObjectMethod(beidouAssistanceObj,
                                  method_beidouAssistanceGetRealTimeIntegrityModels);
    jobject satelliteCorrectionsObj =
            env->CallObjectMethod(beidouAssistanceObj,
                                  method_beidouAssistanceGetSatelliteCorrections);
    jobject auxiliaryInformationsObj =
            env->CallObjectMethod(beidouAssistanceObj,
                                  method_beidouAssistanceGetAuxiliaryInformation);
    setGnssAlmanac(env, beidouAlmanacObj, beidouAssistance.almanac);
    setKlobucharIonosphericModel(env, ionosphericModelObj, beidouAssistance.ionosphericModel);
    setUtcModel(env, utcModelObj, beidouAssistance.utcModel);
    setLeapSecondsModel(env, leapSecondsModelObj, beidouAssistance.leapSecondsModel);
    setTimeModels(env, timeModelsObj, beidouAssistance.timeModels);
    setBeidouSatelliteEphemeris(env, satelliteEphemerisObj, beidouAssistance.satelliteEphemeris);
    setRealTimeIntegrityModels(env, realTimeIntegrityModelsObj,
                               beidouAssistance.realTimeIntegrityModels);
    setSatelliteCorrections(env, satelliteCorrectionsObj, beidouAssistance.satelliteCorrections);
    setAuxiliaryInformations(env, auxiliaryInformationsObj, beidouAssistance.auxiliaryInformations);
    beidouAssistanceOpt = beidouAssistance;
    env->DeleteLocalRef(beidouAlmanacObj);
    env->DeleteLocalRef(ionosphericModelObj);
    env->DeleteLocalRef(utcModelObj);
    env->DeleteLocalRef(leapSecondsModelObj);
    env->DeleteLocalRef(timeModelsObj);
    env->DeleteLocalRef(satelliteEphemerisObj);
    env->DeleteLocalRef(realTimeIntegrityModelsObj);
    env->DeleteLocalRef(satelliteCorrectionsObj);
    env->DeleteLocalRef(auxiliaryInformationsObj);
}

void GnssAssistanceUtil::setBeidouSatelliteEphemeris(
        JNIEnv* env, jobject beidouSatelliteEphemerisListObj,
        std::vector<BeidouSatelliteEphemeris>& beidouSatelliteEphemerisList) {
    if (beidouSatelliteEphemerisListObj == nullptr) return;
    auto len = env->CallIntMethod(beidouSatelliteEphemerisListObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        jobject beidouSatelliteEphemerisObj =
                env->CallObjectMethod(beidouSatelliteEphemerisListObj, method_listGet, i);
        if (beidouSatelliteEphemerisObj == nullptr) continue;
        BeidouSatelliteEphemeris beidouSatelliteEphemeris;

        // Set the svid of the satellite.
        jint svid = env->CallIntMethod(beidouSatelliteEphemerisObj,
                                       method_beidouSatelliteEphemerisGetSvid);
        beidouSatelliteEphemeris.svid = static_cast<int32_t>(svid);

        // Set the satelliteClockModel of the satellite.
        jobject satelliteClockModelObj =
                env->CallObjectMethod(beidouSatelliteEphemerisObj,
                                      method_beidouSatelliteEphemerisGetSatelliteClockModel);
        jdouble af0 = env->CallDoubleMethod(satelliteClockModelObj,
                                            method_beidouSatelliteClockModelGetAf0);
        jdouble af1 = env->CallDoubleMethod(satelliteClockModelObj,
                                            method_beidouSatelliteClockModelGetAf1);
        jdouble af2 = env->CallDoubleMethod(satelliteClockModelObj,
                                            method_beidouSatelliteClockModelGetAf2);
        jdouble tgd1 = env->CallDoubleMethod(satelliteClockModelObj,
                                             method_beidouSatelliteClockModelGetTgd1);
        jdouble tgd2 = env->CallDoubleMethod(satelliteClockModelObj,
                                             method_beidouSatelliteClockModelGetTgd2);
        jdouble aodc = env->CallDoubleMethod(satelliteClockModelObj,
                                             method_beidouSatelliteClockModelGetAodc);
        jlong timeOfClockSeconds =
                env->CallLongMethod(satelliteClockModelObj,
                                    method_beidouSatelliteClockModelGetTimeOfClockSeconds);
        beidouSatelliteEphemeris.satelliteClockModel.af0 = af0;
        beidouSatelliteEphemeris.satelliteClockModel.af1 = af1;
        beidouSatelliteEphemeris.satelliteClockModel.af2 = af2;
        beidouSatelliteEphemeris.satelliteClockModel.tgd1 = tgd1;
        beidouSatelliteEphemeris.satelliteClockModel.tgd2 = tgd2;
        beidouSatelliteEphemeris.satelliteClockModel.aodc = aodc;
        beidouSatelliteEphemeris.satelliteClockModel.timeOfClockSeconds = timeOfClockSeconds;
        env->DeleteLocalRef(satelliteClockModelObj);

        // Set the satelliteOrbitModel of the satellite.
        jobject satelliteOrbitModelObj =
                env->CallObjectMethod(beidouSatelliteEphemerisObj,
                                      method_beidouSatelliteEphemerisGetSatelliteOrbitModel);
        GnssAssistanceUtil::setKeplerianOrbitModel(env, satelliteOrbitModelObj,
                                                   beidouSatelliteEphemeris.satelliteOrbitModel);
        env->DeleteLocalRef(satelliteOrbitModelObj);

        // Set the satelliteHealth of the satellite.
        jobject satelliteHealthObj =
                env->CallObjectMethod(beidouSatelliteEphemerisObj,
                                      method_beidouSatelliteEphemerisGetSatelliteHealth);
        jint satH1 = env->CallIntMethod(satelliteHealthObj, method_beidouSatelliteHealthGetSatH1);
        jint svAccur =
                env->CallIntMethod(satelliteHealthObj, method_beidouSatelliteHealthGetSvAccur);
        beidouSatelliteEphemeris.satelliteHealth.satH1 = static_cast<int32_t>(satH1);
        beidouSatelliteEphemeris.satelliteHealth.svAccur = static_cast<int32_t>(svAccur);
        env->DeleteLocalRef(satelliteHealthObj);

        // Set the satelliteEphemerisTime of the satellite.
        jobject satelliteEphemerisTimeObj =
                env->CallObjectMethod(beidouSatelliteEphemerisObj,
                                      method_beidouSatelliteEphemerisGetSatelliteEphemerisTime);
        jint aode = env->CallIntMethod(satelliteEphemerisTimeObj,
                                       method_beidouSatelliteEphemerisTimeGetAode);
        jint beidouWeekNumber =
                env->CallIntMethod(satelliteEphemerisTimeObj,
                                   method_beidouSatelliteEphemerisTimeGetBeidouWeekNumber);
        jint toeSeconds = env->CallDoubleMethod(satelliteEphemerisTimeObj,
                                                method_beidouSatelliteEphemerisTimeGetToeSeconds);
        beidouSatelliteEphemeris.satelliteEphemerisTime.aode = static_cast<int32_t>(aode);
        beidouSatelliteEphemeris.satelliteEphemerisTime.weekNumber =
                static_cast<int32_t>(beidouWeekNumber);
        beidouSatelliteEphemeris.satelliteEphemerisTime.toeSeconds =
                static_cast<int32_t>(toeSeconds);
        env->DeleteLocalRef(satelliteEphemerisTimeObj);

        beidouSatelliteEphemerisList.push_back(beidouSatelliteEphemeris);
        env->DeleteLocalRef(beidouSatelliteEphemerisObj);
    }
}

void GnssAssistanceUtil::setGpsAssistance(JNIEnv* env, jobject gpsAssistanceObj,
                                          std::optional<GpsAssistance>& gpsAssistanceOpt) {
    if (gpsAssistanceObj == nullptr) return;
    GpsAssistance gpsAssistance;
    jobject gnssAlmanacObj =
            env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetAlmanac);
    jobject ionosphericModelObj =
            env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetIonosphericModel);
    jobject utcModelObj = env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetUtcModel);
    jobject leapSecondsModelObj =
            env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetLeapSecondsModel);
    jobject timeModelsObj =
            env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetTimeModels);
    jobject satelliteEphemerisObj =
            env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetSatelliteEphemeris);
    jobject realTimeIntegrityModelsObj =
            env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetRealTimeIntegrityModels);
    jobject satelliteCorrectionsObj =
            env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetSatelliteCorrections);
    jobject auxiliaryInformationsObj =
            env->CallObjectMethod(gpsAssistanceObj, method_gpsAssistanceGetAuxiliaryInformation);
    setGnssAlmanac(env, gnssAlmanacObj, gpsAssistance.almanac);
    setKlobucharIonosphericModel(env, ionosphericModelObj, gpsAssistance.ionosphericModel);
    setUtcModel(env, utcModelObj, gpsAssistance.utcModel);
    setLeapSecondsModel(env, leapSecondsModelObj, gpsAssistance.leapSecondsModel);
    setTimeModels(env, timeModelsObj, gpsAssistance.timeModels);
    setGpsOrQzssSatelliteEphemeris<GpsSatelliteEphemeris>(env, satelliteEphemerisObj,
                                                          gpsAssistance.satelliteEphemeris);
    setRealTimeIntegrityModels(env, realTimeIntegrityModelsObj,
                               gpsAssistance.realTimeIntegrityModels);
    setSatelliteCorrections(env, satelliteCorrectionsObj, gpsAssistance.satelliteCorrections);
    setAuxiliaryInformations(env, auxiliaryInformationsObj, gpsAssistance.auxiliaryInformations);
    gpsAssistanceOpt = gpsAssistance;
    env->DeleteLocalRef(gnssAlmanacObj);
    env->DeleteLocalRef(ionosphericModelObj);
    env->DeleteLocalRef(utcModelObj);
    env->DeleteLocalRef(leapSecondsModelObj);
    env->DeleteLocalRef(timeModelsObj);
    env->DeleteLocalRef(satelliteEphemerisObj);
    env->DeleteLocalRef(realTimeIntegrityModelsObj);
    env->DeleteLocalRef(satelliteCorrectionsObj);
    env->DeleteLocalRef(auxiliaryInformationsObj);
}

/** Set the GPS/QZSS satellite ephemeris list. */
template <class T>
void GnssAssistanceUtil::setGpsOrQzssSatelliteEphemeris(JNIEnv* env,
                                                        jobject satelliteEphemerisListObj,
                                                        std::vector<T>& satelliteEphemerisList) {
    if (satelliteEphemerisListObj == nullptr) return;
    auto len = env->CallIntMethod(satelliteEphemerisListObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        jobject satelliteEphemerisObj =
                env->CallObjectMethod(satelliteEphemerisListObj, method_listGet, i);
        if (satelliteEphemerisObj == nullptr) continue;
        T satelliteEphemeris;
        // Set the svid of the satellite.
        jint svid = env->CallIntMethod(satelliteEphemerisObj, method_gpsSatelliteEphemerisGetSvid);
        satelliteEphemeris.svid = static_cast<int32_t>(svid);

        // Set the gpsL2Params of the satellite.
        jobject gpsL2ParamsObj = env->CallObjectMethod(satelliteEphemerisObj,
                                                       method_gpsSatelliteEphemerisGetGpsL2Params);
        jint l2Code = env->CallIntMethod(gpsL2ParamsObj, method_gpsL2ParamsGetL2Code);
        jint l2Flag = env->CallIntMethod(gpsL2ParamsObj, method_gpsL2ParamsGetL2Flag);
        satelliteEphemeris.gpsL2Params.l2Code = static_cast<int32_t>(l2Code);
        satelliteEphemeris.gpsL2Params.l2Flag = static_cast<int32_t>(l2Flag);
        env->DeleteLocalRef(gpsL2ParamsObj);

        // Set the satelliteClockModel of the satellite.
        jobject satelliteClockModelObj =
                env->CallObjectMethod(satelliteEphemerisObj,
                                      method_gpsSatelliteEphemerisGetSatelliteClockModel);
        jdouble af0 =
                env->CallDoubleMethod(satelliteClockModelObj, method_gpsSatelliteClockModelGetAf0);
        jdouble af1 =
                env->CallDoubleMethod(satelliteClockModelObj, method_gpsSatelliteClockModelGetAf1);
        jdouble af2 =
                env->CallDoubleMethod(satelliteClockModelObj, method_gpsSatelliteClockModelGetAf2);
        jdouble tgd =
                env->CallDoubleMethod(satelliteClockModelObj, method_gpsSatelliteClockModelGetTgd);
        jint iodc =
                env->CallDoubleMethod(satelliteClockModelObj, method_gpsSatelliteClockModelGetIodc);
        jlong timeOfClockSeconds =
                env->CallLongMethod(satelliteClockModelObj,
                                    method_gpsSatelliteClockModelGetTimeOfClockSeconds);
        satelliteEphemeris.satelliteClockModel.af0 = af0;
        satelliteEphemeris.satelliteClockModel.af1 = af1;
        satelliteEphemeris.satelliteClockModel.af2 = af2;
        satelliteEphemeris.satelliteClockModel.tgd = tgd;
        satelliteEphemeris.satelliteClockModel.iodc = static_cast<int32_t>(iodc);
        satelliteEphemeris.satelliteClockModel.timeOfClockSeconds = timeOfClockSeconds;
        env->DeleteLocalRef(satelliteClockModelObj);

        // Set the satelliteOrbitModel of the satellite.
        jobject satelliteOrbitModelObj =
                env->CallObjectMethod(satelliteEphemerisObj,
                                      method_gpsSatelliteEphemerisGetSatelliteOrbitModel);
        GnssAssistanceUtil::setKeplerianOrbitModel(env, satelliteOrbitModelObj,
                                                   satelliteEphemeris.satelliteOrbitModel);
        env->DeleteLocalRef(satelliteOrbitModelObj);

        // Set the satelliteHealth of the satellite.
        jobject satelliteHealthObj =
                env->CallObjectMethod(satelliteEphemerisObj,
                                      method_gpsSatelliteEphemerisGetSatelliteHealth);
        jint svHealth =
                env->CallIntMethod(satelliteHealthObj, method_gpsSatelliteHealthGetSvHealth);
        jdouble svAccur =
                env->CallDoubleMethod(satelliteHealthObj, method_gpsSatelliteHealthGetSvAccur);
        jdouble fitInt = env->CallIntMethod(satelliteHealthObj, method_gpsSatelliteHealthGetFitInt);
        satelliteEphemeris.satelliteHealth.svHealth = static_cast<int32_t>(svHealth);
        satelliteEphemeris.satelliteHealth.svAccur = svAccur;
        satelliteEphemeris.satelliteHealth.fitInt = fitInt;
        env->DeleteLocalRef(satelliteHealthObj);

        // Set the satelliteEphemerisTime of the satellite.
        jobject satelliteEphemerisTimeObj =
                env->CallObjectMethod(satelliteEphemerisObj,
                                      method_gpsSatelliteEphemerisGetSatelliteEphemerisTime);
        GnssAssistanceUtil::setSatelliteEphemerisTime(env, satelliteEphemerisTimeObj,
                                                      satelliteEphemeris.satelliteEphemerisTime);
        env->DeleteLocalRef(satelliteEphemerisTimeObj);

        satelliteEphemerisList.push_back(satelliteEphemeris);
        env->DeleteLocalRef(satelliteEphemerisObj);
    }
}

void GnssAssistanceUtil::setSatelliteCorrections(
        JNIEnv* env, jobject satelliteCorrectionsObj,
        std::vector<GnssSatelliteCorrections>& gnssSatelliteCorrectionsList) {
    if (satelliteCorrectionsObj == nullptr) return;
    auto len = env->CallIntMethod(satelliteCorrectionsObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        GnssSatelliteCorrections gnssSatelliteCorrections;
        jobject satelliteCorrectionObj =
                env->CallObjectMethod(satelliteCorrectionsObj, method_listGet, i);
        if (satelliteCorrectionObj == nullptr) continue;
        jint svid = env->CallIntMethod(satelliteCorrectionObj, method_satelliteCorrectionGetSvid);
        gnssSatelliteCorrections.svid = svid;
        jobject ionosphericCorrectionsObj =
                env->CallObjectMethod(satelliteCorrectionObj,
                                      method_satelliteCorrectionGetIonosphericCorrections);
        env->DeleteLocalRef(satelliteCorrectionObj);
        auto size = env->CallIntMethod(ionosphericCorrectionsObj, method_listSize);
        for (uint16_t j = 0; j < size; ++j) {
            jobject ionosphericCorrectionObj =
                    env->CallObjectMethod(ionosphericCorrectionsObj, method_listGet, j);
            if (ionosphericCorrectionObj == nullptr) continue;
            IonosphericCorrection ionosphericCorrection;
            jlong carrierFrequencyHz =
                    env->CallLongMethod(ionosphericCorrectionObj,
                                        method_ionosphericCorrectionGetCarrierFrequencyHz);
            ionosphericCorrection.carrierFrequencyHz = carrierFrequencyHz;

            jobject gnssCorrectionComponentObj =
                    env->CallObjectMethod(ionosphericCorrectionObj,
                                          method_ionosphericCorrectionGetIonosphericCorrection);
            env->DeleteLocalRef(ionosphericCorrectionObj);

            jstring sourceKey = static_cast<jstring>(
                    env->CallObjectMethod(gnssCorrectionComponentObj,
                                          method_gnssCorrectionComponentGetSourceKey));
            ScopedJniString jniSourceKey{env, sourceKey};
            ionosphericCorrection.ionosphericCorrectionComponent.sourceKey =
                    android::String16(jniSourceKey.c_str());

            jobject pseudorangeCorrectionObj =
                    env->CallObjectMethod(gnssCorrectionComponentObj,
                                          method_gnssCorrectionComponentGetPseudorangeCorrection);
            jdouble correctionMeters =
                    env->CallDoubleMethod(pseudorangeCorrectionObj,
                                          method_pseudorangeCorrectionGetCorrectionMeters);
            jdouble correctionUncertaintyMeters = env->CallDoubleMethod(
                    pseudorangeCorrectionObj,
                    method_pseudorangeCorrectionGetCorrectionUncertaintyMeters);
            jdouble correctionRateMetersPerSecond = env->CallDoubleMethod(
                    pseudorangeCorrectionObj,
                    method_pseudorangeCorrectionGetCorrectionRateMetersPerSecond);
            ionosphericCorrection.ionosphericCorrectionComponent.pseudorangeCorrection
                    .correctionMeters = correctionMeters;
            ionosphericCorrection.ionosphericCorrectionComponent.pseudorangeCorrection
                    .correctionUncertaintyMeters = correctionUncertaintyMeters;
            ionosphericCorrection.ionosphericCorrectionComponent.pseudorangeCorrection
                    .correctionRateMetersPerSecond = correctionRateMetersPerSecond;
            env->DeleteLocalRef(pseudorangeCorrectionObj);

            jobject gnssIntervalObj =
                    env->CallObjectMethod(gnssCorrectionComponentObj,
                                          method_gnssCorrectionComponentGetValidityInterval);
            jdouble startMillisSinceGpsEpoch =
                    env->CallDoubleMethod(gnssIntervalObj,
                                          method_gnssIntervalGetStartMillisSinceGpsEpoch);
            jdouble endMillisSinceGpsEpoch =
                    env->CallDoubleMethod(gnssIntervalObj,
                                          method_gnssIntervalGetEndMillisSinceGpsEpoch);
            ionosphericCorrection.ionosphericCorrectionComponent.validityInterval
                    .startMillisSinceGpsEpoch = startMillisSinceGpsEpoch;
            ionosphericCorrection.ionosphericCorrectionComponent.validityInterval
                    .endMillisSinceGpsEpoch = endMillisSinceGpsEpoch;
            env->DeleteLocalRef(gnssIntervalObj);

            env->DeleteLocalRef(gnssCorrectionComponentObj);
            gnssSatelliteCorrections.ionosphericCorrections.push_back(ionosphericCorrection);
        }
        gnssSatelliteCorrectionsList.push_back(gnssSatelliteCorrections);
        env->DeleteLocalRef(ionosphericCorrectionsObj);
    }
}

void GnssAssistanceUtil::setRealTimeIntegrityModels(
        JNIEnv* env, jobject realTimeIntegrityModelsObj,
        std::vector<RealTimeIntegrityModel>& realTimeIntegrityModels) {
    if (realTimeIntegrityModelsObj == nullptr) return;
    auto len = env->CallIntMethod(realTimeIntegrityModelsObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        jobject realTimeIntegrityModelObj =
                env->CallObjectMethod(realTimeIntegrityModelsObj, method_listGet, i);
        if (realTimeIntegrityModelObj == nullptr) continue;
        RealTimeIntegrityModel realTimeIntegrityModel;
        jint badSvid = env->CallIntMethod(realTimeIntegrityModelObj,
                                          method_realTimeIntegrityModelGetBadSvid);
        jobject badSignalTypesObj =
                env->CallObjectMethod(realTimeIntegrityModelObj,
                                      method_realTimeIntegrityModelGetBadSignalTypes);
        auto badSignalTypesSize = env->CallIntMethod(badSignalTypesObj, method_listSize);
        for (uint16_t j = 0; j < badSignalTypesSize; ++j) {
            GnssSignalType badSignalType;
            jobject badSignalTypeObj = env->CallObjectMethod(badSignalTypesObj, method_listGet, j);
            if (badSignalTypeObj != nullptr) {
                setGnssSignalType(env, badSignalTypeObj, badSignalType);
                realTimeIntegrityModel.badSignalTypes.push_back(badSignalType);
                env->DeleteLocalRef(badSignalTypeObj);
            }
        }
        jlong startDateSeconds =
                env->CallLongMethod(realTimeIntegrityModelObj,
                                    method_realTimeIntegrityModelGetStartDateSeconds);
        jlong endDateSeconds = env->CallLongMethod(realTimeIntegrityModelObj,
                                                   method_realTimeIntegrityModelGetEndDateSeconds);
        jlong publishDateSeconds =
                env->CallLongMethod(realTimeIntegrityModelObj,
                                    method_realTimeIntegrityModelGetPublishDateSeconds);
        jstring advisoryNumber = static_cast<jstring>(
                env->CallObjectMethod(realTimeIntegrityModelObj,
                                      method_realTimeIntegrityModelGetAdvisoryNumber));
        ScopedJniString jniAdvisoryNumber{env, advisoryNumber};
        jstring advisoryType = static_cast<jstring>(
                env->CallObjectMethod(realTimeIntegrityModelObj,
                                      method_realTimeIntegrityModelGetAdvisoryType));
        ScopedJniString jniAdvisoryType{env, advisoryType};

        realTimeIntegrityModel.badSvid = badSvid;
        realTimeIntegrityModel.startDateSeconds = startDateSeconds;
        realTimeIntegrityModel.endDateSeconds = endDateSeconds;
        realTimeIntegrityModel.publishDateSeconds = publishDateSeconds;
        realTimeIntegrityModel.advisoryNumber = android::String16(jniAdvisoryNumber.c_str());
        realTimeIntegrityModel.advisoryType = android::String16(jniAdvisoryType.c_str());
        realTimeIntegrityModels.push_back(realTimeIntegrityModel);
        env->DeleteLocalRef(badSignalTypesObj);
        env->DeleteLocalRef(realTimeIntegrityModelObj);
    }
}

void GnssAssistanceUtil::setGnssSignalType(JNIEnv* env, jobject gnssSignalTypeObj,
                                           GnssSignalType& gnssSignalType) {
    if (gnssSignalTypeObj == nullptr) {
        ALOGE("gnssSignalTypeObj is null");
        return;
    }
    jint constellationType =
            env->CallIntMethod(gnssSignalTypeObj, method_gnssSignalTypeGetConstellationType);
    jdouble carrierFrequencyHz =
            env->CallIntMethod(gnssSignalTypeObj, method_gnssSignalTypeGetCarrierFrequencyHz);
    jstring codeType = static_cast<jstring>(
            env->CallObjectMethod(gnssSignalTypeObj, method_gnssSignalTypeGetCodeType));
    ScopedJniString jniCodeType{env, codeType};

    gnssSignalType.constellation = static_cast<GnssConstellationType>(constellationType);
    gnssSignalType.carrierFrequencyHz = static_cast<int32_t>(carrierFrequencyHz);
    gnssSignalType.codeType = std::string(jniCodeType.c_str());
}

void GnssAssistanceUtil::setTimeModels(JNIEnv* env, jobject timeModelsObj,
                                       std::vector<TimeModel>& timeModels) {
    if (timeModelsObj == nullptr) return;
    auto len = env->CallIntMethod(timeModelsObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        jobject timeModelObj = env->CallObjectMethod(timeModelsObj, method_listGet, i);
        TimeModel timeModel;
        jint toGnss = env->CallIntMethod(timeModelObj, method_timeModelsGetToGnss);
        jlong timeOfWeek = env->CallLongMethod(timeModelObj, method_timeModelsGetTimeOfWeek);
        jint weekNumber = env->CallIntMethod(timeModelObj, method_timeModelsGetWeekNumber);
        jdouble a0 = env->CallDoubleMethod(timeModelObj, method_timeModelsGetA0);
        jdouble a1 = env->CallDoubleMethod(timeModelObj, method_timeModelsGetA1);
        timeModel.toGnss = static_cast<GnssConstellationType>(toGnss);
        timeModel.timeOfWeek = timeOfWeek;
        timeModel.weekNumber = static_cast<int32_t>(weekNumber);
        timeModel.a0 = a0;
        timeModel.a1 = a1;
        timeModels.push_back(timeModel);
        env->DeleteLocalRef(timeModelObj);
    }
}

void GnssAssistanceUtil::setLeapSecondsModel(JNIEnv* env, jobject leapSecondsModelObj,
                                             std::optional<LeapSecondsModel>& leapSecondsModelOpt) {
    if (leapSecondsModelObj == nullptr) return;
    LeapSecondsModel leapSecondsModel;
    jint dayNumberLeapSecondsFuture =
            env->CallIntMethod(leapSecondsModelObj,
                               method_leapSecondsModelGetDayNumberLeapSecondsFuture);
    jint leapSeconds =
            env->CallIntMethod(leapSecondsModelObj, method_leapSecondsModelGetLeapSeconds);
    jint leapSecondsFuture =
            env->CallIntMethod(leapSecondsModelObj, method_leapSecondsModelGetLeapSecondsFuture);
    jint weekNumberLeapSecondsFuture =
            env->CallIntMethod(leapSecondsModelObj,
                               method_leapSecondsModelGetWeekNumberLeapSecondsFuture);
    leapSecondsModel.dayNumberLeapSecondsFuture = static_cast<int32_t>(dayNumberLeapSecondsFuture);
    leapSecondsModel.leapSeconds = static_cast<int32_t>(leapSeconds);
    leapSecondsModel.leapSecondsFuture = static_cast<int32_t>(leapSecondsFuture);
    leapSecondsModel.weekNumberLeapSecondsFuture =
            static_cast<int32_t>(weekNumberLeapSecondsFuture);
    leapSecondsModelOpt = leapSecondsModel;
}

void GnssAssistanceUtil::setSatelliteEphemerisTime(JNIEnv* env, jobject satelliteEphemerisTimeObj,
                                                   SatelliteEphemerisTime& satelliteEphemerisTime) {
    if (satelliteEphemerisTimeObj == nullptr) return;
    jdouble iode =
            env->CallDoubleMethod(satelliteEphemerisTimeObj, method_satelliteEphemerisTimeGetIode);
    jdouble toeSeconds = env->CallDoubleMethod(satelliteEphemerisTimeObj,
                                               method_satelliteEphemerisTimeGetToeSeconds);
    jint weekNumber = env->CallIntMethod(satelliteEphemerisTimeObj,
                                         method_satelliteEphemerisTimeGetWeekNumber);
    satelliteEphemerisTime.iode = iode;
    satelliteEphemerisTime.toeSeconds = toeSeconds;
    satelliteEphemerisTime.weekNumber = weekNumber;
}

void GnssAssistanceUtil::setKeplerianOrbitModel(JNIEnv* env, jobject keplerianOrbitModelObj,
                                                KeplerianOrbitModel& keplerianOrbitModel) {
    if (keplerianOrbitModelObj == nullptr) return;
    jdouble rootA =
            env->CallDoubleMethod(keplerianOrbitModelObj, method_keplerianOrbitModelGetRootA);
    jdouble eccentricity = env->CallDoubleMethod(keplerianOrbitModelObj,
                                                 method_keplerianOrbitModelGetEccentricity);
    jdouble m0 = env->CallDoubleMethod(keplerianOrbitModelObj, method_keplerianOrbitModelGetM0);
    jdouble omega =
            env->CallDoubleMethod(keplerianOrbitModelObj, method_keplerianOrbitModelGetOmega);
    jdouble omegaDot =
            env->CallDoubleMethod(keplerianOrbitModelObj, method_keplerianOrbitModelGetOmegaDot);
    jdouble deltaN =
            env->CallDoubleMethod(keplerianOrbitModelObj, method_keplerianOrbitModelGetDeltaN);
    jdouble iDot = env->CallDoubleMethod(keplerianOrbitModelObj, method_keplerianOrbitModelGetIDot);
    jobject secondOrderHarmonicPerturbationObj =
            env->CallObjectMethod(keplerianOrbitModelObj,
                                  method_keplerianOrbitModelGetSecondOrderHarmonicPerturbation);
    jdouble cic = env->CallDoubleMethod(secondOrderHarmonicPerturbationObj,
                                        method_secondOrderHarmonicPerturbationGetCic);
    jdouble cis = env->CallDoubleMethod(secondOrderHarmonicPerturbationObj,
                                        method_secondOrderHarmonicPerturbationGetCis);
    jdouble crs = env->CallDoubleMethod(secondOrderHarmonicPerturbationObj,
                                        method_secondOrderHarmonicPerturbationGetCrs);
    jdouble crc = env->CallDoubleMethod(secondOrderHarmonicPerturbationObj,
                                        method_secondOrderHarmonicPerturbationGetCrc);
    jdouble cuc = env->CallDoubleMethod(secondOrderHarmonicPerturbationObj,
                                        method_secondOrderHarmonicPerturbationGetCuc);
    jdouble cus = env->CallDoubleMethod(secondOrderHarmonicPerturbationObj,
                                        method_secondOrderHarmonicPerturbationGetCus);
    keplerianOrbitModel.rootA = rootA;
    keplerianOrbitModel.eccentricity = eccentricity;
    keplerianOrbitModel.m0 = m0;
    keplerianOrbitModel.omega = omega;
    keplerianOrbitModel.omegaDot = omegaDot;
    keplerianOrbitModel.deltaN = deltaN;
    keplerianOrbitModel.iDot = iDot;
    keplerianOrbitModel.secondOrderHarmonicPerturbation.cic = cic;
    keplerianOrbitModel.secondOrderHarmonicPerturbation.cis = cis;
    keplerianOrbitModel.secondOrderHarmonicPerturbation.crs = crs;
    keplerianOrbitModel.secondOrderHarmonicPerturbation.crc = crc;
    keplerianOrbitModel.secondOrderHarmonicPerturbation.cuc = cuc;
    keplerianOrbitModel.secondOrderHarmonicPerturbation.cus = cus;
    env->DeleteLocalRef(secondOrderHarmonicPerturbationObj);
}

void GnssAssistanceUtil::setKlobucharIonosphericModel(
        JNIEnv* env, jobject klobucharIonosphericModelObj,
        std::optional<KlobucharIonosphericModel>& klobucharIonosphericModelOpt) {
    if (klobucharIonosphericModelObj == nullptr) return;
    KlobucharIonosphericModel klobucharIonosphericModel;
    jdouble alpha0 = env->CallDoubleMethod(klobucharIonosphericModelObj,
                                           method_klobucharIonosphericModelGetAlpha0);
    jdouble alpha1 = env->CallDoubleMethod(klobucharIonosphericModelObj,
                                           method_klobucharIonosphericModelGetAlpha1);
    jdouble alpha2 = env->CallDoubleMethod(klobucharIonosphericModelObj,
                                           method_klobucharIonosphericModelGetAlpha2);
    jdouble alpha3 = env->CallDoubleMethod(klobucharIonosphericModelObj,
                                           method_klobucharIonosphericModelGetAlpha3);
    jdouble beta0 = env->CallDoubleMethod(klobucharIonosphericModelObj,
                                          method_klobucharIonosphericModelGetBeta0);
    jdouble beta1 = env->CallDoubleMethod(klobucharIonosphericModelObj,
                                          method_klobucharIonosphericModelGetBeta1);
    jdouble beta2 = env->CallDoubleMethod(klobucharIonosphericModelObj,
                                          method_klobucharIonosphericModelGetBeta2);
    jdouble beta3 = env->CallDoubleMethod(klobucharIonosphericModelObj,
                                          method_klobucharIonosphericModelGetBeta3);
    klobucharIonosphericModel.alpha0 = alpha0;
    klobucharIonosphericModel.alpha1 = alpha1;
    klobucharIonosphericModel.alpha2 = alpha2;
    klobucharIonosphericModel.alpha3 = alpha3;
    klobucharIonosphericModel.beta0 = beta0;
    klobucharIonosphericModel.beta1 = beta1;
    klobucharIonosphericModel.beta2 = beta2;
    klobucharIonosphericModel.beta3 = beta3;
    klobucharIonosphericModelOpt = klobucharIonosphericModel;
}

void GnssAssistanceUtil::setUtcModel(JNIEnv* env, jobject utcModelObj,
                                     std::optional<UtcModel>& utcModelOpt) {
    if (utcModelObj == nullptr) return;
    UtcModel utcModel;
    jdouble a0 = env->CallDoubleMethod(utcModelObj, method_utcModelGetA0);
    jdouble a1 = env->CallDoubleMethod(utcModelObj, method_utcModelGetA1);
    jlong timeOfWeek = env->CallLongMethod(utcModelObj, method_utcModelGetTimeOfWeek);
    jint weekNumber = env->CallIntMethod(utcModelObj, method_utcModelGetWeekNumber);
    utcModel.a0 = a0;
    utcModel.a1 = a1;
    utcModel.timeOfWeek = timeOfWeek;
    utcModel.weekNumber = static_cast<int32_t>(weekNumber);
    utcModelOpt = utcModel;
}

void GnssAssistanceUtil::setGnssAlmanac(JNIEnv* env, jobject gnssAlmanacObj,
                                        std::optional<GnssAlmanac>& gnssAlmanacOpt) {
    if (gnssAlmanacObj == nullptr) return;
    GnssAlmanac gnssAlmanac;
    jlong issueDateMillis =
            env->CallLongMethod(gnssAlmanacObj, method_gnssAlmanacGetIssueDateMillis);
    jint ioda = env->CallIntMethod(gnssAlmanacObj, method_gnssAlmanacGetIoda);
    jint weekNumber = env->CallIntMethod(gnssAlmanacObj, method_gnssAlmanacGetWeekNumber);
    jlong toaSeconds = env->CallLongMethod(gnssAlmanacObj, method_gnssAlmanacGetToaSeconds);
    jboolean isCompleteAlmanacProvided =
            env->CallBooleanMethod(gnssAlmanacObj, method_gnssAlmanacIsCompleteAlmanacProvided);
    gnssAlmanac.issueDateMs = issueDateMillis;
    gnssAlmanac.ioda = ioda;
    gnssAlmanac.weekNumber = weekNumber;
    gnssAlmanac.toaSeconds = toaSeconds;
    gnssAlmanac.isCompleteAlmanacProvided = isCompleteAlmanacProvided;

    jobject satelliteAlmanacsListObj =
            env->CallObjectMethod(gnssAlmanacObj, method_gnssAlmanacGetSatelliteAlmanacs);
    auto len = env->CallIntMethod(satelliteAlmanacsListObj, method_listSize);
    std::vector<GnssSatelliteAlmanac> list(len);
    for (uint16_t i = 0; i < len; ++i) {
        jobject gnssSatelliteAlmanacObj =
                env->CallObjectMethod(satelliteAlmanacsListObj, method_listGet, i);
        if (gnssSatelliteAlmanacObj == nullptr) continue;
        GnssSatelliteAlmanac gnssSatelliteAlmanac;
        jint svid = env->CallIntMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetSvid);
        jint svHealth =
                env->CallIntMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetSvHealth);
        jdouble af0 = env->CallDoubleMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetAf0);
        jdouble af1 = env->CallDoubleMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetAf1);
        jdouble eccentricity = env->CallDoubleMethod(gnssSatelliteAlmanacObj,
                                                     method_satelliteAlmanacGetEccentricity);
        jdouble inclination = env->CallDoubleMethod(gnssSatelliteAlmanacObj,
                                                    method_satelliteAlmanacGetInclination);
        jdouble m0 = env->CallDoubleMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetM0);
        jdouble omega =
                env->CallDoubleMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetOmega);
        jdouble omega0 =
                env->CallDoubleMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetOmega0);
        jdouble omegaDot =
                env->CallDoubleMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetOmegaDot);
        jdouble rootA =
                env->CallDoubleMethod(gnssSatelliteAlmanacObj, method_satelliteAlmanacGetRootA);
        gnssSatelliteAlmanac.svid = static_cast<int32_t>(svid);
        gnssSatelliteAlmanac.svHealth = static_cast<int32_t>(svHealth);
        gnssSatelliteAlmanac.af0 = af0;
        gnssSatelliteAlmanac.af1 = af1;
        gnssSatelliteAlmanac.eccentricity = eccentricity;
        gnssSatelliteAlmanac.inclination = inclination;
        gnssSatelliteAlmanac.m0 = m0;
        gnssSatelliteAlmanac.omega = omega;
        gnssSatelliteAlmanac.omega0 = omega0;
        gnssSatelliteAlmanac.omegaDot = omegaDot;
        gnssSatelliteAlmanac.rootA = rootA;
        list.at(i) = gnssSatelliteAlmanac;
        env->DeleteLocalRef(gnssSatelliteAlmanacObj);
    }
    gnssAlmanac.satelliteAlmanacs = list;
    gnssAlmanacOpt = gnssAlmanac;
    env->DeleteLocalRef(satelliteAlmanacsListObj);
}

void GnssAssistanceUtil::setAuxiliaryInformations(
        JNIEnv* env, jobject auxiliaryInformationListObj,
        std::vector<AuxiliaryInformation>& auxiliaryInformations) {
    if (auxiliaryInformationListObj == nullptr) return;
    auto len = env->CallIntMethod(auxiliaryInformationListObj, method_listSize);
    for (uint16_t i = 0; i < len; ++i) {
        AuxiliaryInformation auxiliaryInformation;
        jobject auxiliaryInformationObj =
                env->CallObjectMethod(auxiliaryInformationListObj, method_listGet, i);
        if (auxiliaryInformationObj == nullptr) continue;

        jint svid = env->CallIntMethod(auxiliaryInformationObj, method_auxiliaryInformationGetSvid);
        auxiliaryInformation.svid = static_cast<int32_t>(svid);

        jobject availableSignalTypesObj =
                env->CallObjectMethod(auxiliaryInformationObj,
                                      method_auxiliaryInformationGetAvailableSignalTypes);
        auto size = env->CallIntMethod(availableSignalTypesObj, method_listSize);
        std::vector<GnssSignalType> availableSignalTypes(size);
        for (uint16_t i = 0; i < size; ++i) {
            jobject availableSignalTypeObj =
                    env->CallObjectMethod(availableSignalTypesObj, method_listGet, i);
            GnssSignalType availableSignalType;
            setGnssSignalType(env, availableSignalTypeObj, availableSignalType);
            availableSignalTypes.at(i) = availableSignalType;
            env->DeleteLocalRef(availableSignalTypeObj);
        }
        auxiliaryInformation.availableSignalTypes = availableSignalTypes;
        env->DeleteLocalRef(availableSignalTypesObj);

        jint frequencyChannelNumber =
                env->CallIntMethod(auxiliaryInformationObj,
                                   method_auxiliaryInformationGetFrequencyChannelNumber);
        auxiliaryInformation.frequencyChannelNumber = static_cast<int32_t>(frequencyChannelNumber);

        jint satType =
                env->CallIntMethod(auxiliaryInformationObj, method_auxiliaryInformationGetSatType);
        auxiliaryInformation.satType = static_cast<BeidouB1CSatelliteOrbitType>(satType);
        env->DeleteLocalRef(auxiliaryInformationObj);
        auxiliaryInformations.push_back(auxiliaryInformation);
    }
}

} // namespace android::gnss
