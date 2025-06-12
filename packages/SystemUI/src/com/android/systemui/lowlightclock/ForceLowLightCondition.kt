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
package com.android.systemui.lowlightclock

import android.text.TextUtils
import android.util.Log
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.condition.Condition
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * This condition registers for and fulfills cmd shell commands to force a device into or out of
 * low-light conditions.
 */
class ForceLowLightCondition
@Inject
constructor(@Background scope: CoroutineScope, commandRegistry: CommandRegistry) :
    Condition(scope, null, true) {
    /**
     * Default Constructor.
     *
     * @param commandRegistry command registry to register commands with.
     */
    init {
        if (DEBUG) {
            Log.d(TAG, "registering commands")
        }
        commandRegistry.registerCommand(COMMAND_ROOT) {
            object : Command {
                override fun execute(pw: PrintWriter, args: List<String>) {
                    if (args.size != 1) {
                        pw.println("no command specified")
                        help(pw)
                        return
                    }

                    val cmd = args[0]

                    if (TextUtils.equals(cmd, COMMAND_ENABLE_LOW_LIGHT)) {
                        logAndPrint(pw, "forcing low light")
                        updateCondition(true)
                    } else if (TextUtils.equals(cmd, COMMAND_DISABLE_LOW_LIGHT)) {
                        logAndPrint(pw, "forcing to not enter low light")
                        updateCondition(false)
                    } else if (TextUtils.equals(cmd, COMMAND_CLEAR_LOW_LIGHT)) {
                        logAndPrint(pw, "clearing any forced low light")
                        clearCondition()
                    } else {
                        pw.println("invalid command")
                        help(pw)
                    }
                }

                override fun help(pw: PrintWriter) {
                    pw.println("Usage: adb shell cmd statusbar low-light <cmd>")
                    pw.println("Supported commands:")
                    pw.println("  - enable")
                    pw.println("    forces device into low-light")
                    pw.println("  - disable")
                    pw.println("    forces device to not enter low-light")
                    pw.println("  - clear")
                    pw.println("    clears any previously forced state")
                }

                private fun logAndPrint(pw: PrintWriter, message: String) {
                    pw.println(message)
                    if (DEBUG) {
                        Log.d(TAG, message)
                    }
                }
            }
        }
    }

    override suspend fun start() {}

    override fun stop() {}

    override val startStrategy: Int
        get() = START_EAGERLY

    companion object {
        /** Command root */
        const val COMMAND_ROOT: String = "low-light"

        /** Command for forcing device into low light. */
        const val COMMAND_ENABLE_LOW_LIGHT: String = "enable"

        /** Command for preventing a device from entering low light. */
        const val COMMAND_DISABLE_LOW_LIGHT: String = "disable"

        /** Command for clearing previously forced low-light conditions. */
        const val COMMAND_CLEAR_LOW_LIGHT: String = "clear"

        private const val TAG = "ForceLowLightCondition"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }
}
