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
package android.app.backup;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.backup.BackupRestoreEventLogger.DataTypeResult;
import android.os.Bundle;
import android.os.Parcel;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

import java.util.Map;

public final class DataTypeResultTest {

    @Rule
    public final Expect expect = Expect.create();

    @Test
    public void testGetters_defaultConstructorFields() {
        var result = new DataTypeResult("The Type is Bond, James Bond!");

        expect.withMessage("getDataType()").that(result.getDataType())
                .isEqualTo("The Type is Bond, James Bond!");
        expect.withMessage("getSuccessCount()").that(result.getSuccessCount()).isEqualTo(0);
        expect.withMessage("getFailCount()").that(result.getFailCount()).isEqualTo(0);
        expect.withMessage("getErrorsCount()").that(result.getErrors()).isEmpty();
        expect.withMessage("getMetadataHash()").that(result.getMetadataHash()).isNull();
        expect.withMessage("describeContents()").that(result.describeContents()).isEqualTo(0);
    }

    @Test
    public void testGetters_allFields() {
        DataTypeResult result = createDataTypeResult("The Type is Bond, James Bond!",
                /* successCount= */ 42, /* failCount= */ 108, Map.of("D'OH!", 666),
                new byte[] { 4, 8, 15, 16, 23, 42 });

        expect.withMessage("getDataType()").that(result.getDataType())
                .isEqualTo("The Type is Bond, James Bond!");
        expect.withMessage("getSuccessCount()").that(result.getSuccessCount()).isEqualTo(42);
        expect.withMessage("getFailCount()").that(result.getFailCount()).isEqualTo(108);
        expect.withMessage("getErrorsCount()").that(result.getErrors()).containsExactly("D'OH!",
                666);
        expect.withMessage("getMetadataHash()").that(result.getMetadataHash()).asList()
                .containsExactly((byte) 4, (byte) 8, (byte) 15, (byte) 16, (byte) 23, (byte) 42)
                .inOrder();
        expect.withMessage("describeContents()").that(result.describeContents()).isEqualTo(0);
    }

    @Test
    public void testParcelMethods() {
        DataTypeResult original = createDataTypeResult("The Type is Bond, James Bond!",
                /* successCount= */ 42, /* failCount= */ 108, Map.of("D'OH!", 666),
                new byte[] { 4, 8, 15, 16, 23, 42 });
        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, /* flags= */ 0);

            parcel.setDataPosition(0);
            var clone = DataTypeResult.CREATOR.createFromParcel(parcel);
            assertWithMessage("createFromParcel()").that(clone).isNotNull();

            expect.withMessage("getDataType()").that(clone.getDataType())
                    .isEqualTo(original.getDataType());
            expect.withMessage("getSuccessCount()").that(clone.getSuccessCount())
                    .isEqualTo(original.getSuccessCount());
            expect.withMessage("getFailCount()").that(clone.getFailCount())
                    .isEqualTo(original.getFailCount());
            expect.withMessage("getErrorsCount()").that(clone.getErrors())
                    .containsExactlyEntriesIn(original.getErrors()).inOrder();
            expect.withMessage("getMetadataHash()").that(clone.getMetadataHash())
                    .isEqualTo(original.getMetadataHash());
            expect.withMessage("describeContents()").that(clone.describeContents()).isEqualTo(0);
        } finally {
            parcel.recycle();
        }
    }

    static DataTypeResult createDataTypeResult(String dataType, int successCount, int failCount,
            Map<String, Integer> errors, byte... metadataHash) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeString(dataType);
            parcel.writeInt(successCount);
            parcel.writeInt(failCount);
            Bundle errorsBundle = new Bundle();
            errors.entrySet()
                    .forEach(entry -> errorsBundle.putInt(entry.getKey(), entry.getValue()));
            parcel.writeBundle(errorsBundle);
            parcel.writeByteArray(metadataHash);

            parcel.setDataPosition(0);
            var result = DataTypeResult.CREATOR.createFromParcel(parcel);
            assertWithMessage("createFromParcel()").that(result).isNotNull();
            return result;
        } finally {
            parcel.recycle();
        }
    }
}
