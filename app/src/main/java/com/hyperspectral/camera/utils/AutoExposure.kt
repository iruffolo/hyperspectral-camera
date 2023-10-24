package com.hyperspectral.camera.utils

import android.graphics.Bitmap
import android.media.Image
import android.media.ImageReader
import android.util.Log


class AutoExposure {

    val autoExposureListener =
        ImageReader.OnImageAvailableListener { reader ->
            Log.d("AutoExposure", "Calculating")
            val image = reader.acquireLatestImage()
//            val imgFormat = image.format
//            val pixelArray1 = image.planes[0].buffer
//            val pixelStride = image.planes[0].pixelStride
//            val rowStride = image.planes[0].rowStride
//            val rowPadding = rowStride - pixelStride * 640
//            val bitmap =
//                Bitmap.createBitmap(640 + rowPadding / pixelStride, 480, Bitmap.Config.RGB_565)
//            bitmap.copyPixelsFromBuffer(pixelArray1)
//            imageView.setImageBitmap(bitmap)


            image.close()
        }

    fun calculateISO(img: Image)
    {

    }
}