package com.wenbo.flownote.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenbo.flownote.data.db.entity.MemoEntity
import com.wenbo.flownote.data.db.entity.MemoTagCrossRef
import com.wenbo.flownote.data.db.entity.TagEntity

@Database(
    entities = [MemoEntity::class, TagEntity::class, MemoTagCrossRef::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FlowNoteDatabase : RoomDatabase() {

    abstract fun memoDao(): MemoDao
    abstract fun tagDao(): TagDao
    abstract fun memoTagDao(): MemoTagDao

    companion object {

        /** v2: memos 新增 pinned 列（置顶）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memos ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): FlowNoteDatabase =
            Room.databaseBuilder(context, FlowNoteDatabase::class.java, "flownote.db")
                .addMigrations(MIGRATION_1_2)
                // v1 阶段 schema 未定型，允许破坏性迁移；正式发布前应替换为显式 Migration。
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
