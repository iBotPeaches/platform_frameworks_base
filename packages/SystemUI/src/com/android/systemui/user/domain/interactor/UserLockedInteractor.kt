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

package com.android.systemui.user.domain.interactor

import android.os.UserHandle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

@SysUISingleton
class UserLockedInteractor
@Inject
constructor(
    @Background val backgroundDispatcher: CoroutineDispatcher,
    val userRepository: UserRepository,
    val selectedUserInteractor: SelectedUserInteractor,
) {
    /** Whether the current user is unlocked */
    val currentUserUnlocked: Flow<Boolean> =
        selectedUserInteractor.selectedUserInfo.flatMapLatestConflated { user ->
            isUserUnlocked(user.userHandle)
        }

    fun isUserUnlocked(userHandle: UserHandle?): Flow<Boolean> =
        userRepository.isUserUnlocked(userHandle).flowOn(backgroundDispatcher)
}
