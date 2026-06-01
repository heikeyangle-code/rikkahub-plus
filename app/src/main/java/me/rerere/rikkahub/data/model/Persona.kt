package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessage
import kotlin.uuid.Uuid

/**
 * Persona — 用户人设
 * 对齐酒馆 Persona 功能：定义用户角色、外观、系统提示词注入
 */
@Serializable
data class Persona(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val title: String = "",                 // 短标题（展示用）
    val description: String = "",            // 外观/背景描述
    val position: PersonaInjectionPosition = PersonaInjectionPosition.AFTER_SYSTEM,
    val avatar: Avatar = Avatar.Dummy,
    val enabled: Boolean = true,
    val lockedCharacterIds: List<Uuid> = emptyList(), // 绑定到特定角色
)

@Serializable
enum class PersonaInjectionPosition {
    BEFORE_SYSTEM,   // 在 system prompt 之前
    AFTER_SYSTEM,    // 在 system prompt 之后（默认）
    TOP_OF_CHAT,     // 对话历史顶部
}
