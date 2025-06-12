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

package com.android.systemui.qs.tiles

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.internal.logging.MetricsLogger
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.modes.shared.ModesUi
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.asQSTileIcon
import com.android.systemui.qs.flags.QsInCompose
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigProvider
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.impl.modes.domain.interactor.ModesDndTileDataInteractor
import com.android.systemui.qs.tiles.impl.modes.domain.interactor.ModesDndTileUserActionInteractor
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesDndTileModel
import com.android.systemui.qs.tiles.impl.modes.ui.ModesDndTileMapper
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Standalone tile used to control the DND Mode. Contrast to [ModesTile] (the tile that opens a
 * dialog showing the list of all modes) and [DndTile] (the tile used to toggle interruption
 * filtering in the pre-MODES_UI world).
 */
class ModesDndTile
@Inject
constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    qsTileConfigProvider: QSTileConfigProvider,
    private val dataInteractor: ModesDndTileDataInteractor,
    private val tileMapper: ModesDndTileMapper,
    private val userActionInteractor: ModesDndTileUserActionInteractor,
) :
    QSTileImpl<BooleanState>(
        host,
        uiEventLogger,
        backgroundLooper,
        mainHandler,
        falsingManager,
        metricsLogger,
        statusBarStateController,
        activityStarter,
        qsLogger,
    ) {

    private lateinit var tileState: QSTileState
    private val config = qsTileConfigProvider.getConfig(TILE_SPEC)

    init {
        lifecycle.coroutineScope.launch {
            lifecycle.repeatOnLifecycle(
                // TODO: b/403434908 - Workaround for "not listening to tile updates". Can be reset
                //   to RESUMED if either b/403434908 is fixed or QsInCompose is inlined.
                if (QsInCompose.isEnabled) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
            ) {
                dataInteractor.tileData().collect { refreshState(it) }
            }
        }
    }

    override fun isAvailable(): Boolean = ModesUi.isEnabled && android.app.Flags.modesUiDndTile()

    override fun getTileLabel(): CharSequence =
        mContext.getString(R.string.quick_settings_dnd_label)

    override fun newTileState(): BooleanState = BooleanState()

    override fun handleClick(expandable: Expandable?) = runBlocking {
        userActionInteractor.handleClick()
    }

    override fun getLongClickIntent(): Intent? = userActionInteractor.getSettingsIntent()

    @VisibleForTesting
    public override fun handleUpdateState(state: BooleanState?, arg: Any?) {
        val model = arg as? ModesDndTileModel ?: dataInteractor.getCurrentTileModel()

        tileState = tileMapper.map(config, model)
        state?.apply {
            value = model.isActivated
            this.state = tileState.activationState.legacyState
            icon =
                tileState.icon?.asQSTileIcon()
                    ?: maybeLoadResourceIcon(iconResId(model.isActivated))
            label = tileLabel
            secondaryLabel = tileState.secondaryLabel
            contentDescription = tileState.contentDescription
            stateDescription = tileState.stateDescription
            expandedAccessibilityClassName = tileState.expandedAccessibilityClassName
        }
    }

    @DrawableRes
    private fun iconResId(activated: Boolean): Int =
        if (activated) R.drawable.qs_dnd_icon_on else R.drawable.qs_dnd_icon_off

    companion object {
        const val TILE_SPEC = "modes_dnd"
    }
}
