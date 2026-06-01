package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 从版本21升级到22：知识库加 tags 和 parent_chunk_id 字段
 */
val Migration_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `knowledge_sources` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `knowledge_chunks` ADD COLUMN `parent_chunk_id` TEXT")
    }
}
