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
package com.android.platform.test.ravenwood.ravenhelper.sourcemap

import com.android.hoststubgen.ArgumentsException
import com.android.hoststubgen.utils.ArgIterator
import com.android.hoststubgen.utils.BaseOptions
import com.android.hoststubgen.utils.SetOnce
import com.android.hoststubgen.utils.ensureFileExists

/**
 * Options for the "ravenhelper map" subcommand.
 */
class MapOptions(
    /** Source files or directories. */
    var sourceFilesOrDirectories: MutableList<String> = mutableListOf(),

    /** Files containing target methods */
    var targetMethodFiles: MutableList<String> = mutableListOf(),

    /** Output script file. */
    var outputScriptFile: SetOnce<String?> = SetOnce(null),

    /** Text to insert. */
    var text: SetOnce<String?> = SetOnce(null),
) : BaseOptions() {

    override fun parseOption(option: String, args: ArgIterator): Boolean {
        fun nextArg(): String = args.nextArgRequired(option)

        when (option) {
            // TODO: Write help
            "-h", "--help" -> TODO("Help is not implemented yet")
            "-s", "--src" -> sourceFilesOrDirectories.add(nextArg().ensureFileExists())
            "-i", "--input" -> targetMethodFiles.add(nextArg().ensureFileExists())
            "-o", "--output-script" -> outputScriptFile.set(nextArg())
            "-t", "--text" -> text.set(nextArg())
            else -> return false
        }

        return true
    }

    override fun checkArgs() {
        if (sourceFilesOrDirectories.size == 0) {
            throw ArgumentsException("Must specify at least one source path")
        }
    }

    override fun dumpFields(): String {
        return """
            sourceFilesOrDirectories=$sourceFilesOrDirectories
            targetMethods=$targetMethodFiles
            outputScriptFile=$outputScriptFile
            text=$text
        """.trimIndent()
    }
}
