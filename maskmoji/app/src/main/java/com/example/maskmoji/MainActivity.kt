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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.maskmoji.ui.theme.MaskmojiTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import androidx.compose.ui.res.painterResource
import kotlin.math.max
import kotlin.math.min

enum class DetectionType(val displayName: String, val emoji: String, val description: String) {
    FACES("Faces", "😷", "Detect and mask faces"),
    DOCUMENTS("Documents", "📄", "Detect and mask text/documents"),
    SCREENS("Screens", "📱", "Detect and mask screens/displays"),
    ALL("Everything", "🎭", "Detect and mask all sensitive content")
}

data class DetectionResult(
    val faces: Int = 0,
    val documents: Int = 0,
    val screens: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaskmojiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FaceDetectorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceDetectorScreen() {
    var resultImageUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var detectionResult by remember { mutableStateOf(DetectionResult()) }
    var selectedDetectionType by remember { mutableStateOf(DetectionType.ALL) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessing = true
            processImage(context, it, selectedDetectionType) { processedUri, result ->
                resultImageUri = processedUri
                detectionResult = result
                isProcessing = false
            }
        }
    }

    // Animated background gradient
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667eea).copy(alpha = 0.1f + animatedOffset * 0.1f),
                        Color(0xFF764ba2).copy(alpha = 0.1f + animatedOffset * 0.1f),
                        Color(0xFFf093fb).copy(alpha = 0.1f + animatedOffset * 0.1f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "🎭 Maskmoji",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Protect your privacy with AI-powered content masking",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // Detection type selector
            if (!isProcessing) {
                DetectionTypeSelector(
                    selectedType = selectedDetectionType,
                    onTypeSelected = { selectedDetectionType = it },
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Main content area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isProcessing -> {
                            ProcessingIndicator(selectedDetectionType)
                        }
                        resultImageUri != null -> {
                            ProcessedImageDisplay(
                                imageUri = resultImageUri!!,
                                detectionResult = detectionResult,
                                onNewPhoto = { launcher.launch("image/*") }
                            )
                        }
                        else -> {
                            EmptyStateContent(
                                selectedType = selectedDetectionType,
                                onSelectPhoto = { launcher.launch("image/*") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom action area
            if (resultImageUri != null && !isProcessing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { launcher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Try Another")
                    }

                    Button(
                        onClick = { /* Share functionality */ },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF667eea)
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
fun DetectionTypeSelector(
    selectedType: DetectionType,
    onTypeSelected: (DetectionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Detection Mode",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetectionType.values().forEach { type ->
                    DetectionChip(
                        type = type,
                        isSelected = selectedType == type,
                        onSelected = { onTypeSelected(type) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DetectionChip(
    type: DetectionType,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chip_scale"
    )

    Card(
        modifier = modifier
            .scale(animatedScale)
            .clickable { onSelected() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFF667eea).copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF667eea)) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = type.emoji,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = type.displayName,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color(0xFF667eea) else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyStateContent(
    selectedType: DetectionType,
    onSelectPhoto: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated emoji
        val scale by rememberInfiniteTransition(label = "scale").animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "emoji_scale"
        )

        Text(
            text = "📸",
            fontSize = 80.sp,
            modifier = Modifier.scale(scale)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No photo selected",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = selectedType.description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSelectPhoto,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF667eea)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Select Photo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
@Composable
fun ProcessingIndicator(detectionType: DetectionType) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val rotation by rememberInfiniteTransition(label = "rotation").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing)
            ), label = "spinner"
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF667eea),
                            Color(0xFF764ba2)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val drawableResource = when (detectionType) {
                DetectionType.FACES -> R.drawable.smile // Your face detection image
                DetectionType.DOCUMENTS -> R.drawable.document // Your document detection image
                DetectionType.SCREENS -> R.drawable.screen // Your screen detection image
                DetectionType.ALL -> R.drawable.document // Your all detection image
            }

            Image(
                painter = painterResource(id = drawableResource),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Scanning for ${detectionType.displayName.lowercase()}...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Applying privacy masks",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ProcessedImageDisplay(
    imageUri: Uri,
    detectionResult: DetectionResult,
    onNewPhoto: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stats chips
        val totalDetections = detectionResult.faces + detectionResult.documents + detectionResult.screens

        AnimatedVisibility(
            visible = totalDetections > 0,
            enter = fadeIn() + slideInVertically()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                if (detectionResult.faces > 0) {
                    DetectionStatsChip(
                        icon = "😷",
                        count = detectionResult.faces,
                        label = "face${if (detectionResult.faces != 1) "s" else ""}"
                    )
                }
                if (detectionResult.documents > 0) {
                    DetectionStatsChip(
                        icon = "📄",
                        count = detectionResult.documents,
                        label = "document${if (detectionResult.documents != 1) "s" else ""}"
                    )
                }
                if (detectionResult.screens > 0) {
                    DetectionStatsChip(
                        icon = "📱",
                        count = detectionResult.screens,
                        label = "screen${if (detectionResult.screens != 1) "s" else ""}"
                    )
                }
            }
        }

        // Image display with rounded corners and shadow
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = "Processed image with privacy masks",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Success message
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    Color(0xFF4CAF50).copy(alpha = 0.1f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "✨",
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Privacy protection applied successfully!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
fun DetectionStatsChip(
    icon: String,
    count: Int,
    label: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count $label",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

// Enhanced processing function that handles multiple detection types
fun processImage(
    context: Context,
    uri: Uri,
    detectionType: DetectionType,
    onResult: (Uri, DetectionResult) -> Unit
) {
    val image = InputImage.fromFilePath(context, uri)
    var result = DetectionResult()
    var processedBitmap: Bitmap? = null
    var completedTasks = 0
    val totalTasks = when (detectionType) {
        DetectionType.FACES -> 1
        DetectionType.DOCUMENTS -> 1
        DetectionType.SCREENS -> 1
        DetectionType.ALL -> 3
    }

    fun checkCompletion() {
        completedTasks++
        if (completedTasks >= totalTasks) {
            val finalBitmap = processedBitmap ?: MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val savedUri = saveBitmap(context, finalBitmap, "masked_${System.currentTimeMillis()}")
            onResult(savedUri, result)
        }
    }

    // Initialize bitmap
    processedBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri).copy(Bitmap.Config.ARGB_8888, true)

    // Face detection
    if (detectionType == DetectionType.FACES || detectionType == DetectionType.ALL) {
        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val faceDetector = FaceDetection.getClient(faceOptions)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                result = result.copy(faces = faces.size)
                if (faces.isNotEmpty()) {
                    processedBitmap = drawEmojiOnFaces(context, processedBitmap!!, faces, R.drawable.smile)
                }
                checkCompletion()
            }
            .addOnFailureListener {
                checkCompletion()
            }
    }

    // Text/Document detection
    if (detectionType == DetectionType.DOCUMENTS || detectionType == DetectionType.ALL) {
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val textBlocks = visionText.textBlocks.filter { block ->
                    // Filter for document-like text (multiple lines, substantial content)
                    block.text.length > 10 && block.lines.size > 1
                }
                result = result.copy(documents = textBlocks.size)
                if (textBlocks.isNotEmpty()) {
                    processedBitmap = drawEmojiOnTextBlocks(context, processedBitmap!!, textBlocks)
                }
                checkCompletion()
            }
            .addOnFailureListener {
                checkCompletion()
            }
    }

    // Screen/Object detection (simplified - you may need to implement custom screen detection)
    if (detectionType == DetectionType.SCREENS || detectionType == DetectionType.ALL) {
        val objectOptions = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableClassification()
            .build()
        val objectDetector = ObjectDetection.getClient(objectOptions)

        objectDetector.process(image)
            .addOnSuccessListener { objects ->
                // Filter for screen-like objects (you can customize this logic)
                val screenLikeObjects = objects.filter { obj ->
                    obj.labels.any { label ->
                        label.text.contains("phone", ignoreCase = true) ||
                                label.text.contains("computer", ignoreCase = true) ||
                                label.text.contains("screen", ignoreCase = true) ||
                                label.text.contains("monitor", ignoreCase = true)
                    }
                }
                result = result.copy(screens = screenLikeObjects.size)
                if (screenLikeObjects.isNotEmpty()) {
                    processedBitmap = drawEmojiOnObjects(context, processedBitmap!!, screenLikeObjects)
                }
                checkCompletion()
            }
            .addOnFailureListener {
                checkCompletion()
            }
    }
}

fun drawEmojiOnFaces(
    context: Context,
    bitmap: Bitmap,
    faces: List<com.google.mlkit.vision.face.Face>,
    emojiResource: Int
): Bitmap {
    val canvas = Canvas(bitmap)
    val emojiBitmap = BitmapFactory.decodeResource(context.resources, emojiResource)

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
    return bitmap
}

fun drawEmojiOnTextBlocks(
    context: Context,
    bitmap: Bitmap,
    textBlocks: List<com.google.mlkit.vision.text.Text.TextBlock>
): Bitmap {
    val canvas = Canvas(bitmap)

    // Load your drawable resource as a bitmap
    val overlayBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.document)

    for (block in textBlocks) {
        val bounds = block.boundingBox
        bounds?.let {
            // Scale the overlay image to fit the entire bounding box
            val scaledOverlay = Bitmap.createScaledBitmap(
                overlayBitmap,
                it.width(),
                it.height(),
                true // Use bilinear filtering for better quality
            )

            // Draw the scaled image over the entire bounding box
            canvas.drawBitmap(
                scaledOverlay,
                it.left.toFloat(),
                it.top.toFloat(),
                null
            )
        }
    }
    return bitmap
}

fun drawEmojiOnObjects(
    context: Context,
    bitmap: Bitmap,
    objects: List<com.google.mlkit.vision.objects.DetectedObject>
): Bitmap {
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        color = AndroidColor.parseColor("#2196F3") // Blue color
        alpha = 180
    }

    for (obj in objects) {
        val bounds = obj.boundingBox
        // Draw semi-transparent overlay
        canvas.drawRect(bounds, paint)

        // Draw screen emoji in center
        val centerX = bounds.centerX() - 20
        val centerY = bounds.centerY() - 20

        val textPaint = Paint().apply {
            color = AndroidColor.WHITE
            textSize = 40f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("📱", centerX.toFloat(), centerY.toFloat(), textPaint)
    }
    return bitmap
}

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