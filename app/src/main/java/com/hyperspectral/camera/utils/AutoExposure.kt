package com.hyperspectral.camera.utils

import android.graphics.Bitmap
import android.media.ImageReader
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class AutoExposure() {

    var mExposure : String = "0"

    private val tag = "AutoExposure"

    val autoExposureListener =
        ImageReader.OnImageAvailableListener { reader ->
            Log.d(tag, "Calculating")
            val image = reader.acquireLatestImage()

            Log.d(tag, "Format ${image.format}")
            val pixelArray = image.planes[0].buffer
            val pixelStride = image.planes[0].pixelStride
            val rowStride = image.planes[0].rowStride
            val bitmap =
                Bitmap.createBitmap(rowStride/pixelStride, image.height, Bitmap.Config.RGB_565)
            bitmap.copyPixelsFromBuffer(pixelArray)

            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)

            Log.d(tag, "${mat.size()}")
            Log.d(tag, "mean ${Core.mean(mat)}")
//            Log.d(tag, "Format ${image.format}")
//            Log.d(tag, "pixelStride ${pixelStride}")
//            Log.d(tag, "rowStride ${rowStride}")

            val avg = Core.mean(mat)

            mExposure = avg.toString().slice(IntRange(1,5))

            image.close()
        }
}