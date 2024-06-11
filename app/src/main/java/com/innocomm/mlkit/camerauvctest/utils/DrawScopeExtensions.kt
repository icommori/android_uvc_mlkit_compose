package com.innocomm.mlkit.camerauvctest.utils

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.pose.PoseLandmark

var gOffsetX = 0F
var gOffsetY = 0F

fun DrawScope.drawLandmark(landmark: PointF, color: Color, radius: Float) {
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(landmark.x+ gOffsetX, landmark.y+ gOffsetY),
    )
}

fun DrawScope.drawBounds(topLeft: PointF, size: Size, color: Color, stroke: Float) {
    drawRect(
        color = color,
        size = size,
        topLeft = Offset(topLeft.x+ gOffsetX, topLeft.y+ gOffsetY),
        style = Stroke(width = stroke)
    )
}

fun DrawScope.drawTriangle(points: List<PointF>, color: Color, stroke: Float) {
    if (points.size < 3) return
    drawPath(
        path = Path().apply {
            moveTo(points[0].x+ gOffsetX, points[0].y+ gOffsetY)
            lineTo(points[1].x+ gOffsetX, points[1].y+ gOffsetY)
            lineTo(points[2].x+ gOffsetX, points[2].y+ gOffsetY)
            close()
        },
        color = color,
        style = Stroke(width = stroke)
    )
}

fun DrawScope.drawLine2(startp: PoseLandmark?, endp: PoseLandmark?, color: Color, stroke: Float,
                       imageWidth: Int,
                       imageHeight: Int,
                       screenWidth: Int,
                       screenHeight: Int) {

    safeLet(startp,endp){start,end->
        val topLeft = adjustPoint(
            PointF(start.position3D.x, start.position3D.y),
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        val bottomright = adjustPoint(
            PointF(end.position3D.x, end.position3D.y),
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        //Log.v("innocomm","drawLine "+topLeft.x+"-"+topLeft.y+ "->"+bottomright.x+"-"+bottomright.y)
        //Log.v("innocomm","screen: "+imageWidth+"-"+imageHeight+ "->"+screenWidth+"-"+screenHeight)
        this.drawLine(
            color = color,
            start = Offset(topLeft.x+gOffsetX,topLeft.y+ gOffsetY),
            end = Offset(bottomright.x+gOffsetX,bottomright.y+ gOffsetY),
            strokeWidth = stroke
        )
    }

}

