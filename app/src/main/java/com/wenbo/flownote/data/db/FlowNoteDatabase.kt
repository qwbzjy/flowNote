package com.wenbo.flownote.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wenbo.flownote.data.db.entity.MemoEntity
import com.wenbo.flownote.data.db.entity.MemoTagCrossRef
import com.wenbo.flownote.data.db.entity.TagEntity

@Database(
    entities = [MemoEntity::class, TagEntity::class, MemoTagCrossRef::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FlowNoteDatabase : RoomDatabase() {

    abstract fun memoDao(): MemoDao
    abstract fun tagDao(): TagDao
    abstract fun memoTagDao(): MemoTagDao

    companion object {
        fun create(context: Context): FlowNoteDatabase =
            Room.databaseBuilder(context, FlowNoteDatabase::class.java, "flownote.db")
                // v1 阶段 schema 未定型，允许破坏性迁移；正式发布前应替换为显式 Migration。
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
