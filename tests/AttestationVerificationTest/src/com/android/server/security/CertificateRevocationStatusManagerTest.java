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
package com.android.server.security;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CertificateRevocationStatusManagerTest {

    private static final String TEST_CERTIFICATE_FILE_1 = "test_attestation_with_root_certs.pem";
    private static final String TEST_CERTIFICATE_FILE_2 = "test_attestation_wrong_root_certs.pem";
    private static final String TEST_REMOTE_REVOCATION_LIST_FILE_NAME =
            "test_remote_revocation_list.json";
    private static final String REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST =
            "test_revocation_list_no_test_certs.json";
    private static final String REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST =
            "test_revocation_list_with_test_certs.json";
    private static final String TEST_STORED_REVOCATION_LIST_FILE_NAME =
            "test_stored_revocation_list.json";
    private static final String FILE_URL_PREFIX = "file://";
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private CertificateFactory mFactory;
    private List<X509Certificate> mCertificates1;
    private List<X509Certificate> mCertificates2;
    private File mRemoteRevocationListFile;
    private String mRevocationListUrl;
    private String mNonExistentRevocationListUrl;
    private File mStoredRevocationListFile;
    private CertificateRevocationStatusManager mCertificateRevocationStatusManager;

    @Before
    public void setUp() throws Exception {
        mFactory = CertificateFactory.getInstance("X.509");
        mCertificates1 = getCertificateChain(TEST_CERTIFICATE_FILE_1);
        mCertificates2 = getCertificateChain(TEST_CERTIFICATE_FILE_2);
        mRemoteRevocationListFile =
                new File(mContext.getFilesDir(), TEST_REMOTE_REVOCATION_LIST_FILE_NAME);
        mRevocationListUrl = FILE_URL_PREFIX + mRemoteRevocationListFile.getAbsolutePath();
        File noSuchFile = new File(mContext.getFilesDir(), "file_does_not_exist");
        mNonExistentRevocationListUrl = FILE_URL_PREFIX + noSuchFile.getAbsolutePath();
        mStoredRevocationListFile =
                new File(mContext.getFilesDir(), TEST_STORED_REVOCATION_LIST_FILE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        mRemoteRevocationListFile.delete();
        mStoredRevocationListFile.delete();
    }

    @Test
    public void checkRevocationStatus_doesNotExistOnRemoteRevocationList_noException()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);

        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void checkRevocationStatus_existsOnRemoteRevocationList_throwsException()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void
            checkRevocationStatus_cannotReachRemoteRevocationList_noStoredStatus_throwsException()
                    throws Exception {
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mStoredRevocationListFile, false);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void checkRevocationStatus_savesRevocationStatus() throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);

        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);

        assertThat(mStoredRevocationListFile.length()).isGreaterThan(0);
    }

    @Test
    public void checkRevocationStatus_cannotReachRemoteList_listSaved_noException()
            throws Exception {
        // call checkRevocationStatus once to save the revocation status
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // call checkRevocationStatus again with mNonExistentRevocationListUrl
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mStoredRevocationListFile, false);

        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void checkRevocationStatus_cannotReachRemoteList_storedListTooOld_exception()
            throws Exception {
        // call checkRevocationStatus once to save the revocation status
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // set the last modified date of the stored list to an expired date
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiredListDate =
                now.minusDays(
                        CertificateRevocationStatusManager.MAX_OFFLINE_REVOCATION_LIST_AGE_DAYS
                                + 1);
        mStoredRevocationListFile.setLastModified(
                expiredListDate.toEpochSecond(OffsetDateTime.now().getOffset()) * 1000);
        // call checkRevocationStatus again with mNonExistentRevocationListUrl
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mStoredRevocationListFile, false);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void checkRevocationStatus_cannotReachRemoteList_storedListIsFresh_noException()
            throws Exception {
        // call checkRevocationStatus once to save the revocation status
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // set the last modified date of the stored list to a barely not expired date
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime barelyFreshDate =
                now.minusDays(
                        CertificateRevocationStatusManager.MAX_OFFLINE_REVOCATION_LIST_AGE_DAYS
                                - 1);
        mStoredRevocationListFile.setLastModified(
                barelyFreshDate.toEpochSecond(OffsetDateTime.now().getOffset()) * 1000);
        // call checkRevocationStatus again with mNonExistentRevocationListUrl
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mStoredRevocationListFile, false);

        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void silentlyStoreRevocationList_storesCorrectly() throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);
        byte[] revocationList =
                mCertificateRevocationStatusManager.fetchRemoteRevocationListBytes();

        mCertificateRevocationStatusManager.silentlyStoreRevocationList(revocationList);

        byte[] bytesFromRemoteList;
        byte[] bytesFromStoredList;
        try (FileInputStream remoteListInputStream =
                new FileInputStream(mRemoteRevocationListFile)) {
            bytesFromRemoteList = remoteListInputStream.readAllBytes();
        }
        try (FileInputStream storedListInputStream =
                new FileInputStream(mStoredRevocationListFile)) {
            bytesFromStoredList = storedListInputStream.readAllBytes();
        }
        assertThat(bytesFromStoredList).isEqualTo(bytesFromRemoteList);
    }

    @Test
    public void checkRevocationStatus_recentlyChecked_doesNotFetchRemoteCrl()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // indirectly verifies the remote list is not fetched by simulating a remote revocation
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);

        // no exception
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void checkRevocationStatus_recentlyCheckedAndRevoked_exception()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);
        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void checkRevocationStatus_barelyRecentlyChecked_doesNotFetchRemoteCrl()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // set the last modified date of the stored list to a barely recent date
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime barelyRecentDate =
                now.minusHours(CertificateRevocationStatusManager.NUM_HOURS_BEFORE_NEXT_FETCH - 1);
        mStoredRevocationListFile.setLastModified(
                barelyRecentDate.toEpochSecond(OffsetDateTime.now().getOffset()) * 1000);
        // indirectly verifies the remote list is not fetched by simulating a remote revocation
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);

        // Indirectly verify the remote CRL is not checked by checking there is no exception despite
        // a certificate being revoked. This test differs from the next only in the stored list last
        // modified date, one before the NUM_HOURS_BEFORE_NEXT_FETCH cutoff and one after
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void checkRevocationStatus_certificatesRevokedAfterCheck_throwsException()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mStoredRevocationListFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // set the last modified date of the stored list to a barely not recent date
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime barelyNotRecentDate =
                now.minusHours(CertificateRevocationStatusManager.NUM_HOURS_BEFORE_NEXT_FETCH + 1);
        mStoredRevocationListFile.setLastModified(
                barelyNotRecentDate.toEpochSecond(OffsetDateTime.now().getOffset()) * 1000);
        // simulate a remote revocation
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRemoteRevocationListFile);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    private List<X509Certificate> getCertificateChain(String fileName) throws Exception {
        Collection<? extends Certificate> certificates =
                mFactory.generateCertificates(mContext.getResources().getAssets().open(fileName));
        ArrayList<X509Certificate> x509Certs = new ArrayList<>();
        for (Certificate cert : certificates) {
            x509Certs.add((X509Certificate) cert);
        }
        return x509Certs;
    }

    private void copyFromAssetToFile(String assetFileName, File targetFile) throws Exception {
        byte[] data;
        try (InputStream in = mContext.getResources().getAssets().open(assetFileName)) {
            data = in.readAllBytes();
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
            fileOutputStream.write(data);
        }
    }
}
