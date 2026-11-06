package com.wenbo.flownote.data.backup

import com.wenbo.flownote.data.db.entity.MemoEntity
import com.wenbo.flownote.data.files.ImageStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val imgA = "img_00000000-0000-0000-0000-00000000000a.jpg"
    private val imgB = "img_00000000-0000-0000-0000-00000000000b.jpg"

    private class FakeSource(var data: List<MemoEntity>) : BackupSource {
        var failRestore = false
        var restoreCalled = false

        override suspend fun readAll(): List<MemoEntity> = data

        override suspend fun restoreAll(memos: List<MemoEntity>) {
            check(!failRestore) { "restore failed" }
            restoreCalled = true
            data = memos
        }
    }

    private fun newImage(dir: File, name: String, seed: Int): File =
        dir.resolve(name).apply { writeBytes(ByteArray(2048).also { Random(seed.toLong()).nextBytes(it) }) }

    private fun listTempDirs(workDir: File): List<File> =
        workDir.listFiles { f -> f.isDirectory && (f.name.startsWith("restore-") || f.name.startsWith("export-")) }
            ?.toList() ?: emptyList()

    @Test
    fun `导出包含 json 与图片且报告计数`() = runBlocking {
        val imagesDir = tmp.newFolder("images")
        val imageStore = ImageStore(imagesDir, Dispatchers.Unconfined)
        val old1 = newImage(imagesDir, imgA, 1)
        val source = FakeSource(
            listOf(
                MemoEntity(id = 1, content = "第一条 #标签", images = listOf(imgA), createdAt = 10, updatedAt = 10),
                MemoEntity(id = 2, content = "第二条", images = emptyList(), createdAt = 20, updatedAt = 20),
            ),
        )
        val workDir = tmp.newFolder("work")
        val manager = BackupManager(source, imageStore, workDir, Dispatchers.Unconfined)
        val stages = mutableListOf<ExportStage>()

        val output = ByteArrayOutputStream()
        val report = manager.exportTo(
            openOutput = { output },
            onStage = { stages += it },
        )

        assertTrue(report is ExportReport.Success)
        report as ExportReport.Success
        assertEquals(2, report.memoCount)
        assertEquals(0, report.skippedImageCount)
        assertEquals(listOf(ExportStage.READ_DB, ExportStage.WRITE_ARCHIVE, ExportStage.COPY_TO_DEST), stages)

        // 解包验证
        val extracted = BackupArchiver.extract(ByteArrayInputStream(output.toByteArray()), tmp.newFolder("check")) { true }
        assertTrue(extracted is BackupArchiver.ExtractResult.Success)
        extracted as BackupArchiver.ExtractResult.Success
        val backup = BackupCodec.decode(extracted.json)
        assertEquals(2, backup.memos.size)
        assertEquals("第一条 #标签", backup.memos[0].content)
        assertArrayEquals(old1.readBytes(), extracted.imageFiles[imgA]!!.readBytes())

        // workDir 临时 zip 应被清理
        assertTrue(listTempDirs(workDir).isEmpty() && workDir.listFiles { f -> f.isFile }!!.isEmpty())
    }

    @Test
    fun `导出时磁盘缺失的图片计入 skipped`() = runBlocking {
        val imagesDir = tmp.newFolder("images2")
        val imageStore = ImageStore(imagesDir, Dispatchers.Unconfined)
        val source = FakeSource(
            listOf(
                // 磁盘上不存在
                MemoEntity(id = 1, content = "a", images = listOf(imgA), createdAt = 1, updatedAt = 1),
            ),
        )
        val manager = BackupManager(source, imageStore, tmp.newFolder("work3"), Dispatchers.Unconfined)
        val report = manager.exportTo(openOutput = { ByteArrayOutputStream() })
        assertTrue(report is ExportReport.Success)
        assertEquals(1, (report as ExportReport.Success).skippedImageCount)
    }

    @Test
    fun `openOutput 为 null 返回失败`() = runBlocking {
        val imagesDir = tmp.newFolder("images3")
        val source = FakeSource(emptyList())
        val manager = BackupManager(source, ImageStore(imagesDir, Dispatchers.Unconfined), tmp.newFolder("work4"), Dispatchers.Unconfined)
        val report = manager.exportTo(openOutput = { null })
        assertTrue(report is ExportReport.Failure)
    }

    @Test
    fun `导入覆盖恢复且 id 原样保留 旧图被清理`() = runBlocking {
        val imagesDir = tmp.newFolder("images4")
        val imageStore = ImageStore(imagesDir, Dispatchers.Unconfined)
        val oldImage = newImage(imagesDir, imgB, 2)
        val source = FakeSource(
            listOf(
                MemoEntity(id = 99, content = "旧笔记 #old", images = listOf(imgB), createdAt = 1, updatedAt = 1),
            ),
        )
        val workDir = tmp.newFolder("work5")
        val manager = BackupManager(source, imageStore, workDir, Dispatchers.Unconfined)

        // 组装备份：一条带新图 imgA 的笔记
        val backupImagesDir = tmp.newFolder("backupimgs")
        val newImage = newImage(backupImagesDir, imgA, 3)
        val zip = tmp.newFile("backup.zip")
        val json = BackupCodec.encode(
            BackupFile(
                version = 1,
                exportedAt = 100,
                memos = listOf(
                    BackupMemo(id = 7, content = "恢复的笔记 #新标签", images = listOf(imgA), createdAt = 5, updatedAt = 6),
                ),
            ),
        )
        BackupArchiver.write(zip, json, mapOf(imgA to newImage))

        val report = manager.importFrom(openInput = { zip.inputStream() })

        assertTrue(report is ImportReport.Success)
        report as ImportReport.Success
        assertEquals(1, report.memoCount)
        assertEquals(0, report.missingImageCount)
        assertTrue(source.restoreCalled)

        // id 原样保留
        assertEquals(1, source.data.size)
        assertEquals(7L, source.data[0].id)
        assertEquals(listOf(imgA), source.data[0].images)

        // 新图就位、旧图被 purge、临时目录清理
        assertTrue(imageStore.fileFor(imgA).exists())
        assertFalse("旧图应被清理", oldImage.exists())
        assertTrue("临时目录应清理", listTempDirs(workDir).isEmpty())
    }

    @Test
    fun `备份缺图时降级保留笔记并报告缺失`() = runBlocking {
        val imagesDir = tmp.newFolder("images5")
        val imageStore = ImageStore(imagesDir, Dispatchers.Unconfined)
        val source = FakeSource(emptyList())
        val manager = BackupManager(source, imageStore, tmp.newFolder("work7"), Dispatchers.Unconfined)

        // zip 里只有 json，图片缺失
        val zip = tmp.newFile("bad.zip")
        val json = BackupCodec.encode(
            BackupFile(
                version = 1,
                exportedAt = 1,
                memos = listOf(BackupMemo(id = 1, content = "a", images = listOf(imgA), createdAt = 1, updatedAt = 1)),
            ),
        )
        BackupArchiver.write(zip, json, emptyMap())

        val report = manager.importFrom(openInput = { zip.inputStream() })
        assertTrue(report is ImportReport.Success)
        report as ImportReport.Success
        assertEquals(1, report.missingImageCount)
        assertEquals(emptyList<String>(), source.data[0].images)
    }

    @Test
    fun `版本过新拒绝且旧数据未动`() = runBlocking {
        val imagesDir = tmp.newFolder("images6")
        val imageStore = ImageStore(imagesDir, Dispatchers.Unconfined)
        val oldImage = newImage(imagesDir, imgB, 4)
        val old = listOf(MemoEntity(id = 1, content = "old", images = listOf(imgB), createdAt = 1, updatedAt = 1))
        val source = FakeSource(old)
        val manager = BackupManager(source, imageStore, tmp.newFolder("work8"), Dispatchers.Unconfined)

        val zip = tmp.newFile("future.zip")
        val json = """{"version":99,"exportedAt":1,"memos":[{"id":1,"content":"x","createdAt":1,"updatedAt":1}]}"""
        BackupArchiver.write(zip, json, emptyMap())

        val report = manager.importFrom(openInput = { zip.inputStream() })
        assertTrue(report is ImportReport.Failure)
        assertEquals(old, source.data)
        assertFalse(source.restoreCalled)
        assertTrue("旧图应完好", oldImage.exists())
    }

    @Test
    fun `restoreAll 失败时旧数据仍在 新图成为孤儿`() = runBlocking {
        val imagesDir = tmp.newFolder("images7")
        val imageStore = ImageStore(imagesDir, Dispatchers.Unconfined)
        val oldImage = newImage(imagesDir, imgB, 5)
        val old = listOf(MemoEntity(id = 1, content = "old", images = listOf(imgB), createdAt = 1, updatedAt = 1))
        val source = FakeSource(old).apply { failRestore = true }
        val manager = BackupManager(source, imageStore, tmp.newFolder("work9"), Dispatchers.Unconfined)

        val backupImagesDir = tmp.newFolder("backupimgs2")
        val zip = tmp.newFile("b2.zip")
        val json = BackupCodec.encode(
            BackupFile(
                version = 1,
                exportedAt = 1,
                memos = listOf(BackupMemo(id = 7, content = "x", images = listOf(imgA), createdAt = 1, updatedAt = 1)),
            ),
        )
        BackupArchiver.write(zip, json, mapOf(imgA to newImage(backupImagesDir, imgA, 6)))

        val report = manager.importFrom(openInput = { zip.inputStream() })
        assertTrue(report is ImportReport.Failure)
        // 旧数据原封不动（fake 模拟回滚语义）
        assertEquals(old, source.data)
        assertTrue("旧图应完好", oldImage.exists())
        assertTrue("新图作为孤儿留在 images 目录，交由启动清理", imageStore.fileFor(imgA).exists())
    }

    @Test
    fun `空备份导入清空全部数据`() = runBlocking {
        val imagesDir = tmp.newFolder("images8")
        val imageStore = ImageStore(imagesDir, Dispatchers.Unconfined)
        newImage(imagesDir, imgB, 7)
        val source = FakeSource(
            listOf(MemoEntity(id = 1, content = "old", images = listOf(imgB), createdAt = 1, updatedAt = 1)),
        )
        val manager = BackupManager(source, imageStore, tmp.newFolder("work10"), Dispatchers.Unconfined)

        val zip = tmp.newFile("empty.zip")
        BackupArchiver.write(zip, BackupCodec.encode(BackupFile(version = 1, exportedAt = 1, memos = emptyList())), emptyMap())

        val report = manager.importFrom(openInput = { zip.inputStream() })
        assertTrue(report is ImportReport.Success)
        assertTrue(source.data.isEmpty())
        assertFalse("无引用的旧图应被清理", imageStore.fileFor(imgB).exists())
    }
}
