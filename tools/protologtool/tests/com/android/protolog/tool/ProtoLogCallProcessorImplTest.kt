/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.protolog.tool

import com.android.internal.protolog.common.InvalidFormatStringException
import com.android.internal.protolog.common.LogLevel
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.expr.MethodCallExpr
import org.junit.Assert.assertEquals
import org.junit.Test
import com.google.common.truth.Truth

class ProtoLogCallProcessorImplTest {
    private data class LogCall(
        val call: MethodCallExpr,
        val messageString: String,
        val level: LogLevel,
        val group: LogGroup
    )

    private val groupMap: MutableMap<String, LogGroup> = mutableMapOf()
    private val calls: MutableList<LogCall> = mutableListOf()
    private val visitor = ProtoLogCallProcessorImpl(
        "org.example.ProtoLog",
        "org.example.ProtoLogGroup",
            groupMap
    )
    private val processor = object : ProtoLogCallVisitor {
        override fun processCall(
            call: MethodCallExpr,
            messageString: String,
            level: LogLevel,
            group: LogGroup,
            lineNumber: Int,
        ) {
            calls.add(LogCall(call, messageString, level, group))
        }
    }

    private fun checkCalls() {
        assertEquals(1, calls.size)
        val c = calls[0]
        assertEquals("test %b", c.messageString)
        assertEquals(groupMap["TEST"], c.group)
        assertEquals(LogLevel.DEBUG, c.level)
    }

    @Test
    fun process_samePackage() {
        val code = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.d(ProtoLogGroup.TEST, "test %b", true);
                    ProtoLog.e(ProtoLogGroup.ERROR, "error %d", 1);
                }
            }
        """
        groupMap["TEST"] = LogGroup("TEST", true, false, "WindowManager")
        groupMap["ERROR"] = LogGroup("ERROR", true, true, "WindowManagerERROR")
        visitor.process(StaticJavaParser.parse(code), processor, "")
        assertEquals(2, calls.size)
        var c = calls[0]
        assertEquals("test %b", c.messageString)
        assertEquals(groupMap["TEST"], c.group)
        assertEquals(LogLevel.DEBUG, c.level)
        c = calls[1]
        assertEquals("error %d", c.messageString)
        assertEquals(groupMap["ERROR"], c.group)
        assertEquals(LogLevel.ERROR, c.level)
    }

    @Test
    fun process_imported() {
        val code = """
            package org.example2;

            import org.example.ProtoLog;
            import org.example.ProtoLogGroup;

            class Test {
                void test() {
                    ProtoLog.d(ProtoLogGroup.TEST, "test %b", true);
                }
            }
        """
        groupMap["TEST"] = LogGroup("TEST", true, true, "WindowManager")
        visitor.process(StaticJavaParser.parse(code), processor, "")
        checkCalls()
    }

    @Test
    fun process_importedStatic() {
        val code = """
            package org.example2;

            import static org.example.ProtoLog.d;
            import static org.example.ProtoLogGroup.TEST;

            class Test {
                void test() {
                    d(TEST, "test %b", true);
                }
            }
        """
        groupMap["TEST"] = LogGroup("TEST", true, true, "WindowManager")
        visitor.process(StaticJavaParser.parse(code), processor, "")
        checkCalls()
    }

    @Test
    fun process_groupNotImported() {
        val code = """
            package org.example2;

            import org.example.ProtoLog;

            class Test {
                void test() {
                    ProtoLog.d(ProtoLogGroup.TEST, "test %b", true);
                }
            }
        """
        groupMap["TEST"] = LogGroup("TEST", true, true, "WindowManager")
        val errors = visitor.process(StaticJavaParser.parse(code), processor, "")

        Truth.assertThat(errors).hasSize(1)
        Truth.assertThat(errors.first()).isInstanceOf(InvalidProtoLogCallException::class.java)
    }

    @Test
    fun process_protoLogNotImported() {
        val code = """
            package org.example2;

            import org.example.ProtoLogGroup;

            class Test {
                void test() {
                    ProtoLog.d(ProtoLogGroup.TEST, "test %b", true);
                }
            }
        """
        groupMap["TEST"] = LogGroup("TEST", true, true, "WindowManager")
        visitor.process(StaticJavaParser.parse(code), processor, "")
        assertEquals(0, calls.size)
    }

    @Test
    fun process_unknownGroup() {
        val code = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.d(ProtoLogGroup.TEST, "test %b", true);
                }
            }
        """
        val errors = visitor.process(StaticJavaParser.parse(code), processor, "")

        Truth.assertThat(errors).hasSize(1)
        Truth.assertThat(errors.first()).isInstanceOf(InvalidProtoLogCallException::class.java)
    }

    @Test
    fun process_staticGroup() {
        val code = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.d(TEST, "test %b", true);
                }
            }
        """
        val errors = visitor.process(StaticJavaParser.parse(code), processor, "")

        Truth.assertThat(errors).hasSize(1)
        Truth.assertThat(errors.first()).isInstanceOf(InvalidProtoLogCallException::class.java)
    }

    @Test
    fun process_badGroup() {
        val code = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.d(0, "test %b", true);
                }
            }
        """
        val errors = visitor.process(StaticJavaParser.parse(code), processor, "")

        Truth.assertThat(errors).hasSize(1)
        Truth.assertThat(errors.first()).isInstanceOf(InvalidProtoLogCallException::class.java)
    }

    @Test
    fun process_invalidSignature() {
        val code = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.d("test");
                }
            }
        """
        val errors = visitor.process(StaticJavaParser.parse(code), processor, "")

        Truth.assertThat(errors).hasSize(1)
        Truth.assertThat(errors.first()).isInstanceOf(InvalidProtoLogCallException::class.java)
    }

    @Test
    fun process_disabled() {
        // Disabled groups are also processed.
        val code = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.d(ProtoLogGroup.TEST, "test %b", true);
                }
            }
        """
        groupMap["TEST"] = LogGroup("TEST", false, true, "WindowManager")
        visitor.process(StaticJavaParser.parse(code), processor, "")
        checkCalls()
    }

    @Test
    fun throws_clear_error_message_on_invalid_format_exception() {
        val code = """
            package org.example;

            class Test {
                void test() {
                    ProtoLog.d(ProtoLogGroup.TEST, "Invalid message %9 %");
                }
            }
        """
        groupMap["TEST"] = LogGroup("TEST", false, true, "WindowManager")

        val processor = object : ProtoLogCallVisitor {
            override fun processCall(
                call: MethodCallExpr,
                messageString: String,
                level: LogLevel,
                group: LogGroup,
                lineNumber: Int,
            ) {
                throw InvalidFormatStringException("Invalid Protolog message format")
            }
        }

        val errors = visitor.process(StaticJavaParser.parse(code), processor, "MyTestFile.java")
        Truth.assertThat(errors).hasSize(1)

        val exception = errors.first()
        Truth.assertThat(exception).hasMessageThat()
            .contains("Code processing error in MyTestFile.java:6")
        Truth.assertThat(exception.cause).hasMessageThat()
            .contains("Invalid Protolog message format")
    }
}
