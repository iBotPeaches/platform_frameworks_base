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

// See: ApplicationSharedMemory.md

#include <cutils/ashmem.h>
#include <errno.h>
#include <fcntl.h>
#include <nativehelper/JNIHelp.h>
#include <string.h>
#include <sys/mman.h>

#include <array>
#include <atomic>
#include <cstddef>
#include <new>

#include "android_app_PropertyInvalidatedCache.h"
#include "core_jni_helpers.h"

namespace {

using namespace android::app::PropertyInvalidatedCache;

class alignas(8) SystemFeaturesCache {
public:
    // We only need enough space to handle the official set of SDK-defined system features (~200).
    // TODO(b/326623529): Reuse the exact value defined by PackageManager.SDK_FEATURE_COUNT.
    static constexpr int32_t kMaxSystemFeatures = 512;

    void writeSystemFeatures(JNIEnv* env, jintArray jfeatures) {
        if (featuresLength.load(std::memory_order_seq_cst) > 0) {
            jniThrowExceptionFmt(env, "java/lang/IllegalStateException",
                                 "SystemFeaturesCache already written.");
            return;
        }

        int32_t jfeaturesLength = env->GetArrayLength(jfeatures);
        if (jfeaturesLength > kMaxSystemFeatures) {
            jniThrowExceptionFmt(env, "java/lang/IllegalArgumentException",
                                 "SystemFeaturesCache only supports %d elements (vs %d requested).",
                                 kMaxSystemFeatures, jfeaturesLength);
            return;
        }
        env->GetIntArrayRegion(jfeatures, 0, jfeaturesLength, features.data());
        featuresLength.store(jfeaturesLength, std::memory_order_seq_cst);
    }

    jintArray readSystemFeatures(JNIEnv* env) const {
        jint jfeaturesLength = static_cast<jint>(featuresLength.load(std::memory_order_seq_cst));
        jintArray jfeatures = env->NewIntArray(jfeaturesLength);
        if (env->ExceptionCheck()) {
            return nullptr;
        }

        env->SetIntArrayRegion(jfeatures, 0, jfeaturesLength, features.data());
        return jfeatures;
    }

private:
    // A fixed length array of feature versions, with |featuresLength| dictating the actual size
    // of features that have been written.
    std::array<int32_t, kMaxSystemFeatures> features = {};
    // The atomic acts as a barrier that precedes reads and follows writes, ensuring a
    // consistent view of |features| across processes. Note that r/w synchronization *within* a
    // process is handled at a higher level.
    std::atomic<int64_t> featuresLength = 0;
};

static_assert(sizeof(SystemFeaturesCache) ==
                      sizeof(int32_t) * SystemFeaturesCache::kMaxSystemFeatures + sizeof(int64_t),
              "Unexpected SystemFeaturesCache size");

// Atomics should be safe to use across processes if they are lock free.
static_assert(std::atomic<int64_t>::is_always_lock_free == true,
              "atomic<int64_t> is not always lock free");

// This is the data structure that is shared between processes.
//
// Tips for extending:
// - Atomics are safe for cross-process use as they are lock free, if they are accessed as
//   individual values.
// - Consider multi-ABI systems, e.g. devices that support launching both 64-bit and 32-bit
//   app processes. Use fixed-size types (e.g. `int64_t`) to ensure that the data structure is
//   the same size across all ABIs. Avoid implicit assumptions about struct packing/padding.
class alignas(8) SharedMemory { // Ensure that `sizeof(SharedMemory)` is the same across 32-bit and
                                // 64-bit systems.
private:
    volatile std::atomic<int64_t> latestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis;

    // LINT.IfChange(invalid_network_time)
    static constexpr int64_t INVALID_NETWORK_TIME = -1;
    // LINT.ThenChange(frameworks/base/core/java/com/android/internal/os/ApplicationSharedMemory.java:invalid_network_time)

public:
    // Default constructor sets initial values
    SharedMemory()
          : latestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis(INVALID_NETWORK_TIME) {}

    int64_t getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis() const {
        return latestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis;
    }

    void setLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis(int64_t offset) {
        latestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis = offset;
    }

    // The fixed size cache storage for SDK-defined system features.
    SystemFeaturesCache systemFeaturesCache;

    // The nonce storage for pic.  The sizing is suitable for the system server module.
    SystemCacheNonce systemPic;
};

// Update the expected values when modifying the members of SharedMemory.
// The goal of this assertion is to ensure that the data structure is the same size across 32-bit
// and 64-bit systems.
// TODO(b/396674280): Add an additional fixed size check for SystemCacheNonce after resolving
// ABI discrepancies.
static_assert(sizeof(SharedMemory) == 8 + sizeof(SystemFeaturesCache) + sizeof(SystemCacheNonce),
              "Unexpected SharedMemory size");
static_assert(offsetof(SharedMemory, systemFeaturesCache) == sizeof(int64_t),
              "Unexpected SystemFeaturesCache offset in SharedMemory");
static_assert(offsetof(SharedMemory, systemPic) ==
                      offsetof(SharedMemory, systemFeaturesCache) + sizeof(SystemFeaturesCache),
              "Unexpected SystemCachceNonce offset in SharedMemory");

static jint nativeCreate(JNIEnv* env, jclass) {
    // Create anonymous shared memory region
    int fd = ashmem_create_region("ApplicationSharedMemory", sizeof(SharedMemory));
    if (fd < 0) {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException", "Failed to create ashmem: %s",
                             strerror(errno));
    }
    return fd;
}

