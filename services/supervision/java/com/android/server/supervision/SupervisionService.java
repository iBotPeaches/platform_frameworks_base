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

package com.android.server.supervision;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_ROLE_HOLDERS;
import static android.Manifest.permission.MANAGE_USERS;
import static android.Manifest.permission.QUERY_USERS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.internal.util.Preconditions.checkCallAuthorization;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.supervision.ISupervisionManager;
import android.app.supervision.SupervisionManagerInternal;
import android.app.supervision.flags.Flags;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.pm.UserManagerInternal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/** Service for handling system supervision. */
public class SupervisionService extends ISupervisionManager.Stub {
    private static final String LOG_TAG = "SupervisionService";

    /**
     * Activity action: Requests user confirmation of supervision credentials.
     *
     * <p>Use {@link Activity#startActivityForResult} to launch this activity. The result will be
     * {@link Activity#RESULT_OK} if credentials are valid.
     *
     * <p>If supervision credentials are not configured, this action initiates the setup flow.
     */
    @VisibleForTesting
    static final String ACTION_CONFIRM_SUPERVISION_CREDENTIALS =
            "android.app.supervision.action.CONFIRM_SUPERVISION_CREDENTIALS";

    // TODO(b/362756788): Does this need to be a LockGuard lock?
    private final Object mLockDoNoUseDirectly = new Object();

    @GuardedBy("getLockObject()")
    private final SparseArray<SupervisionUserData> mUserData = new SparseArray<>();

    private final Context mContext;
    private final Injector mInjector;
    final SupervisionManagerInternal mInternal = new SupervisionManagerInternalImpl();

    public SupervisionService(Context context) {
        mContext = context.createAttributionContext(LOG_TAG);
        mInjector = new Injector(context);
        mInjector.getUserManagerInternal().addUserLifecycleListener(new UserLifecycleListener());
    }

