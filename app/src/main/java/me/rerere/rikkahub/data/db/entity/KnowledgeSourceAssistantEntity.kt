package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 知识源 ↔ 助理 多对多关联表
 *
 * 一个知识源可以被多个助理使用，一个助理可以使用多个知识源。
 * 知识源必须通过此表绑定到助理后才会在搜索/注入时被检索到。
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
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "assistant_id")
    val assistantId: String,
)
