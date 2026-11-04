package com.antigravity.appsentinel.ui.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antigravity.appsentinel.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object IconCache {
    // 200 items capped at scaled size (96x96 is only ~36KB per icon vs 1+ MB unscaled)
    private val cache = LruCache<String, ImageBitmap>(200)
    private val failedPackages = ConcurrentHashMap.newKeySet<String>()

    fun get(packageName: String): ImageBitmap? = cache[packageName]

    fun isFailed(packageName: String): Boolean = failedPackages.contains(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }

    fun markFailed(packageName: String) {
        failedPackages.add(packageName)
    }
}

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    var iconBitmap by remember(packageName) { mutableStateOf(IconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (iconBitmap == null && !IconCache.isFailed(packageName)) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val drawable = try {
                        pm.getApplicationIcon(packageName)
                    } catch (e: Exception) {
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                            pm.getApplicationIcon(appInfo)
                        } catch (_: Exception) {
                            null
                        }
                    } ?: throw IllegalArgumentException("Icon not found")

                    // Render at target device resolution rather than huge intrinsic dimensions (432x432+)
                    val density = context.resources.displayMetrics.density
                    val targetPx = (size.value * density).toInt().coerceIn(72, 144)
                    val bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, targetPx, targetPx)
                    drawable.draw(canvas)
                    val imgBitmap = bitmap.asImageBitmap()
                    IconCache.put(packageName, imgBitmap)
                    imgBitmap
                } catch (e: Throwable) {
                    IconCache.markFailed(packageName)
                    null
                }
            }
            if (loaded != null) {
                iconBitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}
