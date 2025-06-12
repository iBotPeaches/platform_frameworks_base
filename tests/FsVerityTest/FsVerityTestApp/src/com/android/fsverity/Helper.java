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

package com.android.fsverity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.os.Bundle;
import android.security.FileIntegrityManager;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Rule;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Test helper that works with the host-side test to set up a test file, and to verify fs-verity
 * verification is done expectedly.
 */
public class Helper {
    private static final String TAG = "FsVerityTest";

    private static final String FILENAME = "test.file";

    @Rule
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    android.Manifest.permission.SETUP_FSVERITY);

    @Test
    public void prepareTest() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Bundle testArgs = InstrumentationRegistry.getArguments();

        String basename = testArgs.getString("basename");
        context.deleteFile(basename);

        assertThat(testArgs).isNotNull();
        int fileSize = Integer.parseInt(testArgs.getString("fileSize"));
        Log.d(TAG, "Preparing test file with size " + fileSize);

        byte[] bytes = new byte[8192];
        Arrays.fill(bytes, (byte) '1');
        try (FileOutputStream os = context.openFileOutput(basename, Context.MODE_PRIVATE)) {
            for (int i = 0; i < fileSize; i += bytes.length) {
                if (i + bytes.length > fileSize) {
                    os.write(bytes, 0, fileSize % bytes.length);
                } else {
                    os.write(bytes);
                }
            }
        }

        // Enable fs-verity
        FileIntegrityManager fim = context.getSystemService(FileIntegrityManager.class);
        fim.setupFsVerity(context.getFileStreamPath(basename));
    }

    private static long getPageSize() {
        String arch = System.getProperty("os.arch");
        Log.d(TAG, "os.arch=" + arch);
        if ("x86_64".equals(arch)) {
            // Override the fake 16K page size from cf_x86_64_pgagnostic.  The real page size on
            // x86_64 is always 4K.  This test needs the real page size because it is testing I/O
            // error reporting behavior that is dependent on the real page size.
            return 4096;
        }
        return Os.sysconf(OsConstants._SC_PAGE_SIZE);
    }

    @Test
    public void verifyFileRead() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        Bundle testArgs = InstrumentationRegistry.getArguments();
        assertThat(testArgs).isNotNull();
        String filePath = testArgs.getString("filePath");
        String csv = testArgs.getString("brokenByteIndicesCsv");
        Log.d(TAG, "brokenByteIndicesCsv: " + csv);

        // Build the set of pages that contain a corrupted byte.
        final long pageSize = getPageSize();
        Set<Long> corruptedPageIndices = new HashSet();
        for (String s : csv.split(",")) {
            long byteIndex = Long.parseLong(s);
            long pageIndex = byteIndex / pageSize;
            corruptedPageIndices.add(pageIndex);
        }
        Log.d(TAG, "corruptedPageIndices=" + corruptedPageIndices);

        // Read bytes from the file and verify the expected result based on the containing page.
        // (The kernel reports fs-verity errors at page granularity.)
        final long stride = 1024;
        // Using a stride that is a divisor of the page size ensures that the last page is tested.
        assertThat(pageSize % stride).isEqualTo(0);
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            for (long byteIndex = 0; byteIndex < file.length(); byteIndex += stride) {
                file.seek(byteIndex);
                long pageIndex = byteIndex / pageSize;
                if (corruptedPageIndices.contains(pageIndex)) {
                    Log.d(TAG, "Expecting read at byte #" + byteIndex + " to fail");
                    assertThrows(IOException.class, () -> file.read());
                } else {
                    Log.d(TAG, "Expecting read at byte #" + byteIndex + " to succeed");
                    assertThat(file.readByte()).isEqualTo('1');
                }
            }
        }
    }
}
