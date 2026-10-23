package com.karthi.expense

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * The picture behind the cards: the phone's wallpaper or a chosen photo, shrunk and blurred once
 * and kept as a small file, so later starts just load it.
 */
object Backdrop {
    enum class Mode { PLAIN, WALLPAPER, PHOTO }

    private lateinit var prefs: SharedPreferences
    private lateinit var file: File

    var mode by mutableStateOf(Mode.PLAIN)
        private set

    /** The blurred picture, or null for the plain grey background. */
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("backdrop", Context.MODE_PRIVATE)
        file = File(context.filesDir, "backdrop.png")
        mode = prefs.getString(KEY_MODE, null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.PLAIN
    }

    /** Shows the saved picture, then re-reads the wallpaper in case it was changed since. */
    suspend fun load(context: Context) {
        if (mode == Mode.PLAIN) return
        if (image == null) image = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }?.asImageBitmap()
        if (mode == Mode.WALLPAPER && canReadWallpaper(context)) {
            withContext(Dispatchers.IO) { readWallpaper(context)?.let(::soften)?.also(::save) }?.let { image = it.asImageBitmap() }
        }
    }

    /**
     * Android hands out the real wallpaper only to apps allowed to read storage: "All files access"
     * from Android 13, plain storage permission before that. Without it the stock wallpaper comes back.
     */
    fun canReadWallpaper(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() -> true
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> false
        else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** False when the wallpaper can't be read, e.g. a live wallpaper. */
    suspend fun useWallpaper(context: Context): Boolean = use(Mode.WALLPAPER) { readWallpaper(context) }

    suspend fun usePhoto(context: Context, uri: Uri): Boolean = use(Mode.PHOTO) { readPhoto(context, uri) }

    fun usePlain() {
        mode = Mode.PLAIN
        image = null
        prefs.edit().putString(KEY_MODE, Mode.PLAIN.name).apply()
        file.delete()
    }

    private suspend fun use(newMode: Mode, read: () -> Bitmap?): Boolean {
        val soft = withContext(Dispatchers.IO) { runCatching { read() }.getOrNull()?.let(::soften)?.also(::save) } ?: return false
        mode = newMode
        image = soft.asImageBitmap()
        prefs.edit().putString(KEY_MODE, newMode.name).apply()
        return true
    }

    private fun save(bitmap: Bitmap) {
        runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun readWallpaper(context: Context): Bitmap? = runCatching {
        val drawable = WallpaperManager.getInstance(context).drawable ?: return null
        if (drawable is BitmapDrawable) return drawable.bitmap?.software()
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
        val scale = SMALL_WIDTH * 4f / w
        Bitmap.createBitmap((w * scale).roundToInt(), (h * scale).roundToInt(), Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, it.width, it.height)
            drawable.draw(Canvas(it))
        }
    }.getOrNull()

    private fun readPhoto(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        // Only a small copy is needed, so skip decoding the full-size photo.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SMALL_WIDTH * 4) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun Bitmap.software(): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

    /** Shrinks to a thumbnail and box-blurs it three times, which looks like a smooth gaussian blur once stretched. */
    private fun soften(src: Bitmap): Bitmap {
        val w = SMALL_WIDTH
        val h = (src.height * w.toFloat() / src.width).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src.software(), w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxBlur(px, tmp, w, h, horizontal = true)
            boxBlur(tmp, px, w, h, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, horizontal: Boolean) {
        val r = BLUR_RADIUS
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int) = src[if (horizontal) line * w + i.coerceIn(0, len - 1) else i.coerceIn(0, len - 1) * w + line]
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val c = at(i)
                rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF
            }
            for (i in 0 until len) {
                dst[if (horizontal) line * w + i else i * w + line] =
                    (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = at(i - r)
                val inc = at(i + r + 1)
                rs += ((inc shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                gs += ((inc shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                bs += (inc and 0xFF) - (out and 0xFF)
            }
        }
    }

    private const val KEY_MODE = "mode"
    private const val SMALL_WIDTH = 120
    private const val BLUR_RADIUS = 4
}

/** The blurred picture, washed with the background colour so cards and text stay easy to read. */
@Composable
fun BackdropLayer(modifier: Modifier = Modifier) {
    val image = Backdrop.image
    val shown by animateFloatAsState(if (image != null) 1f else 0f, fluidSpring(), label = "backdrop")
    if (image == null && shown == 0f) return
    Box(modifier.fillMaxSize().alpha(shown)) {
        image?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val wash = if (isSystemInDarkTheme()) 0.45f else 0.35f
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = wash)))
    }
}
