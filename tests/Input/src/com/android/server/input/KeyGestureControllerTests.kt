/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.input

import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.hardware.input.AidlKeyGestureEvent
import android.hardware.input.AppLaunchData
import android.hardware.input.IInputManager
import android.hardware.input.IKeyGestureEventListener
import android.hardware.input.IKeyGestureHandler
import android.hardware.input.InputGestureData
import android.hardware.input.InputManager
import android.hardware.input.InputManagerGlobal
import android.hardware.input.KeyGestureEvent
import android.os.Handler
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.os.SystemProperties
import android.os.test.TestLooper
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.platform.test.flag.junit.SetFlagsRule
import android.util.AtomicFile
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.WindowManagerPolicyConstants.FLAG_INTERACTIVE
import androidx.test.core.app.ApplicationProvider
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.internal.R
import com.android.internal.accessibility.AccessibilityShortcutController
import com.android.internal.annotations.Keep
import com.android.internal.util.FrameworkStatsLog
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.server.input.InputManagerService.WindowManagerCallbacks
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.never
import org.mockito.kotlin.times

/**
 * Tests for {@link KeyGestureController}.
 *
 * Build/Install/Run: atest InputTests:KeyGestureControllerTests
 */
@Presubmit
@RunWith(JUnitParamsRunner::class)
class KeyGestureControllerTests {

