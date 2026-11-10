package com.example.automa.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.automa.R
import com.example.automa.ui.MainActivity

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var params: LayoutParams

    companion object {
        const val FOREGROUND_ID = 1001
        const val ACTION_TOGGLE = "com.example.automa.overlay.TOGGLE"
        const val ACTION_CLEAR = "com.example.automa.overlay.CLEAR"
        const val ACTION_SNAP = "com.example.automa.overlay.SNAP"
        var isShowing = false
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)
        params = LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                LayoutParams.TYPE_PHONE
            }
            flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = android.graphics.PixelFormat.TRANSLUCENT
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
        }
        startForeground(FOREGROUND_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_TOGGLE -> toggle()
            ACTION_CLEAR -> overlayView.clear()
            ACTION_SNAP -> overlayView.snapshot()
        }
        return START_STICKY
    }

    private fun toggle() {
        if (overlayView.parent != null) {
            windowManager.removeViewImmediate(overlayView)
            isShowing = false
            stopForeground(true)
            stopSelf()
        } else {
            windowManager.addView(overlayView, params)
            isShowing = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (overlayView.parent != null) {
            windowManager.removeViewImmediate(overlayView)
        }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channel = "automa_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nc = android.app.NotificationChannel(channel, "Automa Overlay",
                android.app.NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(nc)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channel)
            .setContentTitle("Automa — Draw on top")
            .setContentText("Annotation overlay is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .build()
    }

    private class OverlayView(context: Context) : View(context), View.OnTouchListener {

        private val ctx = context
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = Color.RED
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val path = android.graphics.Path()
        private var bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        private var canvas = android.graphics.Canvas(bitmap)

        init {
            setOnTouchListener(this)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            bitmap.recycle()
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            canvas.drawPath(path, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    path.reset()
                    path.moveTo(x, y)
                }
                MotionEvent.ACTION_MOVE -> path.lineTo(x, y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    canvas.drawPath(path, paint)
                    path.reset()
                }
            }
            invalidate()
            return true
        }

        override fun onTouch(v: View?, event: MotionEvent): Boolean = onTouchEvent(event)

        fun setColor(color: Int) { paint.color = color }
        fun setSize(size: Float) { paint.strokeWidth = size }

        fun clear() {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            path.reset()
            invalidate()
        }

        fun snapshot() {
            val dir = ctx.externalCacheDir
            val file = java.io.File(dir, "automa_snapshot_${System.currentTimeMillis()}.jpg")
            try {
                val out = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.close()
            } catch (_: Exception) {}
        }
    }
}
