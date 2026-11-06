package com.wenbo.flownote.data.backup

import com.wenbo.flownote.data.files.ImageStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ExportStage { READ_DB, WRITE_ARCHIVE, COPY_TO_DEST }
enum class ImportStage { EXTRACT, VALIDATE, COPY_IMAGES, RESTORE_DB, CLEANUP }

sealed interface ExportReport {
    data class Success(val memoCount: Int, val skippedImageCount: Int) : ExportReport
    data class Failure(val reason: String) : ExportReport
}

sealed interface ImportReport {
    data class Success(val memoCount: Int, val missingImageCount: Int) : ImportReport
    data class Failure(val reason: String) : ImportReport
}

/**
 * 备份编排器。不接触 Context/Uri —— SAF 流由调用方以 lambda 注入，整个类 JVM 可测。
 *
 * 导出：先在 workDir 组装完整 zip（写到一半不污染目的地），再流式拷贝到输出流。
 * 导入：全量解压校验通过后才触碰任何既有数据；崩溃安全顺序见计划文档
 * （解压 → 校验 → 拷图（短暂孤儿）→ 单事务重建 DB → purgeOrphans 清旧图）。
 */
class BackupManager(
    private val source: BackupSource,
    private val imageStore: ImageStore,
    private val workDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    init {
        workDir.mkdirs()
    }

    suspend fun exportTo(
        openOutput: () -> OutputStream?,
        onStage: suspend (ExportStage) -> Unit = {},
    ): ExportReport = withContext(io) {
        var tempZip: File? = null
        try {
            onStage(ExportStage.READ_DB)
            val entities = source.readAll()
            val exportedAt = System.currentTimeMillis()
            val json = BackupCodec.encode(BackupCodec.fromEntities(entities, exportedAt))

            onStage(ExportStage.WRITE_ARCHIVE)
            tempZip = File(workDir, "export-${System.currentTimeMillis()}.zip")
            val referenced = entities.flatMapTo(mutableSetOf()) { it.images }
            val imageMap = referenced
                .filter { imageStore.isValidImageName(it) }
                .associateWith { imageStore.fileFor(it) }
            val skipped = BackupArchiver.write(tempZip, json, imageMap)

            onStage(ExportStage.COPY_TO_DEST)
            val output = openOutput() ?: return@withContext ExportReport.Failure("无法写入目标文件")
            output.use { out ->
                tempZip.inputStream().buffered().use { input -> input.copyTo(out, 8192) }
            }
            ExportReport.Success(
                memoCount = entities.size,
                skippedImageCount = skipped.size,
            )
        } catch (e: BackupFormatException) {
            ExportReport.Failure(e.message ?: "备份文件格式错误")
        } catch (e: Exception) {
            ExportReport.Failure("导出失败：${e.message ?: "未知错误"}")
        } finally {
            tempZip?.delete()
        }
    }

    suspend fun importFrom(
        openInput: () -> InputStream?,
        onStage: suspend (ImportStage) -> Unit = {},
    ): ImportReport = withContext(io) {
        var tempDir: File? = null
        try {
            // 1. 预清理上次进程死亡可能残留的临时目录
            workDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("restore-") }
                ?.forEach { it.deleteRecursively() }

            // 2. 全量解压（此阶段失败 = 什么都没碰）
            onStage(ImportStage.EXTRACT)
            val input = openInput() ?: return@withContext ImportReport.Failure("无法读取所选文件")
            val dir = File(workDir, "restore-${System.currentTimeMillis()}")
            tempDir = dir
            val extracted = BackupArchiver.extract(input, dir, imageStore::isValidImageName)
            val (jsonText, extractedImages) = when (extracted) {
                is BackupArchiver.ExtractResult.Success -> extracted.json to extracted.imageFiles
                is BackupArchiver.ExtractResult.Failure ->
                    return@withContext ImportReport.Failure(extracted.reason)
            }

            // 3. 解析与版本校验
            onStage(ImportStage.VALIDATE)
            val backup = try {
                BackupCodec.decode(jsonText)
            } catch (e: BackupFormatException) {
                return@withContext ImportReport.Failure(e.message ?: "不是有效的 flowNote 备份文件")
            }
            if (backup.version > BackupCodec.CURRENT_VERSION) {
                return@withContext ImportReport.Failure("备份版本过新，请先升级 App 再导入")
            }
            val entities = BackupCodec.toEntities(backup)

            // 4. 缺图容错：只保留实际解压到的图片（降级救回内容，报告缺失数）
            var missingImageCount = 0
            val available = extractedImages.keys
            val restoredEntities = entities.map { entity ->
                val present = entity.images.filter { it in available }
                missingImageCount += entity.images.size - present.size
                if (present.size == entity.images.size) entity else entity.copy(images = present)
            }

            // 5. 拷贝图片到 images 目录（此时新图全是孤儿；任一失败则 DB 未动）
            onStage(ImportStage.COPY_IMAGES)
            val wanted = restoredEntities.flatMapTo(mutableSetOf()) { it.images }
            val placed = imageStore.importAll(File(dir, BackupArchiver.IMAGE_DIR), wanted)
            if (placed == null || placed.size != wanted.size) {
                return@withContext ImportReport.Failure("恢复图片失败，已中止导入（现有数据未受影响）")
            }

            // 6. 单事务重建 DB（失败自动回滚，旧库原封不动）
            onStage(ImportStage.RESTORE_DB)
            source.restoreAll(restoredEntities)

            // 7. 清理被替换下来的旧图
            val referencedAfter = restoredEntities.flatMapTo(mutableSetOf()) { it.images }
            imageStore.purgeOrphans(referencedAfter)

            onStage(ImportStage.CLEANUP)
            ImportReport.Success(
                memoCount = restoredEntities.size,
                missingImageCount = missingImageCount,
            )
        } catch (e: Exception) {
            ImportReport.Failure("导入失败：${e.message ?: "未知错误"}")
        } finally {
            tempDir?.deleteRecursively()
        }
    }
}
