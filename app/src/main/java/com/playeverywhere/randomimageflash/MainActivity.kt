package com.playeverywhere.randomimageflash

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class DisplayMode { GENERATED, PHOTOS }

class MainActivity : ComponentActivity() {
    private var galleryUris by mutableStateOf<List<Uri>>(emptyList())
    private var galleryPermissionGranted by mutableStateOf(false)

    private val galleryPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        galleryPermissionGranted = granted
        if (granted) refreshGallery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        galleryPermissionGranted = hasGalleryPermission()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FlashScreen(
                    photos = galleryUris,
                    hasPermission = galleryPermissionGranted,
                    onRequestPermission = { permissionRequest.launch(galleryPermission) },
                    onRefreshGallery = { refreshGallery() },
                    loadBitmap = { uri ->
                        try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }
                        catch (_: Exception) { null }
                    }
                )
            }
        }
        if (galleryPermissionGranted) refreshGallery()
        else permissionRequest.launch(galleryPermission)
    }

    override fun onResume() {
        super.onResume()
        if (hasGalleryPermission()) {
            galleryPermissionGranted = true
            refreshGallery()
        }
    }

    private fun hasGalleryPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, galleryPermission) == PackageManager.PERMISSION_GRANTED

    private fun refreshGallery() {
        if (!hasGalleryPermission()) {
            galleryPermissionGranted = false
            return
        }
        val found = mutableListOf<Uri>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        try {
            contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor: Cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    found += Uri.withAppendedPath(collection, id.toString())
                }
            }
            galleryUris = found
            galleryPermissionGranted = true
        } catch (_: SecurityException) {
            galleryPermissionGranted = false
        } catch (_: Exception) {
            galleryUris = emptyList()
        }
    }
}

@Composable
private fun FlashScreen(
    photos: List<Uri>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefreshGallery: () -> Unit,
    loadBitmap: (Uri) -> Bitmap?
) {
    var mode by remember { mutableStateOf(DisplayMode.GENERATED) }
    var rate by remember { mutableFloatStateOf(5f) }
    var running by remember { mutableStateOf(true) }
    var shuffle by remember { mutableStateOf(true) }
    var fit by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(Random.nextInt()) }

    LaunchedEffect(running, rate) {
        while (running) {
            delay((1000f / rate).toLong().coerceAtLeast(1L))
            frame++
            seed = Random.nextInt()
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF101116)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("RANDOM IMAGE FLASH", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == DisplayMode.GENERATED, onClick = { mode = DisplayMode.GENERATED }, label = { Text("Случайные") })
            FilterChip(selected = mode == DisplayMode.PHOTOS, onClick = { mode = DisplayMode.PHOTOS }, label = { Text("Мои фото") })
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            if (mode == DisplayMode.GENERATED) {
                GeneratedArt(seed = seed, modifier = Modifier.fillMaxSize())
            } else {
                val ordered = remember(photos, shuffle) { if (shuffle) photos.shuffled() else photos }
                val uri = if (ordered.isNotEmpty()) ordered[Math.floorMod(frame, ordered.size)] else null
                val bitmap = remember(uri) { uri?.let(loadBitmap) }
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = "Photo", modifier = Modifier.fillMaxSize(),
                        contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                !hasPermission -> "Нужен доступ к фотографиям"
                                photos.isEmpty() -> "В галерее пока нет фотографий"
                                else -> "Не удалось открыть фото"
                            },
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = if (hasPermission) onRefreshGallery else onRequestPermission) {
                            Text(if (hasPermission) "Обновить галерею" else "Разрешить доступ")
                        }
                    }
                }
            }
            Text("${"%.1f".format(java.util.Locale.US, rate)} Гц",
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(8.dp),
                color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text("Частота: ${rate.toInt()} Гц  ·  интервал ~${(1000 / rate).toInt()} мс", color = Color.White)
        Slider(value = rate, onValueChange = { rate = it }, valueRange = 1f..20f, steps = 18, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { running = !running }, modifier = Modifier.weight(1f)) { Text(if (running) "Пауза" else "Старт") }
            OutlinedButton(onClick = { seed = Random.nextInt(); frame++ }, modifier = Modifier.weight(1f)) { Text("Следующее") }
        }
        if (mode == DisplayMode.PHOTOS) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Фото в галерее: ${photos.size}", color = Color.White)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = if (hasPermission) onRefreshGallery else onRequestPermission) {
                    Text(if (hasPermission) "Обновить" else "Разрешить")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = shuffle, onCheckedChange = { shuffle = it })
                Text("Случайный порядок", color = Color.White)
                Spacer(Modifier.weight(1f))
                Checkbox(checked = fit, onCheckedChange = { fit = it })
                Text("Вписать", color = Color.White)
            }
        }
        Text("Новые фото добавляются автоматически при возвращении в приложение", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun GeneratedArt(seed: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val rng = Random(seed)
        drawRect(Color(rng.nextFloat() * 0.18f, rng.nextFloat() * 0.18f, rng.nextFloat() * 0.18f))
        repeat(14) {
            val color = Color(rng.nextInt(60, 256), rng.nextInt(60, 256), rng.nextInt(60, 256), 230)
            val x = rng.nextFloat() * size.width
            val y = rng.nextFloat() * size.height
            val w = rng.nextFloat() * size.width * 0.55f + 12f
            val h = rng.nextFloat() * size.height * 0.45f + 12f
            when (rng.nextInt(3)) {
                0 -> drawCircle(color, radius = minOf(w, h) / 2f, center = Offset(x, y))
                1 -> drawRect(color, topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h))
                else -> drawLine(color, Offset(x, y), Offset(rng.nextFloat() * size.width, rng.nextFloat() * size.height), strokeWidth = rng.nextFloat() * 18f + 2f)
            }
        }
    }
}
