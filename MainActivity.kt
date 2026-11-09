package com.zovedit.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var photoView: MaskableImageView
    private lateinit var status: TextView
    private var original: Bitmap? = null
    private var brightness = 0f
    private var contrast = 1f
    private var saturation = 1f
    private val pickImage = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(18, 20, 24))
        }
        val title = TextView(this).apply {
            text = "ZOV EDIT"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 14)
        }
        root.addView(title)

        photoView = MaskableImageView(this).apply {
            setBackgroundColor(Color.rgb(35, 38, 44))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(photoView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        status = TextView(this).apply {
            text = "Choose a photo to start. Brush mode lets you paint a visible mask."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(2, 10, 2, 6)
        }
        root.addView(status)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(makeButton("Open photo") { openPhoto() }, weighted())
        actions.addView(makeButton("Brush mask") {
            photoView.brushMode = !photoView.brushMode
            status.text = if (photoView.brushMode) "Brush ON: draw on the photo. Tap Brush mask again to stop." else "Brush OFF."
        }, weighted())
        actions.addView(makeButton("Clear mask") {
            photoView.clearMask()
            status.text = "Mask cleared."
        }, weighted())
        root.addView(actions)

        addSlider(root, "Brightness", -100, 100, 0) { brightness = it / 100f; updatePreview() }
        addSlider(root, "Contrast", 50, 200, 100) { contrast = it / 100f; updatePreview() }
        addSlider(root, "Saturation", 0, 200, 100) { saturation = it / 100f; updatePreview() }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(makeButton("Reset") {
            brightness = 0f; contrast = 1f; saturation = 1f
            photoView.clearMask()
            buildUi()
            original?.let { photoView.setImageBitmap(it); updatePreview() }
        }, weighted())
        bottom.addView(makeButton("Save copy") { saveCopy() }, weighted())
        root.addView(bottom)
        setContentView(root)
    }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply { text = label; textSize = 11f; setOnClickListener { action() } }

    private fun addSlider(parent: LinearLayout, label: String, minValue: Int, maxValue: Int, initial: Int, changed: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val text = TextView(this).apply { this.text = "$label: $initial"; textSize = 13f; setTextColor(Color.WHITE) }
        val seek = SeekBar(this).apply {
            max = maxValue - minValue
            progress = initial - minValue
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    val value = p + minValue
                    text.text = "$label: $value"
                    changed(value)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        row.addView(text); row.addView(seek)
        parent.addView(row)
    }

    private fun openPhoto() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(intent, "Select photo"), pickImage)
    }

    @Deprecated("Legacy callback kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickImage && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            contentResolver.openInputStream(uri)?.use {
                original = BitmapFactory.decodeStream(it)
                photoView.setImageBitmap(original)
                updatePreview()
                status.text = "Photo loaded. Brush draws a guide overlay; adjustments apply to the whole image."
            }
        }
    }

    private fun updatePreview() {
        val b = original ?: return
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val c = floatArrayOf(
            contrast, 0f, 0f, 0f, brightness * 255f,
            0f, contrast, 0f, 0f, brightness * 255f,
            0f, 0f, contrast, 0f, brightness * 255f,
            0f, 0f, 0f, 1f, 0f
        )
        val contrastMatrix = ColorMatrix(c)
        matrix.postConcat(contrastMatrix)
        photoView.colorFilter = ColorMatrixColorFilter(matrix)
        photoView.invalidate()
    }

    private fun saveCopy() {
        val b = original ?: run { Toast.makeText(this, "Open a photo first", Toast.LENGTH_SHORT).show(); return }
        val output = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = photoView.colorFilter
        }
        canvas.drawBitmap(b, 0f, 0f, paint)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ZOV_EDIT_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ZOV EDIT")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { Toast.makeText(this, "Could not save image", Toast.LENGTH_LONG).show(); return }
        val stream: OutputStream? = contentResolver.openOutputStream(uri)
        stream?.use { output.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        Toast.makeText(this, "Saved in Pictures/ZOV EDIT", Toast.LENGTH_LONG).show()
    }

    private class MaskableImageView(context: android.content.Context) : ImageView(context) {
        var brushMode = false
        private val paths = mutableListOf<Path>()
        private var active: Path? = null
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 0, 220, 255)
            style = Paint.Style.STROKE
            strokeWidth = 28f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        fun clearMask() { paths.clear(); active = null; invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paths.forEach { canvas.drawPath(it, brush) }
            active?.let { canvas.drawPath(it, brush) }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!brushMode) return super.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { active = Path().apply { moveTo(event.x, event.y) }; invalidate(); return true }
                MotionEvent.ACTION_MOVE -> { active?.lineTo(event.x, event.y); invalidate(); return true }
                MotionEvent.ACTION_UP -> { active?.let { paths.add(it) }; active = null; invalidate(); return true }
            }
            return true
        }
    }
}
