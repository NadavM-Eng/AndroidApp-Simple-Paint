package com.example.task_2_drawing_app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import java.util.LinkedList
import java.util.Queue
import androidx.core.graphics.get
import androidx.core.graphics.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrawingView(context:Context, attrs:AttributeSet) : View(context,attrs) {

    //drawing path
    private lateinit var drawPath: FingerPath

    //defines what to draw
    private lateinit var canvasPaint:Paint

    //defines how to draw
    private lateinit var drawPaint: Paint
    private var color = Color.BLACK
    private lateinit var canvas: Canvas
    private lateinit var canvasBitmap: Bitmap
    private var brushSize: Float = 0.toFloat()

    // NEW: background image to draw beneath strokes
    private var backgroundBitmap: Bitmap? = null

    // used to save the path taken for the drawing
    private val paths = mutableListOf<FingerPath>()

    init {
        setUpDrawing()
    }

    // this function is called by the system when the user touches the screen
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val touchX = event?.x
        val touchY = event?.y

        when(event?.action){
            //this event will be fired when the user put the finger on the screen
            MotionEvent.ACTION_DOWN -> {

                drawPath = FingerPath(color, brushSize)
                drawPath.moveTo(touchX!!, touchY!!)

                paths.add(drawPath)
                invalidate()

            }

            // this event will fire when the user starts to move is finger and stop when he
            // picks up his finger
            MotionEvent.ACTION_MOVE -> {
                drawPath.lineTo(touchX!!,touchY!!)
                invalidate()
            }

            // this event will fire when the finger is up from the screen
            MotionEvent.ACTION_UP -> {
                invalidate()
//                drawPath = FingerPath(color,brushSize)
//                paths.add(drawPath)
            }

            else -> return false

        }

        // refreshing the layout to reflect the changes
        invalidate()

        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = createBitmap(w, h)
        canvas = Canvas(canvasBitmap)

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap,0f,0f,drawPaint)

        canvas.drawColor(Color.WHITE)
        backgroundBitmap?.let { bmp ->
            val viewW = width.toFloat()
            val viewH = height.toFloat()
            if (viewW > 0f && viewH > 0f) {
                val scale = minOf(viewW / bmp.width, viewH / bmp.height)
                val dstW = bmp.width * scale
                val dstH = bmp.height * scale
                val left = (viewW - dstW) / 2f
                val top  = (viewH - dstH) / 2f
                val dst = android.graphics.RectF(left, top, left + dstW, top + dstH)
                canvas.drawBitmap(bmp, null, dst, null)
            }
        }

        for(path in paths){
            drawPaint.strokeWidth = path.brushThickness
            drawPaint.color = path.color
            canvas.drawPath(path,drawPaint)
        }

            // not sure why he keeps it
//        if(!drawPath.isEmpty){
//            drawPaint.strokeWidth = drawPath.brushThickness
//            drawPaint.color = drawPath.color
//            canvas.drawPath(drawPath,drawPaint)
//        }


    }

    private fun setUpDrawing() {
        drawPaint = Paint()
        drawPath = FingerPath(color,brushSize)
        drawPaint.color = color
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeJoin = Paint.Join.ROUND
        drawPaint.strokeCap = Paint.Cap.ROUND

        canvasPaint = Paint(Paint.DITHER_FLAG)
        brushSize = 20.toFloat()
    }

    //changing size of the brush
    fun changeBrushSize(newSize:Float){
        brushSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            newSize,resources.displayMetrics
        )
        drawPaint.strokeWidth = brushSize
    }

    // my own code, subject to change
    fun changeColor(color:Int) {
        this.color = color
    }

    fun undo(){
        invalidate()
        if(paths.size > 0){
            paths.removeAt(paths.size - 1)
        }
        invalidate()
    }

    fun setBackgroundBitmap(bitmap: Bitmap?) {
        backgroundBitmap = bitmap
        invalidate()
    }

    internal inner class FingerPath(var color:Int, var brushThickness:Float): Path()

}