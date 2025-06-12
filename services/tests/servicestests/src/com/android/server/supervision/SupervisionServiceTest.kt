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
 * limitations under the License
 */

package com.android.server.supervision

import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManagerInternal
import android.app.supervision.flags.Flags
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.content.pm.UserInfo.FLAG_FOR_TESTING
import android.content.pm.UserInfo.FLAG_FULL
import android.content.pm.UserInfo.FLAG_MAIN
import android.content.pm.UserInfo.FLAG_SYSTEM
import android.os.Handler
import android.os.PersistableBundle
import android.os.UserHandle
import android.os.UserHandle.MIN_SECONDARY_USER_ID
import android.os.UserHandle.USER_SYSTEM
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.R
import com.android.server.LocalServices
import com.android.server.SystemService.TargetUser
import com.android.server.pm.UserManagerInternal
import com.android.server.supervision.SupervisionService.ACTION_CONFIRM_SUPERVISION_CREDENTIALS
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SupervisionService].
 *
 * Run with `atest SupervisionServiceTest`.
 */
@RunWith(AndroidJUnit4::class)
class SupervisionServiceTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val mocks: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var mockDpmInternal: DevicePolicyManagerInternal

    @Mock
    private lateinit var mockKeyguardManager: KeyguardManager
    @Mock private lateinit var mockPackageManager: PackageManager
    @Mock private lateinit var mockUserManagerInternal: UserManagerInternal

    private lateinit var context: Context
    private lateinit var lifecycle: SupervisionService.Lifecycle
    private lateinit var service: SupervisionService

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().context
        context = SupervisionContextWrapper(context, mockKeyguardManager, mockPackageManager)

        LocalServices.removeServiceForTest(DevicePolicyManagerInternal::class.java)
        LocalServices.addService(DevicePolicyManagerInternal::class.java, mockDpmInternal)

        LocalServices.removeServiceForTest(UserManagerInternal::class.java)
        LocalServices.addService(UserManagerInternal::class.java, mockUserManagerInternal)

        service = SupervisionService(context)
        lifecycle = SupervisionService.Lifecycle(context, service)
        lifecycle.registerProfileOwnerListener()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_supervisionAppIsProfileOwner_enablesSupervision() {
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName(systemSupervisionPackage, "MainActivity"))

        simulateUserStarting(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID))
            .isEqualTo(systemSupervisionPackage)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_legacyProfileOwnerComponent_enablesSupervision() {
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(supervisionProfileOwnerComponent)

        simulateUserStarting(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID))
            .isEqualTo(supervisionProfileOwnerComponent.packageName)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_userPreCreated_doesNotEnableSupervision() {
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName(systemSupervisionPackage, "MainActivity"))

        simulateUserStarting(USER_ID, preCreated = true)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID)).isNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_supervisionAppIsNotProfileOwner_doesNotEnableSupervision() {
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName("other.package", "MainActivity"))

        simulateUserStarting(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID)).isNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun profileOwnerChanged_supervisionAppIsProfileOwner_enablesSupervision() {
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName(systemSupervisionPackage, "MainActivity"))

        broadcastProfileOwnerChanged(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID))
            .isEqualTo(systemSupervisionPackage)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun profileOwnerChanged_legacyProfileOwnerComponent_enablesSupervision() {
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(supervisionProfileOwnerComponent)

        broadcastProfileOwnerChanged(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID))
            .isEqualTo(supervisionProfileOwnerComponent.packageName)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun profileOwnerChanged_supervisionAppIsNotProfileOwner_doesNotDisableSupervision() {
        service.mInternal.setSupervisionEnabledForUser(USER_ID, true)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName("other.package", "MainActivity"))

        broadcastProfileOwnerChanged(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun profileOwnerChanged_supervisionAppIsNotProfileOwner_doesNotEnableSupervision() {
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName("other.package", "MainActivity"))

        broadcastProfileOwnerChanged(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID)).isNull()
    }

    @Test
    fun isActiveSupervisionApp_supervisionUid_supervisionEnabled_returnsTrue() {
        whenever(mockPackageManager.getPackagesForUid(APP_UID))
            .thenReturn(arrayOf(systemSupervisionPackage))
        service.setSupervisionEnabledForUser(USER_ID, true)

        assertThat(service.mInternal.isActiveSupervisionApp(APP_UID)).isTrue()
    }

    @Test
    fun isActiveSupervisionApp_supervisionUid_supervisionNotEnabled_returnsFalse() {
        whenever(mockPackageManager.getPackagesForUid(APP_UID))
            .thenReturn(arrayOf(systemSupervisionPackage))
        service.setSupervisionEnabledForUser(USER_ID, false)

        assertThat(service.mInternal.isActiveSupervisionApp(APP_UID)).isFalse()
    }

    @Test
    fun isActiveSupervisionApp_notSupervisionUid_returnsFalse() {
        whenever(mockPackageManager.getPackagesForUid(APP_UID)).thenReturn(arrayOf())

        assertThat(service.mInternal.isActiveSupervisionApp(APP_UID)).isFalse()
    }

    @Test
    fun setSupervisionEnabledForUser() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()

        service.setSupervisionEnabledForUser(USER_ID, true)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()

        service.setSupervisionEnabledForUser(USER_ID, false)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
    }

    @Test
    fun setSupervisionEnabledForUser_internal() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()

        service.mInternal.setSupervisionEnabledForUser(USER_ID, true)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()

        service.mInternal.setSupervisionEnabledForUser(USER_ID, false)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
    }

    @Test
    fun setSupervisionLockscreenEnabledForUser() {
        var userData = service.getUserDataLocked(USER_ID)
        assertThat(userData.supervisionLockScreenEnabled).isFalse()
        assertThat(userData.supervisionLockScreenOptions).isNull()

        service.mInternal.setSupervisionLockscreenEnabledForUser(USER_ID, true, PersistableBundle())
        userData = service.getUserDataLocked(USER_ID)
        assertThat(userData.supervisionLockScreenEnabled).isTrue()
        assertThat(userData.supervisionLockScreenOptions).isNotNull()

        service.mInternal.setSupervisionLockscreenEnabledForUser(USER_ID, false, null)
        userData = service.getUserDataLocked(USER_ID)
        assertThat(userData.supervisionLockScreenEnabled).isFalse()
        assertThat(userData.supervisionLockScreenOptions).isNull()
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(true)

        val intent = checkNotNull(service.createConfirmSupervisionCredentialsIntent())
        assertThat(intent.action).isEqualTo(ACTION_CONFIRM_SUPERVISION_CREDENTIALS)
        assertThat(intent.getPackage()).isEqualTo("com.android.settings")
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_supervisionNotEnabled_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), false)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(true)

        assertThat(service.createConfirmSupervisionCredentialsIntent()).isNull()
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_noSupervisingUser_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(UserHandle.USER_NULL)

        assertThat(service.createConfirmSupervisionCredentialsIntent()).isNull()
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_supervisingUserMissingSecureLock_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(false)

        assertThat(service.createConfirmSupervisionCredentialsIntent()).isNull()
    }

    fun shouldAllowBypassingSupervisionRoleQualification_returnsTrue() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

        addDefaultAndTestUsers()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()
    }

    @Test
    fun shouldAllowBypassingSupervisionRoleQualification_returnsFalse() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

        addDefaultAndTestUsers()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

        // Enabling supervision on any user will disallow bypassing
        service.setSupervisionEnabledForUser(USER_ID, true)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isFalse()

        // Adding non-default users should also disallow bypassing
        addDefaultAndFullUsers()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isFalse()

        // Turning off supervision with non-default users should still disallow bypassing
        service.setSupervisionEnabledForUser(USER_ID, false)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
    }

    private val systemSupervisionPackage: String
        get() = context.getResources().getString(R.string.config_systemSupervision)

    private val supervisionProfileOwnerComponent: ComponentName
        get() =
            context
                .getResources()
                .getString(R.string.config_defaultSupervisionProfileOwnerComponent)
                .let(ComponentName::unflattenFromString)!!

    private fun simulateUserStarting(userId: Int, preCreated: Boolean = false) {
        val userInfo = UserInfo(userId, /* name= */ "tempUser", /* flags= */ 0)
        userInfo.preCreated = preCreated
        lifecycle.onUserStarting(TargetUser(userInfo))
    }

    private fun broadcastProfileOwnerChanged(userId: Int) {
        val intent = Intent(DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED)
        context.sendBroadcastAsUser(intent, UserHandle.of(userId))
    }

    private fun addDefaultAndTestUsers() {
        val userInfos = userData.map { (userId, flags) ->
            UserInfo(userId, "user" + userId, USER_ICON, flags, USER_TYPE)
        }
        whenever(mockUserManagerInternal.getUsers(any())).thenReturn(userInfos)
    }

    private fun addDefaultAndFullUsers() {
        val userInfos = userData.map { (userId, flags) ->
            UserInfo(userId, "user" + userId, USER_ICON, flags, USER_TYPE)
        } + UserInfo(USER_ID, "user" + USER_ID, USER_ICON, FLAG_FULL, USER_TYPE)
        whenever(mockUserManagerInternal.getUsers(any())).thenReturn(userInfos)
    }

    private companion object {
        const val USER_ID = 100
        const val APP_UID = USER_ID * UserHandle.PER_USER_RANGE
        const val SUPERVISING_USER_ID = 10
        const val USER_ICON = "user_icon"
        const val USER_TYPE = "fake_user_type"
        val userData: Map<Int, Int> = mapOf(
            USER_SYSTEM to FLAG_SYSTEM,
            MIN_SECONDARY_USER_ID to FLAG_MAIN,
            (MIN_SECONDARY_USER_ID + 1) to (FLAG_FULL or FLAG_FOR_TESTING)
        )
    }
}

