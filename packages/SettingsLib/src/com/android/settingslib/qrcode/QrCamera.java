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

package com.android.settingslib.qrcode;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.VisibleForTesting;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class QrCamera extends Handler {
    private static final String TAG = "QrCamera";

    private static final int MSG_AUTO_FOCUS = 1;

    /**
     * The max allowed difference between picture size ratio and preview size ratio.
     * Uses to filter the picture sizes of similar preview size ratio, for example, if a preview
     * size is 1920x1440, MAX_RATIO_DIFF 0.1 could allow picture size of 720x480 or 352x288 or
     * 176x44 but not 1920x1080.
     */
    private static final double MAX_RATIO_DIFF = 0.1;

    private static final long AUTOFOCUS_INTERVAL_MS = 1500L;

    private static Map<DecodeHintType, List<BarcodeFormat>> HINTS = new ArrayMap<>();
    private static List<BarcodeFormat> FORMATS = new ArrayList<>();

    static {
        FORMATS.add(BarcodeFormat.QR_CODE);
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, FORMATS);
    }

    @VisibleForTesting
    Camera mCamera;
    Camera.CameraInfo mCameraInfo;

    /**
     * The size of the preview image as requested to camera, e.g. 1920x1080.
     */
    private Size mPreviewSize;

    /**
     * Whether the preview image would be displayed in "portrait" (width less
     * than height) orientation in current display orientation.
     *
     * Note that we don't distinguish between a rotation of 90 degrees or 270
     * degrees here, since we center crop all the preview.
     *
     * TODO: Handle external camera / multiple display, this likely requires
     * migrating to newer Camera2 API.
     */
    private boolean mPreviewInPortrait;

    private WeakReference<Context> mContext;
    private ScannerCallback mScannerCallback;
    private MultiFormatReader mReader;
    private DecodingTask mDecodeTask;
    @VisibleForTesting
    Camera.Parameters mParameters;

    public QrCamera(Context context, ScannerCallback callback) {
        mContext = new WeakReference<Context>(context);
        mScannerCallback = callback;
        mReader = new MultiFormatReader();
        mReader.setHints(HINTS);
    }

    /**
     * The function start camera preview and capture pictures to decode QR code continuously in a
     * background task.
     *
     * @param surface The surface to be used for live preview.
     */
    public void start(SurfaceTexture surface) {
        if (mDecodeTask == null) {
            mDecodeTask = new DecodingTask(surface);
            // Execute in the separate thread pool to prevent block other AsyncTask.
            mDecodeTask.executeOnExecutor(Executors.newSingleThreadExecutor());
        }
    }

    /**
     * The function stop camera preview and background decode task. Caller call this function when
     * the surface is being destroyed.
     */
    public void stop() {
        removeMessages(MSG_AUTO_FOCUS);
        if (mDecodeTask != null) {
            mDecodeTask.cancel(true);
            mDecodeTask = null;
        }
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
                releaseCamera();
            } catch (RuntimeException e) {
                Log.e(TAG, "Stop previewing camera failed:" + e);
                mCamera = null;
            }
        }
    }

    /** The scanner which includes this QrCodeCamera class should implement this */
    public interface ScannerCallback {

        /**
         * The function used to handle the decoding result of the QR code.
         *
         * @param result the result QR code after decoding.
         */
        void handleSuccessfulResult(String result);

        /** Request the QR code scanner to handle the failure happened. */
        void handleCameraFailure();

        /**
         * The function used to get the background View size.
         *
         * @return Includes the background view size.
         */
        Size getViewSize();

        /**
         * The function used to get the frame position inside the view
         *
         * @param previewSize       Is the preview size set by camera
         * @param cameraOrientation Is the orientation of current Camera
         * @return The rectangle would like to crop from the camera preview shot.
         * @deprecated This is no longer used, and the frame position is
         *     automatically calculated from the preview size and the
         *     background View size.
         */
        @Deprecated
        default @NonNull Rect getFramePosition(@NonNull Size previewSize, int cameraOrientation) {
            throw new AssertionError("getFramePosition shouldn't be used");
        }

        /**
         * Sets the transform to associate with preview area.
         *
         * @param transform The transform to apply to the content of preview
         */
        void setTransform(Matrix transform);

        /**
         * Verify QR code is valid or not. The camera will stop scanning if this callback returns
         * true.
         *
         * @param qrCode The result QR code after decoding.
         * @return Returns true if qrCode hold valid information.
         */
        boolean isValid(String qrCode);
    }

    private boolean setPreviewDisplayOrientation() {
        if (mContext.get() == null) {
            return false;
        }

        final WindowManager winManager =
                (WindowManager) mContext.get().getSystemService(Context.WINDOW_SERVICE);
        final int rotation = winManager.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int rotateDegrees = 0;
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotateDegrees = (mCameraInfo.orientation + degrees) % 360;
            rotateDegrees = (360 - rotateDegrees) % 360;  // compensate the mirror
        } else {
            rotateDegrees = (mCameraInfo.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(rotateDegrees);
        mPreviewInPortrait = (rotateDegrees == 90 || rotateDegrees == 270);
        return true;
    }

    @VisibleForTesting
    void setCameraParameter() {
        mParameters = mCamera.getParameters();
        mPreviewSize = getBestPreviewSize(mParameters);
        mParameters.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Size pictureSize = getBestPictureSize(mParameters);
        mParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());

        final List<String> supportedFlashModes = mParameters.getSupportedFlashModes();
        if (supportedFlashModes != null &&
                supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
            mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }

        final List<String> supportedFocusModes = mParameters.getSupportedFocusModes();
        if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        mCamera.setParameters(mParameters);
    }

    /**
     * Set transform matrix to crop and center the preview picture.
     */
    private void setTransformationMatrix() {
        final Size previewDisplaySize = rotateIfPortrait(mPreviewSize);
        final Size viewSize = mScannerCallback.getViewSize();
        final Rect cropRegion = calculateCenteredCrop(previewDisplaySize, viewSize);

        // Note that strictly speaking, since the preview is mirrored in front
        // camera case, we should also mirror the crop region here. But since
        // we're cropping at the center, mirroring would result in the same
        // crop region other than small off-by-one error from floating point
        // calculation and wouldn't be noticeable.

        // Calculate transformation matrix.
        float scaleX = previewDisplaySize.getWidth() / (float) cropRegion.width();
        float scaleY = previewDisplaySize.getHeight() / (float) cropRegion.height();
        float translateX = -cropRegion.left / (float) cropRegion.width() * viewSize.getWidth();
        float translateY = -cropRegion.top / (float) cropRegion.height() * viewSize.getHeight();

        // Set the transform matrix.
        final Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY);
        matrix.postTranslate(translateX, translateY);
        mScannerCallback.setTransform(matrix);
    }

    private void startPreview() {
        mCamera.startPreview();
        if (Camera.Parameters.FOCUS_MODE_AUTO.equals(mParameters.getFocusMode())) {
            mCamera.autoFocus(/* Camera.AutoFocusCallback */ null);
            sendMessageDelayed(obtainMessage(MSG_AUTO_FOCUS), AUTOFOCUS_INTERVAL_MS);
        }
    }

    private class DecodingTask extends AsyncTask<Void, Void, String> {
        private QrYuvLuminanceSource mImage;
        private SurfaceTexture mSurface;

        private DecodingTask(SurfaceTexture surface) {
            mSurface = surface;
        }

        @Override
        protected String doInBackground(Void... tmp) {
            if (!initCamera(mSurface)) {
                return null;
            }

            final Semaphore imageGot = new Semaphore(0);
            while (true) {
                // This loop will try to capture preview image continuously until a valid QR Code
                // decoded. The caller can also call {@link #stop()} to interrupts scanning loop.
                mCamera.setOneShotPreviewCallback(
                        (imageData, camera) -> {
                            mImage = getFrameImage(imageData);
                            imageGot.release();
                        });
                try {
                    // Semaphore.acquire() blocking until permit is available, or the thread is
                    // interrupted.
                    imageGot.acquire();
                    Result qrCode = decodeQrCode(mImage);
                    if (qrCode == null) {
                        // Check color inversion QR code
                        qrCode = decodeQrCode(mImage.invert());
                    }
                    if (qrCode != null) {
                        if (mScannerCallback.isValid(qrCode.getText())) {
                            return qrCode.getText();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        private Result decodeQrCode(LuminanceSource source) {
            try {
                return mReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
            } catch (ReaderException e) {
                // No logging since every time the reader cannot decode the
                // image, this ReaderException will be thrown.
            } finally {
                mReader.reset();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String qrCode) {
            if (qrCode != null) {
                mScannerCallback.handleSuccessfulResult(qrCode);
            }
        }

        private boolean initCamera(SurfaceTexture surface) {
            final int numberOfCameras = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            try {
                for (int i = 0; i < numberOfCameras; ++i) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        releaseCamera();
                        mCamera = Camera.open(i);
                        mCameraInfo = cameraInfo;
                        break;
                    }
                }
                if (mCamera == null && numberOfCameras > 0) {
                    Log.i(TAG, "Can't find back camera. Opening a different camera");
                    Camera.getCameraInfo(0, cameraInfo);
                    releaseCamera();
                    mCamera = Camera.open(0);
                    mCameraInfo = cameraInfo;
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Fail to open camera: " + e);
                mCamera = null;
                mScannerCallback.handleCameraFailure();
                return false;
            }

            try {
                if (mCamera == null) {
                    throw new IOException("Cannot find available camera");
                }
                mCamera.setPreviewTexture(surface);
                if (!setPreviewDisplayOrientation()) {
                    throw new IOException("Lost context");
                }
                setCameraParameter();
                setTransformationMatrix();
                startPreview();
            } catch (IOException ioe) {
                Log.e(TAG, "Fail to startPreview camera: " + ioe);
                mCamera = null;
                mScannerCallback.handleCameraFailure();
                return false;
            }
            return true;
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * Calculates the crop region in `previewSize` to have the same aspect
     * ratio as `viewSize` and center aligned.
     */
    private Rect calculateCenteredCrop(Size previewSize, Size viewSize) {
        final double previewRatio = getRatio(previewSize);
        final double viewRatio = getRatio(viewSize);
        int width;
        int height;
        if (previewRatio > viewRatio) {
            width = previewSize.getWidth();
            height = (int) Math.round(width * viewRatio);
        } else {
            height = previewSize.getHeight();
            width = (int) Math.round(height / viewRatio);
        }
        final int left = (previewSize.getWidth() - width) / 2;
        final int top = (previewSize.getHeight() - height) / 2;
        return new Rect(left, top, left + width, top + height);
    }

    private QrYuvLuminanceSource getFrameImage(byte[] imageData) {
        final Size viewSize = mScannerCallback.getViewSize();
        final Rect frame = calculateCenteredCrop(mPreviewSize, rotateIfPortrait(viewSize));
        final QrYuvLuminanceSource image = new QrYuvLuminanceSource(imageData,
                mPreviewSize.getWidth(), mPreviewSize.getHeight());
        return (QrYuvLuminanceSource)
                image.crop(frame.left, frame.top, frame.width(), frame.height());
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_AUTO_FOCUS:
                // Calling autoFocus(null) will only trigger the camera to focus once. In order
                // to make the camera continuously auto focus during scanning, need to periodically
                // trigger it.
                mCamera.autoFocus(/* Camera.AutoFocusCallback */ null);
                sendMessageDelayed(obtainMessage(MSG_AUTO_FOCUS), AUTOFOCUS_INTERVAL_MS);
                break;
            default:
                Log.d(TAG, "Unexpected Message: " + msg.what);
        }
    }

    /**
     * Get best preview size from the list of camera supported preview sizes. Compares the
     * preview size and aspect ratio to choose the best one.
     */
    private Size getBestPreviewSize(Camera.Parameters parameters) {
        final double minRatioDiffPercent = 0.1;
        final Size viewSize = rotateIfPortrait(mScannerCallback.getViewSize());
        final double viewRatio = getRatio(viewSize);
        double bestChoiceRatio = 0;
        Size bestChoice = new Size(0, 0);
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            final Size newSize = toAndroidSize(size);
            final double ratio = getRatio(newSize);
            if (size.height * size.width > bestChoice.getWidth() * bestChoice.getHeight()
                    && (Math.abs(bestChoiceRatio - viewRatio) / viewRatio > minRatioDiffPercent
                    || Math.abs(ratio - viewRatio) / viewRatio <= minRatioDiffPercent)) {
                bestChoice = newSize;
                bestChoiceRatio = ratio;
            }
        }
        return bestChoice;
    }

    /**
     * Get best picture size from the list of camera supported picture sizes. Compares the
     * picture size and aspect ratio to choose the best one.
     */
    private Size getBestPictureSize(Camera.Parameters parameters) {
        final Size previewSize = mPreviewSize;
        final double previewRatio = getRatio(previewSize);
        List<Size> bestChoices = new ArrayList<>();
        final List<Size> similarChoices = new ArrayList<>();

        // Filter by ratio
        for (Camera.Size picSize : parameters.getSupportedPictureSizes()) {
            final Size size = toAndroidSize(picSize);
            final double ratio = getRatio(size);
            if (ratio == previewRatio) {
                bestChoices.add(size);
            } else if (Math.abs(ratio - previewRatio) < MAX_RATIO_DIFF) {
                similarChoices.add(size);
            }
        }

        if (bestChoices.size() == 0 && similarChoices.size() == 0) {
            Log.d(TAG, "No proper picture size, return default picture size");
            Camera.Size defaultPictureSize = parameters.getPictureSize();
            return toAndroidSize(defaultPictureSize);
        }

        if (bestChoices.size() == 0) {
            bestChoices = similarChoices;
        }

        // Get the best by area
        int bestAreaDifference = Integer.MAX_VALUE;
        Size bestChoice = null;
        final int previewArea = previewSize.getWidth() * previewSize.getHeight();
        for (Size size : bestChoices) {
            int areaDifference = Math.abs(size.getWidth() * size.getHeight() - previewArea);
            if (areaDifference < bestAreaDifference) {
                bestAreaDifference = areaDifference;
                bestChoice = size;
            }
        }
        return bestChoice;
    }

    private Size rotateIfPortrait(Size size) {
        if (mPreviewInPortrait) {
            return new Size(size.getHeight(), size.getWidth());
        } else {
            return size;
        }
    }

    private double getRatio(Size size) {
        return size.getHeight() / (double) size.getWidth();
    }

    private Size toAndroidSize(Camera.Size size) {
        return new Size(size.width, size.height);
    }

    @VisibleForTesting
    protected void decodeImage(BinaryBitmap image) {
        Result qrCode = null;

        try {
            qrCode = mReader.decodeWithState(image);
        } catch (ReaderException e) {
        } finally {
            mReader.reset();
        }

        if (qrCode != null) {
            mScannerCallback.handleSuccessfulResult(qrCode.getText());
        }
    }

    /**
     * After {@link #start(SurfaceTexture)}, DecodingTask runs continuously to capture images and
     * decode QR code. DecodingTask become null After {@link #stop()}.
     *
     * Uses this method in test case to prevent power consumption problem.
     */
    public boolean isDecodeTaskAlive() {
        return mDecodeTask != null;
    }
}
