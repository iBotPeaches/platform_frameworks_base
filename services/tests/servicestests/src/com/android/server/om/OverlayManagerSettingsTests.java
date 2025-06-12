/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.om;

import static android.content.om.OverlayConstraint.TYPE_DEVICE_ID;
import static android.content.om.OverlayConstraint.TYPE_DISPLAY_ID;
import static android.content.om.OverlayInfo.STATE_DISABLED;
import static android.content.om.OverlayInfo.STATE_ENABLED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.om.OverlayConstraint;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.text.TextUtils;
import android.util.Xml;

import androidx.annotation.NonNull;

import com.android.modules.utils.TypedXmlPullParser;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

@RunWith(JUnitParamsRunner.class)
public class OverlayManagerSettingsTests {
    private OverlayManagerSettings mSettings;
    private static final int USER_0 = 0;
    private static final int USER_1 = 1;

    private static final int DISPLAY_ID = 1;
    private static final int DEVICE_ID = 2;

    private static final OverlayConstraint CONSTRAINT_0 =
            new OverlayConstraint(TYPE_DISPLAY_ID, DISPLAY_ID);
    private static final OverlayConstraint CONSTRAINT_1 =
            new OverlayConstraint(TYPE_DEVICE_ID, DEVICE_ID);

    private static final OverlayIdentifier OVERLAY_A = new OverlayIdentifier("com.test.overlay_a",
            null /* overlayName */);
    private static final OverlayIdentifier OVERLAY_B = new OverlayIdentifier("com.test.overlay_b",
            null /* overlayName */);
    private static final OverlayIdentifier OVERLAY_C = new OverlayIdentifier("com.test.overlay_c",
            null /* overlayName */);

    private static final OverlayInfo OVERLAY_A_USER0 = createInfo(OVERLAY_A, USER_0);
    private static final OverlayInfo OVERLAY_B_USER0 = createInfo(OVERLAY_B, USER_0);
    private static final OverlayInfo OVERLAY_C_USER0 = createInfo(OVERLAY_C, USER_0);

    private static final OverlayInfo OVERLAY_A_USER1 = createInfo(OVERLAY_A, USER_1);
    private static final OverlayInfo OVERLAY_B_USER1 = createInfo(OVERLAY_B, USER_1);

    private static final OverlayInfo OVERLAY_A_USER0_WITH_CONSTRAINTS =
            createInfo(OVERLAY_A, USER_0, List.of(CONSTRAINT_0, CONSTRAINT_1));
    private static final OverlayInfo OVERLAY_B_USER0_WITH_CONSTRAINTS =
            createInfo(OVERLAY_B, USER_0, List.of(CONSTRAINT_1));
    private static final OverlayInfo OVERLAY_B_USER1_WITH_CONSTRAINTS =
            createInfo(OVERLAY_B, USER_1, List.of(CONSTRAINT_1));

    private static final String TARGET_PACKAGE = "com.test.target";

    @Before
    public void setUp() throws Exception {
        mSettings = new OverlayManagerSettings();
    }

    // tests: generic functionality

    @Test
    public void testSettingsInitiallyEmpty() throws Exception {
        final Map<String, List<OverlayInfo>> map = mSettings.getOverlaysForUser(0 /* userId */);
        assertEquals(0, map.size());
    }

    @Test
    public void testBasicSetAndGet() throws Exception {
        assertDoesNotContain(mSettings, OVERLAY_A_USER0);

        insertSetting(OVERLAY_A_USER0);
        assertContains(mSettings, OVERLAY_A_USER0);
        final OverlayInfo oi = mSettings.getOverlayInfo(OVERLAY_A, USER_0);
        assertEquals(OVERLAY_A_USER0, oi);

        assertTrue(mSettings.remove(OVERLAY_A, USER_0));
        assertDoesNotContain(mSettings, OVERLAY_A, USER_0);
    }

    @Test
    public void testGetUsers() throws Exception {
        assertArrayEquals(new int[]{}, mSettings.getUsers());

        insertSetting(OVERLAY_A_USER0);
        assertArrayEquals(new int[]{USER_0}, mSettings.getUsers());

        insertSetting(OVERLAY_A_USER1);
        insertSetting(OVERLAY_B_USER1);
        assertArrayEquals(new int[]{USER_0, USER_1}, mSettings.getUsers());
    }

