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

package com.android.systemui.shade.data.repository

import android.provider.Settings.Global.DEVELOPMENT_SHADE_DISPLAY_AWARENESS
import android.view.Display
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.keyguard.data.repository.KeyguardRepository
import com.android.systemui.shade.ShadeOnDefaultDisplayWhenLocked
import com.android.systemui.shade.display.ShadeDisplayPolicy
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Source of truth for the display currently holding the shade. */
interface ShadeDisplaysRepository {
    /** ID of the display which currently hosts the shade. */
    val displayId: StateFlow<Int>
    /** The current policy set. */
    val currentPolicy: ShadeDisplayPolicy

    /**
     * Id of the display that should host the shade.
     *
     * If this differs from [displayId], it means there is a shade movement in progress. Classes
     * that rely on the shade being already moved (and its context/resources updated) should rely on
     * [displayId]. Classes that need to do work associated with the shade move, should listen at
     * this.
     */
    val pendingDisplayId: StateFlow<Int>
}

/** Provides a way to set whether the display changed succeeded. */
interface MutableShadeDisplaysRepository : ShadeDisplaysRepository {
    /**
     * To be called when the shade changed window, and its resources have been completely updated.
     */
    fun onDisplayChangedSucceeded(displayId: Int)
}

/**
 * Keeps the policy and propagates the display id for the shade from it.
 *
 * If the display set by the policy is not available (e.g. after the cable is disconnected), this
 * falls back to the [Display.DEFAULT_DISPLAY].
 */
@SysUISingleton
class ShadeDisplaysRepositoryImpl
@Inject
constructor(
    globalSettings: GlobalSettings,
    defaultPolicy: ShadeDisplayPolicy,
    @Background bgScope: CoroutineScope,
    policies: Set<@JvmSuppressWildcards ShadeDisplayPolicy>,
    @ShadeOnDefaultDisplayWhenLocked private val shadeOnDefaultDisplayWhenLocked: Boolean,
    keyguardRepository: KeyguardRepository,
    displayRepository: DisplayRepository,
) : MutableShadeDisplaysRepository {

    private val policy: StateFlow<ShadeDisplayPolicy> =
        globalSettings
            .observerFlow(DEVELOPMENT_SHADE_DISPLAY_AWARENESS)
            .onStart { emit(Unit) }
            .map {
                val current = globalSettings.getString(DEVELOPMENT_SHADE_DISPLAY_AWARENESS)
                for (policy in policies) {
                    if (policy.name == current) return@map policy
                }
                globalSettings.putString(DEVELOPMENT_SHADE_DISPLAY_AWARENESS, defaultPolicy.name)
                return@map defaultPolicy
            }
            .distinctUntilChanged()
            .stateIn(bgScope, SharingStarted.Eagerly, defaultPolicy)

    private val displayIdFromPolicy: Flow<Int> =
        policy
            .flatMapLatest { it.displayId }
            .combine(displayRepository.displayIds) { policyDisplayId, availableIds ->
                if (policyDisplayId !in availableIds) Display.DEFAULT_DISPLAY else policyDisplayId
            }

    private val keyguardAwareDisplayPolicy: Flow<Int> =
        if (!shadeOnDefaultDisplayWhenLocked) {
            displayIdFromPolicy
        } else {
            keyguardRepository.isKeyguardShowing.combine(displayIdFromPolicy) {
                isKeyguardShowing,
                currentDisplayId ->
                if (isKeyguardShowing) {
                    Display.DEFAULT_DISPLAY
                } else {
                    currentDisplayId
                }
            }
        }

    override val currentPolicy: ShadeDisplayPolicy
        get() = policy.value

    override val pendingDisplayId: StateFlow<Int> =
        keyguardAwareDisplayPolicy.stateIn(
            bgScope,
            SharingStarted.WhileSubscribed(),
            Display.DEFAULT_DISPLAY,
        )
    private val _committedDisplayId = MutableStateFlow(Display.DEFAULT_DISPLAY)
    override val displayId: StateFlow<Int> = _committedDisplayId

    override fun onDisplayChangedSucceeded(displayId: Int) {
        _committedDisplayId.value = displayId
    }
}
