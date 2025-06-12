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

package com.android.server.permission.access.permission

import android.Manifest
import android.health.connect.HealthPermissions
import android.os.Build
import android.util.Slog
import com.android.server.permission.access.GetStateScope
import com.android.server.permission.access.MutateStateScope
import com.android.server.permission.access.immutable.* // ktlint-disable no-wildcard-imports
import com.android.server.permission.access.util.andInv
import com.android.server.permission.access.util.hasAnyBit
import com.android.server.permission.access.util.hasBits
import com.android.server.pm.pkg.PackageState

class AppIdPermissionUpgrade(private val policy: AppIdPermissionPolicy) {
    /**
     * Upgrade the package permissions, if needed.
     *
     * @param version package version
     * @see [com.android.server.permission.access.util.PackageVersionMigration.getVersion]
     */
    fun MutateStateScope.upgradePackageState(
        packageState: PackageState,
        userId: Int,
        version: Int,
    ) {
        val packageName = packageState.packageName
        if (version <= 3) {
            Slog.v(
                LOG_TAG,
                "Allowlisting and upgrading background location permission for " +
                    "package: $packageName, version: $version, user:$userId",
            )
            allowlistRestrictedPermissions(packageState, userId)
            upgradeBackgroundLocationPermission(packageState, userId)
        }
        if (version <= 10) {
            Slog.v(
                LOG_TAG,
                "Upgrading access media location permission for package: $packageName" +
                    ", version: $version, user: $userId",
            )
            upgradeAccessMediaLocationPermission(packageState, userId)
        }
        // TODO Enable isAtLeastT check, when moving subsystem to mainline.
        if (version <= 12 /*&& SdkLevel.isAtLeastT()*/) {
            Slog.v(
                LOG_TAG,
                "Upgrading scoped media and body sensor permissions for package: $packageName" +
                    ", version: $version, user: $userId",
            )
            upgradeAuralVisualMediaPermissions(packageState, userId)
            upgradeBodySensorBackgroundPermissions(packageState, userId)
        }
        // TODO Enable isAtLeastU check, when moving subsystem to mainline.
        if (version <= 14 /*&& SdkLevel.isAtLeastU()*/) {
            Slog.v(
                LOG_TAG,
                "Upgrading visual media permission for package: $packageName" +
                    ", version: $version, user: $userId",
            )
            upgradeUserSelectedVisualMediaPermission(packageState, userId)
        }
        // TODO Enable isAtLeastB check, when moving subsystem to mainline.
        if (version <= 16 /*&& SdkLevel.isAtLeastB()*/) {
            Slog.v(
                LOG_TAG,
                "Upgrading body sensor / read heart rate permissions for package: $packageName" +
                    ", version: $version, user: $userId",
            )
            upgradeBodySensorReadHeartRatePermissions(packageState, userId)
        }

        // Add a new upgrade step: if (packageVersion <= LATEST_VERSION) { .... }
        // Also increase LATEST_VERSION
    }

    private fun MutateStateScope.allowlistRestrictedPermissions(
        packageState: PackageState,
        userId: Int,
    ) {
        packageState.androidPackage!!.requestedPermissions.forEach { permissionName ->
            if (permissionName in LEGACY_RESTRICTED_PERMISSIONS) {
                with(policy) {
                    updatePermissionFlags(
                        packageState.appId,
                        userId,
                        permissionName,
                        PermissionFlags.UPGRADE_EXEMPT,
                        PermissionFlags.UPGRADE_EXEMPT,
                    )
                }
            }
        }
    }

