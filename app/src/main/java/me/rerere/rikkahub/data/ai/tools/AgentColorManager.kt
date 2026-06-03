package me.rerere.rikkahub.data.ai.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * Agent 颜色管理器，对齐官方 agentColorManager.ts。
 * 管理 agent 类型到颜色的映射，支持动态分配。
 *
 * 与官方区别：Android 无 Ink theme 系统，颜色映射到 Material3 Color。
 */
object AgentColorManager {

    /** 所有可用颜色（按语义顺序） */
    val ALL_COLORS: List<AgentColor> = AgentColor.entries

    /** agentType -> AgentColor 映射 */
    private val colorMap = ConcurrentHashMap<String, AgentColor>()

    /**
     * 获取 agent 的颜色。
     * - general-purpose 返回 null（蓝色默认）
     * - 已分配颜色的 agent 返回其颜色
     * - 未分配颜色的返回 null
     */
    fun getColor(agentType: String): AgentColor? {
        if (agentType == "general-purpose") return null
        return colorMap[agentType]
    }

    /**
     * 设置 agent 颜色。
     */
    fun setColor(agentType: String, color: AgentColor?) {
        if (color == null) {
            colorMap.remove(agentType)
        } else {
            colorMap[agentType] = color
        }
    }

    /**
     * 获取 agent 颜色的 Material3 主题色值。
     */
    fun getMaterialColor(color: AgentColor): Long = color.hex

    /**
     * 清除所有颜色映射。
     */
    fun clear() {
        colorMap.clear()
    }
}
