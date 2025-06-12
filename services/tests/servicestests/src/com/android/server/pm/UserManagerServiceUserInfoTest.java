/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.pm;
import static android.content.pm.UserInfo.FLAG_DEMO;
import static android.content.pm.UserInfo.FLAG_EPHEMERAL;
import static android.content.pm.UserInfo.FLAG_FULL;
import static android.content.pm.UserInfo.FLAG_GUEST;
import static android.content.pm.UserInfo.FLAG_INITIALIZED;
import static android.content.pm.UserInfo.FLAG_MANAGED_PROFILE;
import static android.content.pm.UserInfo.FLAG_PROFILE;
import static android.content.pm.UserInfo.FLAG_RESTRICTED;
import static android.content.pm.UserInfo.FLAG_SYSTEM;
import static android.os.UserManager.USER_TYPE_FULL_DEMO;
import static android.os.UserManager.USER_TYPE_FULL_GUEST;
import static android.os.UserManager.USER_TYPE_FULL_RESTRICTED;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;
import static android.os.UserManager.USER_TYPE_FULL_SYSTEM;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.UserIdInt;
import android.app.PropertyInvalidatedCache;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.content.res.Resources;
import android.multiuser.Flags;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;
import android.util.Xml;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;

import com.android.frameworks.servicestests.R;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerService.UserData;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Run with
 * {@code atest FrameworksServicesTests:com.android.server.pm.UserManagerServiceUserInfoTest}.
 */
@Presubmit
@MediumTest
@SuppressWarnings("deprecation")
public final class UserManagerServiceUserInfoTest {

    @Rule public final Expect expect = Expect.create();

    private UserManagerService mUserManagerService;
    private Resources mResources;

    @Before
    public void setup() {
        // Currently UserManagerService cannot be instantiated twice inside a VM without a cleanup
        // TODO: Remove once UMS supports proper dependency injection
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        // Disable binder caches in this process.
        PropertyInvalidatedCache.disableForTestMode();

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        mUserManagerService = new UserManagerService(InstrumentationRegistry.getContext());

        // The tests assume that the device has one user and its the system user.
        List<UserInfo> users = mUserManagerService.getUsers(/* excludeDying */ false);
        assertWithMessage("initial users").that(users).isNotNull();
        assertWithMessage("initial users").that(users).hasSize(1);
        expect.withMessage("only user present initially is the system user.").that(users.get(0).id)
                .isEqualTo(UserHandle.USER_SYSTEM);

        mResources = InstrumentationRegistry.getTargetContext().getResources();
    }

    @Test
    public void testWriteReadUserInfo() throws Exception {
        UserData data = new UserData();
        data.info = createUser();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        mUserManagerService.writeUserLP(data, out);
        byte[] bytes = baos.toByteArray();

        UserData read = mUserManagerService.readUserLP(
                data.info.id, new ByteArrayInputStream(bytes), 0);

        assertUserInfoEquals(data.info, read.info, /* parcelCopy= */ false);
    }

