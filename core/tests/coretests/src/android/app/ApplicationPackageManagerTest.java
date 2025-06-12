/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.app;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_INFO;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.os.storage.VolumeInfo.STATE_MOUNTED;
import static android.os.storage.VolumeInfo.STATE_UNMOUNTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;

import com.android.internal.annotations.VisibleForTesting;

import junit.framework.TestCase;

import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Presubmit
@LargeTest
public class ApplicationPackageManagerTest extends TestCase {
    private static final String sInternalVolPath = "/data";
    private static final String sAdoptedVolPath = "/mnt/expand/123";
    private static final String sPublicVolPath = "/emulated";
    private static final String sPrivateUnmountedVolPath = "/private";

    private static final String sInternalVolUuid = null; //StorageManager.UUID_PRIVATE_INTERNAL
    private static final String sAdoptedVolUuid = "adopted";
    private static final String sPublicVolUuid = "emulated";
    private static final String sPrivateUnmountedVolUuid = "private";

    private static final VolumeInfo sInternalVol = new VolumeInfo("private",
            VolumeInfo.TYPE_PRIVATE, null /*DiskInfo*/, null /*partGuid*/);

    private static final VolumeInfo sAdoptedVol = new VolumeInfo("adopted",
            VolumeInfo.TYPE_PRIVATE, null /*DiskInfo*/, null /*partGuid*/);

    private static final VolumeInfo sPublicVol = new VolumeInfo("public",
            VolumeInfo.TYPE_PUBLIC, null /*DiskInfo*/, null /*partGuid*/);

    private static final VolumeInfo sPrivateUnmountedVol = new VolumeInfo("private2",
            VolumeInfo.TYPE_PRIVATE, null /*DiskInfo*/, null /*partGuid*/);

    private static final List<VolumeInfo> sVolumes = new ArrayList<>();

    static {
        sInternalVol.path = sInternalVolPath;
        sInternalVol.state = STATE_MOUNTED;
        sInternalVol.fsUuid = sInternalVolUuid;

        sAdoptedVol.path = sAdoptedVolPath;
        sAdoptedVol.state = STATE_MOUNTED;
        sAdoptedVol.fsUuid = sAdoptedVolUuid;

        sPublicVol.state = STATE_MOUNTED;
        sPublicVol.path = sPublicVolPath;
        sPublicVol.fsUuid = sPublicVolUuid;

        sPrivateUnmountedVol.state = STATE_UNMOUNTED;
        sPrivateUnmountedVol.path = sPrivateUnmountedVolPath;
        sPrivateUnmountedVol.fsUuid = sPrivateUnmountedVolUuid;

        sVolumes.add(sInternalVol);
        sVolumes.add(sAdoptedVol);
        sVolumes.add(sPublicVol);
        sVolumes.add(sPrivateUnmountedVol);
    }

    public static class MockedApplicationPackageManager extends ApplicationPackageManager {
        private boolean mForceAllowOnExternal = false;
        private boolean mAllow3rdPartyOnInternal = true;
        private HashMap<ApplicationInfo, Resources> mResourcesMap;

        public MockedApplicationPackageManager() {
            super(null, null);
            mResourcesMap = new HashMap<>();
        }

        public void setForceAllowOnExternal(boolean forceAllowOnExternal) {
            mForceAllowOnExternal = forceAllowOnExternal;
        }

        public void setAllow3rdPartyOnInternal(boolean allow3rdPartyOnInternal) {
            mAllow3rdPartyOnInternal = allow3rdPartyOnInternal;
        }

        public void setResourcesForApplication(ApplicationInfo info, Resources resources) {
            mResourcesMap.put(info, resources);
        }

        @Override
        public boolean isForceAllowOnExternal(Context context) {
            return mForceAllowOnExternal;
        }

        @Override
        public boolean isAllow3rdPartyOnInternal(Context context) {
            return mAllow3rdPartyOnInternal;
        }

