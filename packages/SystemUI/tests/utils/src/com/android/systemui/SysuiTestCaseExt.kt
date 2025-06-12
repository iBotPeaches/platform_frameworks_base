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

package com.android.systemui

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.useStandardTestDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher

/**
 * This definition, which uses standard dispatcher, is eventually going away.
 *
 * If you are calling this method, and want the new default behavior, call `testKosmosNew`, and you
 * will be migrated to the new behavior (unconfined dispatcher). If you want to maintain the old
 * behavior, directly call testKosmosNew().useStandardTestDispatcher().
 *
 * The migration will proceed in multiple steps:
 * 1. All calls to testKosmos will be converted to testKosmosLegacy, maybe over several CLs.
 * 2. When there are zero references to testKosmos, it will be briefly deleted
 * 3. A new testKosmos will be introduced that uses unconfined test dispatcher
 * 4. All callers to testKosmosNew that have been introduced since step 1 will be migrated to this
 *    new definition of testKosmos
 * 5. testKosmosNew will be deleted
 * 6. Over time, test authors will be encouraged to migrate away from testKosmosLegacy
 *
 * For details, see go/thetiger
 */
// TODO(b/342622417)
fun SysuiTestCase.testKosmos(): Kosmos = testKosmosLegacy()

/**
 * Create a new Kosmos instance using the unconfined test dispatcher. See migration notes on
 * [testKosmos]
 */
fun SysuiTestCase.testKosmosNew(): Kosmos =
    Kosmos().apply { testCase = this@testKosmosNew }.useUnconfinedTestDispatcher()

/**
 * This should not be called directly. Instead, you can use:
 * - testKosmosNew().useStandardTestDispatcher() to explicitly choose the standard dispatcher
 * - testKosmosNew() to explicitly choose the unconfined dispatcher (which is the new sysui default)
 *
 * For details, see go/thetiger
 */
@Deprecated("Do not call this directly.  Use testKosmos() with dispatcher functions if needed.")
fun SysuiTestCase.testKosmosLegacy(): Kosmos =
    Kosmos().useStandardTestDispatcher().apply { testCase = this@testKosmosLegacy }

/** Run [f] on the main thread and return its result once completed. */
fun <T : Any> SysuiTestCase.runOnMainThreadAndWaitForIdleSync(f: () -> T): T {
    lateinit var result: T
    context.mainExecutor.execute { result = f() }
    waitForIdleSync()
    return result
}
