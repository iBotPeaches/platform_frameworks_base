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

#define LOG_TAG "GnssAssistanceCbJni"

#include "GnssAssistanceCallback.h"

#include "Utils.h"

namespace {
jmethodID method_gnssAssistanceInjectRequest;
} // anonymous namespace

namespace android::gnss {

using binder::Status;
using hardware::Return;
using hardware::Void;

void GnssAssistanceCallback_class_init_once(JNIEnv* env, jclass clazz) {
    method_gnssAssistanceInjectRequest =
            env->GetStaticMethodID(clazz, "gnssAssistanceInjectRequest", "()V");
}

// Implementation of android::hardware::gnss::gnss_assistance::GnssAssistanceCallback.

Status GnssAssistanceCallback::injectRequestCb() {
    ALOGD("%s.", __func__);
    JNIEnv* env = getJniEnv();
    env->CallVoidMethod(mCallbacksObj, method_gnssAssistanceInjectRequest);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
    return Status::ok();
}

} // namespace android::gnss
