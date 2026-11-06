package com.wenbo.flownote.data.backup

import kotlinx.serialization.Serializable
import com.wenbo.flownote.data.db.entity.MemoEntity
import kotlinx.serialization.json.Json

class BackupFormatException(message: String) : Exception(message)

/** 备份文件内单条笔记。 */
@Serializable
data class BackupMemo(
    /** 0 = 由导入时自动分配；非 0 必须唯一。 */
    val id: Long = 0,
    val content: String,
    val images: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

/** backup.json 根结构。 */
@Serializable
data class BackupFile(
    val version: Int,
    val exportedAt: Long,
    val memos: List<BackupMemo> = emptyList(),
)

/** 备份 DTO ⇄ JSON / 实体映射。零 Android 依赖，纯 JVM 可测。 */
object BackupCodec {

    const val CURRENT_VERSION = 1

    private val json = Json {
        // 向前兼容：旧 App 读新备份时忽略未知字段；新增字段必须带默认值
        ignoreUnknownKeys = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    fun decode(text: String): BackupFile = try {
        json.decodeFromString(BackupFile.serializer(), text)
    } catch (e: Exception) {
        throw BackupFormatException("不是有效的 flowNote 备份文件")
    }

    fun fromEntities(memos: List<MemoEntity>, exportedAt: Long): BackupFile =
        BackupFile(
            version = CURRENT_VERSION,
            exportedAt = exportedAt,
            memos = memos.map { entity ->
                BackupMemo(
                    id = entity.id,
                    content = entity.content,
                    images = entity.images,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                )
            },
        )

    /** 映射为实体并校验：非 0 id 必须唯一、id 不得为负。 */
    fun toEntities(backup: BackupFile): List<MemoEntity> {
        val explicitIds = backup.memos.mapNotNull { memo -> memo.id.takeIf { it != 0L } }
        val duplicated = explicitIds.groupBy { it }.filterValues { it.size > 1 }.keys
        if (duplicated.isNotEmpty()) {
            throw BackupFormatException("备份文件数据异常（笔记 id 重复）")
        }
        if (backup.memos.any { it.id < 0 }) {
            throw BackupFormatException("备份文件数据异常（非法笔记 id）")
        }
        return backup.memos.map { memo ->
            MemoEntity(
                id = memo.id,
                content = memo.content,
                images = memo.images,
                createdAt = memo.createdAt,
                updatedAt = memo.updatedAt,
            )
        }
    }
}
