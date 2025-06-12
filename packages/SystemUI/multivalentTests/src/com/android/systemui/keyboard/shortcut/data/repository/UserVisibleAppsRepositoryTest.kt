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

package com.android.systemui.keyboard.shortcut.data.repository

import android.content.pm.UserInfo
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyboard.shortcut.fakeLauncherApps
import com.android.systemui.keyboard.shortcut.userVisibleAppsRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class UserVisibleAppsRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val fakeLauncherApps = kosmos.fakeLauncherApps
    private val repo = kosmos.userVisibleAppsRepository
    private val userTracker = kosmos.fakeUserTracker
    private val testScope = kosmos.testScope
    private val userVisibleAppsContainsApplication:
        (pkgName: String, clsName: String) -> Flow<Boolean> =
        { pkgName, clsName ->
            repo.userVisibleApps.map { userVisibleApps ->
                userVisibleApps.any {
                    it.componentName.packageName == pkgName && it.componentName.className == clsName
                }
            }
        }

    @Before
    fun setup() {
        switchUser(index = PRIMARY_USER_INDEX)
    }

    @Test
    fun userVisibleApps_emitsUpdatedAppsList_onNewAppInstalled() {
        testScope.runTest {
            val containsPackageOne by
                collectLastValue(userVisibleAppsContainsApplication(TEST_PACKAGE_1, TEST_CLASS_1))

            installPackageOneForUserOne()

            assertThat(containsPackageOne).isTrue()
        }
    }

    @Test
    fun userVisibleApps_emitsUpdatedAppsList_onAppUserChanged() {
        testScope.runTest {
            val containsPackageOne by
                collectLastValue(userVisibleAppsContainsApplication(TEST_PACKAGE_1, TEST_CLASS_1))
            val containsPackageTwo by
                collectLastValue(userVisibleAppsContainsApplication(TEST_PACKAGE_2, TEST_CLASS_2))

            installPackageOneForUserOne()
            installPackageTwoForUserTwo()

            assertThat(containsPackageOne).isTrue()
            assertThat(containsPackageTwo).isFalse()

            switchUser(index = SECONDARY_USER_INDEX)

            assertThat(containsPackageOne).isFalse()
            assertThat(containsPackageTwo).isTrue()
        }
    }

    @Test
    fun userVisibleApps_emitsUpdatedAppsList_onAppUninstalled() {
        testScope.runTest {
            val containsPackageOne by
                collectLastValue(userVisibleAppsContainsApplication(TEST_PACKAGE_1, TEST_CLASS_1))

            installPackageOneForUserOne()
            uninstallPackageOneForUserOne()

            assertThat(containsPackageOne).isFalse()
        }
    }

    private fun switchUser(index: Int) {
        userTracker.set(
            userInfos =
                listOf(
                    UserInfo(/* id= */ PRIMARY_USER_ID, /* name= */ "Primary User", /* flags= */ 0),
                    UserInfo(
                        /* id= */ SECONDARY_USER_ID,
                        /* name= */ "Secondary User",
                        /* flags= */ 0,
                    ),
                ),
            selectedUserIndex = index,
        )
    }

    private fun installPackageOneForUserOne() {
        fakeLauncherApps.installPackageForUser(
            TEST_PACKAGE_1,
            TEST_CLASS_1,
            UserHandle(/* userId= */ PRIMARY_USER_ID),
        )
    }

    private fun uninstallPackageOneForUserOne() {
        fakeLauncherApps.uninstallPackageForUser(
            TEST_PACKAGE_1,
            TEST_CLASS_1,
            UserHandle(/* userId= */ PRIMARY_USER_ID),
        )
    }

    private fun installPackageTwoForUserTwo() {
        fakeLauncherApps.installPackageForUser(
            TEST_PACKAGE_2,
            TEST_CLASS_2,
            UserHandle(/* userId= */ SECONDARY_USER_ID),
        )
    }

    companion object {
        const val TEST_PACKAGE_1 = "test.package.one"
        const val TEST_PACKAGE_2 = "test.package.two"
        const val TEST_CLASS_1 = "TestClassOne"
        const val TEST_CLASS_2 = "TestClassTwo"
        const val PRIMARY_USER_ID = 10
        const val PRIMARY_USER_INDEX = 0
        const val SECONDARY_USER_ID = 11
        const val SECONDARY_USER_INDEX = 1
    }
}
