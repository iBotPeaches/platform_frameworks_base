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

package com.android.systemui.communal.util

import android.view.MotionEvent
import com.android.systemui.communal.dagger.CommunalModule.Companion.TOUCH_NOTIFICATION_RATE_LIMIT
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.power.domain.interactor.PowerInteractor
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * {@link UserTouchActivityNotifier} helps rate limit the user activity notifications sent to {@link
 * PowerManager} from a single touch source.
 */
class UserTouchActivityNotifier
@Inject
constructor(
    @Background private val scope: CoroutineScope,
    private val powerInteractor: PowerInteractor,
    @Named(TOUCH_NOTIFICATION_RATE_LIMIT) private val rateLimitMs: Int,
) {
    private var lastNotification: Long? = null

    fun notifyActivity(event: MotionEvent) {
        val metered =
            when (event.action) {
                MotionEvent.ACTION_CANCEL -> false
                MotionEvent.ACTION_UP -> false
                MotionEvent.ACTION_DOWN -> false
                else -> true
            }

        if (metered && lastNotification?.let { event.eventTime - it < rateLimitMs } == true) {
            return
        }

        lastNotification = event.eventTime

        scope.launch { powerInteractor.onUserTouch() }
    }
}
