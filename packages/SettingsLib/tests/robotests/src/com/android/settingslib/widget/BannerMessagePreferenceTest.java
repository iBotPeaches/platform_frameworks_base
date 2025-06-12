/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.settingslib.widget;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.robolectric.Robolectric.setupActivity;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.widget.preference.banner.R;

import com.google.android.material.button.MaterialButton;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDrawable;
import org.robolectric.shadows.ShadowTouchDelegate;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {BannerMessagePreferenceTest.ShadowSettingsThemeHelper.class})
public class BannerMessagePreferenceTest {

    private Context mContext;
    private View mRootView;
    private BannerMessagePreference mBannerPreference;
    private PreferenceViewHolder mHolder;

    private boolean mClickListenerCalled = false;
    private final View.OnClickListener mClickListener = v -> mClickListenerCalled = true;
    private final int mMinimumTargetSize =
            RuntimeEnvironment.application
                    .getResources()
                    .getDimensionPixelSize(
                            com.android.settingslib.widget.theme.R.dimen
                                    .settingslib_preferred_minimum_touch_target);

    private static final int TEST_STRING_RES_ID = R.string.accessibility_banner_message_dismiss;

    @Mock private View mMockBackgroundView;
    @Mock private Drawable mMockCardBackground;
    @Mock private MaterialButton mMockPositiveBtn;
    @Mock private MaterialButton mMockNegativeBtn;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowSettingsThemeHelper.setExpressiveTheme(false);
        mContext = RuntimeEnvironment.application;
        mClickListenerCalled = false;
        mBannerPreference = new BannerMessagePreference(mContext);
        setUpViewHolder();
    }

    @Test
    public void onBindViewHolder_whenTitleSet_shouldSetTitle() {
        mBannerPreference.setTitle("test");

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((TextView) mRootView.findViewById(R.id.banner_title)).getText())
                .isEqualTo("test");
    }

    @Ignore("b/359066481")
    @Test
    public void onBindViewHolder_andOnLayoutView_dismissButtonTouchDelegate_isCorrectSize() {
        assumeAndroidS();
        mBannerPreference.setTitle("Title");
        mBannerPreference.setDismissButtonOnClickListener(mClickListener);

        mBannerPreference.onBindViewHolder(mHolder);
        setupActivity(Activity.class).setContentView(mRootView);

        assertThat(mRootView.getTouchDelegate()).isNotNull();
        ShadowTouchDelegate delegate = shadowOf(mRootView.getTouchDelegate());
        assertThat(delegate.getBounds().width()).isAtLeast(mMinimumTargetSize);
        assertThat(delegate.getBounds().height()).isAtLeast(mMinimumTargetSize);
        assertThat(delegate.getDelegateView())
                .isEqualTo(mRootView.findViewById(R.id.banner_dismiss_btn));
    }

    @Test
    public void onBindViewHolder_whenSummarySet_shouldSetSummary() {
        mBannerPreference.setSummary("test");

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((TextView) mRootView.findViewById(R.id.banner_summary)).getText())
                .isEqualTo("test");
    }

    @Test
    public void onBindViewHolder_whenPreS_shouldBindView() {
        assumeAndroidR();
        mBannerPreference.setSummary("test");

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((TextView) mRootView.findViewById(R.id.banner_summary)).getText())
                .isEqualTo("test");
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenSubtitleSetByString_shouldSetSubtitle() {
        assumeAndroidS();
        mBannerPreference.setSubtitle("test");

        mBannerPreference.onBindViewHolder(mHolder);

        TextView mSubtitleView = mRootView.findViewById(R.id.banner_subtitle);
        assertThat(mSubtitleView.getText()).isEqualTo("test");
        assertThat(mSubtitleView.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenSubtitleSetByResId_shouldSetSubtitle() {
        assumeAndroidS();
        mBannerPreference.setSubtitle(TEST_STRING_RES_ID);

        mBannerPreference.onBindViewHolder(mHolder);

        TextView mSubtitleView = mRootView.findViewById(R.id.banner_subtitle);
        assertThat(mSubtitleView.getText()).isEqualTo(mContext.getString(TEST_STRING_RES_ID));
        assertThat(mSubtitleView.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenSubtitleXmlAttribute_shouldSetSubtitle() {
        assumeAndroidS();
        AttributeSet mAttributeSet =
                Robolectric.buildAttributeSet().addAttribute(R.attr.subtitle, "Test").build();
        mBannerPreference = new BannerMessagePreference(mContext, mAttributeSet);

        mBannerPreference.onBindViewHolder(mHolder);

        TextView mSubtitleView = mRootView.findViewById(R.id.banner_subtitle);
        assertThat(mSubtitleView.getText()).isEqualTo("Test");
        assertThat(mSubtitleView.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_shouldNotShowSubtitleIfUnset() {
        assumeAndroidS();
        mBannerPreference.onBindViewHolder(mHolder);

        TextView mSubtitleView = mRootView.findViewById(R.id.banner_subtitle);
        assertThat(mSubtitleView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenIconSet_shouldSetIcon() {
        assumeAndroidS();
        mBannerPreference.setIcon(R.drawable.settingslib_ic_cross);

        mBannerPreference.onBindViewHolder(mHolder);

        ImageView mIcon = mRootView.findViewById(R.id.banner_icon);
        ShadowDrawable shadowDrawable = shadowOf(mIcon.getDrawable());
        assertThat(shadowDrawable.getCreatedFromResId()).isEqualTo(R.drawable.settingslib_ic_cross);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenNoIconSet_shouldSetIconToDefault() {
        assumeAndroidS();
        mBannerPreference.onBindViewHolder(mHolder);

        ImageView mIcon = mRootView.findViewById(R.id.banner_icon);
        ShadowDrawable shadowDrawable = shadowOf(mIcon.getDrawable());
        assertThat(shadowDrawable.getCreatedFromResId()).isEqualTo(R.drawable.ic_warning);
    }

    @Test
    public void setPositiveButtonText_shouldShowPositiveButton() {
        mBannerPreference.setPositiveButtonText(TEST_STRING_RES_ID);

        mBannerPreference.onBindViewHolder(mHolder);

        Button mPositiveButton = mRootView.findViewById(R.id.banner_positive_btn);
        assertThat(mPositiveButton.getVisibility()).isEqualTo(View.VISIBLE);

        assertThat(mPositiveButton.getText()).isEqualTo(mContext.getString(TEST_STRING_RES_ID));
    }

    @Test
    public void setNegativeButtonText_shouldShowNegativeButton() {
        mBannerPreference.setNegativeButtonText(TEST_STRING_RES_ID);

        mBannerPreference.onBindViewHolder(mHolder);

        Button mNegativeButton = mRootView.findViewById(R.id.banner_negative_btn);
        assertThat(mNegativeButton.getVisibility()).isEqualTo(View.VISIBLE);

        assertThat(mNegativeButton.getText()).isEqualTo(mContext.getString(TEST_STRING_RES_ID));
    }

    @Test
    public void withoutSetPositiveButtonText_shouldHidePositiveButton() {
        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(mRootView.findViewById(R.id.banner_positive_btn).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void withoutSetNegativeButtonText_shouldHideNegativeButton() {
        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(mRootView.findViewById(R.id.banner_negative_btn).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void setPositiveButtonVisible_withTrue_shouldShowPositiveButton() {
        mBannerPreference.setPositiveButtonText(TEST_STRING_RES_ID);

        mBannerPreference.setPositiveButtonVisible(true);
        mBannerPreference.onBindViewHolder(mHolder);

        assertThat((mRootView.findViewById(R.id.banner_positive_btn)).getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void setPositiveButtonVisible_withFalse_shouldHidePositiveButton() {
        mBannerPreference.setPositiveButtonText(TEST_STRING_RES_ID);

        mBannerPreference.setPositiveButtonVisible(false);
        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(mRootView.findViewById(R.id.banner_positive_btn).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void setNegativeButtonVisible_withTrue_shouldShowNegativeButton() {
        mBannerPreference.setNegativeButtonText(TEST_STRING_RES_ID);

        mBannerPreference.setNegativeButtonVisible(true);
        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(mRootView.findViewById(R.id.banner_negative_btn).getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void setNegativeButtonVisible_withFalse_shouldHideNegativeButton() {
        mBannerPreference.setNegativeButtonText(TEST_STRING_RES_ID);

        mBannerPreference.setNegativeButtonVisible(false);
        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(mRootView.findViewById(R.id.banner_negative_btn).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void setPositiveButtonOnClickListener_setsClickListener() {
        mBannerPreference.setPositiveButtonOnClickListener(mClickListener);

        mBannerPreference.onBindViewHolder(mHolder);
        mRootView.findViewById(R.id.banner_positive_btn).callOnClick();

        assertThat(mClickListenerCalled).isTrue();
    }

    @Test
    public void setNegativeButtonOnClickListener_setsClickListener() {
        mBannerPreference.setNegativeButtonOnClickListener(mClickListener);

        mBannerPreference.onBindViewHolder(mHolder);
        mRootView.findViewById(R.id.banner_negative_btn).callOnClick();

        assertThat(mClickListenerCalled).isTrue();
    }

    @Test
    public void setDismissButtonOnClickListener_whenAtLeastS_setsClickListener() {
        assumeAndroidS();
        mBannerPreference.setDismissButtonOnClickListener(mClickListener);

        mBannerPreference.onBindViewHolder(mHolder);
        mRootView.findViewById(R.id.banner_dismiss_btn).callOnClick();

        assertThat(mClickListenerCalled).isTrue();
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_withDismissOnClickListener_dismissIsVisible() {
        assumeAndroidS();
        mBannerPreference.setDismissButtonOnClickListener(mClickListener);

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat((mRootView.findViewById(R.id.banner_dismiss_btn)).getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_withNoDismissClickListener_dimissButtonIsGone() {
        assumeAndroidS();

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat((mRootView.findViewById(R.id.banner_dismiss_btn)).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_withNoClickListenerAndVisible_dimissButtonIsGone() {
        assumeAndroidS();
        mBannerPreference.setDismissButtonVisible(true);

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(mRootView.findViewById(R.id.banner_dismiss_btn).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_withClickListenerAndNotVisible_dimissButtonIsGone() {
        assumeAndroidS();
        mBannerPreference.setDismissButtonOnClickListener(mClickListener);
        mBannerPreference.setDismissButtonVisible(false);

        mBannerPreference.onBindViewHolder(mHolder);

        ImageButton mDismissButton = mRootView.findViewById(R.id.banner_dismiss_btn);
        assertThat(mDismissButton.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenAttentionUnset_setsHighTheme() {
        assumeAndroidS();

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((ImageView) mHolder.findViewById(R.id.banner_icon)).getColorFilter())
                .isEqualTo(getColorFilter(R.color.banner_accent_attention_high));
        assertThat(getButtonColor(R.id.banner_positive_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_high));
        assertThat(getButtonColor(R.id.banner_negative_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_high));

        verify(mMockCardBackground).setTint(getColorId(R.color.banner_background_attention_high));
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenAttentionHighByXML_setsHighTheme() {
        assumeAndroidS();
        AttributeSet mAttributeSet =
                Robolectric.buildAttributeSet().addAttribute(R.attr.attentionLevel, "high").build();
        mBannerPreference = new BannerMessagePreference(mContext, mAttributeSet);

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((ImageView) mHolder.findViewById(R.id.banner_icon)).getColorFilter())
                .isEqualTo(getColorFilter(R.color.banner_accent_attention_high));
        assertThat(getButtonColor(R.id.banner_positive_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_high));
        assertThat(getButtonColor(R.id.banner_negative_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_high));

        verify(mMockCardBackground).setTint(getColorId(R.color.banner_background_attention_high));
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenAttentionMediumByXML_setsMediumTheme() {
        assumeAndroidS();
        AttributeSet mAttributeSet =
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.attentionLevel, "medium")
                        .build();
        mBannerPreference = new BannerMessagePreference(mContext, mAttributeSet);

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((ImageView) mHolder.findViewById(R.id.banner_icon)).getColorFilter())
                .isEqualTo(getColorFilter(R.color.banner_accent_attention_medium));
        assertThat(getButtonColor(R.id.banner_positive_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_medium));
        assertThat(getButtonColor(R.id.banner_negative_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_medium));

        verify(mMockCardBackground).setTint(getColorId(R.color.banner_background_attention_medium));
    }

    @Test
    public void onBindViewHolder_whenAtLeastS_whenAttentionLowByXML_setsLowTheme() {
        assumeAndroidS();
        AttributeSet mAttributeSet =
                Robolectric.buildAttributeSet().addAttribute(R.attr.attentionLevel, "low").build();
        mBannerPreference = new BannerMessagePreference(mContext, mAttributeSet);

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((ImageView) mHolder.findViewById(R.id.banner_icon)).getColorFilter())
                .isEqualTo(getColorFilter(R.color.banner_accent_attention_low));
        assertThat(getButtonColor(R.id.banner_positive_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_low));
        assertThat(getButtonColor(R.id.banner_negative_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_low));

        verify(mMockCardBackground).setTint(getColorId(R.color.banner_background_attention_low));
    }

    @Test
    public void setAttentionLevel_whenAtLeastS_whenHighAttention_setsHighTheme() {
        assumeAndroidS();
        mBannerPreference.setAttentionLevel(BannerMessagePreference.AttentionLevel.HIGH);

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((ImageView) mHolder.findViewById(R.id.banner_icon)).getColorFilter())
                .isEqualTo(getColorFilter(R.color.banner_accent_attention_high));
        assertThat(getButtonColor(R.id.banner_positive_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_high));
        assertThat(getButtonColor(R.id.banner_negative_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_high));

        verify(mMockCardBackground).setTint(getColorId(R.color.banner_background_attention_high));
    }

    @Test
    public void setAttentionLevel_whenAtLeastS_whenHighAttentionAndExpressiveTheme_setsBtnTheme() {
        setExpressiveTheme(true);
        assumeAndroidS();
        assertThat(SettingsThemeHelper.isExpressiveTheme(mContext)).isTrue();
        assertThat(SettingsThemeHelper.isExpressiveTheme(mContext)).isTrue();
        doReturn(mMockPositiveBtn).when(mHolder).findViewById(R.id.banner_positive_btn);
        doReturn(mMockNegativeBtn).when(mHolder).findViewById(R.id.banner_negative_btn);
        assertThat(SettingsThemeHelper.isExpressiveTheme(mContext)).isTrue();
        mBannerPreference.setAttentionLevel(BannerMessagePreference.AttentionLevel.HIGH);
        final ArgumentCaptor<ColorStateList> captor = ArgumentCaptor.forClass(ColorStateList.class);
        ColorStateList filledBtnBackground =
                getColorStateList(R.color.settingslib_banner_button_background_high);
        ColorStateList filledBtnTextColor =
                getColorStateList(R.color.settingslib_banner_filled_button_content_high);
        ColorStateList outlineBtnTextColor =
                getColorStateList(R.color.settingslib_banner_outline_button_content);

        mBannerPreference.onBindViewHolder(mHolder);

        verify(mMockPositiveBtn).setBackgroundTintList(captor.capture());
        verify(mMockPositiveBtn).setTextColor(captor.capture());
        verify(mMockNegativeBtn).setStrokeColor(captor.capture());
        verify(mMockNegativeBtn).setTextColor(captor.capture());
        List<ColorStateList> colors = captor.getAllValues();
        assertThat(colors.get(0).getColors()).isEqualTo(filledBtnBackground.getColors());
        assertThat(colors.get(1).getColors()).isEqualTo(filledBtnTextColor.getColors());
        assertThat(colors.get(2).getColors()).isEqualTo(filledBtnBackground.getColors());
        assertThat(colors.get(3).getColors()).isEqualTo(outlineBtnTextColor.getColors());
    }

    @Test
    public void setAttentionLevel_whenAtLeastS_whenMedAttention_setsBtnMediumTheme() {
        assumeAndroidS();
        mBannerPreference.setAttentionLevel(BannerMessagePreference.AttentionLevel.MEDIUM);

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((ImageView) mHolder.findViewById(R.id.banner_icon)).getColorFilter())
                .isEqualTo(getColorFilter(R.color.banner_accent_attention_medium));
        assertThat(getButtonColor(R.id.banner_positive_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_medium));
        assertThat(getButtonColor(R.id.banner_negative_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_medium));

        verify(mMockCardBackground).setTint(getColorId(R.color.banner_background_attention_medium));
    }

    @Test
    public void setAttentionLevel_whenAtLeastS_whenMedAttentionAndExpressiveTheme_setsBtnTheme() {
        setExpressiveTheme(true);
        mContext.getResources().getConfiguration().uiMode = Configuration.UI_MODE_NIGHT_NO;
        assumeAndroidS();
        doReturn(mMockPositiveBtn).when(mHolder).findViewById(R.id.banner_positive_btn);
        doReturn(mMockNegativeBtn).when(mHolder).findViewById(R.id.banner_negative_btn);
        mBannerPreference.setAttentionLevel(BannerMessagePreference.AttentionLevel.MEDIUM);
        final ArgumentCaptor<ColorStateList> captor = ArgumentCaptor.forClass(ColorStateList.class);
        ColorStateList filledBtnBackground =
                getColorStateList(R.color.settingslib_banner_button_background_medium);
        ColorStateList filledBtnTextColor =
                getColorStateList(R.color.settingslib_banner_filled_button_content_medium);
        ColorStateList outlineBtnTextColor =
                getColorStateList(R.color.settingslib_banner_outline_button_content);

        mBannerPreference.onBindViewHolder(mHolder);

        verify(mMockPositiveBtn).setBackgroundTintList(captor.capture());
        verify(mMockPositiveBtn).setTextColor(captor.capture());
        verify(mMockNegativeBtn).setStrokeColor(captor.capture());
        verify(mMockNegativeBtn).setTextColor(captor.capture());
        List<ColorStateList> colors = captor.getAllValues();
        assertThat(colors.get(0).getColors()).isEqualTo(filledBtnBackground.getColors());
        assertThat(colors.get(1).getColors()).isEqualTo(filledBtnTextColor.getColors());
        assertThat(colors.get(2).getColors()).isEqualTo(filledBtnBackground.getColors());
        assertThat(colors.get(3).getColors()).isEqualTo(outlineBtnTextColor.getColors());
    }

    @Test
    public void setAttentionLevel_whenAtLeastS_whenLowAttention_setsLowTheme() {
        assumeAndroidS();
        mBannerPreference.setAttentionLevel(BannerMessagePreference.AttentionLevel.LOW);

        mBannerPreference.onBindViewHolder(mHolder);

        assertThat(((ImageView) mHolder.findViewById(R.id.banner_icon)).getColorFilter())
                .isEqualTo(getColorFilter(R.color.banner_accent_attention_low));
        assertThat(getButtonColor(R.id.banner_positive_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_low));
        assertThat(getButtonColor(R.id.banner_negative_btn))
                .isEqualTo(getColorId(R.color.banner_accent_attention_low));
        verify(mMockCardBackground).setTint(getColorId(R.color.banner_background_attention_low));
    }

    @Test
    public void
            setAttentionLevel_whenAtLeastS_whenNormalAttentionAndExpressiveTheme_setsBtnTheme() {
        setExpressiveTheme(true);
        mContext.getResources().getConfiguration().uiMode = Configuration.UI_MODE_NIGHT_NO;
        assumeAndroidS();
        doReturn(mMockPositiveBtn).when(mHolder).findViewById(R.id.banner_positive_btn);
        doReturn(mMockNegativeBtn).when(mHolder).findViewById(R.id.banner_negative_btn);
        mBannerPreference.setAttentionLevel(BannerMessagePreference.AttentionLevel.NORMAL);
        final ArgumentCaptor<ColorStateList> captor = ArgumentCaptor.forClass(ColorStateList.class);
        ColorStateList filledBtnBackground =
                getColorStateList(R.color.settingslib_banner_button_background_normal);
        ColorStateList filledBtnTextColor =
                getColorStateList(R.color.settingslib_banner_filled_button_content_normal);
        ColorStateList outlineBtnStrokeColor =
                getColorStateList(R.color.settingslib_banner_outline_button_stroke_normal);

        mBannerPreference.onBindViewHolder(mHolder);

        verify(mMockPositiveBtn).setBackgroundTintList(captor.capture());
        verify(mMockPositiveBtn).setTextColor(captor.capture());
        verify(mMockNegativeBtn).setStrokeColor(captor.capture());
        verify(mMockNegativeBtn).setTextColor(captor.capture());
        List<ColorStateList> colors = captor.getAllValues();
        assertThat(colors.get(0).getColors()).isEqualTo(filledBtnBackground.getColors());
        assertThat(colors.get(1).getColors()).isEqualTo(filledBtnTextColor.getColors());
        assertThat(colors.get(2).getColors()).isEqualTo(outlineBtnStrokeColor.getColors());
        assertThat(colors.get(3).getColors()).isEqualTo(filledBtnBackground.getColors());
    }

    private int getButtonColor(int buttonResId) {
        Button mButton = mRootView.findViewById(buttonResId);
        return mButton.getTextColors().getDefaultColor();
    }

    private ColorStateList getButtonTextColor(int buttonResId) {
        Button mButton = mRootView.findViewById(buttonResId);
        return mButton.getTextColors();
    }

    private ColorFilter getColorFilter(@ColorRes int colorResId) {
        return new PorterDuffColorFilter(getColorId(colorResId), PorterDuff.Mode.SRC_IN);
    }

    private int getColorId(@ColorRes int colorResId) {
        return mContext.getResources().getColor(colorResId, mContext.getTheme());
    }

    private ColorStateList getColorStateList(@ColorRes int colorResId) {
        return mContext.getResources().getColorStateList(colorResId, mContext.getTheme());
    }

    private void assumeAndroidR() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 30);
        ReflectionHelpers.setStaticField(Build.VERSION.class, "CODENAME", "R");

        // Refresh the static final field IS_AT_LEAST_S
        mBannerPreference = new BannerMessagePreference(mContext);
        setUpViewHolder();
    }

    private void assumeAndroidS() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 31);
        ReflectionHelpers.setStaticField(Build.VERSION.class, "CODENAME", "S");

        // Refresh the static final field IS_AT_LEAST_S
        mBannerPreference = new BannerMessagePreference(mContext);
        setUpViewHolder();
    }

    private void setExpressiveTheme(boolean isExpressiveTheme) {
        ShadowSettingsThemeHelper.setExpressiveTheme(isExpressiveTheme);
        assertThat(SettingsThemeHelper.isExpressiveTheme(mContext)).isEqualTo(isExpressiveTheme);
        if (isExpressiveTheme) {
            doReturn(mContext).when(mMockPositiveBtn).getContext();
            doReturn(mContext).when(mMockNegativeBtn).getContext();
        }
    }

    private void setUpViewHolder() {
        mRootView =
                View.inflate(mContext, mBannerPreference.getLayoutResource(), null /* parent */);
        mHolder = spy(PreferenceViewHolder.createInstanceForTests(mRootView));
        doReturn(mMockBackgroundView).when(mHolder).findViewById(R.id.banner_background);
        doReturn(mMockCardBackground).when(mMockBackgroundView).getBackground();
    }

    @Implements(SettingsThemeHelper.class)
    public static class ShadowSettingsThemeHelper {
        private static boolean sIsExpressiveTheme;

        /** Shadow implementation of isExpressiveTheme */
        @Implementation
        public static boolean isExpressiveTheme(@NonNull Context context) {
            return sIsExpressiveTheme;
        }

        static void setExpressiveTheme(boolean isExpressiveTheme) {
            sIsExpressiveTheme = isExpressiveTheme;
        }
    }
}