    @Test
    public void testGetOverlaysForUser() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER0);
        insertSetting(OVERLAY_A_USER1);
        insertSetting(OVERLAY_B_USER0);

        final Map<String, List<OverlayInfo>> map = mSettings.getOverlaysForUser(USER_0);
        assertEquals(Set.of(TARGET_PACKAGE), map.keySet());

        // Two overlays in user 0 target the same package
        final List<OverlayInfo> list = map.get(TARGET_PACKAGE);
        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_B_USER0), list);

        // No users installed for user 3
        assertEquals(Map.<String, List<OverlayInfo>>of(), mSettings.getOverlaysForUser(3));
    }

    @Test
    public void testRemoveUser() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER0);
        insertSetting(OVERLAY_A_USER1);

        assertContains(mSettings, OVERLAY_A_USER0);
        assertContains(mSettings, OVERLAY_B_USER0);
        assertContains(mSettings, OVERLAY_A_USER1);

        mSettings.removeUser(USER_0);

        assertDoesNotContain(mSettings, OVERLAY_A_USER0);
        assertDoesNotContain(mSettings, OVERLAY_B_USER0);
        assertContains(mSettings, OVERLAY_A_USER1);
    }

    @Test
    public void testOrderOfNewlyAddedItems() throws Exception {
        // new items are appended to the list
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER0);
        insertSetting(OVERLAY_C_USER0);

        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_B_USER0, OVERLAY_C_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));

        // overlays keep their positions when updated
        mSettings.setState(OVERLAY_B, USER_0, STATE_ENABLED);
        final OverlayInfo oi = mSettings.getOverlayInfo(OVERLAY_B, USER_0);
        assertNotNull(oi);

        assertListsAreEqual(List.of(OVERLAY_A_USER0, oi, OVERLAY_C_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));
    }

    @Test
    public void testSetPriority() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER0);
        insertSetting(OVERLAY_C_USER0);

        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_B_USER0, OVERLAY_C_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));

        assertTrue(mSettings.setPriority(OVERLAY_B, OVERLAY_C, USER_0));
        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_C_USER0, OVERLAY_B_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));

        // Nothing happens if the parent package cannot be found
        assertFalse(mSettings.setPriority(OVERLAY_B, new OverlayIdentifier("does.not.exist"),
                USER_0));
        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_C_USER0, OVERLAY_B_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));

        // An overlay should not affect the priority of overlays targeting a different package
        final OverlayInfo otherTarget = new OverlayInfo(
                "com.test.overlay_other",
                null,
                "com.test.some.other.target",
                null,
                "some-category",
                "/data/app/com.test.overlay_other-1/base.apk",
                STATE_DISABLED,
                0,
                0,
                true,
                false);
        insertSetting(otherTarget);
        assertFalse(mSettings.setPriority(OVERLAY_A, otherTarget.getOverlayIdentifier(), USER_0));
    }

    @Test
    public void testSetLowestPriority() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER0);
        insertSetting(OVERLAY_C_USER0);
        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_B_USER0, OVERLAY_C_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));

        assertTrue(mSettings.setLowestPriority(OVERLAY_B, USER_0));
        assertListsAreEqual(List.of(OVERLAY_B_USER0, OVERLAY_A_USER0, OVERLAY_C_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));
    }

    @Test
    public void testSetHighestPriority() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER0);
        insertSetting(OVERLAY_C_USER0);
        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_B_USER0, OVERLAY_C_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));

        assertTrue(mSettings.setHighestPriority(OVERLAY_B, USER_0));
        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_C_USER0, OVERLAY_B_USER0),
                mSettings.getOverlaysForTarget(OVERLAY_A_USER0.targetPackageName, USER_0));
    }

    @Test
    public void testSetConstraints() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER0);
        assertListsAreEqual(List.of(OVERLAY_A_USER0, OVERLAY_B_USER0),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));

        assertTrue(mSettings.setConstraints(OVERLAY_A, USER_0,
                List.of(CONSTRAINT_0, CONSTRAINT_1)));
        assertTrue(mSettings.setConstraints(OVERLAY_B, USER_0, List.of(CONSTRAINT_1)));

        assertListsAreEqual(
                List.of(OVERLAY_A_USER0_WITH_CONSTRAINTS, OVERLAY_B_USER0_WITH_CONSTRAINTS),
                mSettings.getOverlaysForTarget(TARGET_PACKAGE, USER_0));
    }

    // tests: persist and restore

    @Test
    public void testPersistEmpty() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        ByteArrayInputStream xml = new ByteArrayInputStream(os.toByteArray());

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(0, countXmlTags(xml, "item"));
    }

    @Test
    public void testPersistDifferentOverlaysSameUser() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER0);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        ByteArrayInputStream xml = new ByteArrayInputStream(os.toByteArray());

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(2, countXmlTags(xml, "item"));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "packageName",
                OVERLAY_A.getPackageName()));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "packageName",
                OVERLAY_B.getPackageName()));
        assertEquals(2, countXmlAttributesWhere(xml, "item", "userId",
                    Integer.toString(USER_0)));
    }

    @Test
    public void testPersistSameOverlayDifferentUsers() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_A_USER1);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        ByteArrayInputStream xml = new ByteArrayInputStream(os.toByteArray());

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(2, countXmlTags(xml, "item"));
        assertEquals(2, countXmlAttributesWhere(xml, "item", "packageName",
                OVERLAY_A.getPackageName()));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "userId",
                    Integer.toString(USER_0)));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "userId",
                    Integer.toString(USER_1)));
    }

    @Test
    public void testPersistEnabled() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        mSettings.setEnabled(OVERLAY_A, USER_0, true);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        ByteArrayInputStream xml = new ByteArrayInputStream(os.toByteArray());

        assertEquals(1, countXmlAttributesWhere(xml, "item", "isEnabled", "true"));
    }

    @Test
    public void testPersistWithConstraints() throws Exception {
        insertSetting(OVERLAY_A_USER0_WITH_CONSTRAINTS);
        insertSetting(OVERLAY_B_USER1_WITH_CONSTRAINTS);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        ByteArrayInputStream xml = new ByteArrayInputStream(os.toByteArray());

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(2, countXmlTags(xml, "item"));
        assertEquals(3, countXmlTags(xml, "constraint"));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "packageName",
                OVERLAY_A.getPackageName()));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "packageName",
                OVERLAY_B.getPackageName()));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "userId",
                Integer.toString(USER_0)));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "userId",
                Integer.toString(USER_1)));
        assertEquals(1, countXmlAttributesWhere(xml, "constraint", "type",
                TYPE_DISPLAY_ID));
        assertEquals(2, countXmlAttributesWhere(xml, "constraint", "type",
                TYPE_DEVICE_ID));
        assertEquals(1, countXmlAttributesWhere(xml, "constraint", "value",
                DISPLAY_ID));
        assertEquals(2, countXmlAttributesWhere(xml, "constraint", "value",
                DEVICE_ID));
    }

    @Test
    public void testRestoreEmpty() throws Exception {
        final int version = OverlayManagerSettings.Serializer.CURRENT_VERSION;
        final String xml =
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                + "<overlays version=\"" + version + "\" />\n";
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes(UTF_8));

        mSettings.restore(is);
        assertDoesNotContain(mSettings, new OverlayIdentifier("com.test.overlay"), 0);
    }

    @Test
    public void testRestoreSingleUserSingleOverlay() throws Exception {
        final int version = OverlayManagerSettings.Serializer.CURRENT_VERSION;
        final String xml =
                "<?xml version='1.0' encoding='utf-8' standalone='yes'?>\n"
                + "<overlays version='" + version + "'>\n"
                + "<item packageName='com.test.overlay'\n"
                + "      overlayName='test'\n"
                + "      userId='1234'\n"
                + "      targetPackageName='com.test.target'\n"
                + "      baseCodePath='/data/app/com.test.overlay-1/base.apk'\n"
                + "      state='" + STATE_DISABLED + "'\n"
                + "      isEnabled='false'\n"
                + "      category='test-category'\n"
                + "      isStatic='false'\n"
                + "      priority='0' />\n"
                + "</overlays>\n";
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes(UTF_8));

        mSettings.restore(is);
        final OverlayIdentifier identifier = new OverlayIdentifier("com.test.overlay", "test");
        OverlayInfo oi = mSettings.getOverlayInfo(identifier, 1234);
        assertNotNull(oi);
        assertEquals("com.test.overlay", oi.packageName);
        assertEquals("test", oi.overlayName);
        assertEquals("com.test.target", oi.targetPackageName);
        assertEquals("/data/app/com.test.overlay-1/base.apk", oi.baseCodePath);
        assertEquals(1234, oi.userId);
        assertEquals(STATE_DISABLED, oi.state);
        assertFalse(mSettings.getEnabled(identifier, 1234));
        assertTrue(oi.constraints.isEmpty());
    }

    @Test
    public void testRestoreSingleUserSingleOverlayWithConstraints() throws Exception {
        final int version = OverlayManagerSettings.Serializer.CURRENT_VERSION;
        final String xml =
                "<?xml version='1.0' encoding='utf-8' standalone='yes'?>\n"
                        + "<overlays version='" + version + "'>\n"
                        + "<item packageName='com.test.overlay'\n"
                        + "      overlayName='test'\n"
                        + "      userId='1234'\n"
                        + "      targetPackageName='com.test.target'\n"
                        + "      baseCodePath='/data/app/com.test.overlay-1/base.apk'\n"
                        + "      state='" + STATE_DISABLED + "'\n"
                        + "      isEnabled='false'\n"
                        + "      category='test-category'\n"
                        + "      isStatic='false'\n"
                        + "      priority='0' >\n"
                        + "<constraint type='" + TYPE_DISPLAY_ID + "'\n"
                        + "      value = '" + DISPLAY_ID + "' />\n"
                        + "<constraint type='" + TYPE_DEVICE_ID + "'\n"
                        + "      value = '" + DEVICE_ID + "' />\n"
                        + "</item>\n"
                        + "</overlays>\n";
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes(UTF_8));

        mSettings.restore(is);
        final OverlayIdentifier identifier = new OverlayIdentifier("com.test.overlay", "test");
        OverlayInfo oi = mSettings.getOverlayInfo(identifier, 1234);
        assertNotNull(oi);
        assertEquals("com.test.overlay", oi.packageName);
        assertEquals("test", oi.overlayName);
        assertEquals("com.test.target", oi.targetPackageName);
        assertEquals("/data/app/com.test.overlay-1/base.apk", oi.baseCodePath);
        assertEquals(1234, oi.userId);
        assertEquals(STATE_DISABLED, oi.state);
        assertFalse(mSettings.getEnabled(identifier, 1234));
        assertListsAreEqual(List.of(CONSTRAINT_0, CONSTRAINT_1), oi.constraints);
    }

    @Test
    @Parameters(method = "getPreviousVersions")
    public void testRestoreWithPreviousVersion(int version) throws Exception {
        final String xml =
                "<?xml version='1.0' encoding='utf-8' standalone='yes'?>\n"
                        + "<overlays version='" + version + "'>\n"
                        + "<item packageName='com.test.overlay'\n"
                        + "      overlayName='test'\n"
                        + "      userId='1234'\n"
                        + "      targetPackageName='com.test.target'\n"
                        + "      baseCodePath='/data/app/com.test.overlay-1/base.apk'\n"
                        + "      state='" + STATE_DISABLED + "'\n"
                        + "      isEnabled='false'\n"
                        + "      category='test-category'\n"
                        + "      isStatic='false'\n"
                        + "      priority='0' />\n"
                        + "</overlays>\n";
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes(UTF_8));

        mSettings.restore(is);
        final OverlayIdentifier identifier = new OverlayIdentifier("com.test.overlay", "test");
        OverlayInfo oi = mSettings.getOverlayInfo(identifier, 1234);
        assertNotNull(oi);
        assertEquals("com.test.overlay", oi.packageName);
        assertEquals("test", oi.overlayName);
        assertEquals("com.test.target", oi.targetPackageName);
        assertEquals("/data/app/com.test.overlay-1/base.apk", oi.baseCodePath);
        assertEquals(1234, oi.userId);
        assertEquals(STATE_DISABLED, oi.state);
        assertFalse(mSettings.getEnabled(identifier, 1234));
        assertTrue(oi.constraints.isEmpty());
    }

    @Test
    public void testPersistAndRestore() throws Exception {
        insertSetting(OVERLAY_A_USER0);
        insertSetting(OVERLAY_B_USER1);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        OverlayManagerSettings newSettings = new OverlayManagerSettings();
        newSettings.restore(is);

        OverlayInfo a = newSettings.getOverlayInfo(OVERLAY_A, USER_0);
        assertEquals(OVERLAY_A_USER0, a);

        OverlayInfo b = newSettings.getOverlayInfo(OVERLAY_B, USER_1);
        assertEquals(OVERLAY_B_USER1, b);
    }

    @Test
    public void testPersistAndRestoreWithConstraints() throws Exception {
        insertSetting(OVERLAY_A_USER0_WITH_CONSTRAINTS);
        insertSetting(OVERLAY_B_USER1_WITH_CONSTRAINTS);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        OverlayManagerSettings newSettings = new OverlayManagerSettings();
        newSettings.restore(is);

        OverlayInfo a = newSettings.getOverlayInfo(OVERLAY_A, USER_0);
        assertEquals(OVERLAY_A_USER0_WITH_CONSTRAINTS, a);

        OverlayInfo b = newSettings.getOverlayInfo(OVERLAY_B, USER_1);
        assertEquals(OVERLAY_B_USER1_WITH_CONSTRAINTS, b);
    }

    private int countXmlTags(InputStream in, String tagToLookFor) throws Exception {
        in.reset();
        int count = 0;
        TypedXmlPullParser parser = Xml.resolvePullParser(in);
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && tagToLookFor.equals(parser.getName())) {
                count++;
            }
            event = parser.next();
        }
        return count;
    }

    private int countXmlAttributesWhere(InputStream in, String tag, String attr, String value)
            throws Exception {
        in.reset();
        int count = 0;
        TypedXmlPullParser parser = Xml.resolvePullParser(in);
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && tag.equals(parser.getName())) {
                String v = parser.getAttributeValue(null, attr);
                if (value.equals(v)) {
                    count++;
                }
            }
            event = parser.next();
        }
        return count;
    }

    private int countXmlAttributesWhere(InputStream in, String tag, String attr, int value)
            throws Exception {
        in.reset();
        int count = 0;
        TypedXmlPullParser parser = Xml.resolvePullParser(in);
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && tag.equals(parser.getName())) {
                int v = parser.getAttributeInt(null, attr);
                if (value == v) {
                    count++;
                }
            }
            event = parser.next();
        }
        return count;
    }

    private void insertSetting(OverlayInfo oi) throws Exception {
        mSettings.init(oi.getOverlayIdentifier(), oi.userId, oi.targetPackageName, null,
                oi.baseCodePath, true, false,0, oi.category, oi.isFabricated);
        mSettings.setState(oi.getOverlayIdentifier(), oi.userId, oi.state);
        mSettings.setEnabled(oi.getOverlayIdentifier(), oi.userId, false);
        mSettings.setConstraints(oi.getOverlayIdentifier(), oi.userId, oi.constraints);
    }

    private static void assertContains(final OverlayManagerSettings settings,
            final OverlayInfo oi) {
        try {
            settings.getOverlayInfo(oi.getOverlayIdentifier(), oi.userId);
        } catch (OverlayManagerSettings.BadKeyException e) {
            fail(String.format("settings does not contain overlay=%s userId=%d",
                    oi.getOverlayIdentifier(), oi.userId));
        }
    }

    private static void assertDoesNotContain(final OverlayManagerSettings settings,
            final OverlayInfo oi) {
        assertDoesNotContain(settings, oi.getOverlayIdentifier(), oi.userId);
    }

    private static void assertDoesNotContain(final OverlayManagerSettings settings,
            final OverlayIdentifier overlay, int userId) {
        try {
            settings.getOverlayInfo(overlay, userId);
            fail(String.format("settings contains overlay=%s userId=%d", overlay, userId));
        } catch (OverlayManagerSettings.BadKeyException e) {
            // do nothing: we expect to end up here
        }
    }

    private static OverlayInfo createInfo(@NonNull OverlayIdentifier identifier, int userId) {
        return createInfo(identifier, userId, Collections.emptyList());
    }

    private static OverlayInfo createInfo(@NonNull OverlayIdentifier identifier, int userId,
            @NonNull List<OverlayConstraint> constraints) {
        return new OverlayInfo(
                identifier.getPackageName(),
                identifier.getOverlayName(),
                "com.test.target",
                null /* targetOverlayableName */,
                "some-category" /* category */,
                "/data/app/" + identifier + "/base.apk" /* baseCodePath */,
                STATE_DISABLED,
                userId,
                0 /* priority */,
                true /* isMutable */,
                false /* isFabricated */,
                constraints);
    }

    private static <T> void assertListsAreEqual(
            @NonNull List<T> expected, @Nullable List<T> actual) {
        if (!expected.equals(actual)) {
            fail(String.format("lists [%s] and [%s] differ",
                        TextUtils.join(",", expected), TextUtils.join(",", actual)));
        }
    }

    private static Integer[] getPreviousVersions() {
        return new Integer[]{
                3,
                4,
        };
    }
}
