package me.rerere.rikkahub.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 知识源 ↔ 助理 多对多关联表
 *
 * 一个知识源可以被多个助理使用，一个助理可以使用多个知识源。
 * 全局可见的知识源仍用 knowledge_sources.assistant_id IS NULL 表示，
 * 此表仅记录额外绑定的助理。
 */
@Entity(
    tableName = "knowledge_source_assistants",
    primaryKeys = ["source_id", "assistant_id"],
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("assistant_id"),
        Index("source_id"),
    ],
)
data class KnowledgeSourceAssistantEntity(
    val sourceId: String,
    val assistantId: String,
)