/**
 * A context wrapper that allows broadcast intents to immediately invoke the receivers without
 * performing checks on the sending user.
 */
private class SupervisionContextWrapper(
    val context: Context,
    val keyguardManager: KeyguardManager,
    val pkgManager: PackageManager,
) : ContextWrapper(context) {
    val interceptors = mutableListOf<Pair<BroadcastReceiver, IntentFilter>>()

    override fun getSystemService(name: String): Any =
        when (name) {
            Context.KEYGUARD_SERVICE -> keyguardManager
            else -> super.getSystemService(name)
        }

    override fun getPackageManager() = pkgManager

    override fun registerReceiverForAllUsers(
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: Handler?,
    ): Intent? {
        if (receiver != null) {
            interceptors.add(Pair(receiver, filter))
        }
        return null
    }

    override fun sendBroadcastAsUser(intent: Intent, user: UserHandle) {
        val pendingResult =
            BroadcastReceiver.PendingResult(
                Activity.RESULT_OK,
                /* resultData= */ "",
                /* resultExtras= */ null,
                /* type= */ 0,
                /* ordered= */ true,
                /* sticky= */ false,
                /* token= */ null,
                user.identifier,
                /* flags= */ 0,
            )
        for ((receiver, filter) in interceptors) {
            if (filter.match(contentResolver, intent, false, "") > 0) {
                receiver.setPendingResult(pendingResult)
                receiver.onReceive(context, intent)
            }
        }
    }
}
