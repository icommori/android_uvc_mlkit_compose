package com.innocomm.mlkit.camerauvctest.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import androidx.annotation.ColorInt
import androidx.compose.ui.geometry.Size
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.jiangdg.ausbc.CameraClient

fun adjustPoint(point: PointF, imageWidth: Int, imageHeight: Int, screenWidth: Int, screenHeight: Int): PointF {
    val x = point.x / imageWidth * screenWidth
    val y = point.y / imageHeight * screenHeight
    return PointF(x, y)
}

fun adjustSize(size: Size, imageWidth: Int, imageHeight: Int, screenWidth: Int, screenHeight: Int): Size {
    val width = size.width / imageWidth * screenWidth
    val height = size.height / imageHeight * screenHeight
    return Size(width, height)
}

inline fun <T1: Any, T2: Any, R: Any> safeLet(p1: T1?, p2: T2?, block: (T1, T2)->R?): R? {
    return if (p1 != null && p2 != null) block(p1, p2) else null
}
inline fun <T1: Any, T2: Any, T3: Any, R: Any> safeLet(p1: T1?, p2: T2?, p3: T3?, block: (T1, T2, T3)->R?): R? {
    return if (p1 != null && p2 != null && p3 != null) block(p1, p2, p3) else null
}
inline fun <T1: Any, T2: Any, T3: Any, T4: Any, R: Any> safeLet(p1: T1?, p2: T2?, p3: T3?, p4: T4?, block: (T1, T2, T3, T4)->R?): R? {
    return if (p1 != null && p2 != null && p3 != null && p4 != null) block(p1, p2, p3, p4) else null
}
inline fun <T1: Any, T2: Any, T3: Any, T4: Any, T5: Any, R: Any> safeLet(p1: T1?, p2: T2?, p3: T3?, p4: T4?, p5: T5?, block: (T1, T2, T3, T4, T5)->R?): R? {
    return if (p1 != null && p2 != null && p3 != null && p4 != null && p5 != null) block(p1, p2, p3, p4, p5) else null
}

class myPose(val hasVal: Boolean,val pose: Pose?){}
class myCameraClient(val hasVal: Boolean,val cameraclient: CameraClient?){}

fun flipBitmap( source: Bitmap,overlayWidth: Int,overlayHeight:Int): Bitmap {
    //val matrix = Matrix()
    //matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f)
    //val bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    return Bitmap.createScaledBitmap( source , overlayWidth , overlayHeight , true )
}
fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
    val width = bm.getWidth()
    val height = bm.getHeight()
    val scaleWidth = newWidth.toFloat() / width
    val scaleHeight = newHeight.toFloat() / height
    // CREATE A MATRIX FOR THE MANIPULATION
    val matrix = Matrix()
    // RESIZE THE BIT MAP
    matrix.postScale(scaleWidth, scaleHeight)

    // "RECREATE" THE NEW BITMAP
    val resizedBitmap = Bitmap.createBitmap(
        bm, 0, 0, width, height, matrix, false
    )
    bm.recycle()
    return resizedBitmap
}

fun maskColorsFromByteBuffer(segmentationMask: SegmentationMask): IntArray {
    @ColorInt val colors = IntArray(segmentationMask.width * segmentationMask.height)
    for (i in 0 until segmentationMask.width * segmentationMask.height) {
        val backgroundLikelihood = 1 - segmentationMask.buffer.getFloat()
        if (backgroundLikelihood > 0.9) {
            colors[i] = Color.argb(128, 255, 0, 255)
        } else if (backgroundLikelihood > 0.2) {
            // Linear interpolation to make sure when backgroundLikelihood is 0.2, the alpha is 0 and
            // when backgroundLikelihood is 0.9, the alpha is 128.
            // +0.5 to round the float value to the nearest int.
            val alpha = (182.9 * backgroundLikelihood - 36.6 + 0.5).toInt()
            colors[i] = Color.argb(alpha, 255, 0, 255)
        }
    }
    return colors
}

