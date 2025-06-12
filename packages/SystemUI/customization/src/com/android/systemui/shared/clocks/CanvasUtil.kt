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

package com.android.systemui.shared.clocks

import android.graphics.Canvas
import com.android.systemui.plugins.clocks.VPoint
import com.android.systemui.plugins.clocks.VPointF

object CanvasUtil {
    fun Canvas.translate(pt: VPointF) = this.translate(pt.x, pt.y)

    fun Canvas.translate(pt: VPoint) = this.translate(pt.x.toFloat(), pt.y.toFloat())

    fun <T> Canvas.use(func: (Canvas) -> T): T {
        val saveNum = save()
        val result = func(this)
        restoreToCount(saveNum)
        return result
    }
}
