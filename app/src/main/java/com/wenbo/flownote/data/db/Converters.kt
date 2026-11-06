package com.wenbo.flownote.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 图片文件名列表 ⇄ JSON 字符串（Room TypeConverter）。 */
class Converters {

    @TypeConverter
    fun fromImages(images: List<String>): String = ImageListCodec.encode(images)

    @TypeConverter
    fun toImages(data: String): List<String> = ImageListCodec.decode(data)
}

/** 独立于 Room 的编解码工具，供启动孤儿清理等场景复用。 */
object ImageListCodec {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun encode(images: List<String>): String = json.encodeToString(serializer, images)

    fun decode(data: String): List<String> =
        if (data.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(serializer, data) }.getOrDefault(emptyList())
        }
}
