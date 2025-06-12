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
package com.android.hoststubgen

import com.android.hoststubgen.utils.ArgIterator
import com.android.hoststubgen.utils.IntSetOnce
import com.android.hoststubgen.utils.SetOnce
import com.android.hoststubgen.utils.ensureFileExists

/**
 * Options that can be set from command line arguments.
 */
class HostStubGenOptions(
    /** Input jar file*/
    var inJar: SetOnce<String> = SetOnce(""),

    /** Output jar file */
    var outJar: SetOnce<String?> = SetOnce(null),

    var inputJarDumpFile: SetOnce<String?> = SetOnce(null),

    var inputJarAsKeepAllFile: SetOnce<String?> = SetOnce(null),

    var cleanUpOnError: SetOnce<Boolean> = SetOnce(false),

    var statsFile: SetOnce<String?> = SetOnce(null),

    var apiListFile: SetOnce<String?> = SetOnce(null),

    var numShards: IntSetOnce = IntSetOnce(1),
    var shard: IntSetOnce = IntSetOnce(0),
) : HostStubGenClassProcessorOptions() {

    override fun checkArgs() {
        if (!inJar.isSet) {
            throw ArgumentsException("Required option missing: --in-jar")
        }
        if (!outJar.isSet) {
            log.w("--out-jar is not set. $executableName will not generate jar files.")
        }
        if (numShards.isSet != shard.isSet) {
            throw ArgumentsException("--num-shards and --shard-index must be used together")
        }

        if (numShards.isSet) {
            if (shard.get >= numShards.get) {
                throw ArgumentsException("--shard-index must be smaller than --num-shards")
            }
        }
    }

    override fun parseOption(option: String, args: ArgIterator): Boolean {
        // Define some shorthands...
        fun nextArg(): String = args.nextArgRequired(option)

        when (option) {
            // TODO: Write help
            "-h", "--help" -> TODO("Help is not implemented yet")

            "--in-jar" -> inJar.set(nextArg()).ensureFileExists()
            // We support both arguments because some AOSP dependencies
            // still use the old argument
            "--out-jar", "--out-impl-jar" -> outJar.set(nextArg())

            "--clean-up-on-error" -> cleanUpOnError.set(true)
            "--no-clean-up-on-error" -> cleanUpOnError.set(false)

            "--gen-input-dump-file" -> inputJarDumpFile.set(nextArg())
            "--gen-keep-all-file" -> inputJarAsKeepAllFile.set(nextArg())

            "--stats-file" -> statsFile.set(nextArg())
            "--supported-api-list-file" -> apiListFile.set(nextArg())

            "--num-shards" -> numShards.set(nextArg()).also {
                if (it < 1) {
                    throw ArgumentsException("$option must be positive integer")
                }
            }
            "--shard-index" -> shard.set(nextArg()).also {
                if (it < 0) {
                    throw ArgumentsException("$option must be positive integer or zero")
                }
            }

            else -> return super.parseOption(option, args)
        }

        return true
    }

    override fun dumpFields(): String {
        return """
            inJar=$inJar,
            outJar=$outJar,
            inputJarDumpFile=$inputJarDumpFile,
            inputJarAsKeepAllFile=$inputJarAsKeepAllFile,
            cleanUpOnError=$cleanUpOnError,
            statsFile=$statsFile,
            apiListFile=$apiListFile,
            numShards=$numShards,
            shard=$shard,
        """.trimIndent() + '\n' + super.dumpFields()
    }
}
