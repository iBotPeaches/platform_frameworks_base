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

package com.android.systemui.communal.domain.suppression.dagger

import com.android.systemui.Flags.glanceableHubV2
import com.android.systemui.communal.data.model.SuppressionReason
import com.android.systemui.communal.domain.interactor.CarProjectionInteractor
import com.android.systemui.communal.domain.interactor.CommunalAutoOpenInteractor
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.communal.domain.suppression.mapToReasonIfNotAllowed
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.user.domain.interactor.UserLockedInteractor
import com.android.systemui.util.kotlin.BooleanFlowOperators.not
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Module
interface CommunalSuppressionModule {
    /**
     * A set of reasons why communal may be suppressed. Ensures that this can be injected even if
     * it's empty.
     */
    @Multibinds fun suppressorSet(): Set<Flow<SuppressionReason?>>

    companion object {
        @Provides
        @IntoSet
        fun provideCarProjectionSuppressor(
            interactor: CarProjectionInteractor
        ): Flow<SuppressionReason?> {
            if (!glanceableHubV2()) {
                return flowOf(null)
            }
            return not(interactor.projectionActive)
                .mapToReasonIfNotAllowed(SuppressionReason.ReasonCarProjection)
        }

        @Provides
        @IntoSet
        fun provideDevicePolicySuppressor(
            interactor: CommunalSettingsInteractor
        ): Flow<SuppressionReason?> {
            return interactor.allowedForCurrentUserByDevicePolicy.mapToReasonIfNotAllowed(
                SuppressionReason.ReasonDevicePolicy
            )
        }

        @Provides
        @IntoSet
        fun provideSettingDisabledSuppressor(
            interactor: CommunalSettingsInteractor
        ): Flow<SuppressionReason?> {
            return interactor.settingEnabledForCurrentUser.mapToReasonIfNotAllowed(
                SuppressionReason.ReasonSettingDisabled
            )
        }

        @Provides
        @IntoSet
        fun bindUserLockedSuppressor(interactor: UserLockedInteractor): Flow<SuppressionReason?> {
            return interactor.currentUserUnlocked.mapToReasonIfNotAllowed(
                SuppressionReason.ReasonUserLocked
            )
        }

        @Provides
        @IntoSet
        fun provideAutoOpenSuppressor(
            interactor: CommunalAutoOpenInteractor
        ): Flow<SuppressionReason?> {
            return interactor.suppressionReason
        }

        @Provides
        @IntoSet
        fun provideMainUserSuppressor(
            interactor: SelectedUserInteractor
        ): Flow<SuppressionReason?> {
            return interactor.selectedUserInfo
                .map { it.isMain }
                .mapToReasonIfNotAllowed(SuppressionReason.ReasonSecondaryUser)
        }
    }
}