    companion object {
        const val DEVICE_ID = 1
        val HOME_GESTURE_COMPLETE_EVENT =
            KeyGestureEvent.Builder()
                .setDeviceId(DEVICE_ID)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_H))
                .setModifierState(KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON)
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_HOME)
                .setAction(KeyGestureEvent.ACTION_GESTURE_COMPLETE)
                .build()
        val MODIFIER =
            mapOf(
                KeyEvent.KEYCODE_CTRL_LEFT to (KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_ON),
                KeyEvent.KEYCODE_CTRL_RIGHT to
                    (KeyEvent.META_CTRL_RIGHT_ON or KeyEvent.META_CTRL_ON),
                KeyEvent.KEYCODE_ALT_LEFT to (KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON),
                KeyEvent.KEYCODE_ALT_RIGHT to (KeyEvent.META_ALT_RIGHT_ON or KeyEvent.META_ALT_ON),
                KeyEvent.KEYCODE_SHIFT_LEFT to
                    (KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON),
                KeyEvent.KEYCODE_SHIFT_RIGHT to
                    (KeyEvent.META_SHIFT_RIGHT_ON or KeyEvent.META_SHIFT_ON),
                KeyEvent.KEYCODE_META_LEFT to (KeyEvent.META_META_LEFT_ON or KeyEvent.META_META_ON),
                KeyEvent.KEYCODE_META_RIGHT to
                    (KeyEvent.META_META_RIGHT_ON or KeyEvent.META_META_ON),
            )
        const val SEARCH_KEY_BEHAVIOR_DEFAULT_SEARCH = 0
        const val SEARCH_KEY_BEHAVIOR_TARGET_ACTIVITY = 1
        const val SETTINGS_KEY_BEHAVIOR_SETTINGS_ACTIVITY = 0
        const val SETTINGS_KEY_BEHAVIOR_NOTIFICATION_PANEL = 1
        const val SETTINGS_KEY_BEHAVIOR_NOTHING = 2
        const val SYSTEM_PID = 0
        const val TEST_PID = 10
        const val RANDOM_PID1 = 11
        const val RANDOM_PID2 = 12
    }

    @JvmField
    @Rule
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this)
            .mockStatic(FrameworkStatsLog::class.java)
            .mockStatic(SystemProperties::class.java)
            .mockStatic(KeyCharacterMap::class.java)
            .build()!!

    @JvmField @Rule val rule = SetFlagsRule()

    @Mock private lateinit var iInputManager: IInputManager
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var wmCallbacks: WindowManagerCallbacks
    @Mock private lateinit var accessibilityShortcutController: AccessibilityShortcutController

    private var currentPid = 0
    private lateinit var context: Context
    private lateinit var resources: Resources
    private lateinit var keyGestureController: KeyGestureController
    private lateinit var inputManagerGlobalSession: InputManagerGlobal.TestSession
    private lateinit var testLooper: TestLooper
    private lateinit var tempFile: File
    private lateinit var inputDataStore: InputDataStore
    private var events = mutableListOf<KeyGestureEvent>()

    @Before
    fun setup() {
        context = Mockito.spy(ContextWrapper(ApplicationProvider.getApplicationContext()))
        resources = Mockito.spy(context.resources)
        setupInputDevices()
        setupBehaviors()
        testLooper = TestLooper()
        currentPid = Process.myPid()
        tempFile = File.createTempFile("input_gestures", ".xml")
        inputDataStore =
            InputDataStore(
                object : InputDataStore.FileInjector("input_gestures.xml") {
                    private val atomicFile: AtomicFile = AtomicFile(tempFile)

                    override fun openRead(userId: Int): InputStream? {
                        return atomicFile.openRead()
                    }

                    override fun startWrite(userId: Int): FileOutputStream? {
                        return atomicFile.startWrite()
                    }

                    override fun finishWrite(
                        userId: Int,
                        fos: FileOutputStream?,
                        success: Boolean,
                    ) {
                        if (success) {
                            atomicFile.finishWrite(fos)
                        } else {
                            atomicFile.failWrite(fos)
                        }
                    }

                    override fun getAtomicFileForUserId(userId: Int): AtomicFile {
                        return atomicFile
                    }
                }
            )
        startNewInputGlobalTestSession()
    }

    @After
    fun teardown() {
        if (this::inputManagerGlobalSession.isInitialized) {
            inputManagerGlobalSession.close()
        }
    }

    private fun setupBehaviors() {
        Mockito.`when`(SystemProperties.get("ro.debuggable")).thenReturn("1")
        Mockito.`when`(resources.getBoolean(R.bool.config_enableScreenshotChord)).thenReturn(true)
        Mockito.`when`(context.resources).thenReturn(resources)
        Mockito.`when`(packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH))
            .thenReturn(true)
        Mockito.`when`(packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
            .thenReturn(true)
        Mockito.`when`(context.packageManager).thenReturn(packageManager)
    }

    private fun setupBookmarks(bookmarkRes: Int) {
        val testBookmarks: XmlResourceParser = context.resources.getXml(bookmarkRes)
        Mockito.`when`(resources.getXml(R.xml.bookmarks)).thenReturn(testBookmarks)
    }

    private fun setupInputDevices() {
        val correctIm = context.getSystemService(InputManager::class.java)!!
        val virtualDevice = correctIm.getInputDevice(KeyCharacterMap.VIRTUAL_KEYBOARD)!!
        val kcm = virtualDevice.keyCharacterMap!!
        val keyboardDevice = InputDevice.Builder().setId(DEVICE_ID).build()
        Mockito.`when`(iInputManager.inputDeviceIds).thenReturn(intArrayOf(DEVICE_ID))
        Mockito.`when`(iInputManager.getInputDevice(DEVICE_ID)).thenReturn(keyboardDevice)
        ExtendedMockito.`when`(KeyCharacterMap.load(Mockito.anyInt())).thenReturn(kcm)
    }

    private fun startNewInputGlobalTestSession() {
        if (this::inputManagerGlobalSession.isInitialized) {
            inputManagerGlobalSession.close()
        }
        inputManagerGlobalSession = InputManagerGlobal.createTestSession(iInputManager)
        val inputManager = InputManager(context)
        Mockito.`when`(context.getSystemService(Mockito.eq(Context.INPUT_SERVICE)))
            .thenReturn(inputManager)
    }

    private fun setupKeyGestureController() {
        keyGestureController =
            KeyGestureController(
                context,
                testLooper.looper,
                testLooper.looper,
                inputDataStore,
                object : KeyGestureController.Injector() {
                    override fun getAccessibilityShortcutController(
                        context: Context?,
                        handler: Handler?,
                    ): AccessibilityShortcutController {
                        return accessibilityShortcutController
                    }
                },
            )
        Mockito.`when`(iInputManager.registerKeyGestureHandler(Mockito.any(), Mockito.any()))
            .thenAnswer {
                val args = it.arguments
                if (args[0] != null) {
                    keyGestureController.registerKeyGestureHandler(
                        args[0] as IntArray,
                        args[1] as IKeyGestureHandler,
                        SYSTEM_PID,
                    )
                }
            }
        keyGestureController.setWindowManagerCallbacks(wmCallbacks)
        Mockito.`when`(wmCallbacks.isKeyguardLocked(Mockito.anyInt())).thenReturn(false)
        Mockito.`when`(
                accessibilityShortcutController.isAccessibilityShortcutAvailable(
                    Mockito.anyBoolean()
                )
            )
            .thenReturn(true)
        Mockito.`when`(iInputManager.appLaunchBookmarks)
            .thenReturn(keyGestureController.appLaunchBookmarks)
        keyGestureController.systemRunning()
        testLooper.dispatchAll()
    }

    private fun notifyHomeGestureCompleted() {
        keyGestureController.notifyKeyGestureCompleted(
            DEVICE_ID,
            intArrayOf(KeyEvent.KEYCODE_H),
            KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON,
            KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
        )
    }

    @Test
    fun testKeyGestureEvent_registerUnregisterListener() {
        setupKeyGestureController()
        val listener = KeyGestureEventListener()

        // Register key gesture event listener
        keyGestureController.registerKeyGestureEventListener(listener, 0)
        notifyHomeGestureCompleted()
        testLooper.dispatchAll()
        assertEquals("Listener should get callbacks on key gesture event completed", 1, events.size)
        assertEquals(
            "Listener should get callback for key gesture complete event",
            HOME_GESTURE_COMPLETE_EVENT,
            events[0],
        )

        // Unregister listener
        events.clear()
        keyGestureController.unregisterKeyGestureEventListener(listener, 0)
        notifyHomeGestureCompleted()
        testLooper.dispatchAll()
        assertEquals("Listener should not get callback after being unregistered", 0, events.size)
    }

    class TestData(
        val name: String,
        val keys: IntArray,
        val expectedKeyGestureType: Int,
        val expectedKeys: IntArray,
        val expectedModifierState: Int,
        val expectedActions: IntArray,
        val expectedAppLaunchData: AppLaunchData? = null,
    ) {
        override fun toString(): String = name
    }

    @Keep
    private fun systemGesturesTestArguments(): Array<TestData> {
        return arrayOf(
            TestData(
                "META + A -> Launch Assistant",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_A),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_ASSISTANT,
                intArrayOf(KeyEvent.KEYCODE_A),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + H -> Go Home",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_H),
                KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                intArrayOf(KeyEvent.KEYCODE_H),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + ENTER -> Go Home",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ENTER),
                KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                intArrayOf(KeyEvent.KEYCODE_ENTER),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + I -> Launch System Settings",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_I),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SYSTEM_SETTINGS,
                intArrayOf(KeyEvent.KEYCODE_I),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + L -> Lock",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_L),
                KeyGestureEvent.KEY_GESTURE_TYPE_LOCK_SCREEN,
                intArrayOf(KeyEvent.KEYCODE_L),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + N -> Toggle Notification",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_N),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL,
                intArrayOf(KeyEvent.KEYCODE_N),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + S -> Take Screenshot",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_S),
                KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_SCREENSHOT,
                intArrayOf(KeyEvent.KEYCODE_S),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + ESC -> Back",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ESCAPE),
                KeyGestureEvent.KEY_GESTURE_TYPE_BACK,
                intArrayOf(KeyEvent.KEYCODE_ESCAPE),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + DPAD_LEFT -> Back",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
                KeyGestureEvent.KEY_GESTURE_TYPE_BACK,
                intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + CTRL + DPAD_UP -> Multi Window Navigation",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DPAD_UP,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_MULTI_WINDOW_NAVIGATION,
                intArrayOf(KeyEvent.KEYCODE_DPAD_UP),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + CTRL + DPAD_DOWN -> Desktop Mode",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_DESKTOP_MODE,
                intArrayOf(KeyEvent.KEYCODE_DPAD_DOWN),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + CTRL + DPAD_LEFT -> Splitscreen Navigation Left",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT,
                intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + CTRL + DPAD_RIGHT -> Splitscreen Navigation Right",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_RIGHT,
                intArrayOf(KeyEvent.KEYCODE_DPAD_RIGHT),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + / -> Open Shortcut Helper",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_SLASH),
                KeyGestureEvent.KEY_GESTURE_TYPE_OPEN_SHORTCUT_HELPER,
                intArrayOf(KeyEvent.KEYCODE_SLASH),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + ALT -> Toggle Caps Lock",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_CAPS_LOCK,
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "ALT + META -> Toggle Caps Lock",
                intArrayOf(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_META_LEFT),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_CAPS_LOCK,
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + TAB -> Open Overview",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_TAB),
                KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS,
                intArrayOf(KeyEvent.KEYCODE_TAB),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "ALT + TAB -> Toggle Recent Apps Switcher",
                intArrayOf(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_TAB),
                KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER,
                intArrayOf(KeyEvent.KEYCODE_TAB),
                KeyEvent.META_ALT_ON,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
            ),
            TestData(
                "CTRL + SPACE -> Switch Language Forward",
                intArrayOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SPACE),
                KeyGestureEvent.KEY_GESTURE_TYPE_LANGUAGE_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_SPACE),
                KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "CTRL + SHIFT + SPACE -> Switch Language Backward",
                intArrayOf(
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_SPACE,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LANGUAGE_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_SPACE),
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "CTRL + ALT + Z -> Accessibility Shortcut",
                intArrayOf(
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_Z,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_ACCESSIBILITY_SHORTCUT,
                intArrayOf(KeyEvent.KEYCODE_Z),
                KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + B -> Launch Default Browser",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_B),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_B),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForRole(RoleManager.ROLE_BROWSER),
            ),
            TestData(
                "META + C -> Launch Default Contacts",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_P),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_P),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CONTACTS),
            ),
            TestData(
                "META + E -> Launch Default Email",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_E),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_E),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_EMAIL),
            ),
            TestData(
                "META + K -> Launch Default Calendar",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_C),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_C),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALENDAR),
            ),
            TestData(
                "META + M -> Launch Default Maps",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_M),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_M),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_MAPS),
            ),
            TestData(
                "META + U -> Launch Default Calculator",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_U),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_U),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALCULATOR),
            ),
            TestData(
                "META + CTRL + DEL -> Trigger Bug Report",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DEL,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TRIGGER_BUG_REPORT,
                intArrayOf(KeyEvent.KEYCODE_DEL),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "Meta + Alt + 3 -> Toggle Bounce Keys",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_3,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_BOUNCE_KEYS,
                intArrayOf(KeyEvent.KEYCODE_3),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "Meta + Alt + 4 -> Toggle Mouse Keys",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_4,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MOUSE_KEYS,
                intArrayOf(KeyEvent.KEYCODE_4),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "Meta + Alt + 5 -> Toggle Sticky Keys",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_5,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_STICKY_KEYS,
                intArrayOf(KeyEvent.KEYCODE_5),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "Meta + Alt + 6 -> Toggle Slow Keys",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_6,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SLOW_KEYS,
                intArrayOf(KeyEvent.KEYCODE_6),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + CTRL + D -> Move a task to next display",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_D,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_MOVE_TO_NEXT_DISPLAY,
                intArrayOf(KeyEvent.KEYCODE_D),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + [ -> Resizes a task to fit the left half of the screen",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_LEFT_BRACKET),
                KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_LEFT_FREEFORM_WINDOW,
                intArrayOf(KeyEvent.KEYCODE_LEFT_BRACKET),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + ] -> Resizes a task to fit the right half of the screen",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_RIGHT_BRACKET),
                KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_RIGHT_FREEFORM_WINDOW,
                intArrayOf(KeyEvent.KEYCODE_RIGHT_BRACKET),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + '=' -> Toggles maximization of a task to maximized and restore its bounds",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_EQUALS),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAXIMIZE_FREEFORM_WINDOW,
                intArrayOf(KeyEvent.KEYCODE_EQUALS),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + '-' -> Minimizes a freeform task",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_MINUS),
                KeyGestureEvent.KEY_GESTURE_TYPE_MINIMIZE_FREEFORM_WINDOW,
                intArrayOf(KeyEvent.KEYCODE_MINUS),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + ALT + M -> Toggle Magnification",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_M,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                intArrayOf(KeyEvent.KEYCODE_M),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + ALT + S -> Activate Select to Speak",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_S,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
                intArrayOf(KeyEvent.KEYCODE_S),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + ALT + 'V' -> Toggle Voice Access",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_V,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
                intArrayOf(KeyEvent.KEYCODE_V),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
        )
    }

    @Test
    @Parameters(method = "systemGesturesTestArguments")
    @EnableFlags(
        com.android.server.flags.Flags.FLAG_NEW_BUGREPORT_KEYBOARD_SHORTCUT,
        com.android.hardware.input.Flags.FLAG_KEYBOARD_A11Y_SHORTCUT_CONTROL,
        com.android.hardware.input.Flags.FLAG_KEYBOARD_A11Y_MOUSE_KEYS,
        com.android.hardware.input.Flags.FLAG_ENABLE_TALKBACK_AND_MAGNIFIER_KEY_GESTURES,
        com.android.hardware.input.Flags.FLAG_ENABLE_VOICE_ACCESS_KEY_GESTURES,
        com.android.window.flags.Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
        com.android.window.flags.Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
    )
    fun testKeyGestures(test: TestData) {
        setupKeyGestureController()
        testKeyGestureInternal(test)
    }

    @Test
    @Parameters(method = "systemGesturesTestArguments")
    @EnableFlags(
        com.android.server.flags.Flags.FLAG_NEW_BUGREPORT_KEYBOARD_SHORTCUT,
        com.android.hardware.input.Flags.FLAG_KEYBOARD_A11Y_SHORTCUT_CONTROL,
        com.android.hardware.input.Flags.FLAG_KEYBOARD_A11Y_MOUSE_KEYS,
        com.android.hardware.input.Flags.FLAG_ENABLE_TALKBACK_AND_MAGNIFIER_KEY_GESTURES,
        com.android.hardware.input.Flags.FLAG_ENABLE_VOICE_ACCESS_KEY_GESTURES,
        com.android.window.flags.Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
        com.android.window.flags.Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
    )
    fun testCustomKeyGesturesNotAllowedForSystemGestures(test: TestData) {
        setupKeyGestureController()

        val builder =
            InputGestureData.Builder()
                .setKeyGestureType(test.expectedKeyGestureType)
                .setTrigger(
                    InputGestureData.createKeyTrigger(
                        test.expectedKeys[0],
                        test.expectedModifierState,
                    )
                )
        if (test.expectedAppLaunchData != null) {
            builder.setAppLaunchData(test.expectedAppLaunchData)
        }
        assertEquals(
            test.toString(),
            InputManager.CUSTOM_INPUT_GESTURE_RESULT_ERROR_RESERVED_GESTURE,
            keyGestureController.addCustomInputGesture(0, builder.build().aidlData),
        )
    }

    @Keep
    private fun bookmarkArguments(): Array<TestData> {
        return arrayOf(
            TestData(
                "META + B -> Launch Default Browser",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_B),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_B),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForRole(RoleManager.ROLE_BROWSER),
            ),
            TestData(
                "META + P -> Launch Default Contacts",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_P),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_P),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CONTACTS),
            ),
            TestData(
                "META + E -> Launch Default Email",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_E),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_E),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_EMAIL),
            ),
            TestData(
                "META + C -> Launch Default Calendar",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_C),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_C),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALENDAR),
            ),
            TestData(
                "META + M -> Launch Default Maps",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_M),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_M),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_MAPS),
            ),
            TestData(
                "META + U -> Launch Default Calculator",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_U),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_U),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALCULATOR),
            ),
            TestData(
                "META + SHIFT + B -> Launch Default Browser",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_B,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_B),
                KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForRole(RoleManager.ROLE_BROWSER),
            ),
            TestData(
                "META + SHIFT + P -> Launch Default Contacts",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_P,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_P),
                KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CONTACTS),
            ),
            TestData(
                "META + SHIFT + J -> Launch Target Activity",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_J,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_J),
                KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForComponent("com.test", "com.test.BookmarkTest"),
            ),
        )
    }

    @Test
    @Parameters(method = "bookmarkArguments")
    fun testBookmarks(test: TestData) {
        setupBookmarks(com.android.test.input.R.xml.bookmarks)
        setupKeyGestureController()
        testKeyGestureInternal(test)
    }

    @Test
    @Parameters(method = "bookmarkArguments")
    fun testBookmarksLegacy(test: TestData) {
        setupBookmarks(com.android.test.input.R.xml.bookmarks_legacy)
        setupKeyGestureController()
        testKeyGestureInternal(test)
    }

    @Keep
    private fun systemKeysTestArguments(): Array<TestData> {
        return arrayOf(
            TestData(
                "RECENT_APPS -> Show Overview",
                intArrayOf(KeyEvent.KEYCODE_RECENT_APPS),
                KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS,
                intArrayOf(KeyEvent.KEYCODE_RECENT_APPS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "APP_SWITCH -> App Switch",
                intArrayOf(KeyEvent.KEYCODE_APP_SWITCH),
                KeyGestureEvent.KEY_GESTURE_TYPE_APP_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_APP_SWITCH),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
            ),
            TestData(
                "BRIGHTNESS_UP -> Brightness Up",
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_UP),
                KeyGestureEvent.KEY_GESTURE_TYPE_BRIGHTNESS_UP,
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_UP),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "BRIGHTNESS_DOWN -> Brightness Down",
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_DOWN),
                KeyGestureEvent.KEY_GESTURE_TYPE_BRIGHTNESS_DOWN,
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_DOWN),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "KEYBOARD_BACKLIGHT_UP -> Keyboard Backlight Up",
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP),
                KeyGestureEvent.KEY_GESTURE_TYPE_KEYBOARD_BACKLIGHT_UP,
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "KEYBOARD_BACKLIGHT_DOWN -> Keyboard Backlight Down",
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_DOWN),
                KeyGestureEvent.KEY_GESTURE_TYPE_KEYBOARD_BACKLIGHT_DOWN,
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_DOWN),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "KEYBOARD_BACKLIGHT_TOGGLE -> Keyboard Backlight Toggle",
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE),
                KeyGestureEvent.KEY_GESTURE_TYPE_KEYBOARD_BACKLIGHT_TOGGLE,
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "ALL_APPS -> Open App Drawer",
                intArrayOf(KeyEvent.KEYCODE_ALL_APPS),
                KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS,
                intArrayOf(KeyEvent.KEYCODE_ALL_APPS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "NOTIFICATION -> Toggle Notification Panel",
                intArrayOf(KeyEvent.KEYCODE_NOTIFICATION),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL,
                intArrayOf(KeyEvent.KEYCODE_NOTIFICATION),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "LANGUAGE_SWITCH -> Switch Language Forward",
                intArrayOf(KeyEvent.KEYCODE_LANGUAGE_SWITCH),
                KeyGestureEvent.KEY_GESTURE_TYPE_LANGUAGE_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_LANGUAGE_SWITCH),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "SHIFT + LANGUAGE_SWITCH -> Switch Language Backward",
                intArrayOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_LANGUAGE_SWITCH),
                KeyGestureEvent.KEY_GESTURE_TYPE_LANGUAGE_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_LANGUAGE_SWITCH),
                KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "SCREENSHOT -> Take Screenshot",
                intArrayOf(KeyEvent.KEYCODE_SCREENSHOT),
                KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_SCREENSHOT,
                intArrayOf(KeyEvent.KEYCODE_SCREENSHOT),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META -> Open Apps Drawer",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT),
                KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS,
                intArrayOf(KeyEvent.KEYCODE_META_LEFT),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "SYSRQ -> Take screenshot",
                intArrayOf(KeyEvent.KEYCODE_SYSRQ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_SCREENSHOT,
                intArrayOf(KeyEvent.KEYCODE_SYSRQ),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "ESC -> Close All Dialogs",
                intArrayOf(KeyEvent.KEYCODE_ESCAPE),
                KeyGestureEvent.KEY_GESTURE_TYPE_CLOSE_ALL_DIALOGS,
                intArrayOf(KeyEvent.KEYCODE_ESCAPE),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "EXPLORER -> Launch Default Browser",
                intArrayOf(KeyEvent.KEYCODE_EXPLORER),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_EXPLORER),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForRole(RoleManager.ROLE_BROWSER),
            ),
            TestData(
                "ENVELOPE -> Launch Default Email",
                intArrayOf(KeyEvent.KEYCODE_ENVELOPE),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_ENVELOPE),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_EMAIL),
            ),
            TestData(
                "CONTACTS -> Launch Default Contacts",
                intArrayOf(KeyEvent.KEYCODE_CONTACTS),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_CONTACTS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CONTACTS),
            ),
            TestData(
                "CALENDAR -> Launch Default Calendar",
                intArrayOf(KeyEvent.KEYCODE_CALENDAR),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_CALENDAR),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALENDAR),
            ),
            TestData(
                "MUSIC -> Launch Default Music",
                intArrayOf(KeyEvent.KEYCODE_MUSIC),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_MUSIC),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_MUSIC),
            ),
            TestData(
                "CALCULATOR -> Launch Default Calculator",
                intArrayOf(KeyEvent.KEYCODE_CALCULATOR),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_CALCULATOR),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALCULATOR),
            ),
            TestData(
                "LOCK -> Lock Screen",
                intArrayOf(KeyEvent.KEYCODE_LOCK),
                KeyGestureEvent.KEY_GESTURE_TYPE_LOCK_SCREEN,
                intArrayOf(KeyEvent.KEYCODE_LOCK),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "FULLSCREEN -> Turns a task into fullscreen",
                intArrayOf(KeyEvent.KEYCODE_FULLSCREEN),
                KeyGestureEvent.KEY_GESTURE_TYPE_MULTI_WINDOW_NAVIGATION,
                intArrayOf(KeyEvent.KEYCODE_FULLSCREEN),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
        )
    }

    @Test
    @Parameters(method = "systemKeysTestArguments")
    @EnableFlags(com.android.hardware.input.Flags.FLAG_ENABLE_NEW_25Q2_KEYCODES)
    fun testSystemKeys(test: TestData) {
        setupKeyGestureController()
        testKeyGestureInternal(test)
    }

    @Test
    fun testKeycodesFullyConsumed_irrespectiveOfHandlers() {
        setupKeyGestureController()
        val testKeys =
            intArrayOf(
                KeyEvent.KEYCODE_RECENT_APPS,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_BRIGHTNESS_UP,
                KeyEvent.KEYCODE_BRIGHTNESS_DOWN,
                KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_DOWN,
                KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP,
                KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE,
                KeyEvent.KEYCODE_ALL_APPS,
                KeyEvent.KEYCODE_NOTIFICATION,
                KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_LANGUAGE_SWITCH,
                KeyEvent.KEYCODE_SCREENSHOT,
                KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.KEYCODE_META_RIGHT,
                KeyEvent.KEYCODE_ASSIST,
                KeyEvent.KEYCODE_VOICE_ASSIST,
                KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY,
                KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY,
                KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY,
                KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL,
                KeyEvent.KEYCODE_DO_NOT_DISTURB,
                KeyEvent.KEYCODE_LOCK,
                KeyEvent.KEYCODE_FULLSCREEN,
            )

        for (key in testKeys) {
            sendKeys(intArrayOf(key), assertNotSentToApps = true)
        }
    }

    @Test
    fun testSearchKeyGestures_defaultSearch() {
        Mockito.`when`(resources.getInteger(R.integer.config_searchKeyBehavior))
            .thenReturn(SEARCH_KEY_BEHAVIOR_DEFAULT_SEARCH)
        setupKeyGestureController()
        testKeyGestureNotProduced(
            "SEARCH -> Default Search",
            intArrayOf(KeyEvent.KEYCODE_SEARCH),
            intArrayOf(KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SEARCH),
        )
    }

    @Test
    fun testSearchKeyGestures_searchActivity() {
        Mockito.`when`(resources.getInteger(R.integer.config_searchKeyBehavior))
            .thenReturn(SEARCH_KEY_BEHAVIOR_TARGET_ACTIVITY)
        setupKeyGestureController()
        testKeyGestureInternal(
            TestData(
                "SEARCH -> Launch Search Activity",
                intArrayOf(KeyEvent.KEYCODE_SEARCH),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SEARCH,
                intArrayOf(KeyEvent.KEYCODE_SEARCH),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            )
        )
    }

    @Test
    fun testSettingKeyGestures_doNothing() {
        Mockito.`when`(resources.getInteger(R.integer.config_settingsKeyBehavior))
            .thenReturn(SETTINGS_KEY_BEHAVIOR_NOTHING)
        setupKeyGestureController()
        testKeyGestureNotProduced(
            "SETTINGS -> Do Nothing",
            intArrayOf(KeyEvent.KEYCODE_SETTINGS),
            intArrayOf(
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SEARCH,
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL,
            ),
        )
    }

    @Test
    fun testSettingKeyGestures_settingsActivity() {
        Mockito.`when`(resources.getInteger(R.integer.config_settingsKeyBehavior))
            .thenReturn(SETTINGS_KEY_BEHAVIOR_SETTINGS_ACTIVITY)
        setupKeyGestureController()
        testKeyGestureInternal(
            TestData(
                "SETTINGS -> Launch Settings Activity",
                intArrayOf(KeyEvent.KEYCODE_SETTINGS),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SYSTEM_SETTINGS,
                intArrayOf(KeyEvent.KEYCODE_SETTINGS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            )
        )
    }

    @Test
    fun testSettingKeyGestures_notificationPanel() {
        Mockito.`when`(resources.getInteger(R.integer.config_settingsKeyBehavior))
            .thenReturn(SETTINGS_KEY_BEHAVIOR_NOTIFICATION_PANEL)
        setupKeyGestureController()
        testKeyGestureInternal(
            TestData(
                "SETTINGS -> Toggle Notification Panel",
                intArrayOf(KeyEvent.KEYCODE_SETTINGS),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL,
                intArrayOf(KeyEvent.KEYCODE_SETTINGS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            )
        )
    }

    @Test
    fun testCapsLockPressNotified() {
        setupKeyGestureController()
        val listener = KeyGestureEventListener()

        keyGestureController.registerKeyGestureEventListener(listener, 0)
        sendKeys(intArrayOf(KeyEvent.KEYCODE_CAPS_LOCK))
        testLooper.dispatchAll()
        assertEquals("Listener should get callbacks on key gesture event completed", 1, events.size)
        assertEquals(
            "Listener should get callback for Toggle Caps Lock key gesture complete event",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_CAPS_LOCK,
            events[0].keyGestureType,
        )
    }

    @Keep
    private fun systemGesturesTestArguments_forKeyCombinations(): Array<TestData> {
        return arrayOf(
            TestData(
                "VOLUME_DOWN + POWER -> Screenshot Chord",
                intArrayOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER),
                KeyGestureEvent.KEY_GESTURE_TYPE_SCREENSHOT_CHORD,
                intArrayOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
            ),
            TestData(
                "POWER + STEM_PRIMARY -> Screenshot Chord",
                intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_STEM_PRIMARY),
                KeyGestureEvent.KEY_GESTURE_TYPE_SCREENSHOT_CHORD,
                intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_STEM_PRIMARY),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
            ),
            TestData(
                "BACK + DPAD_CENTER -> TV Trigger Bug Report",
                intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER),
                KeyGestureEvent.KEY_GESTURE_TYPE_TV_TRIGGER_BUG_REPORT,
                intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
            ),
        )
    }

    @Test
    @Parameters(method = "systemGesturesTestArguments_forKeyCombinations")
    @EnableFlags(
        com.android.hardware.input.Flags.FLAG_USE_KEY_GESTURE_EVENT_HANDLER,
        com.android.hardware.input.Flags.FLAG_USE_KEY_GESTURE_EVENT_HANDLER_MULTI_KEY_GESTURES,
    )
    fun testKeyCombinationGestures(test: TestData) {
        setupKeyGestureController()
        testKeyGestureInternal(test)
    }

    @Keep
    private fun customInputGesturesTestArguments(): Array<TestData> {
        return arrayOf(
            TestData(
                "META + ALT + Q -> Go Home",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_Q,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                intArrayOf(KeyEvent.KEYCODE_Q),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            TestData(
                "META + ALT + Q -> Launch app",
                intArrayOf(
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_Q,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_Q),
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForComponent("com.test", "com.test.BookmarkTest"),
            ),
        )
    }

    @Test
    @Parameters(method = "customInputGesturesTestArguments")
    fun testCustomKeyGestures(test: TestData) {
        setupKeyGestureController()
        val trigger =
            InputGestureData.createKeyTrigger(test.expectedKeys[0], test.expectedModifierState)
        val builder =
            InputGestureData.Builder()
                .setKeyGestureType(test.expectedKeyGestureType)
                .setTrigger(trigger)
        if (test.expectedAppLaunchData != null) {
            builder.setAppLaunchData(test.expectedAppLaunchData)
        }
        val inputGestureData = builder.build()

        assertNull(test.toString(), keyGestureController.getInputGesture(0, trigger.aidlTrigger))
        assertEquals(
            test.toString(),
            InputManager.CUSTOM_INPUT_GESTURE_RESULT_SUCCESS,
            keyGestureController.addCustomInputGesture(0, builder.build().aidlData),
        )
        assertEquals(
            test.toString(),
            inputGestureData.aidlData,
            keyGestureController.getInputGesture(0, trigger.aidlTrigger),
        )
        testKeyGestureInternal(test)
    }

    @Test
    @Parameters(method = "customInputGesturesTestArguments")
    fun testCustomKeyGesturesSavedAndLoadedByController(test: TestData) {
        val userId = 10
        setupKeyGestureController()
        val builder =
            InputGestureData.Builder()
                .setKeyGestureType(test.expectedKeyGestureType)
                .setTrigger(
                    InputGestureData.createKeyTrigger(
                        test.expectedKeys[0],
                        test.expectedModifierState,
                    )
                )
        if (test.expectedAppLaunchData != null) {
            builder.setAppLaunchData(test.expectedAppLaunchData)
        }
        val inputGestureData = builder.build()

        keyGestureController.setCurrentUserId(userId)
        testLooper.dispatchAll()
        keyGestureController.addCustomInputGesture(userId, inputGestureData.aidlData)
        testLooper.dispatchAll()

        // Reinitialize the gesture controller simulating a login/logout for the user.
        startNewInputGlobalTestSession()
        setupKeyGestureController()
        keyGestureController.setCurrentUserId(userId)
        testLooper.dispatchAll()

        val savedInputGestures = keyGestureController.getCustomInputGestures(userId, null)
        assertEquals(
            "Test: $test doesn't produce correct number of saved input gestures",
            1,
            savedInputGestures.size,
        )
        assertEquals(
            "Test: $test doesn't produce correct input gesture data",
            inputGestureData,
            InputGestureData(savedInputGestures[0]),
        )
    }

    @Test
    @Parameters(method = "customInputGesturesTestArguments")
    fun testCustomKeyGestureRestoredFromBackup(test: TestData) {
        val userId = 10
        setupKeyGestureController()
        val builder =
            InputGestureData.Builder()
                .setKeyGestureType(test.expectedKeyGestureType)
                .setTrigger(
                    InputGestureData.createKeyTrigger(
                        test.expectedKeys[0],
                        test.expectedModifierState,
                    )
                )
        if (test.expectedAppLaunchData != null) {
            builder.setAppLaunchData(test.expectedAppLaunchData)
        }
        val inputGestureData = builder.build()

        keyGestureController.setCurrentUserId(userId)
        testLooper.dispatchAll()
        keyGestureController.addCustomInputGesture(userId, inputGestureData.aidlData)
        testLooper.dispatchAll()
        val backupData = keyGestureController.getInputGestureBackupPayload(userId)

        // Delete the old data and reinitialize the controller simulating a "fresh" install.
        tempFile.delete()
        startNewInputGlobalTestSession()
        setupKeyGestureController()
        keyGestureController.setCurrentUserId(userId)
        testLooper.dispatchAll()

        // Initially there should be no gestures registered.
        var savedInputGestures = keyGestureController.getCustomInputGestures(userId, null)
        assertEquals(
            "Test: $test doesn't produce correct number of saved input gestures",
            0,
            savedInputGestures.size,
        )

        // After the restore, there should be the original gesture re-registered.
        keyGestureController.applyInputGesturesBackupPayload(backupData, userId)
        savedInputGestures = keyGestureController.getCustomInputGestures(userId, null)
        assertEquals(
            "Test: $test doesn't produce correct number of saved input gestures",
            1,
            savedInputGestures.size,
        )
        assertEquals(
            "Test: $test doesn't produce correct input gesture data",
            inputGestureData,
            InputGestureData(savedInputGestures[0]),
        )
    }

    class TouchpadTestData(
        val name: String,
        val touchpadGestureType: Int,
        val expectedKeyGestureType: Int,
        val expectedAction: Int,
        val expectedAppLaunchData: AppLaunchData? = null,
    ) {
        override fun toString(): String = name
    }

    @Keep
    private fun customTouchpadGesturesTestArguments(): Array<TouchpadTestData> {
        return arrayOf(
            TouchpadTestData(
                "3 Finger Tap -> Go Home",
                InputGestureData.TOUCHPAD_GESTURE_TYPE_THREE_FINGER_TAP,
                KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                KeyGestureEvent.ACTION_GESTURE_COMPLETE,
            ),
            TouchpadTestData(
                "3 Finger Tap -> Launch app",
                InputGestureData.TOUCHPAD_GESTURE_TYPE_THREE_FINGER_TAP,
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                AppLaunchData.createLaunchDataForComponent("com.test", "com.test.BookmarkTest"),
            ),
        )
    }

    @Test
    @Parameters(method = "customTouchpadGesturesTestArguments")
    fun testCustomTouchpadGesture(test: TouchpadTestData) {
        setupKeyGestureController()
        val builder =
            InputGestureData.Builder()
                .setKeyGestureType(test.expectedKeyGestureType)
                .setTrigger(InputGestureData.createTouchpadTrigger(test.touchpadGestureType))
        if (test.expectedAppLaunchData != null) {
            builder.setAppLaunchData(test.expectedAppLaunchData)
        }
        val inputGestureData = builder.build()

        keyGestureController.addCustomInputGesture(0, inputGestureData.aidlData)

        val handledEvents = mutableListOf<KeyGestureEvent>()
        val handler = KeyGestureHandler { event, _ -> handledEvents.add(KeyGestureEvent(event)) }
        keyGestureController.registerKeyGestureHandler(
            intArrayOf(test.expectedKeyGestureType),
            handler,
            TEST_PID,
        )
        handledEvents.clear()

        keyGestureController.handleTouchpadGesture(test.touchpadGestureType)

        assertEquals(
            "Test: $test doesn't produce correct number of key gesture events",
            1,
            handledEvents.size,
        )
        val event = handledEvents[0]
        assertEquals(
            "Test: $test doesn't produce correct key gesture type",
            test.expectedKeyGestureType,
            event.keyGestureType,
        )
        assertEquals(
            "Test: $test doesn't produce correct key gesture action",
            test.expectedAction,
            event.action,
        )
        assertEquals(
            "Test: $test doesn't produce correct app launch data",
            test.expectedAppLaunchData,
            event.appLaunchData,
        )

        keyGestureController.unregisterKeyGestureHandler(handler, TEST_PID)
    }

    @Test
    @Parameters(method = "customTouchpadGesturesTestArguments")
    fun testCustomTouchpadGesturesSavedAndLoadedByController(test: TouchpadTestData) {
        val userId = 10
        setupKeyGestureController()
        val builder =
            InputGestureData.Builder()
                .setKeyGestureType(test.expectedKeyGestureType)
                .setTrigger(InputGestureData.createTouchpadTrigger(test.touchpadGestureType))
        if (test.expectedAppLaunchData != null) {
            builder.setAppLaunchData(test.expectedAppLaunchData)
        }
        val inputGestureData = builder.build()
        keyGestureController.setCurrentUserId(userId)
        testLooper.dispatchAll()
        keyGestureController.addCustomInputGesture(userId, inputGestureData.aidlData)
        testLooper.dispatchAll()

        // Reinitialize the gesture controller simulating a login/logout for the user.
        startNewInputGlobalTestSession()
        setupKeyGestureController()
        keyGestureController.setCurrentUserId(userId)
        testLooper.dispatchAll()

        val savedInputGestures = keyGestureController.getCustomInputGestures(userId, null)
        assertEquals(
            "Test: $test doesn't produce correct number of saved input gestures",
            1,
            savedInputGestures.size,
        )
        assertEquals(
            "Test: $test doesn't produce correct input gesture data",
            inputGestureData,
            InputGestureData(savedInputGestures[0]),
        )
    }

    @Test
    @Parameters(method = "customTouchpadGesturesTestArguments")
    fun testCustomTouchpadGesturesRestoredFromBackup(test: TouchpadTestData) {
        val userId = 10
        setupKeyGestureController()
        val builder =
            InputGestureData.Builder()
                .setKeyGestureType(test.expectedKeyGestureType)
                .setTrigger(InputGestureData.createTouchpadTrigger(test.touchpadGestureType))
        if (test.expectedAppLaunchData != null) {
            builder.setAppLaunchData(test.expectedAppLaunchData)
        }
        val inputGestureData = builder.build()
        keyGestureController.setCurrentUserId(userId)
        testLooper.dispatchAll()
        keyGestureController.addCustomInputGesture(userId, inputGestureData.aidlData)
        testLooper.dispatchAll()
        val backupData = keyGestureController.getInputGestureBackupPayload(userId)

        // Delete the old data and reinitialize the controller simulating a "fresh" install.
        tempFile.delete()
        startNewInputGlobalTestSession()
        setupKeyGestureController()
        keyGestureController.setCurrentUserId(userId)
        testLooper.dispatchAll()

        // Initially there should be no gestures registered.
        var savedInputGestures = keyGestureController.getCustomInputGestures(userId, null)
        assertEquals(
            "Test: $test doesn't produce correct number of saved input gestures",
            0,
            savedInputGestures.size,
        )

        // After the restore, there should be the original gesture re-registered.
        keyGestureController.applyInputGesturesBackupPayload(backupData, userId)
        savedInputGestures = keyGestureController.getCustomInputGestures(userId, null)
        assertEquals(
            "Test: $test doesn't produce correct number of saved input gestures",
            1,
            savedInputGestures.size,
        )
        assertEquals(
            "Test: $test doesn't produce correct input gesture data",
            inputGestureData,
            InputGestureData(savedInputGestures[0]),
        )
    }

    @Test
    fun testAccessibilityShortcutChordPressed() {
        setupKeyGestureController()

        sendKeys(
            intArrayOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN),
            // Assuming this value is always greater than the accessibility shortcut timeout, which
            // currently defaults to 3000ms
            timeDelayMs = 10000,
        )
        Mockito.verify(accessibilityShortcutController, times(1)).performAccessibilityShortcut()
    }

    @Test
    fun testAccessibilityTvShortcutChordPressed() {
        setupKeyGestureController()

        sendKeys(intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_DOWN), timeDelayMs = 10000)
        Mockito.verify(accessibilityShortcutController, times(1)).performAccessibilityShortcut()
    }

    @Test
    fun testAccessibilityShortcutChordPressedForLessThanTimeout() {
        setupKeyGestureController()

        sendKeys(
            intArrayOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN),
            timeDelayMs = 0,
        )
        Mockito.verify(accessibilityShortcutController, never()).performAccessibilityShortcut()
    }

    @Test
    fun testAccessibilityTvShortcutChordPressedForLessThanTimeout() {
        setupKeyGestureController()

        sendKeys(intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_DOWN), timeDelayMs = 0)
        Mockito.verify(accessibilityShortcutController, never()).performAccessibilityShortcut()
    }

    @Test
    fun testUnableToRegisterFromSamePidTwice() {
        setupKeyGestureController()

        val handler1 = KeyGestureHandler { _, _ -> }
        val handler2 = KeyGestureHandler { _, _ -> }
        keyGestureController.registerKeyGestureHandler(
            intArrayOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
            handler1,
            RANDOM_PID1,
        )

        assertThrows(IllegalStateException::class.java) {
            keyGestureController.registerKeyGestureHandler(
                intArrayOf(KeyGestureEvent.KEY_GESTURE_TYPE_BACK),
                handler2,
                RANDOM_PID1,
            )
        }
    }

    @Test
    fun testUnableToRegisterSameGestureTwice() {
        setupKeyGestureController()

        val handler1 = KeyGestureHandler { _, _ -> }
        val handler2 = KeyGestureHandler { _, _ -> }
        keyGestureController.registerKeyGestureHandler(
            intArrayOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
            handler1,
            RANDOM_PID1,
        )

        assertThrows(IllegalArgumentException::class.java) {
            keyGestureController.registerKeyGestureHandler(
                intArrayOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
                handler2,
                RANDOM_PID2,
            )
        }
    }

    @Test
    fun testUnableToRegisterEmptyListOfGestures() {
        setupKeyGestureController()

        val handler = KeyGestureHandler { _, _ -> }

        assertThrows(IllegalArgumentException::class.java) {
            keyGestureController.registerKeyGestureHandler(intArrayOf(), handler, RANDOM_PID1)
        }
    }

    @Test
    fun testGestureHandlerNotCalledOnceUnregistered() {
        setupKeyGestureController()

        var callbackCount = 0
        val handler1 = KeyGestureHandler { _, _ -> callbackCount++ }
        keyGestureController.registerKeyGestureHandler(
            intArrayOf(KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS),
            handler1,
            TEST_PID,
        )
        sendKeys(intArrayOf(KeyEvent.KEYCODE_RECENT_APPS))
        assertEquals(1, callbackCount)

        keyGestureController.unregisterKeyGestureHandler(handler1, TEST_PID)

        // Callback should not be sent after unregister
        sendKeys(intArrayOf(KeyEvent.KEYCODE_RECENT_APPS))
        assertEquals(1, callbackCount)
    }

    private fun testKeyGestureInternal(test: TestData) {
        val handledEvents = mutableListOf<KeyGestureEvent>()
        val handler = KeyGestureHandler { event, _ -> handledEvents.add(KeyGestureEvent(event)) }
        keyGestureController.registerKeyGestureHandler(
            intArrayOf(test.expectedKeyGestureType),
            handler,
            TEST_PID,
        )
        handledEvents.clear()

        sendKeys(test.keys)

        assertEquals(
            "Test: $test doesn't produce correct number of key gesture events",
            test.expectedActions.size,
            handledEvents.size,
        )
        for (i in handledEvents.indices) {
            val event = handledEvents[i]
            assertArrayEquals(
                "Test: $test doesn't produce correct key gesture keycodes",
                test.expectedKeys,
                event.keycodes,
            )
            assertEquals(
                "Test: $test doesn't produce correct key gesture modifier state",
                test.expectedModifierState,
                event.modifierState,
            )
            assertEquals(
                "Test: $test doesn't produce correct key gesture type",
                test.expectedKeyGestureType,
                event.keyGestureType,
            )
            assertEquals(
                "Test: $test doesn't produce correct key gesture action",
                test.expectedActions[i],
                event.action,
            )
            assertEquals(
                "Test: $test doesn't produce correct app launch data",
                test.expectedAppLaunchData,
                event.appLaunchData,
            )
        }

        keyGestureController.unregisterKeyGestureHandler(handler, TEST_PID)
    }

    private fun testKeyGestureNotProduced(
        testName: String,
        testKeys: IntArray,
        possibleGestures: IntArray,
    ) {
        var handledEvents = mutableListOf<KeyGestureEvent>()
        val handler = KeyGestureHandler { event, _ -> handledEvents.add(KeyGestureEvent(event)) }
        keyGestureController.registerKeyGestureHandler(possibleGestures, handler, TEST_PID)
        handledEvents.clear()

        sendKeys(testKeys)
        assertEquals("Test: $testName should not produce Key gesture", 0, handledEvents.size)
    }

    private fun sendKeys(
        testKeys: IntArray,
        assertNotSentToApps: Boolean = false,
        timeDelayMs: Long = 0,
    ) {
        var metaState = 0
        val now = SystemClock.uptimeMillis()
        for (key in testKeys) {
            val downEvent =
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_DOWN,
                    key,
                    0 /*repeat*/,
                    metaState,
                    DEVICE_ID,
                    0 /*scancode*/,
                    0 /*flags*/,
                    InputDevice.SOURCE_KEYBOARD,
                )
            interceptKey(downEvent, assertNotSentToApps)
            metaState = metaState or MODIFIER.getOrDefault(key, 0)

            downEvent.recycle()
            testLooper.dispatchAll()
        }

        if (timeDelayMs > 0) {
            testLooper.moveTimeForward(timeDelayMs)
            testLooper.dispatchAll()
        }

        for (key in testKeys.reversed()) {
            val upEvent =
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_UP,
                    key,
                    0 /*repeat*/,
                    metaState,
                    DEVICE_ID,
                    0 /*scancode*/,
                    0 /*flags*/,
                    InputDevice.SOURCE_KEYBOARD,
                )
            interceptKey(upEvent, assertNotSentToApps)
            metaState = metaState and MODIFIER.getOrDefault(key, 0).inv()

            upEvent.recycle()
            testLooper.dispatchAll()
        }
    }

    private fun interceptKey(event: KeyEvent, assertNotSentToApps: Boolean) {
        keyGestureController.interceptKeyBeforeQueueing(event, FLAG_INTERACTIVE)
        testLooper.dispatchAll()

        val consumed = keyGestureController.interceptKeyBeforeDispatching(null, event, 0) == -1L
        if (assertNotSentToApps) {
            assertTrue("interceptKeyBeforeDispatching should consume all events $event", consumed)
        }
        if (!consumed) {
            keyGestureController.interceptUnhandledKey(event, null)
        }
    }

    inner class KeyGestureEventListener : IKeyGestureEventListener.Stub() {
        override fun onKeyGestureEvent(event: AidlKeyGestureEvent) {
            events.add(KeyGestureEvent(event))
        }
    }

    inner class KeyGestureHandler(
        private var handler: (event: AidlKeyGestureEvent, token: IBinder?) -> Unit
    ) : IKeyGestureHandler.Stub() {
        override fun handleKeyGesture(event: AidlKeyGestureEvent, token: IBinder?) {
            handler(event, token)
        }
    }
}
