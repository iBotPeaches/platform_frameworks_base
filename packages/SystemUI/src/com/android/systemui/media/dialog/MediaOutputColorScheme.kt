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

package com.android.systemui.media.dialog

import android.content.Context
import com.android.systemui.monet.ColorScheme
import com.android.systemui.res.R

abstract class MediaOutputColorScheme {
    companion object Factory {
        @JvmStatic
        fun fromDynamicColors(dynamicScheme: ColorScheme): MediaOutputColorScheme {
            return MediaOutputColorSchemeDynamic(dynamicScheme)
        }

        @JvmStatic
        fun fromSystemColors(context: Context): MediaOutputColorScheme {
            return MediaOutputColorSchemeSystem(context)
        }
    }

    abstract fun getPrimary(): Int

    abstract fun getOnPrimary(): Int

    abstract fun getSecondary(): Int

    abstract fun getSecondaryContainer(): Int

    abstract fun getSurfaceContainer(): Int

    abstract fun getSurfaceContainerHigh(): Int

    abstract fun getOnSurface(): Int

    abstract fun getOnSurfaceVariant(): Int

    abstract fun getOutline(): Int

    abstract fun getOutlineVariant(): Int
}

class MediaOutputColorSchemeDynamic(dynamicScheme: ColorScheme) : MediaOutputColorScheme() {
    private val mMaterialScheme = dynamicScheme.materialScheme

    override fun getPrimary() = mMaterialScheme.primary

    override fun getOnPrimary() = mMaterialScheme.onPrimary

    override fun getSecondary() = mMaterialScheme.secondary

    override fun getSecondaryContainer() = mMaterialScheme.secondaryContainer

    override fun getSurfaceContainer() = mMaterialScheme.surfaceContainer

    override fun getSurfaceContainerHigh() = mMaterialScheme.surfaceContainerHigh

    override fun getOnSurface() = mMaterialScheme.onSurface

    override fun getOnSurfaceVariant() = mMaterialScheme.onSurfaceVariant

    override fun getOutline() = mMaterialScheme.outline

    override fun getOutlineVariant() = mMaterialScheme.outlineVariant
}

class MediaOutputColorSchemeSystem(private val mContext: Context) : MediaOutputColorScheme() {
    override fun getPrimary() = mContext.getColor(R.color.media_dialog_primary)

    override fun getOnPrimary() = mContext.getColor(R.color.media_dialog_on_primary)

    override fun getSecondary() = mContext.getColor(R.color.media_dialog_secondary)

    override fun getSecondaryContainer() =
        mContext.getColor(R.color.media_dialog_secondary_container)

    override fun getSurfaceContainer() = mContext.getColor(R.color.media_dialog_surface_container)

    override fun getSurfaceContainerHigh() =
        mContext.getColor(R.color.media_dialog_surface_container_high)

    override fun getOnSurface() = mContext.getColor(R.color.media_dialog_on_surface)

    override fun getOnSurfaceVariant() = mContext.getColor(R.color.media_dialog_on_surface_variant)

    override fun getOutline() = mContext.getColor(R.color.media_dialog_outline)

    override fun getOutlineVariant() = mContext.getColor(R.color.media_dialog_outline_variant)
}
