package me.rerere.rikkahub.data.model

import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 群聊发言人选择器 — 对齐酒馆 + AutoGen 方案
 *
 * NATURAL: talkativeness(0-1) × 骰子 + 名字分词匹配 + 不能连续
 * LIST:    轮换
 * POOLED:  未发言者优先（AutoGen 式 RoundRobin）
 * MANUAL:  用户指定
 */
object GroupSpeakerSelector {

    /** 从用户输入中提取所有单词（对齐酒馆 extractAllWords） */
    private fun extractWords(text: String): Set<String> {
        return text.split(Regex("[\\s,，。！？.!?、；;：:/\\\\()（）\\[\\]【】{}「」『』\"'「」]+"))
            .map { it.trim() }
            .filter { it.length >= 1 }
            .toSet()
    }

    /** 名字分词匹配：提取输入和角色名的单词，逐个比较 */
    private fun findMentionedMembers(
        input: String,
        members: List<Assistant>,
        bannedId: Uuid?,
    ): Set<Uuid> {
        if (input.isBlank()) return emptySet()
        val inputWords = extractWords(input)
        if (inputWords.isEmpty()) return emptySet()

        return members.filter { m ->
            if (bannedId != null && m.id == bannedId) return@filter false
            val nameWords = extractWords(m.name)
            nameWords.isNotEmpty() && nameWords.any { word ->
                inputWords.any { it.equals(word, ignoreCase = true) }
            }
        }.map { it.id }.toSet()
    }

    /**
     * NATURAL 模式（对齐酒馆 activateNaturalOrder）
     *
     * 1. 名字匹配 → 提到的入选
     * 2. 按 talkativeness 掷骰子（打乱顺序遍历）
     * 3. 无人激活时从 talkative > 0 的人里随机
     */
    fun pickNatural(
        members: List<Assistant>,
        userInput: String,
        lastSpeakerId: Uuid?,
        allowSelfResponses: Boolean,
    ): List<Uuid> {
        val activated = mutableSetOf<Uuid>()
        val bannedId = if (allowSelfResponses) null else lastSpeakerId

        // 1. 名字匹配（排除连续同一个人）
        if (userInput.isNotBlank()) {
            activated.addAll(findMentionedMembers(userInput, members, bannedId))
        }

        // 2. 按 talkativeness 掷骰子（打乱顺序）
        val shuffled = members.shuffled()
        for (m in shuffled) {
            if (bannedId != null && m.id == bannedId) continue
            if (Random.nextFloat() < m.talkativeness) {
                activated.add(m.id)
            }
        }

        // 3. 没人通过 → 从 talkative > 0 的人里随机（对标酒馆 chattyMembers）
        if (activated.isEmpty()) {
            val chatty = members.filter { it.talkativeness > 0f && (bannedId == null || it.id != bannedId) }
            val pool = if (chatty.isNotEmpty()) chatty else members.filter { bannedId == null || it.id != bannedId }
            if (pool.isNotEmpty()) {
                activated.add(pool.random().id)
            }
        }

        // 4. 全都被禁了从所有人里随机
        if (activated.isEmpty()) {
            members.firstOrNull()?.let { activated.add(it.id) }
        }

        return activated.toList()
    }

    /**
     * LIST 模式：轮换（从 lastSpeakerId 的下一个开始）
     */
    fun pickList(
        members: List<Assistant>,
        lastSpeakerId: Uuid?,
        allowSelfResponses: Boolean,
    ): List<Uuid> {
        if (members.isEmpty()) return emptyList()
        val pool = if (!allowSelfResponses && lastSpeakerId != null) {
            val idx = members.indexOfFirst { it.id == lastSpeakerId }
            if (idx >= 0) {
                return listOfNotNull(members.getOrNull((idx + 1) % members.size)?.id)
            }
            members
        } else members
        return listOfNotNull(pool.firstOrNull()?.id)
    }

    /**
     * POOLED 模式（对齐酒馆 + AutoGen RoundRobin）：
     * 优先选自上次用户发言后未发言的成员
     */
    fun pickPooled(
        members: List<Assistant>,
        lastSpeakerId: Uuid?,
        speakerHistory: List<Uuid>,  // 按时间的发言者ID列表
        allowSelfResponses: Boolean,
    ): List<Uuid> {
        if (members.isEmpty()) return emptyList()

        // 自上次用户消息后谁还没说过话
        val spokenSinceUser = mutableSetOf<Uuid>()
        for (sid in speakerHistory) {
            if (sid == lastSpeakerId) break  // lastSpeakerId 是用户？不，是助手的
            spokenSinceUser.add(sid)
        }
        // 其实 speakerHistory 里最后一条用户消息之前的就是未发言者
        // 简化：选未发言者，全发言了就随机
        val pool = if (!allowSelfResponses && lastSpeakerId != null) {
            members.filter { it.id != lastSpeakerId && it.id !in spokenSinceUser }
        } else {
            members.filter { it.id !in spokenSinceUser }
        }

        val pick = if (pool.isNotEmpty()) pool.random() else members.random()
        return listOf(pick.id)
    }

    /**
     * 统一入口
     */
    fun pick(
        strategy: GroupActivationStrategy,
        members: List<Assistant>,
        enabledMembers: List<Assistant>,
        userInput: String = "",
        lastSpeakerId: Uuid? = null,
        speakerHistory: List<Uuid> = emptyList(),
        allowSelfResponses: Boolean = false,
        manualSpeakerId: Uuid? = null,
    ): List<Uuid> {
        return when (strategy) {
            GroupActivationStrategy.NATURAL -> pickNatural(
                enabledMembers, userInput, lastSpeakerId, allowSelfResponses,
            )
            GroupActivationStrategy.LIST -> pickList(
                enabledMembers, lastSpeakerId, allowSelfResponses,
            )
            GroupActivationStrategy.POOLED -> pickPooled(
                enabledMembers, lastSpeakerId, speakerHistory, allowSelfResponses,
            )
            GroupActivationStrategy.MANUAL -> listOfNotNull(manualSpeakerId)
        }
    }
}