        @Override
        @VisibleForTesting
        protected @NonNull List<VolumeInfo> getPackageCandidateVolumes(ApplicationInfo app,
                StorageManager storageManager, IPackageManager pm) {
            return super.getPackageCandidateVolumes(app, storageManager, pm);
        }

        @Override
        public Resources getResourcesForApplication(ApplicationInfo app)
                throws NameNotFoundException {
            if (mResourcesMap.containsKey(app)) {
                return mResourcesMap.get(app);
            }

            return super.getResourcesForApplication(app);
        }
    }

    private StorageManager getMockedStorageManager() {
        StorageManager storageManager = mock(StorageManager.class);
        Mockito.when(storageManager.getVolumes()).thenReturn(sVolumes);
        Mockito.when(storageManager.findVolumeById(VolumeInfo.ID_PRIVATE_INTERNAL))
                .thenReturn(sInternalVol);
        Mockito.when(storageManager.findVolumeByUuid(sAdoptedVolUuid))
                .thenReturn(sAdoptedVol);
        Mockito.when(storageManager.findVolumeByUuid(sPublicVolUuid))
                .thenReturn(sPublicVol);
        Mockito.when(storageManager.findVolumeByUuid(sPrivateUnmountedVolUuid))
                .thenReturn(sPrivateUnmountedVol);
        return storageManager;
    }

    private void verifyReturnedVolumes(List<VolumeInfo> actualVols, VolumeInfo... exptectedVols) {
        boolean failed = false;
        if (actualVols.size() != exptectedVols.length) {
            failed = true;
        } else {
            for (VolumeInfo vol : exptectedVols) {
                if (!actualVols.contains(vol)) {
                    failed = true;
                    break;
                }
            }
        }

        if (failed) {
            fail("Wrong volumes returned.\n Expected: " + Arrays.toString(exptectedVols)
                    + "\n Actual: " + Arrays.toString(actualVols.toArray()));
        }
    }

    public void testGetCandidateVolumes_systemApp() throws Exception {
        ApplicationInfo sysAppInfo = new ApplicationInfo();
        sysAppInfo.flags = ApplicationInfo.FLAG_SYSTEM;

        StorageManager storageManager = getMockedStorageManager();
        IPackageManager pm = mock(IPackageManager.class);

        MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();

        appPkgMgr.setAllow3rdPartyOnInternal(true);
        appPkgMgr.setForceAllowOnExternal(true);
        List<VolumeInfo> candidates =
                appPkgMgr.getPackageCandidateVolumes(sysAppInfo, storageManager, pm);
        verifyReturnedVolumes(candidates, sInternalVol);

        appPkgMgr.setAllow3rdPartyOnInternal(true);
        appPkgMgr.setForceAllowOnExternal(false);
        candidates = appPkgMgr.getPackageCandidateVolumes(sysAppInfo, storageManager, pm);
        verifyReturnedVolumes(candidates, sInternalVol);

        appPkgMgr.setAllow3rdPartyOnInternal(false);
        appPkgMgr.setForceAllowOnExternal(false);
        candidates = appPkgMgr.getPackageCandidateVolumes(sysAppInfo, storageManager, pm);
        verifyReturnedVolumes(candidates, sInternalVol);

        appPkgMgr.setAllow3rdPartyOnInternal(false);
        appPkgMgr.setForceAllowOnExternal(true);
        candidates = appPkgMgr.getPackageCandidateVolumes(sysAppInfo, storageManager, pm);
        verifyReturnedVolumes(candidates, sInternalVol);
    }

    public void testGetCandidateVolumes_3rdParty_internalOnly() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        StorageManager storageManager = getMockedStorageManager();

        IPackageManager pm = mock(IPackageManager.class);
        Mockito.when(pm.isPackageDeviceAdminOnAnyUser(Mockito.anyString())).thenReturn(false);

        MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();

