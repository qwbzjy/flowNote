package com.wenbo.flownote.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 图片导入与生命周期：
 * - Photo Picker 授予的是临时读权限，回调后必须立即复制到 app 私有目录；
 * - 导入即压缩（最长边 ≤ [MAX_DIMENSION]，JPEG 85），统一 JPEG 顺带规避 HEIC 兼容；
 * - 存相对文件名，Coil 加载时用 [fileFor] 拼回绝对路径；
 * - 孤儿清理：App 启动时删除数据库中已无引用的文件。
 *
 * 测试可经 [ImageStore] 的 File 构造器注入临时目录（此时 [importFrom] 不可用）。
 */
class ImageStore private constructor(
    private val imagesDir: File,
    private val io: CoroutineDispatcher,
    private val context: Context?,
) {

    /** 生产构造：context 提供 contentResolver（[importFrom] 需要）。 */
    constructor(context: Context, io: CoroutineDispatcher = Dispatchers.IO) : this(
        imagesDir = File(context.applicationContext.filesDir, "images").apply { mkdirs() },
        io = io,
        context = context.applicationContext,
    )

    /** 测试构造：纯文件目录，无 Android 依赖。 */
    constructor(imagesDir: File, io: CoroutineDispatcher) : this(
        imagesDir = imagesDir.apply { mkdirs() },
        io = io,
        context = null,
    )

    /** 导入成功返回生成的文件名，失败（无法解码等）返回 null。 */
    suspend fun importFrom(uri: Uri): String? = withContext(io) {
        val resolver = context?.contentResolver ?: return@withContext null

        // 1. 量尺寸（inJustDecodeBounds 时 decodeStream 恒返回 null，只能以流是否打开判断成败）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return@withContext null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // 2. 采样解码
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        // 3. EXIF 方向校正（相机照片普遍依赖 EXIF，直接解码会横竖颠倒）
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching { ExifInterface(stream).rotationDegrees }.getOrDefault(0)
        } ?: 0
        val rotated = if (orientation != 0) {
            val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        // 4. 精确缩放到上限内
        val longest = maxOf(rotated.width, rotated.height)
        val scaled = if (longest > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / longest
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * scale).toInt().coerceAtLeast(1),
                (rotated.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            rotated
        }

        // 5. 写入 JPEG
        val name = "img_${UUID.randomUUID()}.jpg"
        try {
            FileOutputStream(File(imagesDir, name)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } catch (e: IOException) {
            scaled.recycle()
            if (rotated !== decoded) decoded.recycle()
            return@withContext null
        }
        if (scaled !== decoded && scaled !== rotated) scaled.recycle()
        if (rotated !== decoded) decoded.recycle()
        name
    }

    /**
     * 备份恢复：把 [fromDir] 中 [names] 指定的图片覆盖拷贝到 images 目录。
     * 逐个校验文件名格式；任一失败则整体中止（返回 null），调用方保证此时 DB 未被触碰。
     * 同名覆盖安全：文件名按内容生成（UUID），同名 ⟹ 同源同字节。
     */
    suspend fun importAll(fromDir: File, names: Set<String>): Set<String>? = withContext(io) {
        val placed = mutableSetOf<String>()
        for (name in names) {
            if (!isValidImageName(name)) return@withContext null
            val src = File(fromDir, name)
            val dst = File(imagesDir, name)
            try {
                src.inputStream().use { input ->
                    FileOutputStream(dst).use { output -> input.copyTo(output, 8192) }
                }
            } catch (e: IOException) {
                return@withContext null
            }
            placed += name
        }
        placed
    }

    suspend fun deleteAll(names: List<String>) = withContext(io) {
        names.forEach { name ->
            if (isValidImageName(name)) File(imagesDir, name).delete()
        }
    }

    suspend fun purgeOrphans(referenced: Set<String>) = withContext(io) {
        imagesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in referenced) file.delete()
        }
    }

    fun fileFor(name: String): File = File(imagesDir, name)

    /** 图片文件名的唯一权威校验：形如 img_<uuid>.jpg，天然不含 / .. \ 等路径成分。 */
    fun isValidImageName(name: String): Boolean =
        name.matches(Regex("""img_[0-9a-fA-F-]{36}\.jpg"""))

    private companion object {
        const val MAX_DIMENSION = 2048
        const val JPEG_QUALITY = 85
    }
}
