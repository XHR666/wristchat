package io.github.xhr666.wristchat.ui.screens

import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.wristchat.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 相册条目:来自系统媒体库(uri)或 App 目录(file) */
data class GalleryItem(val uri: Uri?, val file: File?, val date: Long, val name: String)

/**
 * 内置相册(不调用系统选择器):
 * - 数据源:① 系统媒体库图片(截图/照片) ② App 目录 filesDir/images
 * - 打开即刷新,并且每 2 秒自动刷新一次(实时);
 * - 3 列网格,点一下即选中
 */
@Composable
fun GalleryScreen(onPick: (Uri?, File?) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalWrist.current
    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var nonce by remember { mutableStateOf(0) }
    var sizeInfo by remember { mutableStateOf("") }
    var needPerm by remember { mutableStateOf(false) }
    // 读系统媒体库需要权限:Android 13+ 是 READ_MEDIA_IMAGES,12 及以下是 READ_EXTERNAL_STORAGE
    val permName = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES"
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(ctx.checkSelfPermission(permName) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        needPerm = !ok
        nonce++
    }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(permName) }

    suspend fun scan(): List<GalleryItem> = withContext(Dispatchers.IO) {
        val out = mutableListOf<GalleryItem>()
        // ① App 目录
        val dir = File(ctx.filesDir, "images").apply { mkdirs() }
        dir.listFiles()?.filter { it.isFile && it.name.matches(Regex("(?i).*\\.(jpg|jpeg|png|webp|gif)$")) }
            ?.forEach { out.add(GalleryItem(null, it, it.lastModified(), it.name)) }
        // ② 系统媒体库(无权限时只能看到 App 自己的目录)
        if (granted) runCatching {
            val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED)
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cur ->
                val idCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                var n = 0
                while (cur.moveToNext() && n < 300) {
                    val id = cur.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    out.add(GalleryItem(uri, null, cur.getLong(dateCol) * 1000L, cur.getString(nameCol) ?: ""))
                    n++
                }
            }
        }
        out.sortedByDescending { it.date }
    }

    LaunchedEffect(Unit) { items = scan(); loading = false; sizeInfo = "${items.size} 张" }
    // 实时刷新:每 2 秒重扫一次(相册页停留期间)
    LaunchedEffect(nonce) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            val fresh = scan()
            if (fresh.size != items.size || fresh.firstOrNull()?.date != items.firstOrNull()?.date) {
                items = fresh
                sizeInfo = "${fresh.size} 张"
            }
        }
    }

    SwipeBackContainer(onBack = onBack) {
        ScreenScaffold(
            title = "相册" + if (sizeInfo.isNotEmpty()) " · $sizeInfo" else "",
            actions = { SmallAction("⟳") { nonce++ } },
            onHeaderSwipeBack = onBack,
        ) {
            if (needPerm && items.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("需要「读取照片」权限才能显示手表里的截图/照片", color = c.hint, fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    SmallAction("去授权") { permLauncher.launch(permName) }
                }
            } else if (loading) {
                Text("扫描中…", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            } else if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到图片\n(手表截图/照片或应用目录里的图片)", color = c.hint, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = LocalRoundBottom.current),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items) { it ->
                        GalleryCell(it, c) { onPick(it.uri, it.file) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryCell(item: GalleryItem, c: WristColors, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, item.uri, item.file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (item.uri != null) {
                    if (android.os.Build.VERSION.SDK_INT >= 29)
                        ctx.contentResolver.loadThumbnail(item.uri, android.util.Size(160, 160), null)
                    else null
                } else if (item.file != null) {
                    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeFile(item.file.absolutePath, o)
                } else null
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = item.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("…", color = c.hint, fontSize = 12.sp)
        }
    }
}
