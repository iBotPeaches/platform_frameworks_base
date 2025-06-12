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
package com.android.platform.test.ravenwood.ravenhelper.policytoannot

import com.android.hoststubgen.ArgumentsException
import com.android.hoststubgen.utils.ArgIterator
import com.android.hoststubgen.utils.BaseOptions
import com.android.hoststubgen.utils.SetOnce
import com.android.hoststubgen.utils.ensureFileExists

/**
 * Options for the "ravenhelper pta" subcommand.
 */
class PtaOptions(
    /** Text policy files */
    var policyOverrideFiles: MutableList<String> = mutableListOf(),

    /** Annotation allowed list file. */
    var annotationAllowedClassesFile: SetOnce<String?> = SetOnce(null),

    /** Source files or directories. */
    var sourceFilesOrDirectories: MutableList<String> = mutableListOf(),

    /** Output script file. */
    var outputScriptFile: SetOnce<String?> = SetOnce(null),

    /** Dump the operations (for debugging) */
    var dumpOperations: SetOnce<Boolean> = SetOnce(false),
) : BaseOptions() {

    override fun parseOption(option: String, args: ArgIterator): Boolean {
        fun nextArg(): String = args.nextArgRequired(option)

        when (option) {
            // TODO: Write help
            "-h", "--help" -> TODO("Help is not implemented yet")

            "-p", "--policy-override-file" ->
                policyOverrideFiles.add(nextArg().ensureFileExists())

            "-a", "--annotation-allowed-classes-file" ->
                annotationAllowedClassesFile.set(nextArg().ensureFileExists())

            "-s", "--src" -> sourceFilesOrDirectories.add(nextArg().ensureFileExists())
            "--dump" -> dumpOperations.set(true)
            "-o", "--output-script" -> outputScriptFile.set(nextArg())

            else -> return false
        }

        return true
    }

    override fun checkArgs() {
        if (policyOverrideFiles.size == 0) {
            throw ArgumentsException("Must specify at least one policy file")
        }

        if (sourceFilesOrDirectories.size == 0) {
            throw ArgumentsException("Must specify at least one source path")
        }
    }

    override fun dumpFields(): String {
        return """
            policyOverrideFiles=$policyOverrideFiles
            annotationAllowedClassesFile=$annotationAllowedClassesFile
            sourceFilesOrDirectories=$sourceFilesOrDirectories
            outputScriptFile=$outputScriptFile
            dumpOperations=$dumpOperations
        """.trimIndent()
    }
}