    private fun MutateStateScope.upgradeBackgroundLocationPermission(
        packageState: PackageState,
        userId: Int,
    ) {
        if (
            Manifest.permission.ACCESS_BACKGROUND_LOCATION in
                packageState.androidPackage!!.requestedPermissions
        ) {
            val appId = packageState.appId
            val accessFineLocationFlags =
                with(policy) {
                    getPermissionFlags(appId, userId, Manifest.permission.ACCESS_FINE_LOCATION)
                }
            val accessCoarseLocationFlags =
                with(policy) {
                    getPermissionFlags(appId, userId, Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            val isForegroundLocationGranted =
                PermissionFlags.isAppOpGranted(accessFineLocationFlags) ||
                    PermissionFlags.isAppOpGranted(accessCoarseLocationFlags)
            if (isForegroundLocationGranted) {
                grantRuntimePermission(
                    packageState,
                    userId,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                )
            }
        }
    }

    private fun MutateStateScope.upgradeAccessMediaLocationPermission(
        packageState: PackageState,
        userId: Int,
    ) {
        if (
            Manifest.permission.ACCESS_MEDIA_LOCATION in
                packageState.androidPackage!!.requestedPermissions
        ) {
            val flags =
                with(policy) {
                    getPermissionFlags(
                        packageState.appId,
                        userId,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    )
                }
            if (PermissionFlags.isAppOpGranted(flags)) {
                grantRuntimePermission(
                    packageState,
                    userId,
                    Manifest.permission.ACCESS_MEDIA_LOCATION,
                )
            }
        }
    }

    /** Upgrade permissions based on storage permissions grant */
    private fun MutateStateScope.upgradeAuralVisualMediaPermissions(
        packageState: PackageState,
        userId: Int,
    ) {
        val androidPackage = packageState.androidPackage!!
        if (androidPackage.targetSdkVersion < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val requestedPermissionNames = androidPackage.requestedPermissions
        val isStorageUserGranted =
            STORAGE_PERMISSIONS.anyIndexed { _, permissionName ->
                if (permissionName !in requestedPermissionNames) {
                    return@anyIndexed false
                }
                val flags =
                    with(policy) { getPermissionFlags(packageState.appId, userId, permissionName) }
                PermissionFlags.isAppOpGranted(flags) && flags.hasBits(PermissionFlags.USER_SET)
            }
        if (isStorageUserGranted) {
            AURAL_VISUAL_MEDIA_PERMISSIONS.forEachIndexed { _, permissionName ->
                if (permissionName in requestedPermissionNames) {
                    grantRuntimePermission(packageState, userId, permissionName)
                }
            }
        }
    }

    private fun MutateStateScope.upgradeBodySensorBackgroundPermissions(
        packageState: PackageState,
        userId: Int,
    ) {
        if (
            Manifest.permission.BODY_SENSORS_BACKGROUND !in
                packageState.androidPackage!!.requestedPermissions
        ) {
            return
        }

        // Should have been granted when first getting exempt as if the perm was just split
        val appId = packageState.appId
        val backgroundBodySensorsFlags =
            with(policy) {
                getPermissionFlags(appId, userId, Manifest.permission.BODY_SENSORS_BACKGROUND)
            }
        if (backgroundBodySensorsFlags.hasAnyBit(PermissionFlags.MASK_EXEMPT)) {
            return
        }

        // Add Upgrade Exemption - BODY_SENSORS_BACKGROUND is a restricted permission
        with(policy) {
            updatePermissionFlags(
                appId,
                userId,
                Manifest.permission.BODY_SENSORS_BACKGROUND,
                PermissionFlags.UPGRADE_EXEMPT,
                PermissionFlags.UPGRADE_EXEMPT,
            )
        }

        val bodySensorsFlags =
            with(policy) { getPermissionFlags(appId, userId, Manifest.permission.BODY_SENSORS) }
        val isForegroundBodySensorsGranted = PermissionFlags.isAppOpGranted(bodySensorsFlags)
        if (isForegroundBodySensorsGranted) {
            grantRuntimePermission(
                packageState,
                userId,
                Manifest.permission.BODY_SENSORS_BACKGROUND,
            )
        }
    }

    /** Upgrade permission based on the grant in [Manifest.permission_group.READ_MEDIA_VISUAL] */
    private fun MutateStateScope.upgradeUserSelectedVisualMediaPermission(
        packageState: PackageState,
        userId: Int,
    ) {
        val androidPackage = packageState.androidPackage!!
        if (androidPackage.targetSdkVersion < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val requestedPermissionNames = androidPackage.requestedPermissions
        val isVisualMediaUserGranted =
            VISUAL_MEDIA_PERMISSIONS.anyIndexed { _, permissionName ->
                if (permissionName !in requestedPermissionNames) {
                    return@anyIndexed false
                }
                val flags =
                    with(policy) { getPermissionFlags(packageState.appId, userId, permissionName) }
                PermissionFlags.isAppOpGranted(flags) && flags.hasBits(PermissionFlags.USER_SET)
            }
        if (isVisualMediaUserGranted) {
            if (Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED in requestedPermissionNames) {
                grantRuntimePermission(
                    packageState,
                    userId,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
            }
        }
    }

    /**
     * Upgrade permissions based on the body sensors and health permissions status.
     *
     * Starting in BAKLAVA, the BODY_SENSORS and BODY_SENSORS_BACKGROUND permissions are being
     * replaced by the READ_HEART_RATE and READ_HEALTH_DATA_IN_BACKGROUND permissions respectively.
     * To ensure that older apps can continue using BODY_SENSORS without breaking we need to keep
     * their permission state in sync with the new health permissions.
     *
     * The approach we take is to be as conservative as possible. This means if either permission is
     * not granted, then we want to ensure that both end up not granted to force the user to
     * re-grant with the expanded scope.
     */
    private fun MutateStateScope.upgradeBodySensorReadHeartRatePermissions(
        packageState: PackageState,
        userId: Int,
    ) {
        val androidPackage = packageState.androidPackage!!
        if (androidPackage.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
            return
        }

        // First sync BODY_SENSORS and READ_HEART_RATE, if required.
        val isBodySensorsRequested =
            Manifest.permission.BODY_SENSORS in androidPackage.requestedPermissions
        val isReadHeartRateRequested =
            HealthPermissions.READ_HEART_RATE in androidPackage.requestedPermissions
        var isBodySensorsGranted =
            isRuntimePermissionGranted(packageState, userId, Manifest.permission.BODY_SENSORS)
        if (isBodySensorsRequested && isReadHeartRateRequested) {
            val isReadHeartRateGranted =
                isRuntimePermissionGranted(packageState, userId, HealthPermissions.READ_HEART_RATE)
            if (isBodySensorsGranted != isReadHeartRateGranted) {
                if (isBodySensorsGranted) {
                    if (
                        revokeRuntimePermission(
                            packageState,
                            userId,
                            Manifest.permission.BODY_SENSORS,
                        )
                    ) {
                        isBodySensorsGranted = false
                    }
                }
                if (isReadHeartRateGranted) {
                    revokeRuntimePermission(packageState, userId, HealthPermissions.READ_HEART_RATE)
                }
            }
        }

        // Then check to ensure we haven't put the background/foreground permissions out of sync.
        var isBodySensorsBackgroundGranted =
            isRuntimePermissionGranted(
                packageState,
                userId,
                Manifest.permission.BODY_SENSORS_BACKGROUND,
            )
        // Background permission should not be granted without the foreground permission.
        if (!isBodySensorsGranted && isBodySensorsBackgroundGranted) {
            if (
                revokeRuntimePermission(
                    packageState,
                    userId,
                    Manifest.permission.BODY_SENSORS_BACKGROUND,
                )
            ) {
                isBodySensorsBackgroundGranted = false
            }
        }

        // Finally sync BODY_SENSORS_BACKGROUND and READ_HEALTH_DATA_IN_BACKGROUND, if required.
        val isBodySensorsBackgroundRequested =
            Manifest.permission.BODY_SENSORS_BACKGROUND in androidPackage.requestedPermissions
        val isReadHealthDataInBackgroundRequested =
            HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND in androidPackage.requestedPermissions
        if (isBodySensorsBackgroundRequested && isReadHealthDataInBackgroundRequested) {
            val isReadHealthDataInBackgroundGranted =
                isRuntimePermissionGranted(
                    packageState,
                    userId,
                    HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                )
            if (isBodySensorsBackgroundGranted != isReadHealthDataInBackgroundGranted) {
                if (isBodySensorsBackgroundGranted) {
                    revokeRuntimePermission(
                        packageState,
                        userId,
                        Manifest.permission.BODY_SENSORS_BACKGROUND,
                    )
                }
                if (isReadHealthDataInBackgroundGranted) {
                    revokeRuntimePermission(
                        packageState,
                        userId,
                        HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                    )
                }
            }
        }
    }

    private fun GetStateScope.isRuntimePermissionGranted(
        packageState: PackageState,
        userId: Int,
        permissionName: String,
    ): Boolean {
        val permissionFlags =
            with(policy) { getPermissionFlags(packageState.appId, userId, permissionName) }
        return PermissionFlags.isAppOpGranted(permissionFlags)
    }

    private fun MutateStateScope.grantRuntimePermission(
        packageState: PackageState,
        userId: Int,
        permissionName: String,
    ) {
        Slog.v(
            LOG_TAG,
            "Granting runtime permission for package: ${packageState.packageName}, " +
                "permission: $permissionName, userId: $userId",
        )
        val permission = newState.systemState.permissions[permissionName]!!
        if (packageState.getUserStateOrDefault(userId).isInstantApp && !permission.isInstant) {
            return
        }

        val appId = packageState.appId
        var flags = with(policy) { getPermissionFlags(appId, userId, permissionName) }
        if (flags.hasAnyBit(MASK_ANY_FIXED)) {
            Slog.v(
                LOG_TAG,
                "Not allowed to grant $permissionName to package ${packageState.packageName}",
            )
            return
        }

        flags = flags or PermissionFlags.RUNTIME_GRANTED
        flags =
            flags andInv
                (PermissionFlags.APP_OP_REVOKED or
                    PermissionFlags.IMPLICIT or
                    PermissionFlags.LEGACY_GRANTED or
                    PermissionFlags.HIBERNATION or
                    PermissionFlags.ONE_TIME)
        with(policy) { setPermissionFlags(appId, userId, permissionName, flags) }
    }

    /**
     * Revoke a runtime permission for a given user from a given package.
     *
     * @return true if the permission was revoked, false otherwise.
     */
    private fun MutateStateScope.revokeRuntimePermission(
        packageState: PackageState,
        userId: Int,
        permissionName: String,
    ): Boolean {
        Slog.v(
            LOG_TAG,
            "Revoking runtime permission for package: ${packageState.packageName}, " +
                "permission: $permissionName, userId: $userId",
        )

        val appId = packageState.appId
        var flags = with(policy) { getPermissionFlags(appId, userId, permissionName) }
        if (flags.hasAnyBit(MASK_SYSTEM_OR_POLICY_FIXED)) {
            Slog.v(
                LOG_TAG,
                "Cannot revoke fixed runtime permission from package: " +
                    "${packageState.packageName}, permission: $permissionName, userId: $userId",
            )
            return false
        }

        flags =
            flags andInv
                (PermissionFlags.RUNTIME_GRANTED or
                    MASK_USER_SETTABLE or
                    PermissionFlags.PREGRANT or
                    PermissionFlags.ROLE)
        with(policy) { setPermissionFlags(appId, userId, permissionName, flags) }
        return true
    }

    companion object {
        private val LOG_TAG = AppIdPermissionUpgrade::class.java.simpleName

        private const val MASK_ANY_FIXED =
            PermissionFlags.USER_SET or
                PermissionFlags.ONE_TIME or
                PermissionFlags.USER_FIXED or
                PermissionFlags.POLICY_FIXED or
                PermissionFlags.SYSTEM_FIXED

        private const val MASK_SYSTEM_OR_POLICY_FIXED =
            PermissionFlags.SYSTEM_FIXED or PermissionFlags.POLICY_FIXED

        private const val MASK_USER_SETTABLE =
            PermissionFlags.USER_SET or
                PermissionFlags.USER_FIXED or
                PermissionFlags.APP_OP_REVOKED or
                PermissionFlags.ONE_TIME or
                PermissionFlags.HIBERNATION or
                PermissionFlags.USER_SELECTED

        private val LEGACY_RESTRICTED_PERMISSIONS =
            indexedSetOf(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.RECEIVE_WAP_PUSH,
                Manifest.permission.RECEIVE_MMS,
                Manifest.permission.READ_CELL_BROADCASTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.PROCESS_OUTGOING_CALLS,
            )

        private val STORAGE_PERMISSIONS =
            indexedSetOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        private val AURAL_VISUAL_MEDIA_PERMISSIONS =
            indexedSetOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        // Visual media permissions in T
        private val VISUAL_MEDIA_PERMISSIONS =
            indexedSetOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
    }
}