        // must allow 3rd party on internal, otherwise the app wouldn't have been installed before.
        appPkgMgr.setAllow3rdPartyOnInternal(true);

        // INSTALL_LOCATION_INTERNAL_ONLY AND INSTALL_LOCATION_UNSPECIFIED are treated the same.
        int[] locations = {PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY,
                PackageInfo.INSTALL_LOCATION_UNSPECIFIED};

        for (int location : locations) {
            appInfo.installLocation = location;
            appPkgMgr.setForceAllowOnExternal(true);
            List<VolumeInfo> candidates = appPkgMgr.getPackageCandidateVolumes(
                    appInfo, storageManager, pm);
            verifyReturnedVolumes(candidates, sInternalVol, sAdoptedVol);

            appPkgMgr.setForceAllowOnExternal(false);
            candidates = appPkgMgr.getPackageCandidateVolumes(appInfo, storageManager, pm);
            verifyReturnedVolumes(candidates, sInternalVol);
        }
    }

    public void testGetCandidateVolumes_3rdParty_auto() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        StorageManager storageManager = getMockedStorageManager();

        IPackageManager pm = mock(IPackageManager.class);

        MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();
        appPkgMgr.setForceAllowOnExternal(true);

        // INSTALL_LOCATION_AUTO AND INSTALL_LOCATION_PREFER_EXTERNAL are treated the same.
        int[] locations = {PackageInfo.INSTALL_LOCATION_AUTO,
                PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL};

        for (int location : locations) {
            appInfo.installLocation = location;
            appInfo.flags = 0;

            appInfo.volumeUuid = sInternalVolUuid;
            Mockito.when(pm.isPackageDeviceAdminOnAnyUser(appInfo.packageName)).thenReturn(false);
            appPkgMgr.setAllow3rdPartyOnInternal(true);
            List<VolumeInfo> candidates = appPkgMgr.getPackageCandidateVolumes(
                    appInfo, storageManager, pm);
            verifyReturnedVolumes(candidates, sInternalVol, sAdoptedVol);

            appInfo.volumeUuid = sInternalVolUuid;
            appPkgMgr.setAllow3rdPartyOnInternal(true);
            Mockito.when(pm.isPackageDeviceAdminOnAnyUser(appInfo.packageName)).thenReturn(true);
            candidates = appPkgMgr.getPackageCandidateVolumes(appInfo, storageManager, pm);
            verifyReturnedVolumes(candidates, sInternalVol);

            appInfo.flags = ApplicationInfo.FLAG_EXTERNAL_STORAGE;
            appInfo.volumeUuid = sAdoptedVolUuid;
            appPkgMgr.setAllow3rdPartyOnInternal(false);
            candidates = appPkgMgr.getPackageCandidateVolumes(appInfo, storageManager, pm);
            verifyReturnedVolumes(candidates, sAdoptedVol);
        }
    }

    public void testExtractPackageItemInfoAttributes_noServiceInfo() {
        final MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();
        assertThat(appPkgMgr.extractPackageItemInfoAttributes(null, null, null,
                new int[]{})).isNull();
    }

    public void testExtractPackageItemInfoAttributes_noMetaData() {
        final MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();
        final PackageItemInfo packageItemInfo = mock(PackageItemInfo.class);
        assertThat(appPkgMgr.extractPackageItemInfoAttributes(packageItemInfo, null, null,
                new int[]{})).isNull();
    }

    public void testExtractPackageItemInfoAttributes_noParser() {
        final MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();
        final PackageItemInfo packageItemInfo = mock(PackageItemInfo.class);
        final ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        when(packageItemInfo.getApplicationInfo()).thenReturn(applicationInfo);
        assertThat(appPkgMgr.extractPackageItemInfoAttributes(packageItemInfo, null, null,
                new int[]{})).isNull();
    }

    public void testExtractPackageItemInfoAttributes_noMetaDataXml() {
        final MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();
        final PackageItemInfo packageItemInfo = mock(PackageItemInfo.class);
        final ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        when(packageItemInfo.getApplicationInfo()).thenReturn(applicationInfo);
        when(packageItemInfo.loadXmlMetaData(any(), any())).thenReturn(null);
        assertThat(appPkgMgr.extractPackageItemInfoAttributes(packageItemInfo, null, null,
                new int[]{})).isNull();
    }

    public void testExtractPackageItemInfoAttributes_nonMatchingRootTag() throws Exception {
        final String rootTag = "rootTag";
        final MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();
        final PackageItemInfo packageItemInfo = mock(PackageItemInfo.class);
        final ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        final XmlResourceParser parser = mock(XmlResourceParser.class);

        when(packageItemInfo.getApplicationInfo()).thenReturn(applicationInfo);
        packageItemInfo.metaData = new Bundle();
        when(packageItemInfo.loadXmlMetaData(any(), any())).thenReturn(parser);
        when(parser.next()).thenReturn(XmlPullParser.END_DOCUMENT);
        when(parser.getName()).thenReturn(rootTag + "0");
        assertThat(appPkgMgr.extractPackageItemInfoAttributes(packageItemInfo, null, rootTag,
                new int[]{})).isNull();
    }

    public void testExtractPackageItemInfoAttributes_successfulExtraction() throws Exception {
        final String rootTag = "rootTag";
        final MockedApplicationPackageManager appPkgMgr = new MockedApplicationPackageManager();
        final PackageItemInfo packageItemInfo = mock(PackageItemInfo.class);
        final ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        final XmlResourceParser parser = mock(XmlResourceParser.class);
        final Resources resources = mock(Resources.class);
        final TypedArray attributes = mock(TypedArray.class);

        when(packageItemInfo.getApplicationInfo()).thenReturn(applicationInfo);
        packageItemInfo.metaData = new Bundle();
        when(packageItemInfo.loadXmlMetaData(any(), any())).thenReturn(parser);
        when(parser.next()).thenReturn(XmlPullParser.END_DOCUMENT);
        when(parser.getName()).thenReturn(rootTag);
        when(resources.obtainAttributes(any(), any())).thenReturn(attributes);
        appPkgMgr.setResourcesForApplication(applicationInfo, resources);

        assertThat(appPkgMgr.extractPackageItemInfoAttributes(packageItemInfo, null, rootTag,
                new int[]{})).isEqualTo(attributes);
    }

    public void testGetLaunchIntentForPackage_categoryInfoActivity_returnsIt() throws Exception {
        String pkg = "com.some.package";
        int userId = 42;
        ResolveInfo categoryInfoResolveInfo = new ResolveInfo();
        categoryInfoResolveInfo.activityInfo = new ActivityInfo();
        categoryInfoResolveInfo.activityInfo.packageName = pkg;
        categoryInfoResolveInfo.activityInfo.name = "activity";
        Intent baseIntent = new Intent(ACTION_MAIN).setPackage(pkg);

        final MockedApplicationPackageManager pm = spy(new MockedApplicationPackageManager());
        doReturn(userId).when(pm).getUserId();
        doReturn(List.of(categoryInfoResolveInfo))
                .when(pm).queryIntentActivitiesAsUser(
                        eqIntent(new Intent(baseIntent).addCategory(CATEGORY_INFO)),
                        any(ResolveInfoFlags.class),
                        anyInt());
        doReturn(
                List.of())
                .when(pm).queryIntentActivitiesAsUser(
                        eqIntent(new Intent(baseIntent).addCategory(CATEGORY_LAUNCHER)),
                        any(ResolveInfoFlags.class),
                        anyInt());

        Intent intent = pm.getLaunchIntentForPackage(pkg, true);

        assertThat(intent).isNotNull();
        assertThat(intent.getComponent()).isEqualTo(new ComponentName(pkg, "activity"));
        assertThat(intent.getCategories()).containsExactly(CATEGORY_INFO);
        assertThat(intent.getFlags()).isEqualTo(FLAG_ACTIVITY_NEW_TASK);
        verify(pm).queryIntentActivitiesAsUser(
                eqIntent(new Intent(ACTION_MAIN).addCategory(CATEGORY_INFO).setPackage(pkg)),
                eqResolveInfoFlags(MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE),
                eq(userId));
    }

    public void testGetLaunchIntentForPackage_categoryLauncherActivity_returnsIt() {
        String pkg = "com.some.package";
        int userId = 42;
        ResolveInfo categoryLauncherResolveInfo1 = new ResolveInfo();
        categoryLauncherResolveInfo1.activityInfo = new ActivityInfo();
        categoryLauncherResolveInfo1.activityInfo.packageName = pkg;
        categoryLauncherResolveInfo1.activityInfo.name = "activity1";
        ResolveInfo categoryLauncherResolveInfo2 = new ResolveInfo();
        categoryLauncherResolveInfo2.activityInfo = new ActivityInfo();
        categoryLauncherResolveInfo2.activityInfo.packageName = pkg;
        categoryLauncherResolveInfo2.activityInfo.name = "activity2";
        Intent baseIntent = new Intent(ACTION_MAIN).setPackage(pkg);

        final MockedApplicationPackageManager pm = spy(new MockedApplicationPackageManager());
        doReturn(userId).when(pm).getUserId();
        doReturn(List.of())
                .when(pm).queryIntentActivitiesAsUser(
                        eqIntent(new Intent(baseIntent).addCategory(CATEGORY_INFO)),
                        any(ResolveInfoFlags.class),
                        anyInt());
        doReturn(
                List.of(categoryLauncherResolveInfo1, categoryLauncherResolveInfo2))
                .when(pm).queryIntentActivitiesAsUser(
                        eqIntent(new Intent(baseIntent).addCategory(CATEGORY_LAUNCHER)),
                        any(ResolveInfoFlags.class),
                        anyInt());

        Intent intent = pm.getLaunchIntentForPackage(pkg, true);

        assertThat(intent).isNotNull();
        assertThat(intent.getComponent()).isEqualTo(new ComponentName(pkg, "activity1"));
        assertThat(intent.getCategories()).containsExactly(CATEGORY_LAUNCHER);
        assertThat(intent.getFlags()).isEqualTo(FLAG_ACTIVITY_NEW_TASK);
    }

    public void testGetLaunchIntentForPackage_noSuitableActivity_returnsNull() throws Exception {
        String pkg = "com.some.package";
        int userId = 42;

        final MockedApplicationPackageManager pm = spy(new MockedApplicationPackageManager());
        doReturn(userId).when(pm).getUserId();
        doReturn(List.of())
                .when(pm).queryIntentActivitiesAsUser(
                        any(),
                        any(ResolveInfoFlags.class),
                        anyInt());

        Intent intent = pm.getLaunchIntentForPackage(pkg, true);

        assertThat(intent).isNull();
    }

    /** Equality check for intents -- ignoring extras */
    private static Intent eqIntent(Intent wanted) {
        return argThat(
                new ArgumentMatcher<>() {
                    @Override
                    public boolean matches(Intent argument) {
                        return wanted.filterEquals(argument)
                                && wanted.getFlags() == argument.getFlags();
                    }

                    @Override
                    public String toString() {
                        return wanted.toString();
                    }
                });
    }

    private static ResolveInfoFlags eqResolveInfoFlags(long flagsWanted) {
        return argThat(
                new ArgumentMatcher<>() {
                    @Override
                    public boolean matches(ResolveInfoFlags argument) {
                        return argument.getValue() == flagsWanted;
                    }

                    @Override
                    public String toString() {
                        return String.valueOf(flagsWanted);
                    }
                });
    }
}
