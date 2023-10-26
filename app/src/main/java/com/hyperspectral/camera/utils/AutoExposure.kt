package com.hyperspectral.camera.utils

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.SurfaceHolder

import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat

class AutoExposure(imgWidth: Int, imgHeight: Int) {

    private val tag = "AutoExposure"

    private val imageReaderThread = HandlerThread("aeThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    val imageReader : ImageReader = ImageReader.newInstance(
        imgWidth, imgHeight, ImageFormat.JPEG, 1)


    private val autoExposureListener =
        ImageReader.OnImageAvailableListener { reader ->
            Log.d(tag, "Calculating")
            val image = reader.acquireLatestImage()

//             Log.d(tag, "Format ${image.format}")
//            val pixelArray = image.planes[0].buffer
//            val pixelStride = image.planes[0].pixelStride
//            val rowStride = image.planes[0].rowStride
//            val rowPadding = rowStride - pixelStride * 640
//            val bitmap =
//                Bitmap.createBitmap(640 + rowPadding / pixelStride, 480, Bitmap.Config.RGB_565)
//            bitmap.copyPixelsFromBuffer(pixelArray1)
//            imageView.setImageBitmap(bitmap)

//            Log.d(tag, "Format ${image.format}")
//            Log.d(tag, "pixelStride ${pixelStride}")
//            Log.d(tag, "rowStride ${rowStride}")

            image.close()
        }
    init {
        Log.d(tag, "Initializing AE")

        imageReader.setOnImageAvailableListener(autoExposureListener, imageReaderHandler)
    }
}