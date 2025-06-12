/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.clipboardoverlay.dagger;

import static android.view.WindowManager.LayoutParams.TYPE_SCREENSHOT;

import static com.android.systemui.Flags.clipboardOverlayMultiuser;
import static com.android.systemui.shared.Flags.usePreferredImageEditor;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.WindowManager;

import com.android.systemui.clipboardoverlay.ActionIntentCreator;
import com.android.systemui.clipboardoverlay.ClipboardOverlayView;
import com.android.systemui.clipboardoverlay.DefaultIntentCreator;
import com.android.systemui.clipboardoverlay.IntentCreator;
import com.android.systemui.res.R;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.utils.windowmanager.WindowManagerProvider;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import javax.inject.Qualifier;

/** Module for {@link com.android.systemui.clipboardoverlay}. */
@Module
public interface ClipboardOverlayModule {

    /**
     *
     */
    @Provides
    @OverlayWindowContext
    static Context provideWindowContext(DisplayManager displayManager,
            DisplayTracker displayTracker, Context context, UserTracker userTracker) {
        Display display = displayManager.getDisplay(displayTracker.getDefaultDisplayId());
        if (clipboardOverlayMultiuser()) {
            return userTracker.getUserContext().createWindowContext(display, TYPE_SCREENSHOT, null);
        } else {
            return context.createWindowContext(display, TYPE_SCREENSHOT, null);
        }
    }

    /**
     *
     */
    @Provides
    static ClipboardOverlayView provideClipboardOverlayView(
            @OverlayWindowContext Context overlayContext, Context context) {
        if (clipboardOverlayMultiuser()) {
            return (ClipboardOverlayView) LayoutInflater.from(context).inflate(
                    R.layout.clipboard_overlay, null);
        } else {
            return (ClipboardOverlayView) LayoutInflater.from(overlayContext).inflate(
                    R.layout.clipboard_overlay, null);
        }
    }

    /**
     *
     */
    @Provides
    @OverlayWindowContext
    static WindowManager provideWindowManager(@OverlayWindowContext Context context,
            WindowManagerProvider windowManagerProvider) {
        return windowManagerProvider.getWindowManager(context);
    }

    @Provides
    static IntentCreator provideIntentCreator(
            Lazy<DefaultIntentCreator> defaultIntentCreator,
            Lazy<ActionIntentCreator> actionIntentCreator) {
        if (usePreferredImageEditor()) {
            return actionIntentCreator.get();
        } else {
            return defaultIntentCreator.get();
        }
    }

    @Qualifier
    @Documented
    @Retention(RUNTIME)
    @interface OverlayWindowContext {
    }
}
