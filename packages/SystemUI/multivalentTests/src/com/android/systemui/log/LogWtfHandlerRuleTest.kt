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

package com.android.systemui.log

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
@SmallTest
class LogWtfHandlerRuleTest : SysuiTestCase() {

    val underTest = LogWtfHandlerRule()

    @Test
    fun passingTestWithoutWtf_shouldPass() {
        val result = runTestCodeWithRule {
            Log.e(TAG, "just an error", IndexOutOfBoundsException())
        }
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun passingTestWithWtf_shouldFail() {
        val result = runTestCodeWithRule {
            Log.wtf(TAG, "some terrible failure", IllegalStateException())
        }
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(AssertionError::class.java)
        assertThat(exception?.cause).isInstanceOf(Log.TerribleFailure::class.java)
        assertThat(exception?.cause?.cause).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun failingTestWithoutWtf_shouldFail() {
        val result = runTestCodeWithRule {
            Log.e(TAG, "just an error", IndexOutOfBoundsException())
            throw NullPointerException("some npe")
        }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun failingTestWithWtf_shouldFail() {
        val result = runTestCodeWithRule {
            Log.wtf(TAG, "some terrible failure", IllegalStateException())
            throw NullPointerException("some npe")
        }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NullPointerException::class.java)
        val suppressedExceptions = result.exceptionOrNull()!!.suppressedExceptions
        assertThat(suppressedExceptions).hasSize(1)
        val suppressed = suppressedExceptions.first()
        assertThat(suppressed).isInstanceOf(AssertionError::class.java)
        assertThat(suppressed.cause).isInstanceOf(Log.TerribleFailure::class.java)
        assertThat(suppressed.cause?.cause).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun passingTestWithExemptWtf_shouldPass() {
        underTest.addFailureLogExemption { it.tag == TAG_EXPECTED }
        val result = runTestCodeWithRule {
            Log.wtf(TAG_EXPECTED, "some expected failure", IllegalStateException())
        }
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun failingTestWithExemptWtf_shouldFail() {
        underTest.addFailureLogExemption { it.tag == TAG_EXPECTED }
        val result = runTestCodeWithRule {
            Log.wtf(TAG_EXPECTED, "some expected failure", IllegalStateException())
            throw NullPointerException("some npe")
        }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NullPointerException::class.java)
        val suppressedExceptions = result.exceptionOrNull()!!.suppressedExceptions
        assertThat(suppressedExceptions).isEmpty()
    }

    @Test
    fun passingTestWithOneExemptWtfOfTwo_shouldFail() {
        underTest.addFailureLogExemption { it.tag == TAG_EXPECTED }
        val result = runTestCodeWithRule {
            Log.wtf(TAG_EXPECTED, "some expected failure", IllegalStateException())
            Log.wtf(TAG, "some terrible failure", IllegalStateException())
        }
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(AssertionError::class.java)
        assertThat(exception?.cause).isInstanceOf(Log.TerribleFailure::class.java)
        assertThat(exception?.cause?.cause).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun failingTestWithOneExemptWtfOfTwo_shouldFail() {
        underTest.addFailureLogExemption { it.tag == TAG_EXPECTED }
        val result = runTestCodeWithRule {
            Log.wtf(TAG_EXPECTED, "some expected failure", IllegalStateException())
            Log.wtf(TAG, "some terrible failure", IllegalStateException())
            throw NullPointerException("some npe")
        }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NullPointerException::class.java)
        val suppressedExceptions = result.exceptionOrNull()!!.suppressedExceptions
        assertThat(suppressedExceptions).hasSize(1)
        val suppressed = suppressedExceptions.first()
        assertThat(suppressed).isInstanceOf(AssertionError::class.java)
        assertThat(suppressed.cause).isInstanceOf(Log.TerribleFailure::class.java)
        assertThat(suppressed.cause?.cause).isInstanceOf(IllegalStateException::class.java)
    }

    private fun runTestCodeWithRule(testCode: () -> Unit): Result<Unit> {
        val testCodeStatement =
            object : Statement() {
                override fun evaluate() {
                    testCode()
                }
            }
        val wrappedTest = underTest.apply(testCodeStatement, mock())
        return try {
            wrappedTest.evaluate()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    companion object {
        const val TAG = "LogWtfHandlerRuleTest"
        const val TAG_EXPECTED = "EXPECTED"
    }
}
