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

package com.android.systemui.volume.dialog.ui.viewmodel

import com.android.internal.logging.UiEventLogger
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.VolumeDialog
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogPlugin
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogPluginScope
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogCsdWarningInteractor
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogSafetyWarningInteractor
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogVisibilityInteractor
import com.android.systemui.volume.dialog.shared.VolumeDialogLogger
import com.android.systemui.volume.dialog.shared.model.CsdWarningConfigModel
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.android.systemui.volume.dialog.ui.VolumeDialogUiEvent
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest

@VolumeDialogPluginScope
class VolumeDialogPluginViewModel
@Inject
constructor(
    @VolumeDialogPlugin private val coroutineScope: CoroutineScope,
    private val dialogVisibilityInteractor: VolumeDialogVisibilityInteractor,
    private val dialogSafetyWarningInteractor: VolumeDialogSafetyWarningInteractor,
    private val dialogCsdWarningInteractor: VolumeDialogCsdWarningInteractor,
    private val volumeDialogProvider: Provider<VolumeDialog>,
    private val logger: VolumeDialogLogger,
    val csdWarningConfigModel: CsdWarningConfigModel,
    private val uiEventLogger: UiEventLogger,
) {

    fun launchVolumeDialog() {
        dialogVisibilityInteractor.dialogVisibility
            .mapLatest { visibilityModel ->
                with(visibilityModel) {
                    if (this is VolumeDialogVisibilityModel.Visible) {
                        showDialog()
                        toVolumeDialogUiEvent()?.let(uiEventLogger::log)
                        logger.onShow(reason)
                    }
                    if (this is VolumeDialogVisibilityModel.Dismissed) {
                        toVolumeDialogUiEvent()?.let(uiEventLogger::log)
                        logger.onDismiss(reason)
                    }
                }
            }
            .launchIn(coroutineScope)
    }

    val isShowingSafetyWarning: Flow<Boolean> = dialogSafetyWarningInteractor.isShowingSafetyWarning
    val csdWarning: Flow<Int?> = dialogCsdWarningInteractor.csdWarning

    fun onSafetyWarningDialogShown() {
        dialogVisibilityInteractor.resetDismissTimeout()
    }

    fun onSafetyWarningDismissed() {
        dialogSafetyWarningInteractor.onSafetyWarningDismissed()
    }

    fun onCsdWarningDialogShown() {
        dialogVisibilityInteractor.resetDismissTimeout()
    }

    fun onCsdWarningDismissed() {
        dialogCsdWarningInteractor.onCsdWarningDismissed()
    }

    private fun showDialog() {
        volumeDialogProvider
            .get()
            .apply {
                setOnDismissListener {
                    dialogVisibilityInteractor.dismissDialog(Events.DISMISS_REASON_UNKNOWN)
                }
            }
            .show()
    }
}

private fun VolumeDialogVisibilityModel.Dismissed.toVolumeDialogUiEvent(): VolumeDialogUiEvent? {
    return when (reason) {
        Events.DISMISS_REASON_TOUCH_OUTSIDE ->
            VolumeDialogUiEvent.VOLUME_DIALOG_DISMISS_TOUCH_OUTSIDE
        Events.DISMISS_REASON_VOLUME_CONTROLLER -> VolumeDialogUiEvent.VOLUME_DIALOG_DISMISS_SYSTEM
        Events.DISMISS_REASON_TIMEOUT -> VolumeDialogUiEvent.VOLUME_DIALOG_DISMISS_TIMEOUT
        Events.DISMISS_REASON_SCREEN_OFF -> VolumeDialogUiEvent.VOLUME_DIALOG_DISMISS_SCREEN_OFF
        Events.DISMISS_REASON_SETTINGS_CLICKED -> VolumeDialogUiEvent.VOLUME_DIALOG_DISMISS_SETTINGS
        Events.DISMISS_STREAM_GONE -> VolumeDialogUiEvent.VOLUME_DIALOG_DISMISS_STREAM_GONE
        Events.DISMISS_REASON_USB_OVERHEAD_ALARM_CHANGED ->
            VolumeDialogUiEvent.VOLUME_DIALOG_DISMISS_USB_TEMP_ALARM_CHANGED
        else -> null
    }
}

private fun VolumeDialogVisibilityModel.Visible.toVolumeDialogUiEvent(): VolumeDialogUiEvent? {
    return when (reason) {
        Events.SHOW_REASON_VOLUME_CHANGED -> VolumeDialogUiEvent.VOLUME_DIALOG_SHOW_VOLUME_CHANGED
        Events.SHOW_REASON_REMOTE_VOLUME_CHANGED ->
            VolumeDialogUiEvent.VOLUME_DIALOG_SHOW_REMOTE_VOLUME_CHANGED
        Events.SHOW_REASON_USB_OVERHEAD_ALARM_CHANGED ->
            VolumeDialogUiEvent.VOLUME_DIALOG_SHOW_USB_TEMP_ALARM_CHANGED
        else -> null
    }
}
