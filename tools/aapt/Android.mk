#
# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ifeq ($(TARGET_BUILD_APPS)$(filter true,$(TARGET_BUILD_PDK)),)

# ==========================================================
# Setup some common variables for the different build
# targets here.
# ==========================================================
LOCAL_PATH:= $(call my-dir)

aaptHostStaticLibs := \
    libandroidfw \
    libpng \
    libutils \
    liblog \
    libcutils \
    libexpat \
    libziparchive \
    libbase \
    libz

aaptCFlags := -Wall -Werror

# ==========================================================
# Build the host static library: libc++
# ==========================================================
include $(CLEAR_VARS)
LOCAL_MODULE := libc++
LOCAL_CFLAGS += -Wno-format-y2k -DSTATIC_ANDROIDFW_FOR_TOOLS $(aaptCFlags)
LOCAL_CPPFLAGS += $(aaptCppFlags)
LOCAL_CFLAGS_darwin += -D_DARWIN_UNLIMITED_STREAMS
LOCAL_C_INCLUDES += $(aaptCIncludes)
LOCAL_SRC_FILES := $(aaptSources)
include $(BUILD_HOST_STATIC_LIBRARY)

# ==========================================================
# Build the host executable: aapt
# ==========================================================
include $(CLEAR_VARS)

LOCAL_MODULE := aapt
LOCAL_MODULE_HOST_OS := darwin linux windows
LOCAL_CFLAGS := -DAAPT_VERSION=\"$(BUILD_NUMBER_FROM_FILE)\" $(aaptCFlags)
LOCAL_SRC_FILES := Main.cpp
LOCAL_STATIC_LIBRARIES := libaapt $(aaptHostStaticLibs)
LOCAL_CXX_STL := libc++_static

include $(BUILD_HOST_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := aapt_target
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_CFLAGS := -DAAPT_VERSION=\"$(BUILD_NUMBER_FROM_FILE)\" $(aaptCFlags)
LOCAL_SRC_FILES := Main.cpp
LOCAL_STATIC_LIBRARIES := libaapt_target $(aaptHostStaticLibs)
include $(BUILD_EXECUTABLE) 

endif # No TARGET_BUILD_APPS or TARGET_BUILD_PDK
