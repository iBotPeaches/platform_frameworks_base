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

package com.android.settingslib.notification.modes;

import static android.app.AutomaticZenRule.TYPE_BEDTIME;
import static android.app.AutomaticZenRule.TYPE_DRIVING;
import static android.app.AutomaticZenRule.TYPE_IMMERSIVE;
import static android.app.AutomaticZenRule.TYPE_OTHER;
import static android.app.AutomaticZenRule.TYPE_SCHEDULE_CALENDAR;
import static android.app.AutomaticZenRule.TYPE_SCHEDULE_TIME;
import static android.app.AutomaticZenRule.TYPE_THEATER;
import static android.app.AutomaticZenRule.TYPE_UNKNOWN;
import static android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS;
import static android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
import static android.app.NotificationManager.INTERRUPTION_FILTER_NONE;
import static android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY;
import static android.service.notification.SystemZenRules.PACKAGE_ANDROID;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.AutomaticZenRule;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.service.notification.Condition;
import android.service.notification.SystemZenRules;
import android.service.notification.ZenDeviceEffects;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenPolicy;

import androidx.test.core.app.ApplicationProvider;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ZenModeTest {

    private static final ZenPolicy ZEN_POLICY = new ZenPolicy.Builder().allowAllSounds().build();

    private static final AutomaticZenRule ZEN_RULE =
            new AutomaticZenRule.Builder("Driving", Uri.parse("drive"))
                    .setPackage("com.some.driving.thing")
                    .setType(TYPE_DRIVING)
                    .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                    .setZenPolicy(ZEN_POLICY)
                    .build();

    private static final String IMPLICIT_RULE_ID = ZenModeConfig.implicitRuleId("some.package");
    private static final AutomaticZenRule IMPLICIT_ZEN_RULE =
            new AutomaticZenRule.Builder("Implicit", Uri.parse("implicit/some.package"))
                    .setPackage("some.package")
                    .setType(TYPE_OTHER)
                    .build();

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testBasicMethods_mode() {
        ZenMode zenMode = new ZenMode("id", ZEN_RULE, zenConfigRuleFor(ZEN_RULE, true));

        assertThat(zenMode.getId()).isEqualTo("id");
        assertThat(zenMode.getRule()).isEqualTo(ZEN_RULE);
        assertThat(zenMode.isManualDnd()).isFalse();
        assertThat(zenMode.canEditNameAndIcon()).isTrue();
        assertThat(zenMode.canEditPolicy()).isTrue();
        assertThat(zenMode.canBeDeleted()).isTrue();
        assertThat(zenMode.isActive()).isTrue();
    }

    @Test
    public void testBasicMethods_manualDnd() {
        ZenMode manualMode = TestModeBuilder.MANUAL_DND;

        assertThat(manualMode.getId()).isEqualTo(ZenMode.MANUAL_DND_MODE_ID);
        assertThat(manualMode.isManualDnd()).isTrue();
        assertThat(manualMode.canEditNameAndIcon()).isFalse();
        assertThat(manualMode.canEditPolicy()).isTrue();
        assertThat(manualMode.canBeDeleted()).isFalse();
        assertThat(manualMode.isActive()).isFalse();
        assertThat(manualMode.getOwnerPackage()).isEqualTo(PACKAGE_ANDROID);
    }

    @Test
    public void constructor_enabledRule_statusEnabled() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder(ZEN_RULE).setEnabled(true).build();
        ZenModeConfig.ZenRule configZenRule = zenConfigRuleFor(azr, false);

        ZenMode mode = new ZenMode("id", azr, configZenRule);
        assertThat(mode.getStatus()).isEqualTo(ZenMode.Status.ENABLED);
        assertThat(mode.isActive()).isFalse();
    }

    @Test
    public void constructor_activeRule_statusActive() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder(ZEN_RULE).setEnabled(true).build();
        ZenModeConfig.ZenRule configZenRule = zenConfigRuleFor(azr, true);

        ZenMode mode = new ZenMode("id", azr, configZenRule);
        assertThat(mode.getStatus()).isEqualTo(ZenMode.Status.ENABLED_AND_ACTIVE);
        assertThat(mode.isActive()).isTrue();
    }

    @Test
    public void constructor_disabledRuleByUser_statusDisabledByUser() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder(ZEN_RULE).setEnabled(false).build();
        ZenModeConfig.ZenRule configZenRule = zenConfigRuleFor(azr, false);
        configZenRule.disabledOrigin = ZenModeConfig.ORIGIN_USER_IN_SYSTEMUI;

        ZenMode mode = new ZenMode("id", azr, configZenRule);
        assertThat(mode.getStatus()).isEqualTo(ZenMode.Status.DISABLED_BY_USER);
    }

    @Test
    public void constructor_disabledRuleByOther_statusDisabledByOther() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder(ZEN_RULE).setEnabled(false).build();
        ZenModeConfig.ZenRule configZenRule = zenConfigRuleFor(azr, false);
        configZenRule.disabledOrigin = ZenModeConfig.ORIGIN_APP;

        ZenMode mode = new ZenMode("id", azr, configZenRule);
        assertThat(mode.getStatus()).isEqualTo(ZenMode.Status.DISABLED_BY_OTHER);
    }

    @Test
    public void isCustomManual_customManualMode() {
        AutomaticZenRule rule = new AutomaticZenRule.Builder("Mode", Uri.parse("x"))
                .setPackage(SystemZenRules.PACKAGE_ANDROID)
                .setType(AutomaticZenRule.TYPE_OTHER)
                .build();
        ZenMode mode = new ZenMode("id", rule, zenConfigRuleFor(rule, false));

        assertThat(mode.isCustomManual()).isTrue();
    }

    @Test
    public void isCustomManual_scheduleTime_false() {
        AutomaticZenRule rule = new AutomaticZenRule.Builder("Mode", Uri.parse("x"))
                .setPackage(SystemZenRules.PACKAGE_ANDROID)
                .setType(TYPE_SCHEDULE_TIME)
                .build();
        ZenMode mode = new ZenMode("id", rule, zenConfigRuleFor(rule, false));

        assertThat(mode.isCustomManual()).isFalse();
    }

    @Test
    public void isCustomManual_scheduleCalendar_false() {
        AutomaticZenRule rule = new AutomaticZenRule.Builder("Mode", Uri.parse("x"))
                .setPackage(SystemZenRules.PACKAGE_ANDROID)
                .setType(AutomaticZenRule.TYPE_SCHEDULE_CALENDAR)
                .build();
        ZenMode mode = new ZenMode("id", rule, zenConfigRuleFor(rule, false));

        assertThat(mode.isCustomManual()).isFalse();
    }

    @Test
    public void isCustomManual_appProvidedMode_false() {
        AutomaticZenRule rule = new AutomaticZenRule.Builder("Mode", Uri.parse("x"))
                .setPackage("com.some.package")
                .setType(AutomaticZenRule.TYPE_OTHER)
                .build();
        ZenMode mode = new ZenMode("id", rule, zenConfigRuleFor(rule, false));

        assertThat(mode.isCustomManual()).isFalse();
    }

    @Test
    public void isCustomManual_manualDnd_false() {
        AutomaticZenRule dndRule = new AutomaticZenRule.Builder("Mode", Uri.parse("x"))
                .setPackage(SystemZenRules.PACKAGE_ANDROID)
                .setType(AutomaticZenRule.TYPE_OTHER)
                .build();
        ZenMode mode = ZenMode.manualDndMode(dndRule, false);

        assertThat(mode.isCustomManual()).isFalse();
    }

    @Test
    public void getOwner_returnsOwnerDetails() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Rule", Uri.EMPTY)
                .setPackage("package")
                .setConfigurationActivity(new ComponentName("package", "configActivity"))
                .setOwner(new ComponentName("package", "conditionService"))
                .build();
        ZenMode zenMode = new ZenMode("id", azr, zenConfigRuleFor(azr, false));

        ZenMode.Owner owner = zenMode.getOwner();
        assertThat(owner.packageName()).isEqualTo("package");
        assertThat(owner.configurationActivity()).isEqualTo(
                new ComponentName("package", "configActivity"));
        assertThat(owner.conditionProvider()).isEqualTo(
                new ComponentName("package", "conditionService"));
    }

    @Test
    public void getPolicy_interruptionFilterPriority_returnsZenPolicy() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Rule", Uri.EMPTY)
                .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                .setZenPolicy(ZEN_POLICY)
                .build();
        ZenMode zenMode = new ZenMode("id", azr, zenConfigRuleFor(azr, false));

        assertThat(zenMode.getPolicy()).isEqualTo(ZEN_POLICY);
    }

    @Test
    public void getPolicy_interruptionFilterAlarms_returnsPolicyAllowingAlarms() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Rule", Uri.EMPTY)
                .setInterruptionFilter(INTERRUPTION_FILTER_ALARMS)
                .setZenPolicy(ZEN_POLICY) // should be ignored
                .build();
        ZenMode zenMode = new ZenMode("id", azr, zenConfigRuleFor(azr, false));

        assertThat(zenMode.getPolicy()).isEqualTo(
                new ZenPolicy.Builder(ZenModeConfig.getDefaultZenPolicy())
                        .disallowAllSounds()
                        .allowAlarms(true)
                        .allowMedia(true)
                        .allowPriorityChannels(false)
                        .build());
    }

    @Test
    public void getPolicy_interruptionFilterNone_returnsPolicyAllowingNothing() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Rule", Uri.EMPTY)
                .setInterruptionFilter(INTERRUPTION_FILTER_NONE)
                .setZenPolicy(ZEN_POLICY) // should be ignored
                .build();
        ZenMode zenMode = new ZenMode("id", azr, zenConfigRuleFor(azr, false));

        assertThat(zenMode.getPolicy()).isEqualTo(
                new ZenPolicy.Builder(ZenModeConfig.getDefaultZenPolicy())
                        .disallowAllSounds()
                        .allowPriorityChannels(false)
                        .build());
    }

    @Test
    public void setPolicy_setsInterruptionFilterPriority() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Rule", Uri.EMPTY)
                .setInterruptionFilter(INTERRUPTION_FILTER_ALARMS)
                .build();
        ZenMode zenMode = new ZenMode("id", azr, zenConfigRuleFor(azr, false));

        zenMode.setPolicy(ZEN_POLICY);

        assertThat(zenMode.getInterruptionFilter()).isEqualTo(
                INTERRUPTION_FILTER_PRIORITY);
        assertThat(zenMode.getPolicy()).isEqualTo(ZEN_POLICY);
        assertThat(zenMode.getRule().getZenPolicy()).isEqualTo(ZEN_POLICY);
    }

    @Test
    public void getInterruptionFilter_returnsFilter() {
        ZenMode mode = new TestModeBuilder().setInterruptionFilter(
                INTERRUPTION_FILTER_ALARMS).build();

        assertThat(mode.getInterruptionFilter()).isEqualTo(INTERRUPTION_FILTER_ALARMS);
    }

    @Test
    public void setInterruptionFilter_setsFilter() {
        ZenMode mode = new TestModeBuilder().setInterruptionFilter(
                INTERRUPTION_FILTER_ALARMS).build();

        mode.setInterruptionFilter(INTERRUPTION_FILTER_ALL);

        assertThat(mode.getInterruptionFilter()).isEqualTo(INTERRUPTION_FILTER_ALL);
    }

    @Test
    public void setInterruptionFilter_manualDnd_throws() {
        ZenMode manualDnd = TestModeBuilder.MANUAL_DND;

        assertThrows(IllegalStateException.class,
                () -> manualDnd.setInterruptionFilter(INTERRUPTION_FILTER_ALL));
    }

    @Test
    public void canEditPolicy_onlyFalseForSpecialDnd() {
        assertThat(TestModeBuilder.EXAMPLE.canEditPolicy()).isTrue();

        ZenMode inactiveDnd = new TestModeBuilder().makeManualDnd().setActive(false).build();
        assertThat(inactiveDnd.canEditPolicy()).isTrue();

        ZenMode activeDnd = new TestModeBuilder().makeManualDnd().setActive(true).build();
        assertThat(activeDnd.canEditPolicy()).isTrue();

        ZenMode dndWithAlarms = new TestModeBuilder()
                .makeManualDnd()
                .setInterruptionFilter(INTERRUPTION_FILTER_ALARMS)
                .setActive(true)
                .build();
        assertThat(dndWithAlarms.canEditPolicy()).isFalse();

        ZenMode dndWithNone = new TestModeBuilder()
                .makeManualDnd()
                .setInterruptionFilter(INTERRUPTION_FILTER_NONE)
                .setActive(true)
                .build();
        assertThat(dndWithNone.canEditPolicy()).isFalse();

        // Note: Backend will never return an inactive manual mode with custom filter.
        ZenMode badDndWithAlarms = new TestModeBuilder()
                .makeManualDnd()
                .setInterruptionFilter(INTERRUPTION_FILTER_ALARMS)
                .setActive(false)
                .build();
        assertThat(badDndWithAlarms.canEditPolicy()).isFalse();

        ZenMode badDndWithNone = new TestModeBuilder()
                .makeManualDnd()
                .setInterruptionFilter(INTERRUPTION_FILTER_NONE)
                .setActive(false)
                .build();
        assertThat(badDndWithNone.canEditPolicy()).isFalse();
    }

    @Test
    public void canEditPolicy_whenTrue_allowsSettingPolicyAndEffects() {
        ZenMode normalDnd = new TestModeBuilder().makeManualDnd().setActive(true).build();

        assertThat(normalDnd.canEditPolicy()).isTrue();

        ZenPolicy somePolicy = new ZenPolicy.Builder().showBadges(true).build();
        normalDnd.setPolicy(somePolicy);
        assertThat(normalDnd.getPolicy()).isEqualTo(somePolicy);

        ZenDeviceEffects someEffects = new ZenDeviceEffects.Builder()
                .setShouldUseNightMode(true).build();
        normalDnd.setDeviceEffects(someEffects);
        assertThat(normalDnd.getDeviceEffects()).isEqualTo(someEffects);
    }

    @Test
    public void canEditPolicy_whenFalse_preventsSettingFilterPolicyOrEffects() {
        ZenMode specialDnd = new TestModeBuilder()
                .makeManualDnd()
                .setInterruptionFilter(INTERRUPTION_FILTER_ALARMS)
                .setActive(true)
                .build();

        assertThat(specialDnd.canEditPolicy()).isFalse();
        assertThrows(IllegalStateException.class,
                () -> specialDnd.setPolicy(ZEN_POLICY));
        assertThrows(IllegalStateException.class,
                () -> specialDnd.setDeviceEffects(new ZenDeviceEffects.Builder().build()));
    }

    @Test
    public void comparator_prioritizes() {
        ZenMode manualDnd = TestModeBuilder.MANUAL_DND;
        ZenMode driving1 = new TestModeBuilder().setName("b1").setType(TYPE_DRIVING).build();
        ZenMode driving2 = new TestModeBuilder().setName("b2").setType(TYPE_DRIVING).build();
        ZenMode bedtime1 = new TestModeBuilder().setName("c1").setType(TYPE_BEDTIME).build();
        ZenMode bedtime2 = new TestModeBuilder().setName("c2").setType(TYPE_BEDTIME).build();
        ZenMode other = new TestModeBuilder().setName("a1").setType(TYPE_OTHER).build();
        ZenMode immersive = new TestModeBuilder().setName("a2").setType(TYPE_IMMERSIVE).build();
        ZenMode unknown = new TestModeBuilder().setName("a3").setType(TYPE_UNKNOWN).build();
        ZenMode theater = new TestModeBuilder().setName("a4").setType(TYPE_THEATER).build();

        ArrayList<ZenMode> list = new ArrayList<>(List.of(other, theater, bedtime1, unknown,
                driving2, manualDnd, driving1, bedtime2, immersive));
        list.sort(ZenMode.PRIORITIZING_COMPARATOR);

        assertThat(list)
                .containsExactly(manualDnd, bedtime1, bedtime2, driving1, driving2, other,
                        immersive, unknown, theater)
                .inOrder();
    }

    @Test
    public void writeToParcel_equals() {
        assertUnparceledIsEqualToOriginal("example",
                new ZenMode("id", ZEN_RULE, zenConfigRuleFor(ZEN_RULE, false)));

        assertUnparceledIsEqualToOriginal("dnd", ZenMode.manualDndMode(ZEN_RULE, true));

        assertUnparceledIsEqualToOriginal("custom_manual",
                ZenMode.newCustomManual("New mode", R.drawable.ic_zen_mode_type_immersive));

        assertUnparceledIsEqualToOriginal("implicit",
                new ZenMode(IMPLICIT_RULE_ID, IMPLICIT_ZEN_RULE,
                        zenConfigRuleFor(IMPLICIT_ZEN_RULE, false)));
    }

    @Test
    public void getIconKey_normalModeWithCustomIcon_isCustomIcon() {
        ZenMode mode = new TestModeBuilder()
                .setType(TYPE_BEDTIME)
                .setPackage("some.package")
                .setIconResId(123)
                .build();

        ZenIcon.Key iconKey = mode.getIconKey();

        assertThat(iconKey.resPackage()).isEqualTo("some.package");
        assertThat(iconKey.resId()).isEqualTo(123);
        assertThat(mode.getIconResId()).isEqualTo(123);
    }

    @Test
    public void getIconKey_systemOwnedModeWithCustomIcon_isCustomIcon() {
        ZenMode mode = new TestModeBuilder()
                .setType(TYPE_SCHEDULE_CALENDAR)
                .setPackage(PACKAGE_ANDROID)
                .setIconResId(123)
                .build();

        ZenIcon.Key iconKey = mode.getIconKey();

        assertThat(iconKey.resPackage()).isNull();
        assertThat(iconKey.resId()).isEqualTo(123);
        assertThat(mode.getIconResId()).isEqualTo(123);
    }

    @Test
    public void getIconKey_implicitModeWithCustomIcon_isCustomIcon() {
        ZenMode mode = new TestModeBuilder()
                .setId(ZenModeConfig.implicitRuleId("some.package"))
                .setPackage("some.package")
                .setIconResId(123)
                .build();

        ZenIcon.Key iconKey = mode.getIconKey();

        assertThat(iconKey.resPackage()).isEqualTo("some.package");
        assertThat(iconKey.resId()).isEqualTo(123);
        assertThat(mode.getIconResId()).isEqualTo(123);
    }

    @Test
    public void getIconKey_manualDnd_isDndIcon() {
        ZenMode mode = TestModeBuilder.MANUAL_DND;
        ZenIcon.Key iconKey = mode.getIconKey();

        assertThat(iconKey.resPackage()).isNull();
        assertThat(iconKey.resId()).isEqualTo(
                com.android.internal.R.drawable.ic_zen_mode_type_special_dnd);
        assertThat(mode.getIconResId()).isEqualTo(0);
    }

    @Test
    public void getIconKey_normalModeWithoutCustomIcon_isModeTypeIcon() {
        ZenMode mode = new TestModeBuilder()
                .setType(TYPE_BEDTIME)
                .setPackage("some.package")
                .build();

        ZenIcon.Key iconKey = mode.getIconKey();

        assertThat(iconKey.resPackage()).isNull();
        assertThat(iconKey.resId()).isEqualTo(
                com.android.internal.R.drawable.ic_zen_mode_type_bedtime);
        assertThat(mode.getIconResId()).isEqualTo(0);
    }

    @Test
    public void getIconKey_systemOwnedModeWithoutCustomIcon_isModeTypeIcon() {
        ZenMode mode = new TestModeBuilder()
                .setType(TYPE_SCHEDULE_CALENDAR)
                .setPackage(PACKAGE_ANDROID)
                .build();

        ZenIcon.Key iconKey = mode.getIconKey();

        assertThat(iconKey.resPackage()).isNull();
        assertThat(iconKey.resId()).isEqualTo(
                com.android.internal.R.drawable.ic_zen_mode_type_schedule_calendar);
        assertThat(mode.getIconResId()).isEqualTo(0);
    }

    @Test
    public void getIconKey_implicitModeWithoutCustomIcon_isDndIcon() {
        ZenMode mode = new TestModeBuilder()
                .setId(ZenModeConfig.implicitRuleId("some.package"))
                .setPackage("some_package")
                .setType(TYPE_BEDTIME) // Type should be ignored.
                .build();

        ZenIcon.Key iconKey = mode.getIconKey();

        assertThat(iconKey.resPackage()).isNull();
        assertThat(iconKey.resId()).isEqualTo(
                com.android.internal.R.drawable.ic_zen_mode_type_special_dnd);
        assertThat(mode.getIconResId()).isEqualTo(0);
    }

    @Test
    public void setCustomModeConditionId_timeSchedule() {
        ZenMode mode = new TestModeBuilder()
                .setPackage(PACKAGE_ANDROID)
                .build();
        ZenModeConfig.ScheduleInfo timeSchedule = new ZenModeConfig.ScheduleInfo();
        timeSchedule.startHour = 9;
        timeSchedule.endHour = 12;
        timeSchedule.days = new int[] {Calendar.SATURDAY, Calendar.SUNDAY};
        Uri scheduleUri = ZenModeConfig.toScheduleConditionId(timeSchedule);

        mode.setCustomModeConditionId(mContext, scheduleUri);

        assertThat(mode.getType()).isEqualTo(TYPE_SCHEDULE_TIME);
        assertThat(ZenModeSchedules.getTimeSchedule(mode)).isEqualTo(timeSchedule);
        assertThat(mode.getTriggerDescription()).isEqualTo("Sun, Sat, 9:00 AM - 12:00 PM");

        assertThat(mode.getRule().getConditionId()).isEqualTo(scheduleUri);
        assertThat(mode.getRule().getOwner()).isEqualTo(
                ZenModeConfig.getScheduleConditionProvider());
    }

    @Test
    public void setCustomModeConditionId_calendarSchedule() {
        ZenMode mode = new TestModeBuilder()
                .setPackage(PACKAGE_ANDROID)
                .build();
        ZenModeConfig.EventInfo calendarSchedule = new ZenModeConfig.EventInfo();
        calendarSchedule.calendarId = 1L;
        calendarSchedule.calName = "My events";
        Uri scheduleUri = ZenModeConfig.toEventConditionId(calendarSchedule);

        mode.setCustomModeConditionId(mContext, scheduleUri);

        assertThat(mode.getType()).isEqualTo(TYPE_SCHEDULE_CALENDAR);
        assertThat(ZenModeSchedules.getCalendarSchedule(mode)).isEqualTo(calendarSchedule);
        assertThat(mode.getTriggerDescription()).isEqualTo("My events");

        assertThat(mode.getRule().getConditionId()).isEqualTo(scheduleUri);
        assertThat(mode.getRule().getOwner()).isEqualTo(
                ZenModeConfig.getEventConditionProvider());
    }

    @Test
    public void setCustomModeConditionId_manualSchedule() {
        ZenMode mode = new TestModeBuilder()
                .setPackage(PACKAGE_ANDROID)
                .build();

        mode.setCustomModeConditionId(mContext, ZenModeConfig.toCustomManualConditionId());

        assertThat(mode.getType()).isEqualTo(TYPE_OTHER);
        assertThat(mode.getTriggerDescription()).isEqualTo("");

        assertThat(mode.getRule().getConditionId()).isEqualTo(
                ZenModeConfig.toCustomManualConditionId());
        assertThat(mode.getRule().getOwner()).isEqualTo(
                ZenModeConfig.getCustomManualConditionProvider());
    }

    @Test
    public void setCustomModeConditionId_nonSystemRule_throws() {
        ZenMode mode = new TestModeBuilder()
                .setPackage("some.other.package")
                .build();

        assertThrows(IllegalStateException.class,
                () -> mode.setCustomModeConditionId(mContext, Uri.parse("blah")));
    }

    private static void assertUnparceledIsEqualToOriginal(String type, ZenMode original) {
        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ZenMode unparceled = ZenMode.CREATOR.createFromParcel(parcel);

            assertWithMessage("Comparing " + type).that(unparceled).isEqualTo(original);
        } finally {
            parcel.recycle();
        }
    }

    private static ZenModeConfig.ZenRule zenConfigRuleFor(AutomaticZenRule azr, boolean isActive) {
        ZenModeConfig.ZenRule zenRule = new ZenModeConfig.ZenRule();
        zenRule.pkg = azr.getPackageName();
        zenRule.conditionId = azr.getConditionId();
        zenRule.enabled = azr.isEnabled();
        if (isActive) {
            zenRule.condition = new Condition(azr.getConditionId(), "active", Condition.STATE_TRUE);
        }
        return zenRule;
    }
}
