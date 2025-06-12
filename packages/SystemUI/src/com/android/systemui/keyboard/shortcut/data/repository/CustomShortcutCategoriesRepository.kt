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

package com.android.systemui.keyboard.shortcut.data.repository

import android.hardware.input.InputGestureData
import android.hardware.input.InputGestureData.Builder
import android.hardware.input.InputGestureData.KeyTrigger
import android.hardware.input.InputGestureData.Trigger
import android.hardware.input.InputGestureData.createKeyTrigger
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION
import android.hardware.input.KeyGestureEvent.KeyGestureType
import android.hardware.input.KeyGlyphMap
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.systemui.Flags.appShortcutRemovalFix
import com.android.systemui.Flags.shortcutHelperKeyGlyph
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyboard.shared.model.ShortcutCustomizationRequestResult
import com.android.systemui.keyboard.shortcut.shared.model.KeyCombination
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategory
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCustomizationRequestInfo
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCustomizationRequestInfo.SingleShortcutCustomization
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCustomizationRequestInfo.SingleShortcutCustomization.Delete
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutKey
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class CustomShortcutCategoriesRepository
@Inject
constructor(
    private val inputDeviceRepository: ShortcutHelperInputDeviceRepository,
    @Background private val backgroundScope: CoroutineScope,
    private val shortcutCategoriesUtils: ShortcutCategoriesUtils,
    private val inputGestureDataAdapter: InputGestureDataAdapter,
    private val customInputGesturesRepository: CustomInputGesturesRepository,
    private val inputManager: InputManager,
    private val appLaunchDataRepository: AppLaunchDataRepository,
) : ShortcutCategoriesRepository {

    private val _selectedKeyCombination = MutableStateFlow<KeyCombination?>(null)
    private val _shortcutBeingCustomized = MutableStateFlow<ShortcutCustomizationRequestInfo?>(null)

    val pressedKeys =
        _selectedKeyCombination
            .combine(inputDeviceRepository.activeInputDevice) { keyCombination, inputDevice ->
                if (inputDevice == null || keyCombination == null) {
                    return@combine emptyList()
                } else {
                    val keyGlyphMap = getKeyGlyphMap(inputDevice.id)
                    val modifiers =
                        shortcutCategoriesUtils.toShortcutModifierKeys(
                            keyCombination.modifiers,
                            keyGlyphMap,
                        )
                    val triggerKey =
                        keyCombination.keyCode?.let {
                            shortcutCategoriesUtils.toShortcutKey(
                                keyGlyphMap,
                                inputDevice.keyCharacterMap,
                                keyCode = it,
                            )
                        }
                    val keys = mutableListOf<ShortcutKey>()
                    modifiers?.let { keys += it }
                    triggerKey?.let { keys += it }
                    return@combine keys
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )

    override val categories: Flow<List<ShortcutCategory>> =
        combine(
                inputDeviceRepository.activeInputDevice,
                customInputGesturesRepository.customInputGestures,
            ) { inputDevice, inputGestures ->
                if (inputDevice == null) {
                    emptyList()
                } else {
                    val sources = inputGestureDataAdapter.toInternalGroupSources(inputGestures)
                    val supportedKeyCodes =
                        shortcutCategoriesUtils.fetchSupportedKeyCodes(
                            inputDevice.id,
                            sources.map { it.groups },
                        )

                    val result =
                        sources.mapNotNull { source ->
                            shortcutCategoriesUtils.fetchShortcutCategory(
                                type = source.type,
                                groups = source.groups,
                                inputDevice = inputDevice,
                                supportedKeyCodes = supportedKeyCodes,
                            )
                        }
                    result
                }
            }
            .stateIn(
                scope = backgroundScope,
                initialValue = emptyList(),
                started = SharingStarted.Lazily,
            )

    fun updateUserKeyCombination(keyCombination: KeyCombination?) {
        _selectedKeyCombination.value = keyCombination
    }

    fun onCustomizationRequested(requestInfo: ShortcutCustomizationRequestInfo?) {
        _shortcutBeingCustomized.value = requestInfo
    }

    @VisibleForTesting
    fun buildInputGestureDataForShortcutBeingCustomized(): InputGestureData? {
        try {
            return Builder()
                .addKeyGestureTypeForShortcutBeingCustomized()
                .addTriggerFromSelectedKeyCombination()
                .addAppLaunchDataFromShortcutBeingCustomized()
                .build()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "could not add custom shortcut: $e")
            return null
        }
    }

    private fun retrieveInputGestureDataForShortcutBeingDeleted(): InputGestureData? {
        val keyGestureTypeForShortcutBeingDeleted = getKeyGestureTypeForShortcutBeingCustomized()
        if (appShortcutRemovalFix()) {
            val inputGesturesMatchingKeyGestureType =
                customInputGesturesRepository.retrieveCustomInputGestures().filter {
                    it.action.keyGestureType() == keyGestureTypeForShortcutBeingDeleted
                }

            return if (
                keyGestureTypeForShortcutBeingDeleted == KEY_GESTURE_TYPE_LAUNCH_APPLICATION
            ) {
                val shortcutBeingDeleted = getShortcutBeingCustomized() as Delete
                if (shortcutBeingDeleted.customShortcutCommand == null) {
                    Log.w(
                        TAG,
                        "Requested to delete custom shortcut but customShortcutCommand was null",
                    )
                    return null
                }

                inputGesturesMatchingKeyGestureType.firstOrNull {
                    checkShortcutKeyTriggerEquality(
                        it.trigger,
                        shortcutBeingDeleted.customShortcutCommand.keys,
                    ) ?: false
                }
            } else {
                inputGesturesMatchingKeyGestureType.firstOrNull()
            }
        } else {
            return customInputGesturesRepository.retrieveCustomInputGestures().firstOrNull {
                it.action.keyGestureType() == keyGestureTypeForShortcutBeingDeleted
            }
        }
    }

    suspend fun confirmAndSetShortcutCurrentlyBeingCustomized():
        ShortcutCustomizationRequestResult {
        val inputGestureData =
            buildInputGestureDataForShortcutBeingCustomized()
                ?: return ShortcutCustomizationRequestResult.ERROR_OTHER

        return customInputGesturesRepository.addCustomInputGesture(inputGestureData)
    }

    suspend fun deleteShortcutCurrentlyBeingCustomized(): ShortcutCustomizationRequestResult {
        val inputGestureData =
            retrieveInputGestureDataForShortcutBeingDeleted()
                ?: return ShortcutCustomizationRequestResult.ERROR_OTHER
        return customInputGesturesRepository.deleteCustomInputGesture(inputGestureData)
    }

    suspend fun resetAllCustomShortcuts(): ShortcutCustomizationRequestResult {
        return customInputGesturesRepository.resetAllCustomInputGestures()
    }

    suspend fun isSelectedKeyCombinationAvailable(): Boolean {
        val trigger = buildTriggerFromSelectedKeyCombination() ?: return false
        return customInputGesturesRepository.getInputGestureByTrigger(trigger) == null
    }

    private fun checkShortcutKeyTriggerEquality(
        trigger: Trigger,
        keys: List<ShortcutKey>,
    ): Boolean? {
        return getConvertedKeyTrigger(trigger)?.containsAll(keys)
    }

    private fun getConvertedKeyTrigger(trigger: Trigger): List<ShortcutKey>? {
        if (trigger is KeyTrigger) {
            val inputDevice = inputDeviceRepository.activeInputDevice.value ?: return null

            val modifierKeys =
                shortcutCategoriesUtils.toShortcutModifierKeys(
                    keyGlyphMap = getKeyGlyphMap(inputDevice.id),
                    modifiers = trigger.modifierState,
                ) ?: return null

            val keyCodeShortcutKey =
                shortcutCategoriesUtils.toShortcutKey(
                    keyGlyphMap = getKeyGlyphMap(inputDevice.id),
                    keyCharacterMap = inputDevice.keyCharacterMap,
                    keyCode = trigger.keycode,
                ) ?: return null

            return modifierKeys + keyCodeShortcutKey
        }
        return null
    }

    private fun getKeyGlyphMap(deviceId: Int): KeyGlyphMap? {
        return if (shortcutHelperKeyGlyph()) {
            inputManager.getKeyGlyphMap(deviceId)
        } else null
    }

    private fun Builder.addKeyGestureTypeForShortcutBeingCustomized(): Builder {
        val keyGestureType = getKeyGestureTypeForShortcutBeingCustomized()

        if (keyGestureType == null) {
            Log.w(
                TAG,
                "Could not find KeyGestureType for shortcut ${_shortcutBeingCustomized.value}",
            )
            return this
        }
        return setKeyGestureType(keyGestureType)
    }

    private fun Builder.addAppLaunchDataFromShortcutBeingCustomized(): Builder {
        val shortcutBeingCustomized =
            (_shortcutBeingCustomized.value as? SingleShortcutCustomization) ?: return this

        if (shortcutBeingCustomized.categoryType != ShortcutCategoryType.AppCategories) {
            return this
        }

        val defaultShortcutCommand = shortcutBeingCustomized.defaultShortcutCommand ?: return this
        val appLaunchData =
            appLaunchDataRepository.getAppLaunchDataForShortcutWithCommand(defaultShortcutCommand)

        return if (appLaunchData == null) this else this.setAppLaunchData(appLaunchData)
    }

    @KeyGestureType
    private fun getKeyGestureTypeForShortcutBeingCustomized(): Int? {
        val shortcutBeingCustomized = getShortcutBeingCustomized() as? SingleShortcutCustomization

        if (shortcutBeingCustomized == null) {
            Log.w(
                TAG,
                "Requested key gesture type from label but shortcut being customized is null",
            )
            return null
        }

        return inputGestureDataAdapter.getKeyGestureTypeForShortcut(
            shortcutLabel = shortcutBeingCustomized.label,
            shortcutCategoryType = shortcutBeingCustomized.categoryType,
        )
    }

    private fun Builder.addTriggerFromSelectedKeyCombination(): Builder =
        setTrigger(buildTriggerFromSelectedKeyCombination())

    private fun buildTriggerFromSelectedKeyCombination(): Trigger? {
        val selectedKeyCombination = _selectedKeyCombination.value
        if (selectedKeyCombination?.keyCode == null) {
            Log.w(
                TAG,
                "User requested to set shortcut but selected key combination is " +
                    "$selectedKeyCombination",
            )
            return null
        }

        return createKeyTrigger(
            /* keycode= */ selectedKeyCombination.keyCode,
            /* modifierState= */ shortcutCategoriesUtils.removeUnsupportedModifiers(
                selectedKeyCombination.modifiers
            ),
        )
    }

    @VisibleForTesting
    fun getShortcutBeingCustomized(): ShortcutCustomizationRequestInfo? {
        return _shortcutBeingCustomized.value
    }

    private companion object {
        private const val TAG = "CustomShortcutCategoriesRepository"
    }
}
