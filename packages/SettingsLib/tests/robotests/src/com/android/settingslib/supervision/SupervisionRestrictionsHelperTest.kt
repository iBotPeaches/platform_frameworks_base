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
 * limitations under the License
 */

package com.android.settingslib.supervision

import android.app.admin.DeviceAdminReceiver
import android.app.supervision.SupervisionManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Unit tests for [SupervisionRestrictionsHelper].
 *
 * Run with `atest SupervisionRestrictionsHelperTest`.
 */
@RunWith(AndroidJUnit4::class)
class SupervisionRestrictionsHelperTest {
    @get:Rule val mocks: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var mockPackageManager: PackageManager

    @Mock private lateinit var mockSupervisionManager: SupervisionManager

    private lateinit var context: Context

    @Before
    fun setUp() {
        context =
            object : ContextWrapper(InstrumentationRegistry.getInstrumentation().context) {
                override fun getPackageManager() = mockPackageManager

                override fun getSystemService(name: String) =
                    when (name) {
                        Context.SUPERVISION_SERVICE -> mockSupervisionManager
                        else -> super.getSystemService(name)
                    }
            }
    }

    @Test
    fun createEnforcedAdmin_nullSupervisionPackage() {
        `when`(mockSupervisionManager.activeSupervisionAppPackage).thenReturn(null)

        val enforcedAdmin =
            SupervisionRestrictionsHelper.createEnforcedAdmin(context, RESTRICTION, USER_HANDLE)

        assertThat(enforcedAdmin).isNull()
    }

    @Test
    fun createEnforcedAdmin_supervisionAppService() {
        val resolveInfo =
            ResolveInfo().apply {
                serviceInfo =
                    ServiceInfo().apply {
                        packageName = SUPERVISION_APP_PACKAGE
                        name = "service.class"
                    }
            }

        `when`(mockSupervisionManager.activeSupervisionAppPackage)
            .thenReturn(SUPERVISION_APP_PACKAGE)
        `when`(
                mockPackageManager.queryIntentServicesAsUser(
                    argThat(hasAction("android.app.action.BIND_SUPERVISION_APP_SERVICE")),
                    anyInt(),
                    eq(USER_ID),
                )
            )
            .thenReturn(listOf(resolveInfo))

        val enforcedAdmin =
            SupervisionRestrictionsHelper.createEnforcedAdmin(context, RESTRICTION, USER_HANDLE)

        assertThat(enforcedAdmin).isNotNull()
        assertThat(enforcedAdmin!!.component).isEqualTo(resolveInfo.serviceInfo.componentName)
        assertThat(enforcedAdmin.enforcedRestriction).isEqualTo(RESTRICTION)
        assertThat(enforcedAdmin.user).isEqualTo(USER_HANDLE)
    }

    @Test
    fun createEnforcedAdmin_profileOwnerReceiver() {
        val resolveInfo =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = SUPERVISION_APP_PACKAGE
                        name = "service.class"
                    }
            }

        `when`(mockSupervisionManager.activeSupervisionAppPackage)
            .thenReturn(SUPERVISION_APP_PACKAGE)
        `when`(mockPackageManager.queryIntentServicesAsUser(any<Intent>(), anyInt(), eq(USER_ID)))
            .thenReturn(emptyList<ResolveInfo>())
        `when`(
                mockPackageManager.queryBroadcastReceiversAsUser(
                    argThat(hasAction(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED)),
                    anyInt(),
                    eq(USER_ID),
                )
            )
            .thenReturn(listOf(resolveInfo))

        val enforcedAdmin =
            SupervisionRestrictionsHelper.createEnforcedAdmin(context, RESTRICTION, USER_HANDLE)

        assertThat(enforcedAdmin).isNotNull()
        assertThat(enforcedAdmin!!.component).isEqualTo(resolveInfo.activityInfo.componentName)
        assertThat(enforcedAdmin.enforcedRestriction).isEqualTo(RESTRICTION)
        assertThat(enforcedAdmin.user).isEqualTo(USER_HANDLE)
    }

    @Test
    fun createEnforcedAdmin_noSupervisionComponent() {
        `when`(mockSupervisionManager.activeSupervisionAppPackage)
            .thenReturn(SUPERVISION_APP_PACKAGE)
        `when`(mockPackageManager.queryIntentServicesAsUser(any<Intent>(), anyInt(), anyInt()))
            .thenReturn(emptyList<ResolveInfo>())
        `when`(mockPackageManager.queryBroadcastReceiversAsUser(any<Intent>(), anyInt(), anyInt()))
            .thenReturn(emptyList<ResolveInfo>())

        val enforcedAdmin =
            SupervisionRestrictionsHelper.createEnforcedAdmin(context, RESTRICTION, USER_HANDLE)

        assertThat(enforcedAdmin).isNotNull()
        assertThat(enforcedAdmin!!.component).isNull()
        assertThat(enforcedAdmin.enforcedRestriction).isEqualTo(RESTRICTION)
        assertThat(enforcedAdmin.user).isEqualTo(USER_HANDLE)
    }

    private fun hasAction(action: String) =
        object : ArgumentMatcher<Intent> {
            override fun matches(intent: Intent?) = intent?.action == action
        }

    private companion object {
        const val SUPERVISION_APP_PACKAGE = "app.supervision"
        const val RESTRICTION = "restriction"
        val USER_HANDLE = UserHandle.CURRENT
        val USER_ID = USER_HANDLE.identifier
    }
}
