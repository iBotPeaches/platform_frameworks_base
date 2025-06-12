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
package com.android.systemui.statusbar.notification.row

import android.graphics.drawable.Icon
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.row.shared.IconData
import com.android.systemui.statusbar.notification.row.shared.ImageModel
import com.android.systemui.statusbar.notification.row.shared.ImageModelProvider.ImageSizeClass.SmallSquare
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(PromotedNotificationUi.FLAG_NAME)
class RowImageInflaterTest : SysuiTestCase() {
    private lateinit var rowImageInflater: RowImageInflater

    private val resIcon1 = Icon.createWithResource(context, android.R.drawable.ic_info)
    private val resIcon2 = Icon.createWithResource(context, android.R.drawable.ic_delete)
    private val badUriIcon = Icon.createWithContentUri("content://com.test/does_not_exist")

    private var latestImageModelIndex: ImageModelIndex? = null

    @Before
    fun setUp() {
        rowImageInflater = RowImageInflater.newInstance(null, reinflating = false)
    }

    @Test
    fun getNewImageIndex_returnsNullWhenUnused() {
        assertThat(getNewImageIndex()).isNull()
    }

    @Test
    fun getNewImageIndex_returnsEmptyIndexWhenZeroImagesLoaded() {
        assertThat(getImageModelsForIcons()).isEmpty()
        val result = getNewImageIndex()
        assertThat(result).isNotNull()
        assertThat(result?.contentsForTesting).isEmpty()
    }

    @Test
    fun getNewImageIndex_returnsSingleImageWhenOneImageLoaded() {
        assertThat(getImageModelsForIcons(resIcon1)).hasSize(1)
        val result = getNewImageIndex()
        assertThat(result).isNotNull()
        assertThat(result?.contentsForTesting).hasSize(1)
    }

    @Test
    fun exampleFirstGeneration() {
        // GIVEN various models are required
        val providedModels = getImageModelsForIcons(resIcon1, badUriIcon, resIcon1, resIcon2)

        // VERIFY that we get 4 models, and the two with the same value are shared
        assertThat(providedModels).hasSize(4)
        assertThat(providedModels[0]).isNotSameInstanceAs(providedModels[1])
        assertThat(providedModels[0]).isSameInstanceAs(providedModels[2])
        assertThat(providedModels[0]).isNotSameInstanceAs(providedModels[3])

        // THEN load images
        rowImageInflater.loadImagesSynchronously(context)

        // VERIFY that the valid drawables are loaded
        assertThat(providedModels[0].drawable).isNotNull()
        assertThat(providedModels[1].drawable).isNull()
        assertThat(providedModels[2].drawable).isNotNull()
        assertThat(providedModels[3].drawable).isNotNull()

        // VERIFY the returned index has all 3 entries, 2 of which have drawables
        val indexGen1 = getNewImageIndex()
        assertThat(indexGen1).isNotNull()
        assertThat(indexGen1?.contentsForTesting).hasSize(3)
        assertThat(indexGen1?.contentsForTesting?.mapNotNull { it.drawable }).hasSize(2)
    }

    @Test
    fun exampleSecondGeneration_whichLoadsNothing() {
        exampleFirstGeneration()

        // THEN start a new generation of the inflation
        rowImageInflater = RowImageInflater.newInstance(getNewImageIndex(), reinflating = false)

        getNewImageIndex_returnsEmptyIndexWhenZeroImagesLoaded()
    }

    @Test
    fun exampleSecondGeneration_whichLoadsOneImage() {
        exampleFirstGeneration()
        val gen1Index = latestImageModelIndex!!

        // THEN start a new generation of the inflation
        rowImageInflater = RowImageInflater.newInstance(gen1Index, reinflating = false)

        getNewImageIndex_returnsSingleImageWhenOneImageLoaded()
        val gen2Index = latestImageModelIndex!!

        // VERIFY that the drawable was copied from the previous index
        val gen1model = gen1Index.findModel(resIcon1)
        val gen2model = gen2Index.findModel(resIcon1)
        assertThat(gen2model).isNotSameInstanceAs(gen1model)
        assertThat(gen2model.drawable).isSameInstanceAs(gen1model.drawable)
    }

    @Test
    fun exampleSecondGeneration_reinflating_whichLoadsOneImage() {
        exampleFirstGeneration()
        val gen1Index = latestImageModelIndex!!

        // THEN start a new generation of the inflation
        rowImageInflater = RowImageInflater.newInstance(gen1Index, reinflating = true)

        getNewImageIndex_returnsSingleImageWhenOneImageLoaded()
        val gen2Index = latestImageModelIndex!!

        // VERIFY that the drawable was reloaded rather than copied from the previous index
        val gen1model = gen1Index.findModel(resIcon1)
        val gen2model = gen2Index.findModel(resIcon1)
        assertThat(gen2model).isNotSameInstanceAs(gen1model)
        assertThat(gen2model.drawable).isNotSameInstanceAs(gen1model.drawable)
    }

    private fun ImageModelIndex.findModel(icon: Icon): LazyImage =
        IconData.fromIcon(icon)
            .let { iconData -> contentsForTesting.find { it.icon == iconData } }
            .also { assertThat(it).isNotNull() }!!

    private fun getNewImageIndex(): ImageModelIndex? =
        rowImageInflater.getNewImageIndex().also { latestImageModelIndex = it }

    private fun getImageModelsForIcons(vararg icons: Icon): List<ImageModel> {
        val provider = rowImageInflater.useForContentModel()
        return icons.map { icon ->
            requireNotNull(provider.getImageModel(icon, SmallSquare)) { "null model for $icon" }
        }
    }
}
