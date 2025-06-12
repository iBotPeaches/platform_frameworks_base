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

package com.android.internal.os;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.FileDescriptor;
import java.io.IOException;

/** Tests for {@link TimeoutRecord}. */
@SmallTest
@Presubmit
@RunWith(JUnit4.class)
public class ApplicationSharedMemoryTest {

    @Before
    public void setUp() {
        // Skip tests if the feature under test is disabled.
        assumeTrue(Flags.applicationSharedMemoryEnabled());
    }

    /**
     * Every application process, including ours, should have had an instance installed at this
     * point.
     */
    @Test
    public void hasInstance() {
        // This shouldn't throw and shouldn't return null.
        assertNotNull(ApplicationSharedMemory.getInstance());
    }

    /** Any app process should be able to read shared memory values. */
    @Test
    public void canRead() {
        ApplicationSharedMemory instance = ApplicationSharedMemory.getInstance();
        try {
            instance.getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis();
            // Don't actually care about the value of the above.
        } catch (java.time.DateTimeException e) {
            // This exception is okay during testing.  It means there was no time source, which
            // could be because of network problems or a feature being flagged off.
        }
    }

    /** Application processes should not have mutable access. */
    @Test
    public void appInstanceNotMutable() {
        ApplicationSharedMemory instance = ApplicationSharedMemory.getInstance();
        try {
            instance.setLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis(17);
            fail("Attempted mutation in an app process should throw");
            instance.writeSystemFeaturesCache(new int[] {1, 2, 3, 4, 5});
            fail("Attempted feature mutation in an app process should throw");
        } catch (Exception expected) {
        }
    }

    /** Instances share memory if they share the underlying memory region. */
    @Test
    public void instancesShareMemory() throws IOException {
        ApplicationSharedMemory instance1 = ApplicationSharedMemory.create();
        ApplicationSharedMemory instance2 =
                ApplicationSharedMemory.fromFileDescriptor(
                        instance1.getFileDescriptor(), /* mutable= */ true);
        ApplicationSharedMemory instance3 =
                ApplicationSharedMemory.fromFileDescriptor(
                        instance2.getReadOnlyFileDescriptor(), /* mutable= */ false);

        instance1.setLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis(17);
        assertEquals(
                17, instance1.getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis());
        assertEquals(
                17, instance2.getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis());
        assertEquals(
                17, instance3.getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis());

        instance2.setLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis(24);
        assertEquals(
                24, instance1.getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis());
        assertEquals(
                24, instance2.getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis());
        assertEquals(
                24, instance3.getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis());
    }

    /** Can't map read-only memory as mutable. */
    @Test
    public void readOnlyCantBeMutable() throws IOException {
        ApplicationSharedMemory readWriteInstance = ApplicationSharedMemory.create();
        FileDescriptor readOnlyFileDescriptor = readWriteInstance.getReadOnlyFileDescriptor();

        try {
            ApplicationSharedMemory.fromFileDescriptor(readOnlyFileDescriptor, /* mutable= */ true);
            fail("Shouldn't be able to map read-only memory as mutable");
        } catch (Exception expected) {
        }
    }

    /** If system feature caching is enabled, it should be auto-written into app shared memory. */
    @Test
    public void canReadSystemFeatures() throws IOException {
        assumeTrue(android.content.pm.Flags.cacheSdkSystemFeatures());
        ApplicationSharedMemory instance = ApplicationSharedMemory.getInstance();
        assertThat(instance.readSystemFeaturesCache()).isNotEmpty();
    }

    @Test
    public void systemFeaturesShareMemory() throws IOException {
        ApplicationSharedMemory instance1 = ApplicationSharedMemory.create();

        int[] featureVersions = new int[] {1, 2, 3, 4, 5};
        instance1.writeSystemFeaturesCache(featureVersions);
        assertThat(featureVersions).isEqualTo(instance1.readSystemFeaturesCache());

        ApplicationSharedMemory instance2 =
                ApplicationSharedMemory.fromFileDescriptor(
                        instance1.getReadOnlyFileDescriptor(), /* mutable= */ false);
        assertThat(featureVersions).isEqualTo(instance2.readSystemFeaturesCache());
    }

    @Test
    public void systemFeaturesAreWriteOnce() throws IOException {
        ApplicationSharedMemory instance1 = ApplicationSharedMemory.create();

        try {
            instance1.writeSystemFeaturesCache(new int[5000]);
            fail("Cannot write an overly large system feature version buffer.");
        } catch (IllegalArgumentException expected) {
        }

        int[] featureVersions = new int[] {1, 2, 3, 4, 5};
        instance1.writeSystemFeaturesCache(featureVersions);

        int[] newFeatureVersions = new int[] {1, 2, 3, 4, 5, 6, 7};
        try {
            instance1.writeSystemFeaturesCache(newFeatureVersions);
            fail("Cannot update system features after first write.");
        } catch (IllegalStateException expected) {
        }

        ApplicationSharedMemory instance2 =
                ApplicationSharedMemory.fromFileDescriptor(
                        instance1.getReadOnlyFileDescriptor(), /* mutable= */ false);
        try {
            instance2.writeSystemFeaturesCache(newFeatureVersions);
            fail("Cannot update system features for read-only ashmem.");
        } catch (IllegalStateException expected) {
        }
    }
}
