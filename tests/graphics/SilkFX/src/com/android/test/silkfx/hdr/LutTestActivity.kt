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

package com.android.test.silkfx.hdr

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.HardwareBufferRenderer
import android.graphics.RenderNode
import android.hardware.DisplayLuts
import android.hardware.HardwareBuffer
import android.hardware.LutProperties
import android.os.Bundle
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import android.widget.RadioGroup
import android.util.Log
import com.android.test.silkfx.R
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class LutTestActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private var surfaceControl: SurfaceControl? = null
    private var currentBitmap: Bitmap? = null
    private var renderNode = RenderNode("LutRenderNode")
    private var currentLutType: Int = R.id.no_lut // Store current LUT type
    private val TAG = "LutTestActivity"
    private val renderExecutor = Executors.newSingleThreadExecutor()

    /** Called when the activity is first created. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lut_test)

        surfaceView = findViewById(R.id.surfaceView)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                createChildSurfaceControl()
                loadImage("gainmaps/sunflower.jpg", holder)
                currentBitmap?.let {
                    createAndRenderHardwareBuffer(holder, it, getCurrentLut())
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }
        })

        var lutOption = findViewById<RadioGroup>(R.id.lut_option)
        // handle RadioGroup selection changes
        lutOption.setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { _, id ->
                currentLutType = id
                if (surfaceControl != null) {
                    applyCurrentLut()
                }
            }
        )
    }

    private fun applyCurrentLut() {
        when (currentLutType) {
            R.id.lut_1d -> {
                currentBitmap?.let {
                    createAndRenderHardwareBuffer(surfaceView.holder, it, get1DLut())
                }
            }
            R.id.lut_3d -> {
                currentBitmap?.let {
                    createAndRenderHardwareBuffer(surfaceView.holder, it, get3DLut())
                }
            }
            R.id.no_lut -> {
                currentBitmap?.let {
                    createAndRenderHardwareBuffer(surfaceView.holder, it, null)
                }
            }
        }
    }

    private fun getCurrentLut(): DisplayLuts {
        when (currentLutType) {
            R.id.lut_1d -> return get1DLut()
            R.id.lut_3d -> return get3DLut()
            R.id.no_lut -> return DisplayLuts()
        }
        return DisplayLuts()
    }

    private fun get3DLut(): DisplayLuts {
        var luts = DisplayLuts()
        val entry = DisplayLuts.Entry(
            floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
                         0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                         1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f),
            LutProperties.THREE_DIMENSION,
            LutProperties.SAMPLING_KEY_RGB
        )
        luts.set(entry)
        return luts
    }

    private fun get1DLut(): DisplayLuts {
        var luts = DisplayLuts()
        val entry = DisplayLuts.Entry(
            floatArrayOf(0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f),
            LutProperties.ONE_DIMENSION,
            LutProperties.SAMPLING_KEY_RGB
        )
        luts.set(entry)
        return luts
    }

    private fun createChildSurfaceControl() {
        surfaceView.surfaceControl?.let { parentSC ->
            surfaceControl = SurfaceControl.Builder()
                .setParent(parentSC)
                .setBufferSize(surfaceView.width, surfaceView.height)
                .setName("LutTestSurfaceControl")
                .setHidden(false)
                .build()
        }
    }

    private fun loadImage(assetPath: String, holder: SurfaceHolder) {
        try {
            val source = ImageDecoder.createSource(assets, assetPath)
            currentBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, source ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading image: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createAndRenderHardwareBuffer(holder: SurfaceHolder, bitmap: Bitmap, luts: DisplayLuts?) {
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height

        val buffer = HardwareBuffer.create(
            imageWidth,
            imageHeight,
            HardwareBuffer.RGBA_8888,
            1, // layers
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        )
        val renderer = HardwareBufferRenderer(buffer)
        renderNode.setPosition(0, 0, buffer.width, buffer.height)
        renderer.setContentRoot(renderNode)

        val canvas = renderNode.beginRecording()

        // calculate the scale to match the screen
        val surfaceWidth = holder.surfaceFrame.width()
        val surfaceHeight = holder.surfaceFrame.height()

        val scaleX = surfaceWidth.toFloat() / imageWidth.toFloat()
        val scaleY = surfaceHeight.toFloat() / imageHeight.toFloat()
        val scale = minOf(scaleX, scaleY)

        val matrix = Matrix().apply{ postScale(scale, scale) }
        canvas.drawBitmap(bitmap, matrix, null)
        renderNode.endRecording()

        val colorSpace = ColorSpace.get(ColorSpace.Named.BT2020_HLG)
        val latch = CountDownLatch(1)
        renderer.obtainRenderRequest().setColorSpace(colorSpace).draw(renderExecutor) { renderResult ->
            surfaceControl?.let {
                SurfaceControl.Transaction().setBuffer(it, buffer, renderResult.fence).setLuts(it, luts).apply()
            }
            latch.countDown()
        }
        latch.await() // Wait for the fence to complete.
        buffer.close()
    }
}