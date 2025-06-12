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
@file:JvmName("RavenizerMain")

package com.android.platform.test.ravenwood.ravenizer

import com.android.hoststubgen.LogLevel
import com.android.hoststubgen.executableName
import com.android.hoststubgen.log
import com.android.hoststubgen.runMainWithBoilerplate
import com.android.hoststubgen.utils.JAR_RESOURCE_PREFIX
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * If this file exits, we also read options from it. This is "unsafe" because it could break
 * incremental builds, if it sets any flag that affects the output file.
 * (however, for now, there's no such options.)
 *
 * For example, to enable verbose logging, do `echo '-v' > ~/.raveniezr-unsafe`
 *
 * (but even the content of this file changes, soong won't rerun the command, so you need to
 * remove the output first and then do a build again.)
 */
private val RAVENIZER_DOTFILE = System.getenv("HOME") + "/.ravenizer-unsafe"

/**
 * This is the name of the standard option text file embedded inside ravenizer.jar.
 */
private const val RAVENIZER_STANDARD_OPTIONS = "texts/ravenizer-standard-options.txt"

/**
 * Entry point.
 */
fun main(args: Array<String>) {
    executableName = "Ravenizer"
    log.setConsoleLogLevel(LogLevel.Info)

    runMainWithBoilerplate {
        val newArgs = args.toMutableList()
        newArgs.add(0, "@$JAR_RESOURCE_PREFIX$RAVENIZER_STANDARD_OPTIONS")

        if (Paths.get(RAVENIZER_DOTFILE).exists()) {
            log.i("Reading options from $RAVENIZER_DOTFILE")
            newArgs.add(0, "@$RAVENIZER_DOTFILE")
        }

        val options = RavenizerOptions().apply { parseArgs(newArgs) }

        log.i("$executableName started")
        log.v("Options: $options")

        // Run.
        Ravenizer().run(options)
    }
}
