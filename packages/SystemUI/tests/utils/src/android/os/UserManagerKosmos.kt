/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.os

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.user.data.repository.FakeUserRepository.Companion.MAIN_USER_ID
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

var Kosmos.userManager by
    Kosmos.Fixture {
        mock<UserManager> {
            whenever(it.mainUser).thenReturn(UserHandle(MAIN_USER_ID))
            whenever(it.getUserSerialNumber(eq(MAIN_USER_ID))).thenReturn(0)
        }
    }
