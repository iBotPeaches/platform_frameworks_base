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

package com.android.internal.widget;

import static com.android.internal.widget.NotificationProgressBar.NotEnoughWidthToFitAllPartsException;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification.ProgressStyle;
import android.graphics.Color;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.widget.NotificationProgressBar.Part;
import com.android.internal.widget.NotificationProgressBar.Point;
import com.android.internal.widget.NotificationProgressBar.Segment;
import com.android.internal.widget.NotificationProgressDrawable.DrawablePart;
import com.android.internal.widget.NotificationProgressDrawable.DrawablePoint;
import com.android.internal.widget.NotificationProgressDrawable.DrawableSegment;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class NotificationProgressBarTest {

    @Test(expected = IllegalArgumentException.class)
    public void processAndConvertToParts_segmentsIsEmpty() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 50;
        int progressMax = 100;

        NotificationProgressBar.processModelAndConvertToViewParts(segments, points, progress,
                progressMax);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processAndConvertToParts_segmentsLengthNotMatchingProgressMax() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(50));
        segments.add(new ProgressStyle.Segment(100));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 50;
        int progressMax = 100;

        NotificationProgressBar.processModelAndConvertToViewParts(segments, points, progress,
                progressMax);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processAndConvertToParts_segmentLengthIsNegative() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(-50));
        segments.add(new ProgressStyle.Segment(150));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 50;
        int progressMax = 100;

        NotificationProgressBar.processModelAndConvertToViewParts(segments, points, progress,
                progressMax);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processAndConvertToParts_segmentLengthIsZero() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(0));
        segments.add(new ProgressStyle.Segment(100));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 50;
        int progressMax = 100;

        NotificationProgressBar.processModelAndConvertToViewParts(segments, points, progress,
                progressMax);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processAndConvertToParts_progressIsNegative() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = -50;
        int progressMax = 100;

        NotificationProgressBar.processModelAndConvertToViewParts(segments, points, progress,
                progressMax);
    }

    @Test
    public void processAndConvertToParts_progressIsZero()
            throws NotificationProgressBar.NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.RED));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 0;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(List.of(new Segment(1f, Color.RED)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 310, Color.RED)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        // Colors with 50% opacity
        int fadedRed = 0x80FF0000;
        expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 310, fadedRed, true)));

        assertThat(p.second).isEqualTo(10);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test
    public void processAndConvertToParts_progressAtMax()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.RED));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 100;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(List.of(new Segment(1f, Color.RED)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 310, Color.RED)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        assertThat(p.second).isEqualTo(310);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processAndConvertToParts_progressAboveMax() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 150;
        int progressMax = 100;

        NotificationProgressBar.processModelAndConvertToViewParts(segments, points, progress,
                progressMax);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processAndConvertToParts_pointPositionIsNegative() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(-50).setColor(Color.RED));
        int progress = 50;
        int progressMax = 100;

        NotificationProgressBar.processModelAndConvertToViewParts(segments, points, progress,
                progressMax);
    }

    @Test
    public void processAndConvertToParts_pointPositionIsZero() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.RED));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(0).setColor(Color.RED));
        int progress = 50;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        // Point at the start is dropped.
        List<Part> expectedParts = new ArrayList<>(List.of(new Segment(1f, Color.RED)));

        assertThat(parts).isEqualTo(expectedParts);
    }

    @Test
    public void processAndConvertToParts_pointPositionAtMax() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.RED));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(100).setColor(Color.RED));
        int progress = 50;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        // Point at the end is dropped.
        List<Part> expectedParts = new ArrayList<>(List.of(new Segment(1f, Color.RED)));

        assertThat(parts).isEqualTo(expectedParts);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processAndConvertToParts_pointPositionAboveMax() {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(150).setColor(Color.RED));
        int progress = 50;
        int progressMax = 100;

        NotificationProgressBar.processModelAndConvertToViewParts(segments, points, progress,
                progressMax);
    }

    @Test
    public void processAndConvertToParts_singleSegmentWithoutPoints()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.BLUE));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 60;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(1, Color.BLUE)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 310, Color.BLUE)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        // Colors with 50% opacity
        int fadedBlue = 0x800000FF;
        expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 190, Color.BLUE),
                        new DrawableSegment(190, 310, fadedBlue, true)));

        assertThat(p.second).isEqualTo(190);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test
    public void processAndConvertToParts_multipleSegmentsWithoutPoints()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(50).setColor(Color.RED));
        segments.add(new ProgressStyle.Segment(50).setColor(Color.GREEN));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 60;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.50f, Color.RED), new Segment(0.50f, Color.GREEN)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 156, Color.RED),
                        new DrawableSegment(160, 310, Color.GREEN)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;
        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        // Colors with 50% opacity
        int fadedGreen = 0x8000FF00;
        expectedDrawableParts = new ArrayList<>(List.of(new DrawableSegment(10, 156, Color.RED),
                new DrawableSegment(160, 190, Color.GREEN),
                new DrawableSegment(190, 310, fadedGreen, true)));

        assertThat(p.second).isEqualTo(190);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test
    public void processAndConvertToParts_multipleSegmentsWithoutPoints_noTracker()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(50).setColor(Color.RED));
        segments.add(new ProgressStyle.Segment(50).setColor(Color.GREEN));
        List<ProgressStyle.Point> points = new ArrayList<>();
        int progress = 60;
        int progressMax = 100;
        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.50f, Color.RED), new Segment(0.50f, Color.GREEN)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 300;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = false;
        int trackerDrawWidth = 0;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(0, 146, Color.RED),
                        new DrawableSegment(150, 300, Color.GREEN)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;
        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        // Colors with 50% opacity
        int fadedGreen = 0x8000FF00;
        expectedDrawableParts = new ArrayList<>(List.of(new DrawableSegment(0, 146, Color.RED),
                new DrawableSegment(150, 176, Color.GREEN),
                new DrawableSegment(180, 300, fadedGreen, true)));

        assertThat(p.second).isEqualTo(180);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test
    public void processAndConvertToParts_singleSegmentWithPoints()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.BLUE));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(15).setColor(Color.RED));
        points.add(new ProgressStyle.Point(25).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(60).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(75).setColor(Color.YELLOW));
        int progress = 60;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.15f, Color.BLUE),
                        new Point(Color.RED),
                        new Segment(0.10f, Color.BLUE),
                        new Point(Color.BLUE),
                        new Segment(0.35f, Color.BLUE),
                        new Point(Color.BLUE),
                        new Segment(0.15f, Color.BLUE),
                        new Point(Color.YELLOW),
                        new Segment(0.25f, Color.BLUE)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 45, Color.BLUE),
                        new DrawablePoint(49, 61, Color.RED),
                        new DrawableSegment(65, 75, Color.BLUE),
                        new DrawablePoint(79, 91, Color.BLUE),
                        new DrawableSegment(95, 180, Color.BLUE),
                        new DrawablePoint(184, 196, Color.BLUE),
                        new DrawableSegment(200, 225, Color.BLUE),
                        new DrawablePoint(229, 241, Color.YELLOW),
                        new DrawableSegment(245, 310, Color.BLUE)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        // Colors with 50% opacity
        int fadedBlue = 0x800000FF;
        int fadedYellow = 0x80FFFF00;
        expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 44.219177F, Color.BLUE),
                        new DrawablePoint(48.219177F, 60.219177F, Color.RED),
                        new DrawableSegment(64.219177F, 80.21918F, Color.BLUE),
                        new DrawablePoint(84.21918F, 96.21918F, Color.BLUE),
                        new DrawableSegment(100.21918F, 182.38356F, Color.BLUE),
                        new DrawablePoint(186.38356F, 198.38356F, Color.BLUE),
                        new DrawableSegment(202.38356F, 227.0137F, fadedBlue, true),
                        new DrawablePoint(231.0137F, 243.0137F, fadedYellow),
                        new DrawableSegment(247.0137F, 310F, fadedBlue, true)));

        assertThat(p.second).isEqualTo(192.38356F);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test
    public void processAndConvertToParts_multipleSegmentsWithPoints()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(50).setColor(Color.RED));
        segments.add(new ProgressStyle.Segment(50).setColor(Color.GREEN));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(15).setColor(Color.RED));
        points.add(new ProgressStyle.Point(25).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(60).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(75).setColor(Color.YELLOW));
        int progress = 60;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.15f, Color.RED),
                        new Point(Color.RED),
                        new Segment(0.10f, Color.RED),
                        new Point(Color.BLUE),
                        new Segment(0.25f, Color.RED),
                        new Segment(0.10f, Color.GREEN),
                        new Point(Color.BLUE),
                        new Segment(0.15f, Color.GREEN),
                        new Point(Color.YELLOW),
                        new Segment(0.25f, Color.GREEN)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 45, Color.RED),
                        new DrawablePoint(49, 61, Color.RED),
                        new DrawableSegment(65, 75, Color.RED),
                        new DrawablePoint(79, 91, Color.BLUE),
                        new DrawableSegment(95, 156, Color.RED),
                        new DrawableSegment(160, 180, Color.GREEN),
                        new DrawablePoint(184, 196, Color.BLUE),
                        new DrawableSegment(200, 225, Color.GREEN),
                        new DrawablePoint(229, 241, Color.YELLOW),
                        new DrawableSegment(245, 310, Color.GREEN)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        // Colors with 50% opacity
        int fadedGreen = 0x8000FF00;
        int fadedYellow = 0x80FFFF00;
        expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 44.095238F, Color.RED),
                        new DrawablePoint(48.095238F, 60.095238F, Color.RED),
                        new DrawableSegment(64.095238F, 80.09524F, Color.RED),
                        new DrawablePoint(84.09524F, 96.09524F, Color.BLUE),
                        new DrawableSegment(100.09524F, 158.9524F, Color.RED),
                        new DrawableSegment(162.95238F, 182.7619F, Color.GREEN),
                        new DrawablePoint(186.7619F, 198.7619F, Color.BLUE),
                        new DrawableSegment(202.7619F, 227.33333F, fadedGreen, true),
                        new DrawablePoint(231.33333F, 243.33333F, fadedYellow),
                        new DrawableSegment(247.33333F, 309.99997F, fadedGreen, true)));

        assertThat(p.second).isEqualTo(192.7619F);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    // The points are so close to start/end that they would go out of bounds without the minimum
    // segment width requirement.
    @Test
    public void processAndConvertToParts_multipleSegmentsWithPointsNearStartAndEnd()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(50).setColor(Color.RED));
        segments.add(new ProgressStyle.Segment(50).setColor(Color.GREEN));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(1).setColor(Color.RED));
        points.add(new ProgressStyle.Point(25).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(60).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(99).setColor(Color.YELLOW));
        int progress = 60;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.01f, Color.RED),
                        new Point(Color.RED),
                        new Segment(0.24f, Color.RED),
                        new Point(Color.BLUE),
                        new Segment(0.25f, Color.RED),
                        new Segment(0.10f, Color.GREEN),
                        new Point(Color.BLUE),
                        new Segment(0.39f, Color.GREEN),
                        new Point(Color.YELLOW),
                        new Segment(0.01f, Color.GREEN)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 3, Color.RED),
                        new DrawablePoint(7, 19, Color.RED),
                        new DrawableSegment(23, 75, Color.RED),
                        new DrawablePoint(79, 91, Color.BLUE),
                        new DrawableSegment(95, 156, Color.RED),
                        new DrawableSegment(160, 180, Color.GREEN),
                        new DrawablePoint(184, 196, Color.BLUE),
                        new DrawableSegment(200, 297, Color.GREEN),
                        new DrawablePoint(301, 313, Color.YELLOW),
                        new DrawableSegment(317, 310, Color.GREEN)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        // Colors with 50% opacity
        int fadedGreen = 0x8000FF00;
        int fadedYellow = 0x80FFFF00;
        expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 26, Color.RED),
                        new DrawablePoint(30, 42, Color.RED),
                        new DrawableSegment(46, 88.02409F, Color.RED),
                        new DrawablePoint(92.02409F, 104.02409F, Color.BLUE),
                        new DrawableSegment(108.02409F, 156.55421F, Color.RED),
                        new DrawableSegment(160.55421F, 179.44579F, Color.GREEN),
                        new DrawablePoint(183.44579F, 195.44579F, Color.BLUE),
                        new DrawableSegment(199.44579F, 274, fadedGreen, true),
                        new DrawablePoint(278, 290, fadedYellow),
                        new DrawableSegment(294, 310, fadedGreen, true)));

        assertThat(p.second).isEqualTo(189.44579F);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test
    public void processAndConvertToParts_multipleSegmentsWithPoints_notStyledByProgress()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(50).setColor(Color.RED));
        segments.add(new ProgressStyle.Segment(50).setColor(Color.GREEN));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(15).setColor(Color.RED));
        points.add(new ProgressStyle.Point(25).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(75).setColor(Color.YELLOW));
        int progress = 60;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.15f, Color.RED),
                        new Point(Color.RED),
                        new Segment(0.10f, Color.RED),
                        new Point(Color.BLUE),
                        new Segment(0.25f, Color.RED),
                        new Segment(0.25f, Color.GREEN),
                        new Point(Color.YELLOW),
                        new Segment(0.25f, Color.GREEN)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 45, Color.RED),
                        new DrawablePoint(49, 61, Color.RED),
                        new DrawableSegment(65, 75, Color.RED),
                        new DrawablePoint(79, 91, Color.BLUE),
                        new DrawableSegment(95, 156, Color.RED),
                        new DrawableSegment(160, 225, Color.GREEN),
                        new DrawablePoint(229, 241, Color.YELLOW),
                        new DrawableSegment(245, 310, Color.GREEN)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = false;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 44.296295F, Color.RED),
                        new DrawablePoint(48.296295F, 60.296295F, Color.RED),
                        new DrawableSegment(64.296295F, 80.296295F, Color.RED),
                        new DrawablePoint(84.296295F, 96.296295F, Color.BLUE),
                        new DrawableSegment(100.296295F, 159.62962F, Color.RED),
                        new DrawableSegment(163.62962F, 226.8148F, Color.GREEN),
                        new DrawablePoint(230.81482F, 242.81482F, Color.YELLOW),
                        new DrawableSegment(246.81482F, 310, Color.GREEN)));

        assertThat(p.second).isEqualTo(192.9037F);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    // The only difference from the `segmentWidthAtMin` test below is the longer
    // segmentMinWidth (= 16dp).
    @Test
    public void maybeStretchAndRescaleSegments_segmentWidthBelowMin()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(200).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(100).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(300).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(400).setColor(Color.BLUE));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(200).setColor(Color.BLUE));
        int progress = 1000;
        int progressMax = 1000;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.2f, Color.BLUE),
                        new Point(Color.BLUE),
                        new Segment(0.1f, Color.BLUE),
                        new Segment(0.3f, Color.BLUE),
                        new Segment(0.4f, Color.BLUE)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 220;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 40, Color.BLUE),
                        new DrawablePoint(44, 56, Color.BLUE),
                        new DrawableSegment(60, 66, Color.BLUE),
                        new DrawableSegment(70, 126, Color.BLUE),
                        new DrawableSegment(130, 210, Color.BLUE)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 38.81356F, Color.BLUE),
                        new DrawablePoint(42.81356F, 54.81356F, Color.BLUE),
                        new DrawableSegment(58.81356F, 74.81356F, Color.BLUE),
                        new DrawableSegment(78.81356F, 131.42374F, Color.BLUE),
                        new DrawableSegment(135.42374F, 210, Color.BLUE)));

        assertThat(p.second).isEqualTo(210);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    // The only difference from the `segmentWidthBelowMin` test above is the shorter
    // segmentMinWidth (= 10dp).
    @Test
    public void maybeStretchAndRescaleSegments_segmentWidthAtMin()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(200).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(100).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(300).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(400).setColor(Color.BLUE));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(200).setColor(Color.BLUE));
        int progress = 1000;
        int progressMax = 1000;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.2f, Color.BLUE),
                        new Point(Color.BLUE),
                        new Segment(0.1f, Color.BLUE),
                        new Segment(0.3f, Color.BLUE),
                        new Segment(0.4f, Color.BLUE)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 220;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 40, Color.BLUE),
                        new DrawablePoint(44, 56, Color.BLUE),
                        new DrawableSegment(60, 66, Color.BLUE),
                        new DrawableSegment(70, 126, Color.BLUE),
                        new DrawableSegment(130, 210, Color.BLUE)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 10;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 39.411766F, Color.BLUE),
                        new DrawablePoint(43.411766F, 55.411766F, Color.BLUE),
                        new DrawableSegment(59.411766F, 69.411766F, Color.BLUE),
                        new DrawableSegment(73.411766F, 128.05884F, Color.BLUE),
                        new DrawableSegment(132.05882F, 210, Color.BLUE)));

        assertThat(p.second).isEqualTo(210);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test
    public void maybeStretchAndRescaleSegments_noStretchingNecessary()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(200).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(300).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(400).setColor(Color.BLUE));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(100).setColor(Color.BLUE));
        int progress = 1000;
        int progressMax = 1000;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.1f, Color.BLUE),
                        new Point(Color.BLUE),
                        new Segment(0.2f, Color.BLUE),
                        new Segment(0.3f, Color.BLUE),
                        new Segment(0.4f, Color.BLUE)));

        assertThat(parts).isEqualTo(expectedParts);

        float drawableWidth = 220;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 20, Color.BLUE),
                        new DrawablePoint(24, 36, Color.BLUE),
                        new DrawableSegment(40, 66, Color.BLUE),
                        new DrawableSegment(70, 126, Color.BLUE),
                        new DrawableSegment(130, 210, Color.BLUE)));

        assertThat(drawableParts).isEqualTo(expectedDrawableParts);

        float segmentMinWidth = 10;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p = NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);

        assertThat(p.second).isEqualTo(210);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test(expected = NotEnoughWidthToFitAllPartsException.class)
    public void maybeStretchAndRescaleSegments_notEnoughWidthToFitAllParts()
            throws NotEnoughWidthToFitAllPartsException {
        final int orange = 0xff7f50;
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(10).setColor(orange));
        segments.add(new ProgressStyle.Segment(10).setColor(Color.YELLOW));
        segments.add(new ProgressStyle.Segment(10).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(10).setColor(Color.GREEN));
        segments.add(new ProgressStyle.Segment(10).setColor(Color.RED));
        segments.add(new ProgressStyle.Segment(10).setColor(orange));
        segments.add(new ProgressStyle.Segment(10).setColor(Color.YELLOW));
        segments.add(new ProgressStyle.Segment(10).setColor(Color.BLUE));
        segments.add(new ProgressStyle.Segment(10).setColor(Color.GREEN));
        segments.add(new ProgressStyle.Segment(10).setColor(Color.RED));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(1).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(10).setColor(orange));
        points.add(new ProgressStyle.Point(55).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(90).setColor(orange));
        int progress = 50;
        int progressMax = 100;

        List<Part> parts = NotificationProgressBar.processModelAndConvertToViewParts(segments,
                points, progress, progressMax);

        List<Part> expectedParts = new ArrayList<>(
                List.of(new Segment(0.01f, orange),
                        new Point(Color.BLUE),
                        new Segment(0.09f, orange),
                        new Point(orange),
                        new Segment(0.1f, Color.YELLOW),
                        new Segment(0.1f, Color.BLUE),
                        new Segment(0.1f, Color.GREEN),
                        new Segment(0.1f, Color.RED),
                        new Segment(0.05f, orange),
                        new Point(Color.BLUE),
                        new Segment(0.05f, orange),
                        new Segment(0.1f, Color.YELLOW),
                        new Segment(0.1f, Color.BLUE),
                        new Segment(0.1f, Color.GREEN),
                        new Point(orange),
                        new Segment(0.1f, Color.RED)));

        assertThat(parts).isEqualTo(expectedParts);

        // For the list of ProgressStyle.Part used in this test, 300 is the minimum width.
        float drawableWidth = 319;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        List<DrawablePart> drawableParts =
                NotificationProgressBar.processPartsAndConvertToDrawableParts(
                        parts, drawableWidth, segSegGap, segPointGap, pointRadius, hasTrackerIcon,
                        trackerDrawWidth);

        // Skips the validation of the intermediate list of DrawableParts.

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        NotificationProgressBar.maybeStretchAndRescaleSegments(
                parts, drawableParts, segmentMinWidth, pointRadius, (float) progress / progressMax,
                isStyledByProgress, hasTrackerIcon ? 0 : segSegGap);
    }

    @Test
    public void processModelAndConvertToFinalDrawableParts_singleSegmentWithPoints()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.BLUE));
        List<ProgressStyle.Point> points = new ArrayList<>();
        points.add(new ProgressStyle.Point(15).setColor(Color.RED));
        points.add(new ProgressStyle.Point(25).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(60).setColor(Color.BLUE));
        points.add(new ProgressStyle.Point(75).setColor(Color.YELLOW));
        int progress = 60;
        int progressMax = 100;

        float drawableWidth = 320;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p =
                NotificationProgressBar.processModelAndConvertToFinalDrawableParts(
                        segments,
                        points,
                        progress,
                        progressMax,
                        drawableWidth,
                        segSegGap,
                        segPointGap,
                        pointRadius,
                        hasTrackerIcon,
                        segmentMinWidth,
                        isStyledByProgress,
                        trackerDrawWidth);

        // Colors with 50% opacity
        int fadedBlue = 0x800000FF;
        int fadedYellow = 0x80FFFF00;
        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 44.219177F, Color.BLUE),
                        new DrawablePoint(48.219177F, 60.219177F, Color.RED),
                        new DrawableSegment(64.219177F, 80.21918F, Color.BLUE),
                        new DrawablePoint(84.21918F, 96.21918F, Color.BLUE),
                        new DrawableSegment(100.21918F, 182.38356F, Color.BLUE),
                        new DrawablePoint(186.38356F, 198.38356F, Color.BLUE),
                        new DrawableSegment(202.38356F, 227.0137F, fadedBlue, true),
                        new DrawablePoint(231.0137F, 243.0137F, fadedYellow),
                        new DrawableSegment(247.0137F, 310F, fadedBlue, true)));

        assertThat(p.second).isEqualTo(192.38356F);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }

    @Test
    public void processModelAndConvertToFinalDrawableParts_singleSegmentWithoutPoints()
            throws NotEnoughWidthToFitAllPartsException {
        List<ProgressStyle.Segment> segments = new ArrayList<>();
        segments.add(new ProgressStyle.Segment(100).setColor(Color.BLUE));
        int progress = 60;
        int progressMax = 100;

        float drawableWidth = 120;
        float segSegGap = 4;
        float segPointGap = 4;
        float pointRadius = 6;
        boolean hasTrackerIcon = true;
        int trackerDrawWidth = 20;

        float segmentMinWidth = 16;
        boolean isStyledByProgress = true;

        Pair<List<DrawablePart>, Float> p =
                NotificationProgressBar.processModelAndConvertToFinalDrawableParts(
                        segments,
                        Collections.emptyList(),
                        progress,
                        progressMax,
                        drawableWidth,
                        segSegGap,
                        segPointGap,
                        pointRadius,
                        hasTrackerIcon,
                        segmentMinWidth,
                        isStyledByProgress,
                        trackerDrawWidth);

        // Colors with 50% opacity
        int fadedBlue = 0x800000FF;
        List<DrawablePart> expectedDrawableParts = new ArrayList<>(
                List.of(new DrawableSegment(10, 70F, Color.BLUE),
                        new DrawableSegment(70F, 110, fadedBlue, true)));

        assertThat(p.second).isEqualTo(70);
        assertThat(p.first).isEqualTo(expectedDrawableParts);
    }
}
