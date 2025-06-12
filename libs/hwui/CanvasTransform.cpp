/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "CanvasTransform.h"

#include <SkAndroidFrameworkUtils.h>
#include <SkBlendMode.h>
#include <SkColorFilter.h>
#include <SkGradientShader.h>
#include <SkHighContrastFilter.h>
#include <SkPaint.h>
#include <SkShader.h>
#include <log/log.h>

#include <algorithm>
#include <cmath>

#include "Properties.h"
#include "utils/Color.h"

namespace android::uirenderer {

SkColor makeLight(SkColor color) {
    Lab lab = sRGBToLab(color);
    float invertedL = std::min(110 - lab.L, 100.0f);
    if (invertedL > lab.L) {
        lab.L = invertedL;
        return LabToSRGB(lab, SkColorGetA(color));
    } else {
        return color;
    }
}

SkColor makeDark(SkColor color) {
    Lab lab = sRGBToLab(color);
    float invertedL = std::min(110 - lab.L, 100.0f);
    if (invertedL < lab.L) {
        lab.L = invertedL;
        return LabToSRGB(lab, SkColorGetA(color));
    } else {
        return color;
    }
}

SkColor invert(SkColor color) {
    Lab lab = sRGBToLab(color);
    lab.L = 100 - lab.L;
    return LabToSRGB(lab, SkColorGetA(color));
}

SkColor transformColor(ColorTransform transform, SkColor color) {
    switch (transform) {
        case ColorTransform::Light:
            return makeLight(color);
        case ColorTransform::Dark:
            return makeDark(color);
        case ColorTransform::Invert:
            return invert(color);
        default:
            return color;
    }
}

SkColor transformColorInverse(ColorTransform transform, SkColor color) {
    switch (transform) {
        case ColorTransform::Dark:
            return makeLight(color);
        case ColorTransform::Light:
            return makeDark(color);
        default:
            return color;
    }
}

static void applyColorTransform(ColorTransform transform, SkPaint& paint) {
    if (transform == ColorTransform::None) return;

    SkColor newColor = transformColor(transform, paint.getColor());
    paint.setColor(newColor);

    if (paint.getShader()) {
        SkAndroidFrameworkUtils::LinearGradientInfo info;
        std::array<SkColor, 10> _colorStorage;
        std::array<SkScalar, _colorStorage.size()> _offsetStorage;
        info.fColorCount = _colorStorage.size();
        info.fColors = _colorStorage.data();
        info.fColorOffsets = _offsetStorage.data();

        if (SkAndroidFrameworkUtils::ShaderAsALinearGradient(paint.getShader(), &info) &&
            info.fColorCount <= _colorStorage.size()) {
            for (int i = 0; i < info.fColorCount; i++) {
                info.fColors[i] = transformColor(transform, info.fColors[i]);
            }
            paint.setShader(SkGradientShader::MakeLinear(
                    info.fPoints, info.fColors, info.fColorOffsets, info.fColorCount,
                    info.fTileMode, info.fGradientFlags, nullptr));
        } else {
            if (transform == ColorTransform::Invert) {
                // Since we're trying to invert every thing around this draw call, we invert
                // the color of the draw call if we don't know what it is.
                auto filter = SkHighContrastFilter::Make(
                        {/* grayscale= */ false,
                         SkHighContrastConfig::InvertStyle::kInvertLightness,
                         /* contrast= */ 0.0f});

                if (paint.getColorFilter()) {
                    paint.setColorFilter(SkColorFilters::Compose(filter, paint.refColorFilter()));
                } else {
                    paint.setColorFilter(filter);
                }
                return;
            }
        }
    }

    if (paint.getColorFilter()) {
        SkBlendMode mode;
        SkColor color;
        // TODO: LRU this or something to avoid spamming new color mode filters
        if (paint.getColorFilter()->asAColorMode(&color, &mode)) {
            color = transformColor(transform, color);
            paint.setColorFilter(SkColorFilters::Blend(color, mode));
        }
    }
}

static BitmapPalette paletteForColorHSV(SkColor color) {
    float hsv[3];
    SkColorToHSV(color, hsv);
    return hsv[2] >= .5f ? BitmapPalette::Light : BitmapPalette::Dark;
}

static BitmapPalette filterPalette(const SkPaint* paint, BitmapPalette palette) {
    if (palette == BitmapPalette::Unknown || !paint || !paint->getColorFilter()) {
        return palette;
    }

    SkColor4f color = palette == BitmapPalette::Light ? SkColors::kWhite : SkColors::kBlack;
    sk_sp<SkColorSpace> srgb = SkColorSpace::MakeSRGB();
    color = paint->getColorFilter()->filterColor4f(color, srgb.get(), srgb.get());
    return paletteForColorHSV(color.toSkColor());
}

bool transformPaint(ColorTransform transform, SkPaint* paint) {
    // TODO
    applyColorTransform(transform, *paint);
    return true;
}

bool transformPaint(ColorTransform transform, SkPaint* paint, BitmapPalette palette) {
    bool shouldInvert = false;
    if (transform == ColorTransform::Invert && palette != BitmapPalette::Colorful) {
        // When the transform is Invert we invert any image that is not deemed "colorful",
        // regardless of calculated image brightness.
        shouldInvert = true;
    }
    palette = filterPalette(paint, palette);
    if (palette == BitmapPalette::Light && transform == ColorTransform::Dark) {
        shouldInvert = true;
    }
    if (palette == BitmapPalette::Dark && transform == ColorTransform::Light) {
        shouldInvert = true;
    }
    if (shouldInvert) {
        SkHighContrastConfig config;
        config.fInvertStyle = SkHighContrastConfig::InvertStyle::kInvertLightness;
        paint->setColorFilter(SkHighContrastFilter::Make(config)->makeComposed(paint->refColorFilter()));
    }
    return shouldInvert;
}

}  // namespace android::uirenderer