static jlong nativeMap(JNIEnv* env, jclass, jint fd, jboolean isMutable) {
    void* ptr = mmap(nullptr, sizeof(SharedMemory), isMutable ? PROT_READ | PROT_WRITE : PROT_READ,
                     MAP_SHARED, fd, 0);
    if (ptr == MAP_FAILED) {
        close(fd);
        jniThrowExceptionFmt(env, "java/lang/RuntimeException", "Failed to mmap shared memory: %s",
                             strerror(errno));
    }

    return reinterpret_cast<jlong>(ptr);
}

static void nativeInit(JNIEnv* env, jclass, jlong ptr) {
    new (reinterpret_cast<SharedMemory*>(ptr)) SharedMemory();
}

static void nativeUnmap(JNIEnv* env, jclass, jlong ptr) {
    if (munmap(reinterpret_cast<void*>(ptr), sizeof(SharedMemory)) == -1) {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                             "Failed to munmap shared memory: %s", strerror(errno));
    }
}

static jint nativeDupAsReadOnly(JNIEnv* env, jclass, jint fd) {
    // Duplicate file descriptor
    fd = fcntl(fd, F_DUPFD_CLOEXEC, 0);
    if (fd < 0) {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException", "Failed to dup fd: %s",
                             strerror(errno));
    }

    // Set new file descriptor to read-only
    if (ashmem_set_prot_region(fd, PROT_READ)) {
        close(fd);
        jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                             "Failed to ashmem_set_prot_region: %s", strerror(errno));
    }

    return fd;
}

static void nativeSetLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis(jlong ptr,
                                                                                 jlong offset) {
    SharedMemory* sharedMemory = reinterpret_cast<SharedMemory*>(ptr);
    sharedMemory->setLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis(offset);
}

static jlong nativeGetLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis(jlong ptr) {
    SharedMemory* sharedMemory = reinterpret_cast<SharedMemory*>(ptr);
    return sharedMemory->getLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis();
}

// This is a FastNative method.  It takes the usual JNIEnv* and jclass* arguments.
static jlong nativeGetSystemNonceBlock(JNIEnv*, jclass*, jlong ptr) {
    SharedMemory* sharedMemory = reinterpret_cast<SharedMemory*>(ptr);
    return reinterpret_cast<jlong>(&sharedMemory->systemPic);
}

static void nativeWriteSystemFeaturesCache(JNIEnv* env, jclass*, jlong ptr, jintArray jfeatures) {
    SharedMemory* sharedMemory = reinterpret_cast<SharedMemory*>(ptr);
    sharedMemory->systemFeaturesCache.writeSystemFeatures(env, jfeatures);
}

static jintArray nativeReadSystemFeaturesCache(JNIEnv* env, jclass*, jlong ptr) {
    SharedMemory* sharedMemory = reinterpret_cast<SharedMemory*>(ptr);
    return sharedMemory->systemFeaturesCache.readSystemFeatures(env);
}

static const JNINativeMethod gMethods[] = {
        {"nativeCreate", "()I", (void*)nativeCreate},
        {"nativeMap", "(IZ)J", (void*)nativeMap},
        {"nativeInit", "(J)V", (void*)nativeInit},
        {"nativeUnmap", "(J)V", (void*)nativeUnmap},
        {"nativeDupAsReadOnly", "(I)I", (void*)nativeDupAsReadOnly},
        {"nativeSetLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis", "(JJ)V",
         (void*)nativeSetLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis},
        {"nativeGetLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis", "(J)J",
         (void*)nativeGetLatestNetworkTimeUnixEpochMillisAtZeroElapsedRealtimeMillis},
        {"nativeGetSystemNonceBlock", "(J)J", (void*)nativeGetSystemNonceBlock},
        {"nativeWriteSystemFeaturesCache", "(J[I)V", (void*)nativeWriteSystemFeaturesCache},
        {"nativeReadSystemFeaturesCache", "(J)[I", (void*)nativeReadSystemFeaturesCache},
};

static const char kApplicationSharedMemoryClassName[] =
        "com/android/internal/os/ApplicationSharedMemory";
static jclass gApplicationSharedMemoryClass;

} // anonymous namespace

namespace android {

int register_com_android_internal_os_ApplicationSharedMemory(JNIEnv* env) {
    gApplicationSharedMemoryClass =
            MakeGlobalRefOrDie(env, FindClassOrDie(env, kApplicationSharedMemoryClassName));
    RegisterMethodsOrDie(env, "com/android/internal/os/ApplicationSharedMemory", gMethods,
                         NELEM(gMethods));
    return JNI_OK;
}

} // namespace android