    /** Tests that device policy restrictions are written/read properly. */
    @Test
    public void testWriteReadDevicePolicyUserRestrictions() throws Exception {
        final String globalRestriction = UserManager.DISALLOW_FACTORY_RESET;
        final String localRestriction = UserManager.DISALLOW_CONFIG_DATE_TIME;

        UserData data = new UserData();
        data.info = createUser(100, FLAG_FULL, "A type");

        mUserManagerService.putUserInfo(data.info);

        //Local restrictions are written to the user specific files and global restrictions
        // are written to the SYSTEM user file.
        setUserRestrictions(data.info.id, globalRestriction, localRestriction, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        mUserManagerService.writeUserLP(data, out);
        byte[] secondaryUserBytes = baos.toByteArray();
        baos.reset();

        byte[] systemUserBytes = new byte[0];
        if (Flags.saveGlobalAndGuestRestrictionsOnSystemUserXmlReadOnly()) {
            UserData systemUserData = new UserData();
            systemUserData.info = mUserManagerService.getUserInfo(UserHandle.USER_SYSTEM);
            mUserManagerService.writeUserLP(systemUserData, baos);
            systemUserBytes = baos.toByteArray();
        }

        // Clear the restrictions to see if they are properly read in from the user file.
        setUserRestrictions(data.info.id, globalRestriction, localRestriction, false);

        final int userVersion = 10;
        //read the secondary and SYSTEM user file to fetch local/global device policy restrictions.
        mUserManagerService.readUserLP(data.info.id, new ByteArrayInputStream(secondaryUserBytes),
                userVersion);
        if (Flags.saveGlobalAndGuestRestrictionsOnSystemUserXmlReadOnly()) {
            mUserManagerService.readUserLP(UserHandle.USER_SYSTEM,
                    new ByteArrayInputStream(systemUserBytes), userVersion);
        }

        expect.withMessage("hasUserRestrictionOnAnyUser(%s)", globalRestriction)
                .that(mUserManagerService.hasUserRestrictionOnAnyUser(globalRestriction)).isTrue();
        expect.withMessage("hasUserRestrictionOnAnyUser(%s)", localRestriction)
                .that(mUserManagerService.hasUserRestrictionOnAnyUser(localRestriction)).isTrue();
    }

    /** Sets a global and local restriction and verifies they were set properly **/
    private void setUserRestrictions(int id, String global, String local, boolean enabled) {
        mUserManagerService.setUserRestrictionInner(UserHandle.USER_ALL, global, enabled);
        expect.withMessage("hasUserRestrictionOnAnyUser(%s)", global)
                .that(mUserManagerService.hasUserRestrictionOnAnyUser(global)).isEqualTo(enabled);

        mUserManagerService.setUserRestrictionInner(id, local, enabled);
        expect.withMessage("hasUserRestrictionOnAnyUser(%s)", local)
                .that(mUserManagerService.hasUserRestrictionOnAnyUser(local)).isEqualTo(enabled);
    }

    @Test
    public void testGetUserName() throws Exception {
        expect.withMessage("System user name is set")
                .that(mUserManagerService.isUserNameSet(UserHandle.USER_SYSTEM)).isFalse();
        UserInfo userInfo = mUserManagerService.getUserInfo(UserHandle.USER_SYSTEM);
        expect.withMessage("A system provided name returned for primary user is empty")
                .that(TextUtils.isEmpty(userInfo.name)).isFalse();

        userInfo = createUser();
        userInfo.partial = false;
        final int TEST_ID = 100;
        userInfo.id = TEST_ID;
        mUserManagerService.putUserInfo(userInfo);
        expect.withMessage("user name is set").that(mUserManagerService.isUserNameSet(TEST_ID))
                .isTrue();
        expect.withMessage("name").that(mUserManagerService.getUserInfo(TEST_ID).name)
                .isEqualTo("A Name");
    }

    /** Test UMS.isUserOfType(). */
    @Test
    public void testIsUserOfType() throws Exception {
        expect.withMessage("System user type is valid").that(
                mUserManagerService.isUserOfType(UserHandle.USER_SYSTEM, USER_TYPE_SYSTEM_HEADLESS)
                        || mUserManagerService.isUserOfType(UserHandle.USER_SYSTEM,
                                USER_TYPE_FULL_SYSTEM))
                .isTrue();

        final int testId = 100;
        final String typeName = "A type";
        UserInfo userInfo = createUser(testId, 0, typeName);
        mUserManagerService.putUserInfo(userInfo);
        expect.withMessage("isUserOfType()")
                .that(mUserManagerService.isUserOfType(testId, typeName)).isTrue();
    }

    /** Tests upgradeIfNecessaryLP (but without locking) for upgrading from version 8 to 9+. */
    @Test
    public void testUpgradeIfNecessaryLP_9() {
        final int versionToTest = 9;
        // do not trigger a user type upgrade
        final int userTypeVersion = UserTypeFactory.getUserTypeVersion();

        mUserManagerService.putUserInfo(createUser(100, FLAG_MANAGED_PROFILE, null));
        mUserManagerService.putUserInfo(createUser(101,
                FLAG_GUEST | FLAG_EPHEMERAL | FLAG_FULL, null));
        mUserManagerService.putUserInfo(createUser(102, FLAG_RESTRICTED | FLAG_FULL, null));
        mUserManagerService.putUserInfo(createUser(103, FLAG_FULL, null));
        mUserManagerService.putUserInfo(createUser(104, FLAG_SYSTEM, null));
        mUserManagerService.putUserInfo(createUser(105, FLAG_SYSTEM | FLAG_FULL, null));
        mUserManagerService.putUserInfo(createUser(106, FLAG_DEMO | FLAG_FULL, null));

        mUserManagerService.upgradeIfNecessaryLP(versionToTest - 1, userTypeVersion);

        expect.withMessage("isUserOfType(100, USER_TYPE_PROFILE_MANAGED)")
                .that(mUserManagerService.isUserOfType(100, USER_TYPE_PROFILE_MANAGED)).isTrue();
        expect.withMessage("getUserInfo(100).flags & FLAG_PROFILE)")
                .that(mUserManagerService.getUserInfo(100).flags & FLAG_PROFILE).isNotEqualTo(0);

        expect.withMessage("isUserOfType(101, USER_TYPE_FULL_GUEST)")
                .that(mUserManagerService.isUserOfType(101, USER_TYPE_FULL_GUEST)).isTrue();

        expect.withMessage("isUserOfType(102, USER_TYPE_FULL_RESTRICTED)")
                .that(mUserManagerService.isUserOfType(102, USER_TYPE_FULL_RESTRICTED)).isTrue();
        expect.withMessage("getUserInfo(102).flags & FLAG_PROFILE)")
                .that(mUserManagerService.getUserInfo(102).flags & FLAG_PROFILE).isEqualTo(0);

        expect.withMessage("isUserOfType(103, USER_TYPE_FULL_SECONDARY)")
                .that(mUserManagerService.isUserOfType(103, USER_TYPE_FULL_SECONDARY)).isTrue();
        expect.withMessage("getUserInfo(103).flags & FLAG_PROFILE)")
                .that(mUserManagerService.getUserInfo(103).flags & FLAG_PROFILE).isEqualTo(0);

        expect.withMessage("isUserOfType(104, USER_TYPE_SYSTEM_HEADLESS)")
                .that(mUserManagerService.isUserOfType(104, USER_TYPE_SYSTEM_HEADLESS)).isTrue();

        expect.withMessage("isUserOfType(105, USER_TYPE_FULL_SYSTEM)")
                .that(mUserManagerService.isUserOfType(105, USER_TYPE_FULL_SYSTEM)).isTrue();

        expect.withMessage("isUserOfType(106, USER_TYPE_FULL_DEMO)")
                .that(mUserManagerService.isUserOfType(106, USER_TYPE_FULL_DEMO)).isTrue();
    }

    /** Tests readUserLP upgrading from version 9 to 10+. */
    @Test
    public void testUserRestrictionsUpgradeFromV9() throws Exception {
        final String[] localRestrictions = new String[] {
            UserManager.DISALLOW_CAMERA,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        };

        final int userId = 100;
        UserData data = new UserData();
        data.info = createUser(userId, FLAG_FULL, "A type");

        mUserManagerService.putUserInfo(data.info);

        for (String restriction : localRestrictions) {
            expect.withMessage("hasBaseUserRestriction(%s, %s)", restriction, userId)
                    .that(mUserManagerService.hasBaseUserRestriction(restriction, userId))
                    .isFalse();
            expect.withMessage("hasUserRestriction(%s, %s)", restriction, userId)
                    .that(mUserManagerService.hasUserRestriction(restriction, userId)).isFalse();
        }

        // Convert the xml resource to the system storage xml format.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(baos);
        XmlPullParser in = mResources.getXml(R.xml.user_100_v9);
        XmlSerializer out = Xml.newBinarySerializer();
        out.setOutput(os, StandardCharsets.UTF_8.name());
        Xml.copy(in, out);
        byte[] userBytes = baos.toByteArray();
        baos.reset();

        final int userVersion = 9;
        mUserManagerService.readUserLP(data.info.id, new ByteArrayInputStream(userBytes),
                userVersion);

        for (String restriction : localRestrictions) {
            expect.withMessage("hasBaseUserRestriction(%s, %s)", restriction, userId)
                    .that(mUserManagerService.hasBaseUserRestriction(restriction, userId))
                    .isFalse();
            expect.withMessage("hasUserRestriction(%s, %s)", restriction, userId)
                    .that(mUserManagerService.hasUserRestriction(restriction, userId)).isTrue();
        }
    }

    /** Creates a UserInfo with the given flags and userType. */
    private UserInfo createUser(@UserIdInt int userId, @UserInfoFlag int flags, String userType) {
        return new UserInfo(userId, "A Name", "A path", flags, userType);
    }

    private UserInfo createUser() {
        UserInfo user = new UserInfo(/*id*/ 21, "A Name", "A path", /*flags*/ 0x0ff0ff, "A type");
        user.serialNumber = 5;
        user.creationTime = 4L << 32;
        user.lastLoggedInTime = 5L << 32;
        user.lastLoggedInFingerprint = "afingerprint";
        user.profileGroupId = 45;
        user.restrictedProfileParentId = 4;
        user.profileBadge = 2;
        user.partial = true;
        user.guestToRemove = true;
        user.preCreated = true;
        user.convertedFromPreCreated = true;
        return user;
    }

    private void assertUserInfoEquals(UserInfo one, UserInfo two, boolean parcelCopy) {
        expect.withMessage("Id").that(two.id).isEqualTo(one.id);
        expect.withMessage("Name").that(two.name).isEqualTo(one.name);
        expect.withMessage("Icon path").that(two.iconPath).isEqualTo(one.iconPath);
        expect.withMessage("Flags").that(two.flags).isEqualTo(one.flags);
        expect.withMessage("User type").that(two.userType).isEqualTo(one.userType);
        expect.withMessage("Profile group").that(two.profileGroupId).isEqualTo(one.profileGroupId);
        expect.withMessage("Restricted profile parent").that(two.restrictedProfileParentId)
                .isEqualTo(one.restrictedProfileParentId);
        expect.withMessage("Profile badge").that(two.profileBadge).isEqualTo(one.profileBadge);
        expect.withMessage("Partial").that(two.partial).isEqualTo(one.partial);
        expect.withMessage("Guest to remove").that(two.guestToRemove).isEqualTo(one.guestToRemove);
        expect.withMessage("Pre created").that(two.preCreated).isEqualTo(one.preCreated);
        if (parcelCopy) {
            expect.withMessage("convertedFromPreCreated").that(two.convertedFromPreCreated)
                    .isFalse();
        } else {
            expect.withMessage("convertedFromPreCreated").that(two.convertedFromPreCreated)
                    .isEqualTo(one.convertedFromPreCreated);
        }
    }

    /** Tests upgrading profile types */
    @Test
    public void testUpgradeProfileType_updateTypeAndFlags() {
        final int userId = 42;
        final String newUserTypeName = "new.user.type";
        final String oldUserTypeName = USER_TYPE_PROFILE_MANAGED;

        UserTypeDetails.Builder oldUserTypeBuilder = new UserTypeDetails.Builder()
                .setName(oldUserTypeName)
                .setBaseType(FLAG_PROFILE)
                .setDefaultUserInfoPropertyFlags(FLAG_MANAGED_PROFILE)
                .setMaxAllowedPerParent(32)
                .setIconBadge(401)
                .setBadgeColors(402, 403, 404)
                .setBadgeLabels(23, 24, 25);
        UserTypeDetails oldUserType = oldUserTypeBuilder.createUserTypeDetails();

        UserInfo userInfo = createUser(userId,
                oldUserType.getDefaultUserInfoFlags() | FLAG_INITIALIZED, oldUserTypeName);
        mUserManagerService.putUserInfo(userInfo);

        UserTypeDetails.Builder newUserTypeBuilder = new UserTypeDetails.Builder()
                .setName(newUserTypeName)
                .setBaseType(FLAG_PROFILE)
                .setMaxAllowedPerParent(32)
                .setIconBadge(401)
                .setBadgeColors(402, 403, 404)
                .setBadgeLabels(23, 24, 25);
        UserTypeDetails newUserType = newUserTypeBuilder.createUserTypeDetails();

        mUserManagerService.upgradeProfileToTypeLU(userInfo, newUserType);

        expect.withMessage("isUserOfType(%s)", newUserTypeName)
                .that(mUserManagerService.isUserOfType(userId, newUserTypeName)).isTrue();
        expect.withMessage("flags(FLAG_PROFILE)")
                .that(mUserManagerService.getUserInfo(userId).flags & FLAG_PROFILE).isNotEqualTo(0);
        expect.withMessage("flags(FLAG_MANAGED_PROFILE)")
                .that(mUserManagerService.getUserInfo(userId).flags & FLAG_MANAGED_PROFILE)
                .isEqualTo(0);
        expect.withMessage("flags(FLAG_FLAG_INITIALIZED")
                .that(mUserManagerService.getUserInfo(userId).flags & FLAG_INITIALIZED)
                .isNotEqualTo(0);
    }

    @Test
    public void testUpgradeProfileType_updateRestrictions() {
        final int userId = 42;
        final String newUserTypeName = "new.user.type";
        final String oldUserTypeName = USER_TYPE_PROFILE_MANAGED;

        UserTypeDetails.Builder oldUserTypeBuilder = new UserTypeDetails.Builder()
                .setName(oldUserTypeName)
                .setBaseType(FLAG_PROFILE)
                .setDefaultUserInfoPropertyFlags(FLAG_MANAGED_PROFILE)
                .setMaxAllowedPerParent(32)
                .setIconBadge(401)
                .setBadgeColors(402, 403, 404)
                .setBadgeLabels(23, 24, 25);
        UserTypeDetails oldUserType = oldUserTypeBuilder.createUserTypeDetails();

        UserInfo userInfo = createUser(userId, oldUserType.getDefaultUserInfoFlags(),
                oldUserTypeName);
        mUserManagerService.putUserInfo(userInfo);
        mUserManagerService.setUserRestriction(UserManager.DISALLOW_CAMERA, true, userId);
        mUserManagerService.setUserRestriction(UserManager.DISALLOW_PRINTING, true, userId);

        UserTypeDetails.Builder newUserTypeBuilder = new UserTypeDetails.Builder()
                .setName(newUserTypeName)
                .setBaseType(FLAG_PROFILE)
                .setMaxAllowedPerParent(32)
                .setIconBadge(401)
                .setBadgeColors(402, 403, 404)
                .setBadgeLabels(23, 24, 25)
                .setDefaultRestrictions(
                        UserManagerServiceUserTypeTest.makeRestrictionsBundle(
                                UserManager.DISALLOW_WALLPAPER));
        UserTypeDetails newUserType = newUserTypeBuilder.createUserTypeDetails();

        mUserManagerService.upgradeProfileToTypeLU(userInfo, newUserType);

        expect.withMessage("getUserRestrictions(DISALLOW_PRINTING)").that(mUserManagerService
                .getUserRestrictions(userId).getBoolean(UserManager.DISALLOW_PRINTING)).isTrue();
        expect.withMessage("getUserRestrictions(DISALLOW_CAMERA)").that(mUserManagerService
                .getUserRestrictions(userId).getBoolean(UserManager.DISALLOW_CAMERA)).isTrue();
        expect.withMessage("getUserRestrictions(DISALLOW_WALLPAPER)").that(mUserManagerService
                .getUserRestrictions(userId).getBoolean(UserManager.DISALLOW_WALLPAPER)).isTrue();
    }
}
