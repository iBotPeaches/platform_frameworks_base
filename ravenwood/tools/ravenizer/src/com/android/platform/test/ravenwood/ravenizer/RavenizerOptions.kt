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
package com.android.platform.test.ravenwood.ravenizer

import com.android.hoststubgen.ArgumentsException
import com.android.hoststubgen.HostStubGenClassProcessorOptions
import com.android.hoststubgen.utils.ArgIterator
import com.android.hoststubgen.utils.SetOnce
import com.android.hoststubgen.utils.ensureFileExists

class RavenizerOptions(
    /** Input jar file*/
    var inJar: SetOnce<String> = SetOnce(""),

    /** Output jar file */
    var outJar: SetOnce<String> = SetOnce(""),

    /** Whether to enable test validation. */
    var enableValidation: SetOnce<Boolean> = SetOnce(true),

    /** Whether the validation failure is fatal or not. */
    var fatalValidation: SetOnce<Boolean> = SetOnce(true),

    /** Whether to remove mockito and dexmaker classes. */
    var stripMockito: SetOnce<Boolean> = SetOnce(false),
) : HostStubGenClassProcessorOptions() {

    override fun parseOption(option: String, args: ArgIterator): Boolean {
        fun nextArg(): String = args.nextArgRequired(option)

        when (option) {
            // TODO: Write help
            "-h", "--help" -> TODO("Help is not implemented yet")

            "--in-jar" -> inJar.set(nextArg()).ensureFileExists()
            "--out-jar" -> outJar.set(nextArg())

            "--enable-validation" -> enableValidation.set(true)
            "--disable-validation" -> enableValidation.set(false)

            "--fatal-validation" -> fatalValidation.set(true)
            "--no-fatal-validation" -> fatalValidation.set(false)

            "--strip-mockito" -> stripMockito.set(true)
            "--no-strip-mockito" -> stripMockito.set(false)

            else -> return super.parseOption(option, args)
        }

        return true
    }

    override fun checkArgs() {
        if (!inJar.isSet) {
            throw ArgumentsException("Required option missing: --in-jar")
        }
        if (!outJar.isSet) {
            throw ArgumentsException("Required option missing: --out-jar")
        }
    }

    override fun dumpFields(): String {
        return """
            inJar=$inJar,
            outJar=$outJar,
            enableValidation=$enableValidation,
            fatalValidation=$fatalValidation,
            stripMockito=$stripMockito,
        """.trimIndent() + '\n' + super.dumpFields()
    }
}
