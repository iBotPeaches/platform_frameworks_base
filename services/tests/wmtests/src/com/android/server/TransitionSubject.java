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

package com.android.server.wm;

import android.annotation.Nullable;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.util.ArrayList;
import java.util.List;

public class TransitionSubject extends Subject {

    @Nullable
    private final Transition actual;

    /**
     * Internal constructor.
     *
     * @see TransitionSubject#assertThat(Transition)
     */
    private TransitionSubject(FailureMetadata metadata, @Nullable Transition actual) {
        super(metadata, actual);
        this.actual = actual;
    }

    /**
     * In a fluent assertion chain, the argument to the "custom" overload of {@link
     * StandardSubjectBuilder#about(CustomSubjectBuilder.Factory) about}, the method that specifies
     * what kind of {@link Subject} to create.
     */
    public static Factory<TransitionSubject, Transition> transitions() {
        return TransitionSubject::new;
    }

    /**
     * Typical entry point for making assertions about Transitions.
     *
     * @see @Truth#assertThat(Object)
     */
    public static TransitionSubject assertThat(Transition transition) {
        return Truth.assertAbout(transitions()).that(transition);
    }

    /**
     * Converts to a {@link IterableSubject} containing {@link Transition#getFlags()} separated into
     * a list of individual flags for assertions such as {@code flags().contains(TRANSIT_FLAG_XYZ)}.
     *
     * <p>If the subject is null, this will fail instead of returning a null subject.
     */
    public IterableSubject flags() {
        isNotNull();

        final List<Integer> sortedFlags = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            if ((actual.getFlags() & (1 << i)) != 0) {
                sortedFlags.add((1 << i));
            }
        }
        return com.google.common.truth.Truth.assertThat(sortedFlags);
    }
}
