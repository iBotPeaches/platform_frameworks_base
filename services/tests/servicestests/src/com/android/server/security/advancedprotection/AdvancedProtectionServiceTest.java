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

package com.android.server.security.advancedprotection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.test.FakePermissionEnforcer;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.security.advancedprotection.AdvancedProtectionFeature;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.security.advancedprotection.IAdvancedProtectionCallback;

import androidx.annotation.NonNull;

import com.android.server.pm.UserManagerInternal;
import com.android.server.security.advancedprotection.features.AdvancedProtectionHook;
import com.android.server.security.advancedprotection.features.AdvancedProtectionProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@SuppressLint("VisibleForTests")
@RunWith(JUnit4.class)
public class AdvancedProtectionServiceTest {
    private FakePermissionEnforcer mPermissionEnforcer;
    private UserManagerInternal mUserManager;
    private Context mContext;
    private TestLooper mLooper;
    AdvancedProtectionFeature mTestFeature2g =
            new AdvancedProtectionFeature(
                    AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G);

    @Before
    public void setup() throws Settings.SettingNotFoundException {
        mContext = mock(Context.class);
        mUserManager = mock(UserManagerInternal.class);
        mLooper = new TestLooper();
        mPermissionEnforcer = new FakePermissionEnforcer();
        mPermissionEnforcer.grant(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        mPermissionEnforcer.grant(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        Mockito.when(mUserManager.getUserInfo(ArgumentMatchers.anyInt()))
                .thenReturn(new UserInfo(0, "user", UserInfo.FLAG_ADMIN));
    }

    private AdvancedProtectionService createService(
            AdvancedProtectionHook hook, AdvancedProtectionProvider provider) {

        AdvancedProtectionService.AdvancedProtectionStore
                store = new AdvancedProtectionService.AdvancedProtectionStore(mContext) {
            private Map<String, Integer> mStoredValues = new HashMap<>();
            private boolean mEnabled = false;

            @Override
            boolean retrieveAdvancedProtectionModeEnabled() {
                return mEnabled;
            }

            @Override
            void storeAdvancedProtectionModeEnabled(boolean enabled) {
                this.mEnabled = enabled;
            }

            @Override
            void storeInt(String key, int value) {
                mStoredValues.put(key, value);
            }

            @Override
            int retrieveInt(String key, int defaultValue) {
                return mStoredValues.getOrDefault(key, defaultValue);
            }
        };

        return new AdvancedProtectionService(
                mContext,
                store,
                mUserManager,
                mLooper.getLooper(),
                mPermissionEnforcer,
                hook,
                provider);
    }

    @Test
    public void testToggleProtection() {
        AdvancedProtectionService service = createService(null, null);
        service.setAdvancedProtectionEnabled(true);
        assertTrue(service.isAdvancedProtectionEnabled());

        service.setAdvancedProtectionEnabled(false);
        assertFalse(service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testDisableProtection_byDefault() {
        AdvancedProtectionService service = createService(null, null);
        assertFalse(service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testSetProtection_nonAdminUser() {
        Mockito.when(mUserManager.getUserInfo(ArgumentMatchers.anyInt()))
                .thenReturn(new UserInfo(1, "user2", UserInfo.FLAG_FULL));
        AdvancedProtectionService service = createService(null, null);
        assertThrows(SecurityException.class, () -> service.setAdvancedProtectionEnabled(true));
    }

    @Test
    public void testEnableProtection_withHook() {
        AtomicBoolean callbackCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook =
                new AdvancedProtectionHook(mContext, true) {
                    @NonNull
                    @Override
                    public AdvancedProtectionFeature getFeature() {
                        return mTestFeature2g;
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public void onAdvancedProtectionChanged(boolean enabled) {
                        callbackCaptor.set(enabled);
                    }
                };

        AdvancedProtectionService service = createService(hook, null);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();

        assertTrue(callbackCaptor.get());
    }

    @Test
    public void testEnableProtection_withFeature_notAvailable() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook =
                new AdvancedProtectionHook(mContext, true) {
                    @NonNull
                    @Override
                    public AdvancedProtectionFeature getFeature() {
                        return mTestFeature2g;
                    }

                    @Override
                    public boolean isAvailable() {
                        return false;
                    }

                    @Override
                    public void onAdvancedProtectionChanged(boolean enabled) {
                        callbackCalledCaptor.set(true);
                    }
                };

        AdvancedProtectionService service = createService(hook, null);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();
        assertFalse(callbackCalledCaptor.get());
    }

    @Test
    public void testEnableProtection_withFeature_notCalledIfModeNotChanged() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook =
                new AdvancedProtectionHook(mContext, true) {
                    @NonNull
                    @Override
                    public AdvancedProtectionFeature getFeature() {
                        return mTestFeature2g;
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public void onAdvancedProtectionChanged(boolean enabled) {
                        callbackCalledCaptor.set(true);
                    }
                };

        AdvancedProtectionService service = createService(hook, null);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();
        assertTrue(callbackCalledCaptor.get());

        callbackCalledCaptor.set(false);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();
        assertFalse(callbackCalledCaptor.get());
    }

    @Test
    public void testRegisterCallback() throws RemoteException {
        AtomicBoolean callbackCaptor = new AtomicBoolean(false);
        IAdvancedProtectionCallback callback =
                new IAdvancedProtectionCallback.Stub() {
                    @Override
                    public void onAdvancedProtectionChanged(boolean enabled) {
                        callbackCaptor.set(enabled);
                    }
                };

        AdvancedProtectionService service = createService(null, null);

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        service.registerAdvancedProtectionCallback(callback);
        mLooper.dispatchNext();
        assertTrue(callbackCaptor.get());

        service.setAdvancedProtectionEnabled(false);
        mLooper.dispatchNext();

        assertFalse(callbackCaptor.get());
    }

    @Test
    public void testUnregisterCallback() throws RemoteException {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        IAdvancedProtectionCallback callback =
                new IAdvancedProtectionCallback.Stub() {
                    @Override
                    public void onAdvancedProtectionChanged(boolean enabled) {
                        callbackCalledCaptor.set(true);
                    }
                };

        AdvancedProtectionService service = createService(null, null);

        service.setAdvancedProtectionEnabled(true);
        service.registerAdvancedProtectionCallback(callback);
        mLooper.dispatchAll();
        callbackCalledCaptor.set(false);

        service.unregisterAdvancedProtectionCallback(callback);
        service.setAdvancedProtectionEnabled(false);

        mLooper.dispatchNext();
        assertFalse(callbackCalledCaptor.get());
    }

    @Test
    public void testGetFeatures() {
        AdvancedProtectionFeature feature1 =
                new AdvancedProtectionFeature(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G);
        AdvancedProtectionFeature feature2 =
                new AdvancedProtectionFeature(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES);
        AdvancedProtectionHook hook =
                new AdvancedProtectionHook(mContext, true) {
                    @NonNull
                    @Override
                    public AdvancedProtectionFeature getFeature() {
                        return feature1;
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }
                };

        AdvancedProtectionProvider provider =
                new AdvancedProtectionProvider() {
                    @Override
                    public List<AdvancedProtectionFeature> getFeatures(Context context) {
                        return List.of(feature2);
                    }
                };

        AdvancedProtectionService service = createService(hook, provider);
        List<AdvancedProtectionFeature> features = service.getAdvancedProtectionFeatures();
        assertThat(features, containsInAnyOrder(feature1, feature2));
    }

    @Test
    public void testGetFeatures_featureNotAvailable() {
        AdvancedProtectionFeature feature1 =
                new AdvancedProtectionFeature(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G);
        AdvancedProtectionFeature feature2 =
                new AdvancedProtectionFeature(
                        AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES);
        AdvancedProtectionHook hook =
                new AdvancedProtectionHook(mContext, true) {
                    @NonNull
                    @Override
                    public AdvancedProtectionFeature getFeature() {
                        return feature1;
                    }

                    @Override
                    public boolean isAvailable() {
                        return false;
                    }
                };

        AdvancedProtectionProvider provider =
                new AdvancedProtectionProvider() {
                    @Override
                    public List<AdvancedProtectionFeature> getFeatures(Context context) {
                        return List.of(feature2);
                    }
                };

        AdvancedProtectionService service = createService(hook, provider);
        List<AdvancedProtectionFeature> features = service.getAdvancedProtectionFeatures();
        assertThat(features, containsInAnyOrder(feature2));
    }

    @Test
    public void testSetProtection_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        assertThrows(SecurityException.class, () -> service.setAdvancedProtectionEnabled(true));
    }

    @Test
    public void testGetProtection_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(SecurityException.class, () -> service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testRegisterCallback_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(
                SecurityException.class,
                () ->
                        service.registerAdvancedProtectionCallback(
                                new IAdvancedProtectionCallback.Default()));
    }

    @Test
    public void testUnregisterCallback_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(
                SecurityException.class,
                () ->
                        service.unregisterAdvancedProtectionCallback(
                                new IAdvancedProtectionCallback.Default()));
    }
}
