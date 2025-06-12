/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef ANDROID_GUI_BUFFERITEMCONSUMER_H
#define ANDROID_GUI_BUFFERITEMCONSUMER_H

#include <com_android_graphics_libgui_flags.h>
#include <gui/BufferQueue.h>
#include <gui/ConsumerBase.h>
#include <gui/IGraphicBufferConsumer.h>
#include <utils/RefBase.h>

namespace android {

class BufferItemConsumer : public ConsumerBase {
public:
    BufferItemConsumer(const sp<IGraphicBufferConsumer>& consumer, uint64_t consumerUsage,
                       int bufferCount = -1, bool controlledByApp = false)
          : mConsumer(consumer) {}

#if COM_ANDROID_GRAPHICS_LIBGUI_FLAGS(WB_CONSUMER_BASE_OWNS_BQ)
    BufferItemConsumer(uint64_t consumerUsage, int bufferCount = -1,
                       bool controlledByApp = false, bool isConsumerSurfaceFlinger = false) {
        sp<IGraphicBufferProducer> producer;
        BufferQueue::createBufferQueue(&producer, &mConsumer);
        mSurface = sp<Surface>::make(producer, controlledByApp);
    }

    status_t setConsumerIsProtected(bool isProtected) {
        return OK;
    }
#endif // COM_ANDROID_GRAPHICS_LIBGUI_FLAGS(WB_CONSUMER_BASE_OWNS_BQ)

    status_t acquireBuffer(BufferItem* item, nsecs_t presentWhen, bool waitForFence = true) {
        return mConsumer->acquireBuffer(item, presentWhen, 0);
    }

    status_t attachBuffer(BufferItem*, const sp<GraphicBuffer>&) {
        return INVALID_OPERATION;
    }

    status_t releaseBuffer(const BufferItem& item,
                           const sp<Fence>& releaseFence = Fence::NO_FENCE) {
        return OK;
    }

    void setName(const String8& name) {}

    void setFrameAvailableListener(const wp<FrameAvailableListener>& listener) {}

    status_t setDefaultBufferSize(uint32_t width, uint32_t height) {
        return mConsumer->setDefaultBufferSize(width, height);
    }

    status_t setDefaultBufferFormat(PixelFormat defaultFormat) {
        return mConsumer->setDefaultBufferFormat(defaultFormat);
    }

    status_t setDefaultBufferDataSpace(android_dataspace defaultDataSpace) {
        return mConsumer->setDefaultBufferDataSpace(defaultDataSpace);
    }

    void abandon() {}

    status_t detachBuffer(int slot) {
        return OK;
    }

    status_t discardFreeBuffers() {
        return OK;
    }

    void freeBufferLocked(int slotIndex) {}

    status_t addReleaseFenceLocked(int slot, const sp<GraphicBuffer> graphicBuffer,
                                   const sp<Fence>& fence) {
        return OK;
    }

#if COM_ANDROID_GRAPHICS_LIBGUI_FLAGS(WB_CONSUMER_BASE_OWNS_BQ)
// Returns a Surface that can be used as the producer for this consumer.
    sp<Surface> getSurface() const {
        return mSurface;
    }
#endif // COM_ANDROID_GRAPHICS_LIBGUI_FLAGS(WB_CONSUMER_BASE_OWNS_BQ)

private:
    sp<IGraphicBufferConsumer> mConsumer;
#if COM_ANDROID_GRAPHICS_LIBGUI_FLAGS(WB_CONSUMER_BASE_OWNS_BQ)
        // This Surface wraps the IGraphicBufferConsumer created for this
    // ConsumerBase.
    sp<Surface> mSurface;
#endif // COM_ANDROID_GRAPHICS_LIBGUI_FLAGS(WB_CONSUMER_BASE_OWNS_BQ)
};

} // namespace android

#endif // ANDROID_GUI_BUFFERITEMCONSUMER_H
