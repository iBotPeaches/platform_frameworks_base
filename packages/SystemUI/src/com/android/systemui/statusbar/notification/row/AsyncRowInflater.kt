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

package com.android.systemui.statusbar.notification.row

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.annotation.UiThread
import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.withContextTraced
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.qualifiers.NotifInflation
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

@SysUISingleton
class AsyncRowInflater
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    @Main private val mainCoroutineDispatcher: CoroutineDispatcher,
    @NotifInflation private val inflationCoroutineDispatcher: CoroutineDispatcher,
) {
    /**
     * Inflate the layout on the background thread, and invoke the listener on the main thread when
     * finished.
     *
     * If the inflation fails on the background, it will be retried once on the main thread.
     */
    @UiThread
    fun inflate(
        context: Context,
        layoutFactory: LayoutInflater.Factory2,
        @LayoutRes resId: Int,
        parent: ViewGroup,
        listener: OnInflateFinishedListener,
    ): Job {
        val inflater = BasicRowInflater(context).apply { factory2 = layoutFactory }
        return applicationScope.launchTraced("AsyncRowInflater-bg", inflationCoroutineDispatcher) {
            val view =
                try {
                    inflater.inflate(resId, parent, false)
                } catch (ex: RuntimeException) {
                    // Probably a Looper failure, retry on the UI thread
                    Log.w(
                        "AsyncRowInflater",
                        "Failed to inflate resource in the background!" +
                            " Retrying on the UI thread",
                        ex,
                    )
                    null
                }
            withContextTraced("AsyncRowInflater-ui", mainCoroutineDispatcher) {
                // If the inflate failed on the inflation thread, try again on the main thread
                val finalView = view ?: inflater.inflate(resId, parent, false)
                // Inform the listener of the completion
                listener.onInflateFinished(finalView, resId, parent)
            }
        }
    }

    /**
     * Callback interface (identical to the one from AsyncLayoutInflater) for receiving the inflated
     * view
     */
    interface OnInflateFinishedListener {
        @UiThread fun onInflateFinished(view: View, @LayoutRes resId: Int, parent: ViewGroup?)
    }
}
