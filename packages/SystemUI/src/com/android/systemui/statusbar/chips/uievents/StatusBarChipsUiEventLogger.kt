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

package com.android.systemui.statusbar.chips.uievents

import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.chips.call.ui.viewmodel.CallChipViewModel
import com.android.systemui.statusbar.chips.casttootherdevice.ui.viewmodel.CastToOtherDeviceChipViewModel
import com.android.systemui.statusbar.chips.screenrecord.ui.viewmodel.ScreenRecordChipViewModel
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.ShareToAppChipViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.model.ChipsVisibilityModel
import com.android.systemui.util.kotlin.pairwise
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Does all the UiEvent-related logging for the status bar chips. */
@SysUISingleton
class StatusBarChipsUiEventLogger @Inject constructor(private val logger: UiEventLogger) {
    private val instanceIdSequence = InstanceIdSequence(INSTANCE_ID_MAX)

    /** Get a new instance ID for a status bar chip. */
    fun createNewInstanceId(): InstanceId {
        return instanceIdSequence.newInstanceId()
    }

    /** Logs that the chip with the given ID was tapped to show additional information. */
    fun logChipTapToShow(instanceId: InstanceId?) {
        logger.log(StatusBarChipUiEvent.STATUS_BAR_CHIP_TAP_TO_SHOW, instanceId)
    }

    /**
     * Logs that the chip with the given ID was tapped to hide the additional information that was
     * previously shown.
     */
    fun logChipTapToHide(instanceId: InstanceId?) {
        logger.log(StatusBarChipUiEvent.STATUS_BAR_CHIP_TAP_TO_HIDE, instanceId)
    }

    /** Starts UiEvent logging for the chips. */
    suspend fun hydrateUiEventLogging(chipsFlow: Flow<ChipsVisibilityModel>) {
        coroutineScope {
            launch {
                chipsFlow
                    .map { it.chips }
                    .distinctUntilChanged()
                    .pairwise()
                    .collect { (old, new) ->
                        val oldActive: Map<String, Pair<InstanceId?, Int>> =
                            old.active.withIndex().associate {
                                it.value.key to Pair(it.value.instanceId, it.index)
                            }
                        val newActive: Map<String, Pair<InstanceId?, Int>> =
                            new.active.withIndex().associate {
                                it.value.key to Pair(it.value.instanceId, it.index)
                            }

                        // Newly active keys
                        newActive.keys.minus(oldActive.keys).forEach { key ->
                            val uiEvent = key.getUiEventForNewChip()
                            val instanceId = newActive[key]!!.first
                            val position = newActive[key]!!.second
                            logger.logWithInstanceIdAndPosition(
                                uiEvent,
                                /* uid= */ 0,
                                /* packageName= */ null,
                                instanceId,
                                position,
                            )
                        }

                        // Newly inactive keys
                        oldActive.keys.minus(newActive.keys).forEach { key ->
                            val instanceId = oldActive[key]?.first
                            logger.log(StatusBarChipUiEvent.STATUS_BAR_CHIP_REMOVED, instanceId)
                        }
                    }
            }
        }
    }

    companion object {
        private const val INSTANCE_ID_MAX = 1 shl 20

        /**
         * Given a key from an [OngoingActivityChipModel.Active] instance that was just added,
         * return the right UiEvent type to log.
         */
        private fun String.getUiEventForNewChip(): StatusBarChipUiEvent {
            return when {
                this == ScreenRecordChipViewModel.KEY ->
                    StatusBarChipUiEvent.STATUS_BAR_NEW_CHIP_SCREEN_RECORD
                this == ShareToAppChipViewModel.KEY ->
                    StatusBarChipUiEvent.STATUS_BAR_NEW_CHIP_SHARE_TO_APP
                this == CastToOtherDeviceChipViewModel.KEY ->
                    StatusBarChipUiEvent.STATUS_BAR_NEW_CHIP_CAST_TO_OTHER_DEVICE
                this.startsWith(CallChipViewModel.KEY_PREFIX) ->
                    StatusBarChipUiEvent.STATUS_BAR_NEW_CHIP_CALL
                else -> StatusBarChipUiEvent.STATUS_BAR_NEW_CHIP_NOTIFICATION
            }
        }
    }
}
