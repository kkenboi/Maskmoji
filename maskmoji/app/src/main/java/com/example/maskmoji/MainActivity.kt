package com.example.maskmoji

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.maskmoji.ui.theme.MaskmojiTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaskmojiTheme {
                FaceDetectorScreen()
            }
        }
    }
}

@Composable
fun FaceDetectorScreen() {
    var resultImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            detectFaces(context, it) { processedUri ->
                resultImageUri = processedUri
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(onClick = { launcher.launch("image/*") }) {
                Text("Pick an Image")
            }

            Spacer(modifier = Modifier.height(16.dp))

            resultImageUri?.let {
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

// 顔検出 → 円を描画 → 保存 → Uri を返す
fun detectFaces(context: Context, uri: Uri, onResult: (Uri) -> Unit) {
    val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()

    val detector = FaceDetection.getClient(options)
    val image = InputImage.fromFilePath(context, uri)

    detector.process(image)
        .addOnSuccessListener { faces ->
            Log.d("Maskmoji", "Detected ${faces.size} faces")

            val processed = drawCirclesOnFaces(context, uri, faces)
            val savedUri = saveBitmap(context, processed, "masked_${System.currentTimeMillis()}")
            onResult(savedUri)
        }
        .addOnFailureListener { e ->
            Log.e("Maskmoji", "Face detection failed", e)
        }
}

// 画像に円を描画
fun drawCirclesOnFaces(context: Context, uri: Uri, faces: List<com.google.mlkit.vision.face.Face>): Bitmap {
    val source = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    val mutableBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)
    val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 200 // 少し透過
    }

    val emojiBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.smile)

    // 顔ごとに重ねる
    for (face in faces) {
        val bounds = face.boundingBox
        val scaledEmoji = Bitmap.createScaledBitmap(
            emojiBitmap,
            bounds.width(),
            bounds.height(),
            false
        )
        canvas.drawBitmap(
            scaledEmoji,
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            null
        )
    }

    return mutableBitmap
}

// Bitmap を保存
fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): Uri {
    val fos: OutputStream?
    val imageUri: Uri?

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Maskmoji")
        }
        val resolver = context.contentResolver
        imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        fos = imageUri?.let { resolver.openOutputStream(it) }
    } else {
        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
        val image = File(imagesDir, "$fileName.jpg")
        fos = FileOutputStream(image)
        imageUri = Uri.fromFile(image)
    }

    fos?.use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
    }

    return imageUri!!
}