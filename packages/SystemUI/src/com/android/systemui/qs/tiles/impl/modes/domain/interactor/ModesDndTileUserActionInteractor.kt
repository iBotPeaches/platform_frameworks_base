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

package com.android.systemui.qs.tiles.impl.modes.domain.interactor

import android.content.Intent
import android.provider.Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS
import android.provider.Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID
import android.util.Log
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.qs.shared.QSSettingsPackageRepository
import com.android.systemui.qs.tiles.base.domain.actions.QSTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.interactor.QSTileUserActionInteractor
import com.android.systemui.qs.tiles.base.domain.model.QSTileInput
import com.android.systemui.qs.tiles.base.shared.model.QSTileUserAction
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesDndTileModel
import com.android.systemui.statusbar.policy.domain.interactor.ZenModeInteractor
import com.android.systemui.statusbar.policy.ui.dialog.ModesDialogDelegate
import com.android.systemui.statusbar.policy.ui.dialog.ModesDialogEventLogger
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

@SysUISingleton
class ModesDndTileUserActionInteractor
@Inject
constructor(
    @Main private val mainContext: CoroutineContext,
    private val qsTileIntentUserInputHandler: QSTileIntentUserInputHandler,
    // TODO(b/353896370): The domain layer should not have to depend on the UI layer.
    private val dialogDelegate: ModesDialogDelegate,
    private val zenModeInteractor: ZenModeInteractor,
    private val dialogEventLogger: ModesDialogEventLogger,
    private val settingsPackageRepository: QSSettingsPackageRepository,
) : QSTileUserActionInteractor<ModesDndTileModel> {

    override suspend fun handleInput(input: QSTileInput<ModesDndTileModel>) {
        with(input) {
            when (action) {
                is QSTileUserAction.Click,
                is QSTileUserAction.ToggleClick -> {
                    handleClick()
                }
                is QSTileUserAction.LongClick -> {
                    handleLongClick(action.expandable)
                }
            }
        }
    }

    suspend fun handleClick() {
        val dnd = zenModeInteractor.dndMode.value
        if (dnd == null) {
            Log.wtf(TAG, "No DND!?")
            return
        }

        if (!dnd.isActive) {
            if (zenModeInteractor.shouldAskForZenDuration(dnd)) {
                dialogEventLogger.logOpenDurationDialog(dnd)
                withContext(mainContext) {
                    // NOTE: The dialog handles turning on the mode itself.
                    val dialog = dialogDelegate.makeDndDurationDialog()
                    dialog.show()
                }
            } else {
                dialogEventLogger.logModeOn(dnd)
                zenModeInteractor.activateMode(dnd)
            }
        } else {
            dialogEventLogger.logModeOff(dnd)
            zenModeInteractor.deactivateMode(dnd)
        }
    }

    private fun handleLongClick(expandable: Expandable?) {
        val intent = getSettingsIntent()
        if (intent != null) {
            qsTileIntentUserInputHandler.handle(expandable, intent)
        }
    }

    fun getSettingsIntent(): Intent? {
        val dnd = zenModeInteractor.dndMode.value
        if (dnd == null) {
            Log.wtf(TAG, "No DND!?")
            return null
        }

        return Intent(ACTION_AUTOMATIC_ZEN_RULE_SETTINGS)
            .putExtra(EXTRA_AUTOMATIC_ZEN_RULE_ID, dnd.id)
            .setPackage(settingsPackageRepository.getSettingsPackageName())
    }

    companion object {
        const val TAG = "ModesDndTileUserActionInteractor"
    }
}
