package com.wenbo.flownote.data.backup

import com.wenbo.flownote.data.db.entity.MemoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val entities = listOf(
        MemoEntity(
            id = 1,
            content = "开会记录 #工作/项目A #灵感",
            images = listOf("img_0f1e2d3c-1111-2222-3333-445566778899.jpg"),
            createdAt = 1760000000000,
            updatedAt = 1760000000001,
        ),
        MemoEntity(
            id = 2,
            content = "纯文字，无标签",
            images = emptyList(),
            createdAt = 100,
            updatedAt = 100,
        ),
    )

    @Test
    fun `round-trip 保持全部字段`() {
        val backup = BackupCodec.fromEntities(entities, exportedAt = 1762400000000)
        val decoded = BackupCodec.decode(BackupCodec.encode(backup))
        val restored = BackupCodec.toEntities(decoded)

        assertEquals(1, decoded.version)
        assertEquals(1762400000000, decoded.exportedAt)
        assertEquals(entities, restored)
    }

    @Test
    fun `id 为 0 的条目被保留`() {
        val backup = BackupCodec.fromEntities(
            listOf(MemoEntity(id = 0, content = "x", images = emptyList(), createdAt = 1, updatedAt = 1)),
            exportedAt = 1,
        )
        val restored = BackupCodec.toEntities(BackupCodec.decode(BackupCodec.encode(backup)))
        assertEquals(0L, restored[0].id)
    }

    @Test
    fun `未知字段被忽略（向前兼容）`() {
        val json = """
            {"version":1,"exportedAt":1,"futureField":"whatever",
             "memos":[{"id":1,"content":"hi","createdAt":1,"updatedAt":1,"extra":{"a":1}}]}
        """.trimIndent()
        val backup = BackupCodec.decode(json)
        assertEquals(1, backup.memos.size)
        assertEquals("hi", backup.memos[0].content)
    }

    @Test
    fun `v1 旧备份无 pinned 字段解码为 false`() {
        val json = """
            {"version":1,"exportedAt":1,
             "memos":[{"id":1,"content":"hi","createdAt":1,"updatedAt":1}]}
        """.trimIndent()
        val restored = BackupCodec.toEntities(BackupCodec.decode(json))
        assertEquals(false, restored[0].pinned)
    }

    @Test
    fun `pinned 状态随备份往返保留`() {
        val backup = BackupFile(
            version = 1,
            exportedAt = 1,
            memos = listOf(
                BackupMemo(id = 1, content = "置顶的", pinned = true, createdAt = 1, updatedAt = 1),
                BackupMemo(id = 2, content = "普通的", pinned = false, createdAt = 2, updatedAt = 2),
            ),
        )
        val restored = BackupCodec.toEntities(BackupCodec.decode(BackupCodec.encode(backup)))
        assertEquals(true, restored[0].pinned)
        assertEquals(false, restored[1].pinned)
    }

    @Test
    fun `非法 JSON 抛 BackupFormatException`() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json at all") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("[1,2,3]") }
    }

    @Test
    fun `重复的非零 id 校验失败`() {
        val backup = BackupFile(
            version = 1,
            exportedAt = 1,
            memos = listOf(
                BackupMemo(id = 5, content = "a", createdAt = 1, updatedAt = 1),
                BackupMemo(id = 5, content = "b", createdAt = 1, updatedAt = 1),
            ),
        )
        assertThrows(BackupFormatException::class.java) { BackupCodec.toEntities(backup) }
    }

    @Test
    fun `负数 id 校验失败`() {
        val backup = BackupFile(
            version = 1,
            exportedAt = 1,
            memos = listOf(BackupMemo(id = -1, content = "a", createdAt = 1, updatedAt = 1)),
        )
        assertThrows(BackupFormatException::class.java) { BackupCodec.toEntities(backup) }
    }

    @Test
    fun `多个 id 为 0 允许（导入时自动分配）`() {
        val backup = BackupFile(
            version = 1,
            exportedAt = 1,
            memos = listOf(
                BackupMemo(id = 0, content = "a", createdAt = 1, updatedAt = 1),
                BackupMemo(id = 0, content = "b", createdAt = 1, updatedAt = 1),
            ),
        )
        assertEquals(2, BackupCodec.toEntities(backup).size)
    }

    @Test
    fun `空笔记列表合法`() {
        val backup = BackupCodec.fromEntities(emptyList(), exportedAt = 1)
        assertTrue(BackupCodec.toEntities(BackupCodec.decode(BackupCodec.encode(backup))).isEmpty())
    }
}
