package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 从版本22升级到23：添加知识库-助理多对多关联表
 */
val Migration_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `knowledge_source_assistants` (
                `source_id` TEXT NOT NULL,
                `assistant_id` TEXT NOT NULL,
                PRIMARY KEY(`source_id`, `assistant_id`),
                FOREIGN KEY(`source_id`) REFERENCES `knowledge_sources`(`id`) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_ksa_source_id` ON `knowledge_source_assistants`(`source_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_ksa_assistant_id` ON `knowledge_source_assistants`(`assistant_id`)")
    }
}
