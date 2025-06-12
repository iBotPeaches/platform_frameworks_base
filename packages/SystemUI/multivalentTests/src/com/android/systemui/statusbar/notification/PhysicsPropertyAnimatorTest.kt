/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.systemui.statusbar.notification

import android.animation.AnimatorTestRule
import android.util.FloatProperty
import android.util.Property
import android.view.View

import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.dynamicanimation.animation.DynamicAnimation
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.stack.AnimationProperties
import com.android.systemui.statusbar.notification.stack.ViewState
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any

@SmallTest
@RunWith(AndroidJUnit4::class)
@UiThreadTest
class PhysicsPropertyAnimatorTest : SysuiTestCase() {
    private var view: View = View(context)
    private val effectiveProperty =
        object : FloatProperty<View>("TEST") {
            private var _value: Float = 100f

            override fun setValue(view: View, value: Float) {
                this._value = value
            }

            override fun get(`object`: View): Float {
                return _value
            }
        }
    @get:Rule
    val animatorTestRule = AnimatorTestRule(this)
    private val property: PhysicsProperty =
        PhysicsProperty(R.id.scale_x_animator_tag, effectiveProperty)
    private var finishListener: DynamicAnimation.OnAnimationEndListener? = null
    private val animationProperties: AnimationProperties = AnimationProperties()

    @Before
    fun setUp() {
        finishListener = Mockito.mock(DynamicAnimation.OnAnimationEndListener::class.java)
    }

    @Test
    fun testAnimationStarted() {
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            true, /* animate */
        )
        Assert.assertTrue(PhysicsPropertyAnimator.isAnimating(view, property))
    }

    @Test
    fun testNoAnimationStarted() {
        PhysicsPropertyAnimator.setProperty(view, property, 200f, animationProperties, false)
        Assert.assertFalse(PhysicsPropertyAnimator.isAnimating(view, property))
    }

    @Test
    fun testEndValueUpdated() {
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            true, /* animate */
        )
        Assert.assertEquals(
            (ViewState.getChildTag(view, property.tag) as PropertyData).finalValue,
            200f,
        )
    }

    @Test
    fun testOffset() {
        effectiveProperty.setValue(view, 100f)
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            true, /* animate */
        )
        val propertyData = ViewState.getChildTag(view, property.tag) as PropertyData
        Assert.assertEquals(propertyData.finalValue, 200f)
        Assert.assertEquals(propertyData.offset, -100f)
    }

    @Test
    fun testValueIsSetUnAnimated() {
        effectiveProperty.setValue(view, 100f)
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            false, /* animate */
        )
        Assert.assertEquals(200f, effectiveProperty[view])
    }

    @Test
    fun testAnimationToRightValueUpdated() {
        effectiveProperty.setValue(view, 100f)
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            true, /* animate */
        )
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            220f,
            animationProperties,
            false, /* animate */
        )
        Assert.assertTrue(PhysicsPropertyAnimator.isAnimating(view, property))
        Assert.assertEquals(120f, effectiveProperty[view])
        Assert.assertEquals(
            (ViewState.getChildTag(view, property.tag) as PropertyData).finalValue,
            220f,
        )
    }

    @Test
    fun testAnimationToRightValueUpdateAnimated() {
        effectiveProperty.setValue(view, 100f)
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            true, /* animate */
        )
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            220f,
            animationProperties,
            true, /* animate */
        )
        Assert.assertTrue(PhysicsPropertyAnimator.isAnimating(view, property))
        Assert.assertEquals(100f, effectiveProperty[view])
        val propertyData = ViewState.getChildTag(view, property.tag) as PropertyData
        Assert.assertEquals(propertyData.finalValue, 220f)
        Assert.assertEquals(propertyData.offset, -120f)
    }

    @Test
    fun testUsingDelay() {
        effectiveProperty.setValue(view, 100f)
        animationProperties.setDelay(200)
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            true, /* animate */
        )
        val propertyData = ViewState.getChildTag(view, property.tag) as PropertyData
        Assert.assertNotNull(propertyData.delayRunnable)
        Assert.assertFalse(propertyData.animator?.isRunning ?: true)
    }

    @Test
    fun testUsingListener() {
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            true,
            finishListener,
        )
        val propertyData = ViewState.getChildTag(view, property.tag) as PropertyData
        propertyData.animator?.cancel()
        Mockito.verify(finishListener!!).onAnimationEnd(any(), any(), any(), any())
    }

    @Test
    fun testCancelAnimationResetsOffset() {
        PhysicsPropertyAnimator.setProperty(
            view,
            property,
            200f,
            animationProperties,
            true,
            finishListener,
        )
        val propertyData = ViewState.getChildTag(view, property.tag) as PropertyData
        propertyData.animator?.cancel()
        Assert.assertTrue(propertyData.offset == 0f)
    }

    @Test
    fun testUsingListenerProperties() {
        val finishListener2 = Mockito.mock(DynamicAnimation.OnAnimationEndListener::class.java)
        val animationProperties: AnimationProperties =
            object : AnimationProperties() {
                override fun getAnimationEndListener(
                    property: Property<*, *>?
                ): DynamicAnimation.OnAnimationEndListener {
                    return finishListener2
                }
            }
        PhysicsPropertyAnimator.setProperty(view, property, 200f, animationProperties, true)
        val propertyData = ViewState.getChildTag(view, property.tag) as PropertyData
        propertyData.animator?.cancel()
        Mockito.verify(finishListener2).onAnimationEnd(any(), any(), any(), any())
    }
}
