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

package android.content.pm;

import static android.content.pm.Flags.FLAG_OPTIMIZE_PARSING_IN_REGISTERED_SERVICES_CACHE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.RegisteredServicesCacheTest.TestServiceType;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.AttributeSet;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.os.BackgroundThread;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Unit tests for {@link android.content.pm.RegisteredServicesCache}
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_OPTIMIZE_PARSING_IN_REGISTERED_SERVICES_CACHE)
public class RegisteredServicesCacheUnitTest {
    private static final String TAG = "RegisteredServicesCacheUnitTest";
    private static final int U0 = 0;
    private static final int U1 = 1;
    private static final int UID1 = 1;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final TestResolveInfo mResolveInfo1 = new TestResolveInfo();
    private final TestResolveInfo mResolveInfo2 = new TestResolveInfo();
    private final TestServiceType mTestServiceType1 = new TestServiceType("t1", "value1");
    private final TestServiceType mTestServiceType2 = new TestServiceType("t2", "value2");
    @Mock
    RegisteredServicesCache.Injector<TestServiceType> mMockInjector;
    @Mock
    Context mMockContext;
    Handler mMockBackgroundHandler;
    @Mock
    PackageManager mMockPackageManager;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockInjector.getContext()).thenReturn(mMockContext);
        mMockBackgroundHandler = spy(BackgroundThread.getHandler());
        when(mMockInjector.getBackgroundHandler()).thenReturn(mMockBackgroundHandler);
        doReturn(mock(Intent.class)).when(mMockContext).registerReceiverAsUser(any(), any(), any(),
                any(), any());
        doReturn(mock(Intent.class)).when(mMockContext).registerReceiver(any(), any(), any(),
                any());
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        addServiceInfoIntoResolveInfo(mResolveInfo1, "r1.package.name" /* packageName */,
                "r1.service.name" /* serviceName */);
        addServiceInfoIntoResolveInfo(mResolveInfo2, "r2.package.name" /* packageName */,
                "r2.service.name" /* serviceName */);
    }

    @Test
    public void testSaveServiceInfoIntoCaches() throws Exception {
        PackageInfo packageInfo1 = createPackageInfo(1000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U0))).thenReturn(packageInfo1);
        PackageInfo packageInfo2 = createPackageInfo(2000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo2.serviceInfo.packageName),
                anyInt(), eq(U1))).thenReturn(packageInfo2);

        TestRegisteredServicesCache testServicesCache = spy(
                new TestRegisteredServicesCache(mMockInjector, null /* serializerAndParser */));
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo1 = newServiceInfo(
                mTestServiceType1, UID1, mResolveInfo1.serviceInfo.getComponentName(),
                1000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U0, mResolveInfo1, serviceInfo1);

        int u1uid = UserHandle.getUid(U1, UID1);
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo2 = newServiceInfo(
                mTestServiceType2, u1uid, mResolveInfo2.serviceInfo.getComponentName(),
                2000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U1, mResolveInfo2, serviceInfo2);

        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));
        testServicesCache.getAllServices(U1);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo2), eq(2000L));

        reset(testServicesCache);

        testServicesCache.invalidateCache(U0);
        testServicesCache.invalidateCache(U1);
        testServicesCache.getAllServices(U0);
        verify(testServicesCache, never()).parseServiceInfo(eq(mResolveInfo1), eq(1000L));
        testServicesCache.getAllServices(U1);
        verify(testServicesCache, never()).parseServiceInfo(eq(mResolveInfo2), eq(2000L));
    }

    @Test
    public void testClearServiceInfoCachesAfterRemoveUserId() throws Exception {
        PackageInfo packageInfo1 = createPackageInfo(1000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U0))).thenReturn(packageInfo1);

        TestRegisteredServicesCache testServicesCache = spy(
                new TestRegisteredServicesCache(mMockInjector, null /* serializerAndParser */));
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo1 = newServiceInfo(
                mTestServiceType1, UID1, mResolveInfo1.serviceInfo.getComponentName(),
                1000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U0, mResolveInfo1, serviceInfo1);

        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));

        reset(testServicesCache);

        testServicesCache.onUserRemoved(U0);
        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));
    }

    @Test
    public void testGetServiceInfoCachesForMultiUser() throws Exception {
        PackageInfo packageInfo1 = createPackageInfo(1000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U0))).thenReturn(packageInfo1);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U1))).thenReturn(packageInfo1);

        TestRegisteredServicesCache testServicesCache = spy(
                new TestRegisteredServicesCache(mMockInjector, null /* serializerAndParser */));
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo1 = newServiceInfo(
                mTestServiceType1, UID1, mResolveInfo1.serviceInfo.getComponentName(),
                1000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U0, mResolveInfo1, serviceInfo1);

        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));

        reset(testServicesCache);

        int u1uid = UserHandle.getUid(U1, UID1);
        assertThat(u1uid).isNotEqualTo(UID1);

        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo2 = newServiceInfo(
                mTestServiceType1, u1uid, mResolveInfo1.serviceInfo.getComponentName(),
                1000L /* lastUpdateTime */);
        mResolveInfo1.setResolveInfoId(U1);
        testServicesCache.addServiceForQuerying(U1, mResolveInfo1, serviceInfo2);

        testServicesCache.getAllServices(U1);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));

        reset(testServicesCache);

        testServicesCache.invalidateCache(U0);
        testServicesCache.invalidateCache(U1);

        // There is a bug to return the same info from the cache for different users. Make sure it
        // will return the different info from the cache for different users.
        Collection<RegisteredServicesCache.ServiceInfo<TestServiceType>> serviceInfos;
        serviceInfos = testServicesCache.getAllServices(U0);
        // Make sure the service info is retrieved from the cache for U0.
        verify(testServicesCache, never()).parseServiceInfo(eq(mResolveInfo1), eq(1000L));
        for (RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo : serviceInfos) {
            assertThat(serviceInfo.componentInfo.applicationInfo.uid).isEqualTo(UID1);
        }

        serviceInfos = testServicesCache.getAllServices(U1);
        // Make sure the service info is retrieved from the cache for U1.
        verify(testServicesCache, never()).parseServiceInfo(eq(mResolveInfo2), eq(2000L));
        for (RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo : serviceInfos) {
            assertThat(serviceInfo.componentInfo.applicationInfo.uid).isEqualTo(u1uid);
        }
    }

    @Test
    public void testUpdateServiceInfoIntoCachesWhenPackageInfoNotFound() throws Exception {
        PackageInfo packageInfo1 = createPackageInfo(1000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U0))).thenReturn(packageInfo1);

        TestRegisteredServicesCache testServicesCache = spy(
                new TestRegisteredServicesCache(mMockInjector, null /* serializerAndParser */));
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo1 = newServiceInfo(
                mTestServiceType1, UID1, mResolveInfo1.serviceInfo.getComponentName(),
                1000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U0, mResolveInfo1, serviceInfo1);

        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));

        reset(testServicesCache);
        reset(mMockPackageManager);

        doThrow(new SecurityException("")).when(mMockPackageManager).getPackageInfoAsUser(
                eq(mResolveInfo1.serviceInfo.packageName), anyInt(), eq(U0));

        testServicesCache.invalidateCache(U0);
        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), anyLong());
    }

    @Test
    public void testUpdateServiceInfoIntoCachesWhenTheApplicationHasBeenUpdated() throws Exception {
        PackageInfo packageInfo1 = createPackageInfo(1000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U0))).thenReturn(packageInfo1);

        TestRegisteredServicesCache testServicesCache = spy(
                new TestRegisteredServicesCache(mMockInjector, null /* serializerAndParser */));
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo1 = newServiceInfo(
                mTestServiceType1, UID1, mResolveInfo1.serviceInfo.getComponentName(),
                1000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U0, mResolveInfo1, serviceInfo1);

        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));

        reset(testServicesCache);
        reset(mMockPackageManager);

        PackageInfo packageInfo2 = createPackageInfo(2000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U0))).thenReturn(packageInfo2);

        testServicesCache.invalidateCache(U0);
        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(2000L));
    }

    @Test
    public void testClearServiceInfoCachesForSingleUserAfterTimeout() throws Exception {
        PackageInfo packageInfo1 = createPackageInfo(1000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U0))).thenReturn(packageInfo1);

        TestRegisteredServicesCache testServicesCache = spy(
                new TestRegisteredServicesCache(mMockInjector, null /* serializerAndParser */));
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo1 = newServiceInfo(
                mTestServiceType1, UID1, mResolveInfo1.serviceInfo.getComponentName(),
                1000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U0, mResolveInfo1, serviceInfo1);

        // Immediately invoke run on the Runnable posted to the handler
        doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.getCallback().run();
            return true;
        }).when(mMockBackgroundHandler).sendMessageAtTime(any(Message.class), anyLong());

        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));
        verify(mMockBackgroundHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());

        reset(testServicesCache);

        testServicesCache.invalidateCache(U0);
        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));
    }

    @Test
    public void testClearServiceInfoCachesForMultiUserAfterTimeout() throws Exception {
        PackageInfo packageInfo1 = createPackageInfo(1000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo1.serviceInfo.packageName),
                anyInt(), eq(U0))).thenReturn(packageInfo1);
        PackageInfo packageInfo2 = createPackageInfo(2000L /* lastUpdateTime */);
        when(mMockPackageManager.getPackageInfoAsUser(eq(mResolveInfo2.serviceInfo.packageName),
                anyInt(), eq(U1))).thenReturn(packageInfo2);

        TestRegisteredServicesCache testServicesCache = spy(
                new TestRegisteredServicesCache(mMockInjector, null /* serializerAndParser */));
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo1 = newServiceInfo(
                mTestServiceType1, UID1, mResolveInfo1.serviceInfo.getComponentName(),
                1000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U0, mResolveInfo1, serviceInfo1);

        int u1uid = UserHandle.getUid(U1, UID1);
        final RegisteredServicesCache.ServiceInfo<TestServiceType> serviceInfo2 = newServiceInfo(
                mTestServiceType2, u1uid, mResolveInfo2.serviceInfo.getComponentName(),
                2000L /* lastUpdateTime */);
        testServicesCache.addServiceForQuerying(U1, mResolveInfo2, serviceInfo2);

        // Don't invoke run on the Runnable for U0 user, and it will not clear the service info of
        // U0 user. Invoke run on the Runnable for U1 user, and it will just clear the service info
        // of U1 user.
        doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (!message.obj.equals(Integer.valueOf(U0))) {
                message.getCallback().run();
            }
            return true;
        }).when(mMockBackgroundHandler).sendMessageAtTime(any(Message.class), anyLong());

        // It will generate the service info of U0 user into cache.
        testServicesCache.getAllServices(U0);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo1), eq(1000L));
        // It will generate the service info of U1 user into cache.
        testServicesCache.getAllServices(U1);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo2), eq(2000L));
        verify(mMockBackgroundHandler, times(2)).sendMessageAtTime(any(Message.class), anyLong());

        reset(testServicesCache);

        testServicesCache.invalidateCache(U0);
        testServicesCache.getAllServices(U0);
        verify(testServicesCache, never()).parseServiceInfo(eq(mResolveInfo1), eq(1000L));

        testServicesCache.invalidateCache(U1);
        testServicesCache.getAllServices(U1);
        verify(testServicesCache, times(1)).parseServiceInfo(eq(mResolveInfo2), eq(2000L));
    }

    private static RegisteredServicesCache.ServiceInfo<TestServiceType> newServiceInfo(
            TestServiceType type, int uid, ComponentName componentName, long lastUpdateTime) {
        final ComponentInfo info = new ComponentInfo();
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.uid = uid;
        return new RegisteredServicesCache.ServiceInfo<>(type, info, componentName, lastUpdateTime);
    }

    private void addServiceInfoIntoResolveInfo(TestResolveInfo resolveInfo, String packageName,
            String serviceName) {
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = packageName;
        serviceInfo.name = serviceName;
        resolveInfo.serviceInfo = serviceInfo;
    }

    private PackageInfo createPackageInfo(long lastUpdateTime) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.lastUpdateTime = lastUpdateTime;
        return packageInfo;
    }

    /**
     * Mock implementation of {@link android.content.pm.RegisteredServicesCache} for testing
     */
    public class TestRegisteredServicesCache extends RegisteredServicesCache<TestServiceType> {
        static final String SERVICE_INTERFACE = "RegisteredServicesCacheUnitTest";
        static final String SERVICE_META_DATA = "RegisteredServicesCacheUnitTest";
        static final String ATTRIBUTES_NAME = "test";
        private SparseArray<Map<TestResolveInfo, ServiceInfo<TestServiceType>>> mServices =
                new SparseArray<>();

        public TestRegisteredServicesCache(Injector<TestServiceType> injector,
                XmlSerializerAndParser<TestServiceType> serializerAndParser) {
            super(injector, SERVICE_INTERFACE, SERVICE_META_DATA, ATTRIBUTES_NAME,
                    serializerAndParser);
        }

        @Override
        public TestServiceType parseServiceAttributes(Resources res, String packageName,
                AttributeSet attrs) {
            return null;
        }

        @Override
        protected List<ResolveInfo> queryIntentServices(int userId) {
            Map<TestResolveInfo, ServiceInfo<TestServiceType>> map = mServices.get(userId,
                    new HashMap<TestResolveInfo, ServiceInfo<TestServiceType>>());
            return new ArrayList<>(map.keySet());
        }

        void addServiceForQuerying(int userId, TestResolveInfo resolveInfo,
                ServiceInfo<TestServiceType> serviceInfo) {
            Map<TestResolveInfo, ServiceInfo<TestServiceType>> map = mServices.get(userId);
            if (map == null) {
                map = new HashMap<>();
                mServices.put(userId, map);
            }
            map.put(resolveInfo, serviceInfo);
        }

        @Override
        protected ServiceInfo<TestServiceType> parseServiceInfo(ResolveInfo resolveInfo,
                long lastUpdateTime) throws XmlPullParserException, IOException {
            int size = mServices.size();
            for (int i = 0; i < size; i++) {
                Map<TestResolveInfo, ServiceInfo<TestServiceType>> map = mServices.valueAt(i);
                ServiceInfo<TestServiceType> serviceInfo = map.get(resolveInfo);
                if (serviceInfo != null) {
                    return serviceInfo;
                }
            }
            throw new IllegalArgumentException("Unexpected service " + resolveInfo);
        }

        @Override
        public void onUserRemoved(int userId) {
            super.onUserRemoved(userId);
        }
    }

    /**
     * Create different hash code with the same {@link android.content.pm.ResolveInfo} for testing.
     */
    public static class TestResolveInfo extends ResolveInfo {
        int mResolveInfoId = 0;

        @Override
        public int hashCode() {
            return Objects.hash(mResolveInfoId, serviceInfo);
        }

        public void setResolveInfoId(int resolveInfoId) {
            mResolveInfoId = resolveInfoId;
        }
    }
}
