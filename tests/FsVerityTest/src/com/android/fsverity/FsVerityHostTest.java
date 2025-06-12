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

import android.platform.test.annotations.RootPermissionTest;

import com.android.blockdevicewriter.BlockDeviceWriter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test verifies fs-verity works end-to-end. There is a corresponding helper app.
 *
 * <p>The helper app uses a FileIntegrityManager API to enable fs-verity on a file. The host test
 * here tampers with the file's backing storage, then tells the helper app to read and expect
 * success/failure on read.
 *
 * <p>Since the filesystem by design provides no way to corrupt fs-verity files itself, the test
 * needs to bypass the filesystem and write directly to the block device to corrupt the files.
 */
@RootPermissionTest
@RunWith(DeviceJUnit4ClassRunner.class)
public class FsVerityHostTest extends BaseHostJUnit4Test {
    private static final String TARGET_PACKAGE = "com.android.fsverity";

    private static final String BASENAME = "test.file";

    @Test
    public void testFsVeritySmallFile() throws Exception {
        prepareTest(10000);

        ITestDevice device = getDevice();
        BlockDeviceWriter.damageFileAgainstBlockDevice(device, getTargetFilePath(), 0);
        BlockDeviceWriter.damageFileAgainstBlockDevice(device, getTargetFilePath(), 8192);
        BlockDeviceWriter.dropCaches(device);

        verifyRead(getTargetFilePath(), "0,8192");
    }

    @Test
    public void testFsVerityLargerFileWithOneMoreMerkleTreeLevel() throws Exception {
        prepareTest(128 * 4096 + 1);

        ITestDevice device = getDevice();
        BlockDeviceWriter.damageFileAgainstBlockDevice(device, getTargetFilePath(), 4096);
        BlockDeviceWriter.damageFileAgainstBlockDevice(device, getTargetFilePath(), 100 * 4096);
        BlockDeviceWriter.damageFileAgainstBlockDevice(device, getTargetFilePath(), 128 * 4096 + 1);
        BlockDeviceWriter.dropCaches(device);

        verifyRead(getTargetFilePath(), "4096,409600,524289");
    }

    private String getTargetFilePath() throws DeviceNotAvailableException {
        return "/data/user/" + getDevice().getCurrentUser() + "/" + TARGET_PACKAGE + "/files/"
                + BASENAME;
    }

    private void prepareTest(int fileSize) throws Exception {
        DeviceTestRunOptions options = new DeviceTestRunOptions(TARGET_PACKAGE);
        options.setTestClassName(TARGET_PACKAGE + ".Helper");
        options.setTestMethodName("prepareTest");
        options.addInstrumentationArg("basename", BASENAME);
        options.addInstrumentationArg("fileSize", String.valueOf(fileSize));
        assertThat(runDeviceTests(options)).isTrue();
    }

    /**
     * Verifies the read success/failure expectation given the corrupted byte indices in the file.
     *
     * @param path the remote file path to read.
     * @param indicesCsv a comma-separated list of indices of bytes that were corrupted.
     */
    private void verifyRead(String path, String indicesCsv) throws Exception {
        DeviceTestRunOptions options = new DeviceTestRunOptions(TARGET_PACKAGE);
        options.setTestClassName(TARGET_PACKAGE + ".Helper");
        options.setTestMethodName("verifyFileRead");
        options.addInstrumentationArg("brokenByteIndicesCsv", indicesCsv);
        options.addInstrumentationArg("filePath", getTargetFilePath());
        assertThat(runDeviceTests(options)).isTrue();
    }
}
