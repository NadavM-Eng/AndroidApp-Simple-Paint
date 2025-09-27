package com.example.task_2_drawing_app

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import yuku.ambilwarna.AmbilWarnaDialog
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), View.OnClickListener{

    private lateinit var drawingView: DrawingView
    private lateinit var brushButton: ImageButton
    private lateinit var colorPicker: ImageButton
    private lateinit var undo: ImageButton
    private lateinit var uploadDrawing: ImageButton
    private lateinit var openGallery: ImageButton

    // opens the Gallery and let the user pick a picture to be the background of the canvas.
    private val openGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
            drawingView.setBackgroundBitmap(bitmap)
        }

    // Single permissions launcher; we’ll set an action to run after grant
    private var pendingAction: (() -> Unit)? = null
    private val requestPermissionsLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val readGranted =
                (perms[Manifest.permission.READ_MEDIA_IMAGES] == true) ||
                        (perms[Manifest.permission.READ_EXTERNAL_STORAGE] == true)

            val writeGrantedApi28 = (perms[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true)

            // If any needed permission has been granted, run the pending action
            if (readGranted || writeGrantedApi28) {
                pendingAction?.invoke()
            }
            else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
            pendingAction = null
        }
    var colorTemp: Int = 0

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_canvas)
        brushButton = findViewById(R.id.brush_size)
        openGallery = findViewById(R.id.gallery)
        uploadDrawing = findViewById(R.id.upload)
        undo = findViewById(R.id.undo)
        colorPicker = findViewById(R.id.color_picker)

        undo.setOnClickListener(this)
        brushButton.setOnClickListener(this)
        openGallery.setOnClickListener(this)
        uploadDrawing.setOnClickListener(this)
        colorPicker.setOnClickListener(this)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun showBrushChooserDialog(){
        val brushDialog = Dialog(this@MainActivity)
        brushDialog.setContentView(R.layout.dialog_brush)

        val seekBar = brushDialog.findViewById<SeekBar>(R.id.dialog_seek_bar)
        val showProgress = brushDialog.findViewById<TextView>(R.id.dialog_seek_bar_progress)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                drawingView.changeBrushSize(seekBar.progress.toFloat())

                showProgress.text = seekBar.progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })

        brushDialog.show()
    }

    // a change to check how git works

    private fun openColorPickerDialogue(){
        val colorPickerDialog = AmbilWarnaDialog(this, colorTemp,object: OnAmbilWarnaListener {
            override fun onCancel(dialog: AmbilWarnaDialog?) {

            }

            override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                colorTemp = color
                drawingView.changeColor(color)
            }

        })
        colorPickerDialog.show()

    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.brush_size -> {
                showBrushChooserDialog()
            }
            R.id.color_picker -> {
                openColorPickerDialogue()
            }
            R.id.undo -> {
                drawingView.undo()
            }
            R.id.upload -> {
                val layout = findViewById<ConstraintLayout>(R.id.constraintLy1)
                val bitmap = getBitmapFromView(layout)
                saveImageHybridWithPerms(bitmap)
            }
            R.id.gallery -> {

                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)


            }

        }
    }
    private fun getBitmapFromView(view:View): Bitmap {

        val bitmap = createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        view.draw(canvas)
        return bitmap
    }

    private fun toastResult(success: Boolean) {
        Toast.makeText(
            this,
            if (success) "Saved to gallery ✓" else "Save failed",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun ensureGalleryPermissionThen(action: () -> Unit) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) action() else {
            pendingAction = action
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissionsLauncher.launch(perms)
        }
    }

    private fun ensureLegacyWriteApi28Then(action: () -> Unit) {
        // Only relevant on Android 9 (API 28). On 29+ we never request write.
        val granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (granted) action() else {
            pendingAction = action
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    // Saving (hybrid: MediaStore for 29+, legacy for 28)
    private fun saveImageHybridWithPerms(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+): MediaStore path, no write permission needed
            CoroutineScope(IO).launch {
                val uri = saveImageToPicturesModern(bitmap, subFolder = "saved_images", fileName = "Image-${'$'}{System.currentTimeMillis()}.jpg")
                withContext(Main) {
                    toastResult(uri != null)
                }
            }
        } else {
            // Android 9 (API 28): need WRITE_EXTERNAL_STORAGE to write public Pictures
            ensureLegacyWriteApi28Then {
                CoroutineScope(IO).launch {
                    val ok = saveImageToPicturesLegacyApi28(bitmap, subfolder = "saved_images", fileName = "Image-${'$'}{System.currentTimeMillis()}.jpg")
                    withContext(Main) { toastResult(ok) }
                }
            }
        }
    }
    private fun saveImageToPicturesModern(bitmap: Bitmap, subFolder:String, fileName:String): Uri? {

        return try {
            val values = ContentValues().apply {

                put(MediaStore.Images.Media.DISPLAY_NAME,fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${'$'}subfolder")
                    put(MediaStore.Images.Media.IS_PENDING,1)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if(uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG,90,out)
                }
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri,values, null,null)
                }
            }
            uri
        }
        catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    // Legacy saver for API 28 (Android 9)
    @Suppress("DEPRECATION")
    private fun saveImageToPicturesLegacyApi28(bitmap: Bitmap, subfolder: String, fileName: String): Boolean {
        return try {
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = File(root, subfolder)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            // Ask MediaScanner to index so it appears in Gallery
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)))
            true
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }
}