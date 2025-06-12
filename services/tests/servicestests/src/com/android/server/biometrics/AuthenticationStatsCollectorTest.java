/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.biometrics;

import static com.android.server.biometrics.AuthenticationStatsCollector.FRR_MINIMAL_DURATION;
import static com.android.server.biometrics.AuthenticationStatsCollector.MAXIMUM_ENROLLMENT_NOTIFICATIONS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.Collections.emptySet;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.biometrics.BiometricsProtoEnums;
import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.server.biometrics.sensors.BiometricNotification;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.time.Clock;

@Presubmit
@SmallTest
public class AuthenticationStatsCollectorTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private AuthenticationStatsCollector mAuthenticationStatsCollector;
    private static final float FRR_THRESHOLD = 0.2f;
    private static final int USER_ID_1 = 1;

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private FingerprintManager mFingerprintManager;
    @Mock
    private FaceManager mFaceManager;
    @Mock
    private SharedPreferences mSharedPreferences;
    @Mock
    private SharedPreferences.Editor mEditor;
    @Mock
    private BiometricNotification mBiometricNotification;
    @Mock
    private Clock mClock;

    @Before
    public void setUp() {

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(eq(R.bool.config_biometricFrrNotificationEnabled)))
                .thenReturn(true);
        when(mResources.getFraction(eq(R.fraction.config_biometricNotificationFrrThreshold),
                anyInt(), anyInt())).thenReturn(FRR_THRESHOLD);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        when(mContext.getSystemServiceName(FingerprintManager.class))
                .thenReturn(Context.FINGERPRINT_SERVICE);
        when(mContext.getSystemService(Context.FINGERPRINT_SERVICE))
                .thenReturn(mFingerprintManager);
        when(mContext.getSystemServiceName(FaceManager.class)).thenReturn(Context.FACE_SERVICE);
        when(mContext.getSystemService(Context.FACE_SERVICE)).thenReturn(mFaceManager);

        when(mContext.getSharedPreferences(any(File.class), anyInt()))
                .thenReturn(mSharedPreferences);
        when(mSharedPreferences.getStringSet(anyString(), anySet())).thenReturn(emptySet());
        when(mSharedPreferences.edit()).thenReturn(mEditor);
        when(mEditor.putFloat(anyString(), anyFloat())).thenReturn(mEditor);
        when(mEditor.putStringSet(anyString(), anySet())).thenReturn(mEditor);
        when(mBiometricNotification.sendCustomizeFpFrrNotification(eq(mContext)))
                .thenReturn(true);
        when(mClock.millis()).thenReturn(Clock.systemUTC().millis());

        mAuthenticationStatsCollector = new AuthenticationStatsCollector(mContext,
                0 /* modality */, mBiometricNotification, mClock);
    }

    @Test
    public void authenticate_authenticationSucceeded_mapShouldBeUpdated() {
        // Assert that the user doesn't exist in the map initially.
        assertThat(mAuthenticationStatsCollector.getAuthenticationStatsForUser(USER_ID_1)).isNull();

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(true);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, true /* authenticated */);

        AuthenticationStats authenticationStats =
                mAuthenticationStatsCollector.getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getUserId()).isEqualTo(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(1);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(0);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(0L);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(0L);
    }

    @Test
    public void authenticate_authenticationFailed_mapShouldBeUpdated() {
        // Assert that the user doesn't exist in the map initially.
        assertThat(mAuthenticationStatsCollector.getAuthenticationStatsForUser(USER_ID_1)).isNull();

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        AuthenticationStats authenticationStats =
                mAuthenticationStatsCollector.getAuthenticationStatsForUser(USER_ID_1);

        assertThat(authenticationStats.getUserId()).isEqualTo(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(1);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(1);
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(0);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(0L);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(0L);
    }

    /**
     * Our current use case does not need the counters to update after the notification
     * limit is reached. If we need these counters to continue counting in the future,
     * a separate privacy review must be done.
     */
    @Test
    public void authenticate_notificationExceeded_mapMustNotBeUpdated() {

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 400 /* totalAttempts */,
                        40 /* rejectedAttempts */,
                        MAXIMUM_ENROLLMENT_NOTIFICATIONS /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        0 /* modality */));

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        AuthenticationStats authenticationStats =
                mAuthenticationStatsCollector.getAuthenticationStatsForUser(USER_ID_1);

        assertThat(authenticationStats.getUserId()).isEqualTo(USER_ID_1);
        // Assert that counters haven't been updated.
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(400);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(40);
        assertThat(authenticationStats.getEnrollmentNotifications())
                .isEqualTo(MAXIMUM_ENROLLMENT_NOTIFICATIONS);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(200L);
    }

    // TODO WIP
    @Test
    public void authenticate_frrNotExceeded_notificationNotExceeded_shouldNotSendNotification() {

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        40 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        0 /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that no notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data has been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(-1.0f);
        // lastEnrollmentTime and lastFrrNotificationTime shall be kept
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(200L);
    }

    @Test
    public void authenticate_frrExceeded_notificationExceeded_shouldNotSendNotification() {

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */,
                        MAXIMUM_ENROLLMENT_NOTIFICATIONS /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        0 /* modality */));

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that no notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data hasn't been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(500);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(400);
        assertThat(authenticationStats.getEnrollmentNotifications())
                .isEqualTo(MAXIMUM_ENROLLMENT_NOTIFICATIONS);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(200L);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(0.8f);
    }

    @Test
    @DisableFlags(Flags.FLAG_FRR_DIALOG_IMPROVEMENT)
    public void authenticate_frrExceeded_bothBiometricsEnrolled_shouldNotSendNotification() {

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        0 /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(true);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that no notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data hasn't been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(500);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(400);
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(0.8f);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(200L);
    }

    @Test
    @EnableFlags(Flags.FLAG_FRR_DIALOG_IMPROVEMENT)
    public void authenticate_enrollTimeNotPass_bothBiometricsEnrolled_shouldNotSendNotification() {

        long lastEnrollmentTime = 60L * 60L * 1000L;
        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        lastEnrollmentTime, 0L /* lastFrrNotificationTime */,
                        0 /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mClock.millis()).thenReturn(lastEnrollmentTime + FRR_MINIMAL_DURATION.toMillis());

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that no notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data hasn't been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(500);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(400);
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(0.8f);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(lastEnrollmentTime);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(0L);
    }

    @Test
    @EnableFlags(Flags.FLAG_FRR_DIALOG_IMPROVEMENT)
    public void authenticate_lastFrrTimeNotPass_bothBiometricsEnrolled_shouldNotSendNotification() {

        long lastFrrNotificationTime = 200L;
        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, lastFrrNotificationTime,
                        0 /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mClock.millis()).thenReturn(lastFrrNotificationTime + FRR_MINIMAL_DURATION.toMillis());

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that no notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data hasn't been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(500);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(400);
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(0.8f);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(
                lastFrrNotificationTime);
    }

    @Test
    public void authenticate_frrExceeded_singleModality_shouldNotSendNotification() {

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        0 /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(false);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mContext.getSystemService(Context.FACE_SERVICE)).thenReturn(null);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that no notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data hasn't been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(500);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(400);
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(0.8f);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(200L);
    }

    @Test
    @DisableFlags(Flags.FLAG_FRR_DIALOG_IMPROVEMENT)
    public void authenticate_frrExceeded_faceEnrolled_shouldSendFpNotification() {
        // Use correct modality
        mAuthenticationStatsCollector = new AuthenticationStatsCollector(mContext,
                BiometricsProtoEnums.MODALITY_FACE, mBiometricNotification, mClock);

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        BiometricsProtoEnums.MODALITY_FACE /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(false);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mClock.millis()).thenReturn(3344L);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that fingerprint enrollment notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, times(1)).sendFpEnrollNotification(mContext);
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        // Assert that data has been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(-1.0f);
        // Assert that notification count has been updated.
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(1);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        // Assert that lastFrrNotificationTime has been updated.
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(3344L);
    }

    @Test
    @EnableFlags(Flags.FLAG_FRR_DIALOG_IMPROVEMENT)
    public void authenticate_frrExceeded_faceEnrolled_shouldSendFpNotification_withFrrFlag() {
        // Use correct modality
        mAuthenticationStatsCollector = new AuthenticationStatsCollector(mContext,
                BiometricsProtoEnums.MODALITY_FACE, mBiometricNotification, mClock);

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        BiometricsProtoEnums.MODALITY_FACE /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(false);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        long newLastFrrNotificationTime = 200L + FRR_MINIMAL_DURATION.toMillis() + 1;
        when(mClock.millis()).thenReturn(newLastFrrNotificationTime);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that fingerprint enrollment notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, times(1)).sendFpEnrollNotification(mContext);
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        // Assert that data has been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(-1.0f);
        // Assert that notification count has been updated.
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(1);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        // Assert that lastFrrNotificationTime has been updated.
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(
                newLastFrrNotificationTime);
    }

    @Test
    @DisableFlags(Flags.FLAG_FRR_DIALOG_IMPROVEMENT)
    public void authenticate_frrExceeded_fpEnrolled_shouldSendFaceNotification() {
        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        BiometricsProtoEnums.MODALITY_FINGERPRINT /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(false);
        when(mClock.millis()).thenReturn(3344L);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that fingerprint enrollment notification should be sent.
        verify(mBiometricNotification, never()).sendCustomizeFpFrrNotification(any());
        verify(mBiometricNotification, times(1)).sendFaceEnrollNotification(mContext);
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data has been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(-1.0f);
        // Assert that notification count has been updated.
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(1);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        // Assert that lastFrrNotificationTime has been updated.
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(3344L);
    }

    @Test
    @EnableFlags(Flags.FLAG_FRR_DIALOG_IMPROVEMENT)
    public void authenticate_frrExceeded_fpEnrolled_shouldSendCustNotification_withFrrFlag() {
        // Use correct modality
        mAuthenticationStatsCollector = new AuthenticationStatsCollector(mContext,
                BiometricsProtoEnums.MODALITY_FINGERPRINT, mBiometricNotification, mClock);

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        BiometricsProtoEnums.MODALITY_FINGERPRINT /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(false);
        long newFrrNotificationTime = 200L + FRR_MINIMAL_DURATION.toMillis() + 1;
        when(mClock.millis()).thenReturn(newFrrNotificationTime);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that fingerprint enrollment notification should be sent.
        verify(mBiometricNotification, times(1)).sendCustomizeFpFrrNotification(mContext);
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data has been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(-1.0f);
        // Assert that notification count has been updated.
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(1);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        // Assert that lastFrrNotificationTime has been updated.
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(
                newFrrNotificationTime);
    }

    @Test
    @EnableFlags(Flags.FLAG_FRR_DIALOG_IMPROVEMENT)
    public void authenticate_frrExceeded_fpEnrolled_shouldSendFaceNotification_withFrrFlag() {
        // Use correct modality
        mAuthenticationStatsCollector = new AuthenticationStatsCollector(mContext,
                BiometricsProtoEnums.MODALITY_FINGERPRINT, mBiometricNotification, mClock);

        mAuthenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        100L /* lastEnrollmentTime */, 200L /* lastFrrNotificationTime */,
                        BiometricsProtoEnums.MODALITY_FINGERPRINT /* modality */));

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFingerprintManager.hasEnrolledTemplates(anyInt())).thenReturn(true);
        when(mFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(false);
        long newFrrNotificationTime = 200L + FRR_MINIMAL_DURATION.toMillis() + 1;
        when(mClock.millis()).thenReturn(newFrrNotificationTime);
        when(mBiometricNotification.sendCustomizeFpFrrNotification(eq(mContext)))
                .thenReturn(false);

        mAuthenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that fingerprint enrollment notification should be sent.
        verify(mBiometricNotification, times(1)).sendCustomizeFpFrrNotification(mContext);
        verify(mBiometricNotification, times(1)).sendFaceEnrollNotification(mContext);
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data has been reset.
        AuthenticationStats authenticationStats = mAuthenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(-1.0f);
        // Assert that notification count has been updated.
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(1);
        assertThat(authenticationStats.getLastEnrollmentTime()).isEqualTo(100L);
        // Assert that lastFrrNotificationTime has been updated.
        assertThat(authenticationStats.getLastFrrNotificationTime()).isEqualTo(
                newFrrNotificationTime);
    }

    @Test
    public void authenticate_featureDisabled_mapMustNotBeUpdated() {
        // Disable the feature.
        when(mResources.getBoolean(eq(R.bool.config_biometricFrrNotificationEnabled)))
                .thenReturn(false);
        AuthenticationStatsCollector authenticationStatsCollector =
                new AuthenticationStatsCollector(mContext, 0 /* modality */,
                        mBiometricNotification, Clock.systemUTC());

        authenticationStatsCollector.setAuthenticationStatsForUser(USER_ID_1,
                new AuthenticationStats(USER_ID_1, 500 /* totalAttempts */,
                        400 /* rejectedAttempts */, 0 /* enrollmentNotifications */,
                        0L /* lastEnrollmentTime */, 0L /* lastFrrNotificationTime */,
                        0 /* modality */));

        authenticationStatsCollector.authenticate(USER_ID_1, false /* authenticated */);

        // Assert that no notification should be sent.
        verify(mBiometricNotification, never()).sendFaceEnrollNotification(any());
        verify(mBiometricNotification, never()).sendFpEnrollNotification(any());
        // Assert that data hasn't been updated.
        AuthenticationStats authenticationStats = authenticationStatsCollector
                .getAuthenticationStatsForUser(USER_ID_1);
        assertThat(authenticationStats.getTotalAttempts()).isEqualTo(500);
        assertThat(authenticationStats.getRejectedAttempts()).isEqualTo(400);
        assertThat(authenticationStats.getEnrollmentNotifications()).isEqualTo(0);
        assertThat(authenticationStats.getFrr()).isWithin(0f).of(0.8f);
    }
}
