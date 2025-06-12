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

package com.android.systemui.communal.domain.interactor

import android.content.pm.UserInfo
import com.android.systemui.communal.data.model.FEATURE_AUTO_OPEN
import com.android.systemui.communal.data.model.FEATURE_ENABLED
import com.android.systemui.communal.data.model.FEATURE_MANUAL_OPEN
import com.android.systemui.communal.data.model.SuppressionReason
import com.android.systemui.communal.data.repository.CommunalSettingsRepository
import com.android.systemui.communal.shared.model.CommunalBackgroundType
import com.android.systemui.communal.shared.model.WhenToDream
import com.android.systemui.communal.shared.model.WhenToStartHub
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.UserTracker
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class CommunalSettingsInteractor
@Inject
constructor(
    @Background private val bgScope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    @Background private val bgExecutor: Executor,
    private val repository: CommunalSettingsRepository,
    userInteractor: SelectedUserInteractor,
    private val userTracker: UserTracker,
) {
    /** Whether communal is enabled at all. */
    val isCommunalEnabled: StateFlow<Boolean> =
        repository
            .isEnabled(FEATURE_ENABLED)
            .stateIn(scope = bgScope, started = SharingStarted.Eagerly, initialValue = false)

    /** Whether manually opening the hub is enabled */
    val manualOpenEnabled: StateFlow<Boolean> =
        repository
            .isEnabled(FEATURE_MANUAL_OPEN)
            .stateIn(scope = bgScope, started = SharingStarted.Eagerly, initialValue = false)

    /** Whether auto-opening the hub is enabled */
    val autoOpenEnabled: StateFlow<Boolean> =
        repository
            .isEnabled(FEATURE_AUTO_OPEN)
            .stateIn(scope = bgScope, started = SharingStarted.Eagerly, initialValue = false)

    /** When to dream for the currently selected user. */
    val whenToDream: Flow<WhenToDream> =
        userInteractor.selectedUserInfo.flatMapLatestConflated { user ->
            repository.getWhenToDreamState(user)
        }

    /** When to automatically start hub for the currently selected user. */
    val whenToStartHub: Flow<WhenToStartHub> =
        userInteractor.selectedUserInfo.flatMapLatest { user ->
            repository.getWhenToStartHubState(user)
        }

    /** Whether communal hub is allowed by device policy for the current user */
    val allowedForCurrentUserByDevicePolicy: Flow<Boolean> =
        userInteractor.selectedUserInfo.flatMapLatestConflated { user ->
            repository.getAllowedByDevicePolicy(user)
        }

    /** Whether the hub is enabled for the current user */
    val settingEnabledForCurrentUser: Flow<Boolean> =
        userInteractor.selectedUserInfo.flatMapLatestConflated { user ->
            repository.getSettingEnabledByUser(user)
        }

    /**
     * Returns true if any glanceable hub functionality should be enabled via configs and flags.
     *
     * This should be used for preventing basic glanceable hub functionality from running on devices
     * that don't need it.
     *
     * If this is false, then the hub is definitely not available on the device. If this is true,
     * refer to [isCommunalEnabled] which takes into account other factors that can change at
     * runtime.
     *
     * If the glanceable_hub_v2 flag is enabled, checks the config_glanceableHubEnabled Android
     * config boolean. Otherwise, checks the old config_communalServiceEnabled config and
     * communal_hub flag.
     */
    fun isCommunalFlagEnabled(): Boolean = repository.getFlagEnabled()

    /**
     * Returns true if the Android config config_glanceableHubEnabled and the glanceable_hub_v2 flag
     * are enabled.
     *
     * This should be used to flag off new glanceable hub or dream behavior that should launch
     * together with the new hub experience that brings the hub to mobile.
     *
     * The trunk-stable flag is controlled by server rollout and is on all devices. The Android
     * config flag is enabled via resource overlay only on products we want the hub to be present
     * on.
     */
    fun isV2FlagEnabled(): Boolean = repository.getV2FlagEnabled()

    /**
     * Suppresses the hub with the given reasons. If there are no reasons, the hub will not be
     * suppressed.
     */
    fun setSuppressionReasons(reasons: List<SuppressionReason>) {
        repository.setSuppressionReasons(reasons)
    }

    /** The type of background to use for the hub. Used to experiment with different backgrounds */
    val communalBackground: Flow<CommunalBackgroundType> =
        userInteractor.selectedUserInfo
            .flatMapLatest { user -> repository.getBackground(user) }
            .flowOn(bgDispatcher)

    private val workProfileUserInfoCallbackFlow: Flow<UserInfo?> = conflatedCallbackFlow {
        fun send(profiles: List<UserInfo>) {
            trySend(profiles.find { it.isManagedProfile })
        }

        val callback =
            object : UserTracker.Callback {
                override fun onProfilesChanged(profiles: List<UserInfo>) {
                    send(profiles)
                }
            }
        userTracker.addCallback(callback, bgExecutor)
        send(userTracker.userProfiles)

        awaitClose { userTracker.removeCallback(callback) }
    }

    /**
     * A user that device policy says shouldn't allow communal widgets, or null if there are no
     * restrictions.
     */
    val workProfileUserDisallowedByDevicePolicy: StateFlow<UserInfo?> =
        workProfileUserInfoCallbackFlow
            .flatMapLatest { workProfile ->
                workProfile?.let {
                    repository.getAllowedByDevicePolicy(it).map { allowed ->
                        if (!allowed) it else null
                    }
                } ?: flowOf(null)
            }
            .stateIn(
                scope = bgScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = null,
            )
}
