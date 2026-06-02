package me.rerere.rikkahub.data.model

import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 群聊发言人选择器 — 对齐酒馆
 *
 * NATURAL: talkativeness(0-1) × 骰子 + 名字分词匹配 + 至少选2人
 * LIST:    轮换
 * POOLED:  按权重随机（AutoGen 式 RoundRobin）
 * MANUAL:  用户指定
 */
object GroupSpeakerSelector {

    private fun extractWords(text: String): Set<String> {
        return text.split(Regex("[\\s,，。！？.!?、；;：:/\\\\()（）\\[\\]【】{}「」『』\"']+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()
    }

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
     * NATURAL 模式（对齐酒馆）
     *
     * 1. 名字匹配 → 提到的入选
     * 2. 按 talkativeness 掷骰子（打乱顺序遍历）
     * 3. 至少选 2 人（如果总人数 >= 2 且骰子没选中至少 2 人，补到 2 个）
     * 4. 禁止连续同一个人发言
     */
    fun pickNatural(
        members: List<Assistant>,
        userInput: String,
        lastSpeakerId: Uuid?,
        allowSelfResponses: Boolean,
    ): List<Uuid> {
        if (members.isEmpty()) return emptyList()
        val activated = mutableSetOf<Uuid>()
        val bannedId = if (allowSelfResponses) null else lastSpeakerId

        // 1. 名字匹配
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

        // 3. 用户已通过名字指定了某人 → 不加随机人"
        if (userInput.isNotBlank() && findMentionedMembers(userInput, members, null).isNotEmpty()) {
            // 已有名字匹配，不强制增补
        } else if (activated.size < 2 && members.size >= 2) {
            // 至少选 2 人（成员数 >= 2 且当前选中不足 2 时）
            val candidates = members.filter { it.id !in activated && (bannedId == null || it.id != bannedId) }
            val toAdd = candidates.shuffled().take(2 - activated.size)
            activated.addAll(toAdd.map { it.id })
        }

        // 4. 仍没人 → 从所有成员里随机选一个
        if (activated.isEmpty()) {
            members.firstOrNull()?.let { activated.add(it.id) }
        }

        return activated.toList()
    }

    /**
     * LIST 模式：轮换
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
                // 找下一个
                val nextIdx = (idx + 1) % members.size
                // 如果一轮只有一个人，返回 2 个
                if (members.size == 1) return listOf(members[0].id)
                val afterNext = (nextIdx + 1) % members.size
                return listOf(members[nextIdx].id, members[afterNext].id)
            }
            members
        } else members

        val picks = if (pool.size >= 2) pool.shuffled().take(2) else pool.take(1)
        return picks.map { it.id }
    }

    /**
     * POOLED 模式：按权重随机
     * 优先选上次用户发言后未发言的成员
     * 权重越高，出场几率越大
     */
    fun pickPooled(
        members: List<Assistant>,
        lastSpeakerId: Uuid?,
        speakerHistory: List<Uuid>,
        allowSelfResponses: Boolean,
        weights: Map<Uuid, Int> = emptyMap(),
    ): List<Uuid> {
        if (members.isEmpty()) return emptyList()

        // 自上次用户消息后未发言的成员
        val spokenSinceUser = mutableSetOf<Uuid>()
        for (sid in speakerHistory) {
            spokenSinceUser.add(sid)
        }

        val available = if (!allowSelfResponses && lastSpeakerId != null) {
            members.filter { it.id != lastSpeakerId }
        } else members

        // 未发言者优先
        val unspoken = available.filter { it.id !in spokenSinceUser }
        val pool = if (unspoken.isNotEmpty()) unspoken else available

        // 按权重随机
        val pick = weightedRandom(pool, weights)

        // 至少选2人（如果有2个以上的可用成员）
        val result = mutableListOf(pick.id)
        if (members.size >= 2) {
            val secondPool = available.filter { it.id != pick.id }
            if (secondPool.isNotEmpty()) {
                val second = weightedRandom(secondPool, weights)
                result.add(second.id)
            }
        }

        return result
    }

    /** 加权随机选取 */
    private fun weightedRandom(
        members: List<Assistant>,
        weights: Map<Uuid, Int>,
    ): Assistant {
        if (members.isEmpty()) error("empty pool")
        if (members.size == 1 || weights.isEmpty()) return members.random()
        val totalWeight = members.sumOf { weights[it.id] ?: 1 }
        if (totalWeight <= 0) return members.random()
        var roll = Random.nextInt(totalWeight)
        for (m in members) {
            roll -= (weights[m.id] ?: 1)
            if (roll < 0) return m
        }
        return members.last()
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
        speakerWeights: Map<Uuid, Int> = emptyMap(),
    ): List<Uuid> {
        return when (strategy) {
            GroupActivationStrategy.NATURAL -> pickNatural(
                enabledMembers, userInput, lastSpeakerId, allowSelfResponses,
            )
            GroupActivationStrategy.LIST -> pickList(
                enabledMembers, lastSpeakerId, allowSelfResponses,
            )
            GroupActivationStrategy.POOLED -> pickPooled(
                enabledMembers, lastSpeakerId, speakerHistory, allowSelfResponses, speakerWeights,
            )
            GroupActivationStrategy.MANUAL -> listOfNotNull(manualSpeakerId)
        }
    }
}
