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

package com.android.systemui.cursorposition.data.repository

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_TOUCHPAD
import android.view.MotionEvent
import com.android.app.displaylib.PerDisplayInstanceProviderWithTeardown
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.system.InputChannelCompat
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver
import com.android.systemui.shared.system.InputMonitorCompat
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/** Repository for cursor position in single display. */
interface SingleDisplayCursorPositionRepository {
    /** Flow of [CursorPosition] for the display. */
    val cursorPositions: Flow<CursorPosition>

    /** Destroys the repository. */
    fun destroy()
}

/**
 * Implementation of [SingleDisplayCursorPositionRepository].
 *
 * @param displayId the display id
 * @param backgroundHandler the background handler
 * @param listenerBuilder the builder for [InputChannelCompat.InputEventListener]
 * @param inputMonitorBuilder the builder for [InputMonitorCompat]
 */
class SingleDisplayCursorPositionRepositoryImpl
@AssistedInject
constructor(
    @Assisted displayId: Int,
    @Background private val backgroundHandler: Handler,
    @Assisted
    private val listenerBuilder: InputEventListenerBuilder = defaultInputEventListenerBuilder,
    @Assisted private val inputMonitorBuilder: InputMonitorBuilder = defaultInputMonitorBuilder,
) : SingleDisplayCursorPositionRepository {

    private var scope: ProducerScope<CursorPosition>? = null

    private fun createInputMonitorCallbackFlow(displayId: Int): Flow<CursorPosition> =
        conflatedCallbackFlow {
                val inputMonitor: InputMonitorCompat = inputMonitorBuilder.build(TAG, displayId)
                val inputReceiver: InputEventReceiver =
                    inputMonitor.getInputReceiver(
                        Looper.myLooper(),
                        Choreographer.getInstance(),
                        listenerBuilder.build(this),
                    )
                scope = this
                awaitClose {
                    inputMonitor.dispose()
                    inputReceiver.dispose()
                }
            }
            // Use backgroundHandler as dispatcher because it has a looper (unlike
            // "backgroundDispatcher" which does not have a looper) and input receiver could use
            // its background looper and choreographer
            .flowOn(backgroundHandler.asCoroutineDispatcher())

    override val cursorPositions: Flow<CursorPosition> = createInputMonitorCallbackFlow(displayId)

    override fun destroy() {
        scope?.close()
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates a new instance of [SingleDisplayCursorPositionRepositoryImpl] for a given
         * [displayId].
         */
        fun create(
            displayId: Int,
            listenerBuilder: InputEventListenerBuilder = defaultInputEventListenerBuilder,
            inputMonitorBuilder: InputMonitorBuilder = defaultInputMonitorBuilder,
        ): SingleDisplayCursorPositionRepositoryImpl
    }

    companion object {
        private const val TAG = "CursorPositionPerDisplayRepositoryImpl"

        private val defaultInputMonitorBuilder = InputMonitorBuilder { name, displayId ->
            InputMonitorCompat(name, displayId)
        }

        val defaultInputEventListenerBuilder = InputEventListenerBuilder { channel ->
            InputChannelCompat.InputEventListener { event ->
                if (
                    event is MotionEvent &&
                        (event.source == SOURCE_MOUSE || event.source == SOURCE_TOUCHPAD)
                ) {
                    val cursorEvent = CursorPosition(event.x, event.y, event.displayId)
                    channel.trySendWithFailureLogging(cursorEvent, TAG)
                }
            }
        }
    }
}

fun interface InputEventListenerBuilder {
    fun build(channel: SendChannel<CursorPosition>): InputChannelCompat.InputEventListener
}

fun interface InputMonitorBuilder {
    fun build(name: String, displayId: Int): InputMonitorCompat
}

@SysUISingleton
class SingleDisplayCursorPositionRepositoryFactory
@Inject
constructor(private val factory: SingleDisplayCursorPositionRepositoryImpl.Factory) :
    PerDisplayInstanceProviderWithTeardown<SingleDisplayCursorPositionRepository> {
    override fun createInstance(displayId: Int): SingleDisplayCursorPositionRepository {
        return factory.create(displayId)
    }

    override fun destroyInstance(instance: SingleDisplayCursorPositionRepository) {
        instance.destroy()
    }
}
