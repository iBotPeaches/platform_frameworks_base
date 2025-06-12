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

package com.android.systemui.clipboardoverlay

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.text.TextUtils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class ActionIntentCreator
@Inject
constructor(
    private val context: Context,
    private val packageManager: PackageManager,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : IntentCreator {
    override fun getTextEditorIntent(context: Context?) =
        Intent(context, EditTextActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

    override fun getShareIntent(clipData: ClipData, context: Context?): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND)

        // From the ACTION_SEND docs:
        //   "If using EXTRA_TEXT, the MIME type should be "text/plain"; otherwise it should be the
        //    MIME type of the data in EXTRA_STREAM"
        val uri = clipData.getItemAt(0).uri
        shareIntent.apply {
            if (uri != null) {
                // We don't use setData here because some apps interpret this as "to:".
                setType(clipData.description.getMimeType(0))
                // Include URI in ClipData also, so that grantPermission picks it up.
                setClipData(
                    ClipData(
                        ClipDescription("content", arrayOf(clipData.description.getMimeType(0))),
                        ClipData.Item(uri),
                    )
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, clipData.getItemAt(0).coerceToText(context).toString())
                setType("text/plain")
            }
        }

        return Intent.createChooser(shareIntent, null)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    suspend fun getImageEditIntent(uri: Uri?, context: Context): Intent {
        return Intent(Intent.ACTION_EDIT).apply {
            // Use the preferred editor if it's available, otherwise fall back to the default editor
            component = preferredEditor() ?: defaultEditor()
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_EDIT_SOURCE, EDIT_SOURCE_CLIPBOARD)
        }
    }

    override fun getImageEditIntentAsync(
        uri: Uri?,
        context: Context,
        outputConsumer: Consumer<Intent>,
    ) {
        applicationScope.launch { outputConsumer.accept(getImageEditIntent(uri, context)) }
    }

    override fun getRemoteCopyIntent(clipData: ClipData?, context: Context): Intent {
        val remoteCopyPackage = context.getString(R.string.config_remoteCopyPackage)
        return Intent(REMOTE_COPY_ACTION).apply {
            if (!TextUtils.isEmpty(remoteCopyPackage)) {
                setComponent(ComponentName.unflattenFromString(remoteCopyPackage))
            }

            setClipData(clipData)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private suspend fun preferredEditor(): ComponentName? =
        runCatching {
                val preferredEditor = context.getString(R.string.config_preferredScreenshotEditor)
                val component = ComponentName.unflattenFromString(preferredEditor) ?: return null

                return if (isComponentAvailable(component)) component else null
            }
            .getOrNull()

    private suspend fun isComponentAvailable(component: ComponentName): Boolean =
        withContext(backgroundDispatcher) {
            try {
                val info =
                    packageManager.getPackageInfo(
                        component.packageName,
                        PackageManager.GET_ACTIVITIES,
                    )
                info.activities?.firstOrNull {
                    it.componentName.className == component.className
                } != null
            } catch (e: NameNotFoundException) {
                false
            }
        }

    private fun defaultEditor(): ComponentName? =
        runCatching {
                context.getString(R.string.config_screenshotEditor).let {
                    ComponentName.unflattenFromString(it)
                }
            }
            .getOrNull()

    companion object {
        private const val EXTRA_EDIT_SOURCE: String = "edit_source"
        private const val EDIT_SOURCE_CLIPBOARD: String = "clipboard"
        private const val REMOTE_COPY_ACTION: String = "android.intent.action.REMOTE_COPY"
    }
}
