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

package com.android.systemui.util.settings.repository

import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import com.android.systemui.util.settings.UserSettingsProxy
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Repository observing values of a [UserSettingsProxy] for the specified user. This repository
 * should be used for any system that tracks the desired user internally (e.g. the Quick Settings
 * tiles system). In other cases, use a [UserAwareSettingsRepository] instead.
 */
abstract class SettingsForUserRepository(
    private val userSettings: UserSettingsProxy,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Background private val backgroundContext: CoroutineContext,
) {
    fun boolSettingForUser(
        userId: Int,
        name: String,
        defaultValue: Boolean = false,
    ): Flow<Boolean> =
        settingObserver(name, userId) { userSettings.getBoolForUser(name, defaultValue, userId) }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)

    fun intSettingForUser(userId: Int, name: String, defaultValue: Int = 0): Flow<Int> =
        settingObserver(name, userId) { userSettings.getIntForUser(name, defaultValue, userId) }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)

    fun <T> settingObserver(name: String, userId: Int, settingsReader: () -> T): Flow<T> {
        return userSettings
            .observerFlow(userId, name)
            .onStart { emit(Unit) }
            .map { settingsReader.invoke() }
    }

    suspend fun setBoolForUser(userId: Int, name: String, value: Boolean) {
        withContext(backgroundContext) { userSettings.putBoolForUser(name, value, userId) }
    }

    suspend fun getBoolForUser(userId: Int, name: String, defaultValue: Boolean = false): Boolean {
        return withContext(backgroundContext) {
            userSettings.getBoolForUser(name, defaultValue, userId)
        }
    }

    suspend fun setIntForUser(userId: Int, name: String, value: Int) {
        withContext(backgroundContext) { userSettings.putIntForUser(name, value, userId) }
    }

    suspend fun getIntForUser(userId: Int, name: String, defaultValue: Int = 0): Int {
        return withContext(backgroundContext) {
            userSettings.getIntForUser(name, defaultValue, userId)
        }
    }
}