    /**
     * Returns whether supervision is enabled for the given user.
     *
     * <p>Supervision is automatically enabled when the supervision app becomes the profile owner or
     * explicitly enabled via an internal call to {@link #setSupervisionEnabledForUser}.
     */
    @Override
    public boolean isSupervisionEnabledForUser(@UserIdInt int userId) {
        enforceAnyPermission(QUERY_USERS, MANAGE_USERS);
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }
        synchronized (getLockObject()) {
            return getUserDataLocked(userId).supervisionEnabled;
        }
    }

    @Override
    public void setSupervisionEnabledForUser(@UserIdInt int userId, boolean enabled) {
        // TODO(b/395630828): Ensure that this method can only be called by the system.
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }
        setSupervisionEnabledForUserInternal(userId, enabled, getSystemSupervisionPackage());
    }

    /**
     * Returns the package name of the active supervision app or null if supervision is disabled.
     */
    @Override
    @Nullable
    public String getActiveSupervisionAppPackage(@UserIdInt int userId) {
        if (UserHandle.getUserId(Binder.getCallingUid()) != userId) {
            enforcePermission(INTERACT_ACROSS_USERS);
        }
        synchronized (getLockObject()) {
            return getUserDataLocked(userId).supervisionAppPackage;
        }
    }

    /**
     * Creates an {@link Intent} that can be used with {@link Context#startActivity(Intent)} to
     * launch the activity to verify supervision credentials.
     *
     * <p>A valid {@link Intent} is always returned if supervision is enabled at the time this
     * method is called, the launched activity still need to perform validity checks as the
     * supervision state can change when it's launched. A null intent is returned if supervision is
     * disabled at the time of this method call.
     *
     * <p>A result code of {@link android.app.Activity#RESULT_OK} indicates successful verification
     * of the supervision credentials.
     */
    @Override
    @Nullable
    public Intent createConfirmSupervisionCredentialsIntent() {
        enforceAnyPermission(QUERY_USERS, MANAGE_USERS);
        if (!isSupervisionEnabledForUser(mContext.getUserId())) {
            return null;
        }
        // Verify the supervising user profile exists and has a secure credential set.
        final int supervisingUserId = mInjector.getUserManagerInternal().getSupervisingProfileId();
        final long token = Binder.clearCallingIdentity();
        try {
            if (supervisingUserId == UserHandle.USER_NULL
                    || !mInjector.getKeyguardManager().isDeviceSecure(supervisingUserId)) {
                return null;
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        final Intent intent = new Intent(ACTION_CONFIRM_SUPERVISION_CREDENTIALS);
        // explicitly set the package for security
        intent.setPackage("com.android.settings");

        return intent;
    }

    @Override
    public boolean shouldAllowBypassingSupervisionRoleQualification() {
        enforcePermission(MANAGE_ROLE_HOLDERS);

        if (hasNonTestDefaultUsers()) {
            return false;
        }

        synchronized (getLockObject()) {
            for (int i = 0; i < mUserData.size(); i++) {
                if (mUserData.valueAt(i).supervisionEnabled) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns true if there are any non-default non-test users.
     *
     * This excludes the system and main user(s) as those users are created by default.
     */
    private boolean hasNonTestDefaultUsers() {
        List<UserInfo> users = mInjector.getUserManagerInternal().getUsers(true);
        for (var user : users) {
            if (!user.isForTesting() && !user.isMain() && !isSystemUser(user)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSystemUser(UserInfo userInfo) {
        return (userInfo.flags & UserInfo.FLAG_SYSTEM) == UserInfo.FLAG_SYSTEM;
    }

    @Override
    public void onShellCommand(
            @Nullable FileDescriptor in,
            @Nullable FileDescriptor out,
            @Nullable FileDescriptor err,
            @NonNull String[] args,
            @Nullable ShellCallback callback,
            @NonNull ResultReceiver resultReceiver)
            throws RemoteException {
        new SupervisionServiceShellCommand(this)
                .exec(this, in, out, err, args, callback, resultReceiver);
    }

    @Override
    protected void dump(
            @NonNull FileDescriptor fd, @NonNull PrintWriter printWriter, @Nullable String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, LOG_TAG, printWriter)) return;

        try (var pw = new IndentingPrintWriter(printWriter, "  ")) {
            pw.println("SupervisionService state:");
            pw.increaseIndent();

            List<UserInfo> users = mInjector.getUserManagerInternal().getUsers(false);
            synchronized (getLockObject()) {
                for (var user : users) {
                    getUserDataLocked(user.id).dump(pw);
                    pw.println();
                }
            }
        }
    }

    private Object getLockObject() {
        return mLockDoNoUseDirectly;
    }

    @NonNull
    @GuardedBy("getLockObject()")
    SupervisionUserData getUserDataLocked(@UserIdInt int userId) {
        SupervisionUserData data = mUserData.get(userId);
        if (data == null) {
            // TODO(b/362790738): Do not create user data for nonexistent users.
            data = new SupervisionUserData(userId);
            mUserData.append(userId, data);
        }
        return data;
    }

    /**
     * Sets supervision as enabled or disabled for the given user and, in case supervision is being
     * enabled, the package of the active supervision app.
     */
    private void setSupervisionEnabledForUserInternal(
            @UserIdInt int userId, boolean enabled, @Nullable String supervisionAppPackage) {
        synchronized (getLockObject()) {
            SupervisionUserData data = getUserDataLocked(userId);
            data.supervisionEnabled = enabled;
            data.supervisionAppPackage = enabled ? supervisionAppPackage : null;
        }
    }

    /**
     * Ensures that supervision is enabled when the supervision app is the profile owner.
     *
     * <p>The state syncing with the DevicePolicyManager can only enable supervision and never
     * disable. Supervision can only be disabled explicitly via calls to the {@link
     * #setSupervisionEnabledForUser} method.
     */
    private void syncStateWithDevicePolicyManager(@UserIdInt int userId) {
        final DevicePolicyManagerInternal dpmInternal = mInjector.getDpmInternal();
        final ComponentName po =
                dpmInternal != null ? dpmInternal.getProfileOwnerAsUser(userId) : null;

        if (po != null && po.getPackageName().equals(getSystemSupervisionPackage())) {
            setSupervisionEnabledForUserInternal(userId, true, getSystemSupervisionPackage());
        } else if (po != null && po.equals(getSupervisionProfileOwnerComponent())) {
            // TODO(b/392071637): Consider not enabling supervision in case profile owner is given
            // to the legacy supervision profile owner component.
            setSupervisionEnabledForUserInternal(userId, true, po.getPackageName());
        }
    }

    /**
     * Returns the {@link ComponentName} of the supervision profile owner component.
     *
     * <p>This component is used to give GMS Kids Module permission to supervise the device and may
     * still be active during the transition to the {@code SYSTEM_SUPERVISION} role.
     */
    private ComponentName getSupervisionProfileOwnerComponent() {
        return ComponentName.unflattenFromString(
                mContext.getResources()
                        .getString(R.string.config_defaultSupervisionProfileOwnerComponent));
    }

    /** Returns the package assigned to the {@code SYSTEM_SUPERVISION} role. */
    private String getSystemSupervisionPackage() {
        return mContext.getResources().getString(R.string.config_systemSupervision);
    }

    /** Enforces that the caller has the given permission. */
    private void enforcePermission(String permission) {
        checkCallAuthorization(
                mContext.checkCallingOrSelfPermission(permission) == PERMISSION_GRANTED);
    }

    /** Enforces that the caller has at least one of the given permission. */
    private void enforceAnyPermission(String... permissions) {
        boolean authorized = false;
        for (String permission : permissions) {
            if (mContext.checkCallingOrSelfPermission(permission) == PERMISSION_GRANTED) {
                authorized = true;
            }
        }
        checkCallAuthorization(authorized);
    }

    /** Provides local services in a lazy manner. */
    static class Injector {
        private final Context mContext;
        private DevicePolicyManagerInternal mDpmInternal;
        private KeyguardManager mKeyguardManager;
        private PackageManager mPackageManager;
        private UserManagerInternal mUserManagerInternal;

        Injector(Context context) {
            mContext = context;
        }

        @Nullable
        DevicePolicyManagerInternal getDpmInternal() {
            if (mDpmInternal == null) {
                mDpmInternal = LocalServices.getService(DevicePolicyManagerInternal.class);
            }
            return mDpmInternal;
        }

        KeyguardManager getKeyguardManager() {
            if (mKeyguardManager == null) {
                mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
            }
            return mKeyguardManager;
        }

        PackageManager getPackageManager() {
            if (mPackageManager == null) {
                mPackageManager = mContext.getPackageManager();
            }
            return mPackageManager;
        }

        UserManagerInternal getUserManagerInternal() {
            if (mUserManagerInternal == null) {
                mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
            }
            return mUserManagerInternal;
        }
    }

    /** Publishes local and binder services and allows the service to act during initialization. */
    public static class Lifecycle extends SystemService {
        private final SupervisionService mSupervisionService;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mSupervisionService = new SupervisionService(context);
        }

        @VisibleForTesting
        Lifecycle(Context context, SupervisionService supervisionService) {
            super(context);
            mSupervisionService = supervisionService;
        }

        @Override
        public void onStart() {
            publishLocalService(SupervisionManagerInternal.class, mSupervisionService.mInternal);
            publishBinderService(Context.SUPERVISION_SERVICE, mSupervisionService);
            if (Flags.enableSyncWithDpm()) {
                registerProfileOwnerListener();
            }
        }

        @VisibleForTesting
        @SuppressLint("MissingPermission")
        void registerProfileOwnerListener() {
            IntentFilter poIntentFilter = new IntentFilter();
            poIntentFilter.addAction(DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED);
            poIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            getContext()
                    .registerReceiverForAllUsers(
                            new ProfileOwnerBroadcastReceiver(),
                            poIntentFilter,
                            /* broadcastPermission= */ null,
                            /* scheduler= */ null);
        }

        @Override
        public void onUserStarting(@NonNull TargetUser user) {
            if (Flags.enableSyncWithDpm() && !user.isPreCreated()) {
                mSupervisionService.syncStateWithDevicePolicyManager(user.getUserIdentifier());
            }
        }

        private final class ProfileOwnerBroadcastReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                mSupervisionService.syncStateWithDevicePolicyManager(getSendingUserId());
            }
        }
    }

    /** Implementation of the local service, API used by other services. */
    private final class SupervisionManagerInternalImpl extends SupervisionManagerInternal {
        @Override
        public boolean isActiveSupervisionApp(int uid) {
            int userId = UserHandle.getUserId(uid);
            String supervisionAppPackage = getActiveSupervisionAppPackage(userId);
            if (supervisionAppPackage == null) {
                return false;
            }

            String[] packages = mInjector.getPackageManager().getPackagesForUid(uid);
            if (packages != null) {
                for (var packageName : packages) {
                    if (supervisionAppPackage.equals(packageName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean isSupervisionEnabledForUser(@UserIdInt int userId) {
            return SupervisionService.this.isSupervisionEnabledForUser(userId);
        }

        @Override
        public void setSupervisionEnabledForUser(@UserIdInt int userId, boolean enabled) {
            SupervisionService.this.setSupervisionEnabledForUser(userId, enabled);
        }

        @Override
        public boolean isSupervisionLockscreenEnabledForUser(@UserIdInt int userId) {
            synchronized (getLockObject()) {
                return getUserDataLocked(userId).supervisionLockScreenEnabled;
            }
        }

        @Override
        public void setSupervisionLockscreenEnabledForUser(
                @UserIdInt int userId, boolean enabled, @Nullable PersistableBundle options) {
            synchronized (getLockObject()) {
                SupervisionUserData data = getUserDataLocked(userId);
                data.supervisionLockScreenEnabled = enabled;
                data.supervisionLockScreenOptions = options;
            }
        }
    }

    /** Deletes user data when the user gets removed. */
    private final class UserLifecycleListener implements UserManagerInternal.UserLifecycleListener {
        @Override
        public void onUserRemoved(UserInfo user) {
            synchronized (getLockObject()) {
                mUserData.remove(user.id);
            }
        }
    }
}
