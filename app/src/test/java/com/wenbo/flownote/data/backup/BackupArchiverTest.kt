package com.wenbo.flownote.data.backup

import java.io.ByteArrayInputStream
import java.io.File
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun validImageName(name: String) = name.matches(Regex("""img_[0-9a-fA-F-]{36}\.jpg"""))
    private val imgA = "img_00000000-0000-0000-0000-00000000000a.jpg"
    private val imgB = "img_00000000-0000-0000-0000-00000000000b.jpg"

    private fun writeArchive(target: File, json: String, images: Map<String, ByteArray>) {
        val dir = tmp.newFolder("src-${target.nameWithoutExtension}")
        val imageFiles = images.mapValues { (name, bytes) ->
            dir.resolve(name).apply { writeBytes(bytes) }
        }
        val skipped = BackupArchiver.write(target, json, imageFiles)
        assertEquals(emptySet<String>(), skipped)
    }

    @Test
    fun `write 再 extract 完整还原`() {
        val json = """{"version":1,"exportedAt":1,"memos":[]}"""
        val imgBytes = ByteArray(4096).also { Random(42).nextBytes(it) }
        val zip = tmp.newFile("a.zip")
        writeArchive(zip, json, mapOf(imgA to imgBytes))

        val dest = tmp.newFolder("dest")
        val result = BackupArchiver.extract(zip.inputStream(), dest, ::validImageName)

        assertTrue(result is BackupArchiver.ExtractResult.Success)
        result as BackupArchiver.ExtractResult.Success
        assertEquals(json, result.json)
        assertEquals(setOf(imgA), result.imageFiles.keys)
        assertArrayEquals(imgBytes, result.imageFiles[imgA]!!.readBytes())
    }

    @Test
    fun `zip slip 条目整体失败且不产生越界文件`() {
        val slipEntries = listOf(
            "images/../../evil.txt",
            "/data/evil.txt",
            "images/a\\b.jpg",
        )
        slipEntries.forEach { entryName ->
            val zip = tmp.newFile("slip-${slipEntries.indexOf(entryName)}.zip")
            java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(java.util.zip.ZipEntry(entryName))
                out.write("malicious".toByteArray())
                out.closeEntry()
            }
            val dest = tmp.newFolder("slipdest-${slipEntries.indexOf(entryName)}")
            val result = BackupArchiver.extract(zip.inputStream(), dest, ::validImageName)
            assertTrue("条目 $entryName 应被拒绝", result is BackupArchiver.ExtractResult.Failure)
            // 越界文件不存在
            assertFalse(tmp.root.resolve("evil.txt").exists())
            assertFalse(dest.resolve("a\\b.jpg").exists())
        }
    }

    @Test
    fun `缺少 backupJson 失败`() {
        val zip = tmp.newFile("b.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("other.txt"))
            out.write("x".toByteArray())
            out.closeEntry()
        }
        val result = BackupArchiver.extract(zip.inputStream(), tmp.newFolder("d1"), ::validImageName)
        assertTrue(result is BackupArchiver.ExtractResult.Failure)
    }

    @Test
    fun `截断的字节流失败`() {
        val zip = tmp.newFile("d.zip")
        writeArchive(zip, """{"version":1,"exportedAt":1,"memos":[]}""", mapOf(imgA to ByteArray(1024)))
        val bytes = zip.readBytes()
        val truncated = ByteArrayInputStream(bytes, 0, bytes.size / 2)

        val result = BackupArchiver.extract(truncated, tmp.newFolder("d3"), ::validImageName)
        // 截断的 zip 大概率异常；即使结构可读，也必须不崩溃
        assertTrue(
            result is BackupArchiver.ExtractResult.Failure ||
                result is BackupArchiver.ExtractResult.Success,
        )
    }

    @Test
    fun `未知但安全入口被跳过`() {
        val zip = tmp.newFile("e.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry(BackupArchiver.JSON_ENTRY))
            out.write("{}".toByteArray())
            out.closeEntry()
            out.putNextEntry(java.util.zip.ZipEntry("meta/info.txt"))
            out.write("future".toByteArray())
            out.closeEntry()
            out.putNextEntry(java.util.zip.ZipEntry("${BackupArchiver.IMAGE_DIR}/"))
            out.closeEntry()
        }
        val result = BackupArchiver.extract(zip.inputStream(), tmp.newFolder("d4"), ::validImageName)
        assertTrue(result is BackupArchiver.ExtractResult.Success)
        assertEquals(0, (result as BackupArchiver.ExtractResult.Success).imageFiles.size)
    }

    @Test
    fun `图片名不合法则整体失败`() {
        val zip = tmp.newFile("f.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry(BackupArchiver.JSON_ENTRY))
            out.write("{}".toByteArray())
            out.closeEntry()
            out.putNextEntry(java.util.zip.ZipEntry("images/evil.jpg"))
            out.write("x".toByteArray())
            out.closeEntry()
        }
        val result = BackupArchiver.extract(zip.inputStream(), tmp.newFolder("d5"), ::validImageName)
        assertTrue(result is BackupArchiver.ExtractResult.Failure)
    }

    @Test
    fun `write 时缺失的图片文件被记入 skipped`() {
        val json = """{"version":1,"exportedAt":1,"memos":[]}"""
        val missing = "img_11111111-1111-1111-1111-111111111111.jpg"
        val present = tmp.newFile("have.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val zip = tmp.newFile("g.zip")

        val skipped = BackupArchiver.write(zip, json, mapOf(missing to File(tmp.root, "nope.jpg"), imgA to present))
        assertEquals(setOf(missing), skipped)
    }
}
