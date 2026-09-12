package io.github.xhr666.wristchat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 图片附件存储:filesDir/attachments/。
 * 官方视觉模型(deepseek-flash / V4.1-Flash)要求 image_url 走 data:base64 内联,
 * 服务端会把大图自动缩到 ~800×800(每张 ≤384 tokens),所以本地先缩到 1280 内即可,减小请求体。
 */
object Attachments {
    const val MAX_SIDE = 1280      // 上传最长边(px)
    const val JPEG_Q = 86
    const val THUMB_PX = 260       // 列表缩略图最长边(px)

    fun dir(ctx: Context): File = File(ctx.filesDir, "attachments").apply { mkdirs() }

    fun file(ctx: Context, name: String): File = File(dir(ctx), name)

    /** 从 content uri 导入:读原图 → 等比缩放 → 透明铺白 → JPEG;返回文件名,失败 null */
    fun importFromUri(ctx: Context, uri: Uri, sessionId: String): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 采样:解码后最长边不超过 2×MAX_SIDE,避免手表内存爆掉
            var sample = 1
            val targetDecode = MAX_SIDE * 2
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > targetDecode && sample < 32) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val full = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val scaled = if (full.width > MAX_SIDE || full.height > MAX_SIDE) {
                val sc = MAX_SIDE.toFloat() / maxOf(full.width, full.height)
                val s = Bitmap.createScaledBitmap(full, (full.width * sc).toInt().coerceAtLeast(1), (full.height * sc).toInt().coerceAtLeast(1), true)
                full.recycle()
                s
            } else full

            // 透明像素铺白底(截图常见),统一 JPEG
            val out = ByteArrayOutputStream()
            val flat = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
            flat.compress(Bitmap.CompressFormat.JPEG, JPEG_Q, out)
            scaled.recycle()
            flat.recycle()

            val name = "${sessionId}_${System.currentTimeMillis()}.jpg"
            File(dir(ctx), name).writeBytes(out.toByteArray())
            name
        } catch (e: Exception) { null }
    }

    /** 解码缩略图(等比,最长边 THUMB_PX);文件不存在/失败返回 null */
    fun loadThumb(ctx: Context, name: String): Bitmap? {
        return try {
            val f = file(ctx, name)
            if (!f.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= THUMB_PX * 2 && bounds.outHeight / (sample * 2) >= THUMB_PX * 2 && sample < 8) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var b = BitmapFactory.decodeFile(f.absolutePath, opts) ?: return null
            if (b.width > THUMB_PX || b.height > THUMB_PX) {
                val sc = THUMB_PX.toFloat() / maxOf(b.width, b.height)
                val s = Bitmap.createScaledBitmap(b, (b.width * sc).toInt().coerceAtLeast(1), (b.height * sc).toInt().coerceAtLeast(1), true)
                if (s !== b) b.recycle()
                b = s
            }
            b
        } catch (e: Exception) { null }
    }

    /** 读取整图 → base64(data URL 用) */
    fun readBase64(ctx: Context, name: String): String? {
        return try {
            val f = file(ctx, name)
            if (!f.exists()) return null
            android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    /** 删除某会话的全部附件文件 */
    fun deleteForSession(ctx: Context, sessionId: String) {
        val prefix = "${sessionId}_"
        dir(ctx).listFiles()?.filter { it.isFile && it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    /** 清空全部附件 */
    fun clearAll(ctx: Context) {
        try { dir(ctx).listFiles()?.forEach { it.delete() } } catch (e: Exception) {}
    }
}
