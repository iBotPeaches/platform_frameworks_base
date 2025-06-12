/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.media;

import static android.media.MediaRoute2ProviderService.REASON_FAILED_TO_REROUTE_SYSTEM_MEDIA;
import static android.media.MediaRoute2ProviderService.REASON_INVALID_COMMAND;
import static android.media.MediaRoute2ProviderService.REASON_NETWORK_ERROR;
import static android.media.MediaRoute2ProviderService.REASON_REJECTED;
import static android.media.MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE;
import static android.media.MediaRoute2ProviderService.REASON_UNIMPLEMENTED;
import static android.media.MediaRoute2ProviderService.REASON_UNKNOWN_ERROR;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_FAILED_TO_REROUTE_SYSTEM_MEDIA;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_NETWORK_ERROR;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTE_NOT_AVAILABLE;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNIMPLEMENTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNKNOWN_ERROR;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class MediaRouterMetricLoggerTest {
    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(MediaRouterStatsLog.class).build();

    private MediaRouterMetricLogger mLogger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLogger = new MediaRouterMetricLogger();
    }

    @Test
    public void addRequestInfo_addsRequestInfoToCache() {
        long requestId = 123;
        int eventType = MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION;

        mLogger.addRequestInfo(requestId, eventType);

        assertThat(mLogger.getRequestCacheSize()).isEqualTo(1);
    }

    @Test
    public void removeRequestInfo_removesRequestInfoFromCache() {
        long requestId = 123;
        int eventType = MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION;
        mLogger.addRequestInfo(requestId, eventType);

        mLogger.removeRequestInfo(requestId);

        assertThat(mLogger.getRequestCacheSize()).isEqualTo(0);
    }

    @Test
    public void logOperationFailure_logsOperationFailure() {
        int eventType = MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION;
        int result = MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED;
        mLogger.logOperationFailure(eventType, result);
        verify(
                () ->
                        MediaRouterStatsLog.write( // Use ExtendedMockito.verify and lambda
                                MEDIA_ROUTER_EVENT_REPORTED, eventType, result));
    }

    @Test
    public void logRequestResult_logsRequestResult() {
        long requestId = 123;
        int eventType = MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION;
        int result = MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS;
        mLogger.addRequestInfo(requestId, eventType);

        mLogger.logRequestResult(requestId, result);

        assertThat(mLogger.getRequestCacheSize()).isEqualTo(0);
        verify(
                () ->
                        MediaRouterStatsLog.write( // Use ExtendedMockito.verify and lambda
                                MEDIA_ROUTER_EVENT_REPORTED, eventType, result));
    }

    @Test
    public void convertResultFromReason_returnsCorrectResult() {
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_UNKNOWN_ERROR))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNKNOWN_ERROR);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_REJECTED))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_NETWORK_ERROR))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_NETWORK_ERROR);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_ROUTE_NOT_AVAILABLE))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTE_NOT_AVAILABLE);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_INVALID_COMMAND))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_UNIMPLEMENTED))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNIMPLEMENTED);
        assertThat(
                        MediaRouterMetricLogger.convertResultFromReason(
                                REASON_FAILED_TO_REROUTE_SYSTEM_MEDIA))
                .isEqualTo(
                        MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_FAILED_TO_REROUTE_SYSTEM_MEDIA);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(-1))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED);
    }

    @Test
    public void getRequestCacheSize_returnsCorrectSize() {
        assertThat(mLogger.getRequestCacheSize()).isEqualTo(0);
        mLogger.addRequestInfo(
                123, MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION);
        assertThat(mLogger.getRequestCacheSize()).isEqualTo(1);
    }
}
