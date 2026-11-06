package com.wenbo.flownote.data.backup

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份 zip 的流式读写。
 *
 * ZIP 内布局：
 * ```
 * backup.json
 * images/img_<uuid>.jpg
 * ```
 * 入口名白名单 + canonicalPath 前缀核对双保险，在创建任何文件之前拒绝 zip slip。
 */
object BackupArchiver {

    const val JSON_ENTRY = "backup.json"
    const val IMAGE_DIR = "images"

    private const val MAX_JSON_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    private const val BUFFER_SIZE = 8192

    /**
     * 写包：json 一次写入，图片逐个流式拷贝（不整载内存）。
     * 返回被跳过的图片名（文件缺失 / 名字非法 / 读取失败）。
     */
    fun write(target: File, json: String, images: Map<String, File>): Set<String> {
        val skipped = mutableSetOf<String>()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            json.toByteArray(Charsets.UTF_8).inputStream().use { it.copyTo(zip, BUFFER_SIZE) }
            zip.closeEntry()

            images.forEach { (name, file) ->
                if (!file.exists() || !file.isFile) {
                    skipped += name
                    return@forEach
                }
                try {
                    zip.putNextEntry(ZipEntry("$IMAGE_DIR/$name"))
                    file.inputStream().use { it.copyTo(zip, BUFFER_SIZE) }
                    zip.closeEntry()
                } catch (e: Exception) {
                    skipped += name
                }
            }
        }
        return skipped
    }

    sealed interface ExtractResult {
        /** [json] 为 backup.json 原文；[imageFiles] 为落盘图片（名 → 文件）。 */
        data class Success(val json: String, val imageFiles: Map<String, File>) : ExtractResult
        data class Failure(val reason: String) : ExtractResult
    }

    /**
     * 读包并全量解压到 [destDir]（先校验后恢复的前提）。
     * - 仅接受 `backup.json` 与 `images/<isValidImageName>`；形态危险的入口整体失败；
     * - 其他未知但安全的入口跳过（向前兼容）；目录条目跳过；
     * - backup.json 缺失、重复或超过大小上限 → Failure。
     */
    fun extract(archive: InputStream, destDir: File, isValidImageName: (String) -> Boolean): ExtractResult {
        destDir.mkdirs()
        var jsonText: String? = null
        val imageFiles = mutableMapOf<String, File>()
        var totalBytes = 0L

        ZipInputStream(archive.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name

                if (name == JSON_ENTRY) {
                    if (jsonText != null) {
                        return failure("备份文件结构异常（backup.json 重复）")
                    }
                    val bytes = readEntry(zip, MAX_JSON_BYTES)
                        ?: return failure("backup.json 过大，疑似无效备份")
                    totalBytes += bytes.size
                    jsonText = String(bytes, Charsets.UTF_8)
                    continue
                }

                if (name.startsWith("$IMAGE_DIR/")) {
                    val base = name.removePrefix("$IMAGE_DIR/").substringBefore('/')
                    if (name.endsWith("/") || base.isEmpty()) continue // 目录条目（images/）
                    if (containsUnsafeComponent(base)) {
                        return failure("备份文件存在路径穿越风险，已拒绝导入")
                    }
                    if (base != name.removePrefix("$IMAGE_DIR/")) continue // 子目录条目，向前兼容跳过
                    if (!isValidImageName(base)) {
                        return failure("备份内图片文件名异常（$base）")
                    }
                    if (!isSafePath(destDir, name)) {
                        return failure("备份文件存在路径穿越风险，已拒绝导入")
                    }
                    val out = File(destDir, name)
                    val written = writeEntry(zip, out, MAX_TOTAL_BYTES - totalBytes)
                        ?: return failure("备份文件过大，疑似 zip 炸弹")
                    totalBytes += written
                    imageFiles[base] = out
                    continue
                }

                // 未知但形态安全的入口（含 ".."、"\"、绝对路径以外）：向前兼容跳过
                if (containsUnsafeComponent(name)) {
                    return failure("备份文件存在路径穿越风险，已拒绝导入")
                }
            }
        }

        val json = jsonText ?: return failure("不是有效的 flowNote 备份文件（缺少 backup.json）")
        return ExtractResult.Success(json, imageFiles)
    }

    private fun failure(reason: String): ExtractResult = ExtractResult.Failure(reason)

    /** 单条目限量读取（json 整体入内存，有上限约束）。超限返回 null。 */
    private fun readEntry(input: InputStream, limit: Long): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > limit) return null
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }

    /** 条目落盘，剩余配额不足返回 null（防 zip 炸弹）。 */
    private fun writeEntry(input: InputStream, target: File, remainingQuota: Long): Long? {
        if (remainingQuota < 0) return null
        val parent = target.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        var total = 0L
        try {
            target.outputStream().buffered().use { output ->
                val chunk = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > remainingQuota) return null
                    output.write(chunk, 0, n)
                }
            }
        } catch (e: Exception) {
            target.delete()
            return null
        }
        return total
    }

    /** 入口名是否包含危险路径成分（..、\、绝对路径、NUL）。 */
    private fun containsUnsafeComponent(name: String): Boolean {
        if (name.contains('\u0000') || name.contains('\\')) return true
        if (name.startsWith("/")) return true
        if (File.separatorChar == '\\' && name.contains(':')) return true
        return name.split('/').any { it == ".." }
    }

    /** canonicalPath 前缀核对（第二道保险，与白名单规则配合）。 */
    private fun isSafePath(destDir: File, entryName: String): Boolean {
        val destCanonical = destDir.canonicalPath + File.separator
        val targetCanonical = File(destDir, entryName).canonicalPath
        return targetCanonical.startsWith(destCanonical)
    }
}
