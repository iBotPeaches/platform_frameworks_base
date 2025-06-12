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

import java.lang.Exception

open class CodeProcessingException(
    message: String, context: ParsingContext, cause: Throwable? = null
) : Exception("Code processing error in ${context.filePath}:${context.lineNumber}:\n" +
        "  $message", cause)

class HashCollisionException(
    message: String, context: ParsingContext, cause: Throwable? = null
) : CodeProcessingException(message, context, cause)

class IllegalImportException(message: String, context: ParsingContext, cause: Throwable? = null) :
        CodeProcessingException("Illegal import: $message", context, cause)

class InvalidProtoLogCallException(
    message: String, context: ParsingContext, cause: Throwable? = null
) : CodeProcessingException("InvalidProtoLogCall: $message", context, cause)

class ParsingException(message: String, context: ParsingContext, cause: Throwable? = null) :
    CodeProcessingException(message, context, cause)

class InvalidViewerConfigException(message: String) : Exception(message)

class InvalidInputException(message: String) : Exception(message)

class InvalidCommandException(message: String) : Exception(message)
