package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata

/**
 * Skill 工具 — Claude Code 风格
 * 一个 use_skill 工具：加载 SKILL.md，自动返回 linked_files
 */
fun createSkillTools(
    enabledSkills: Set<String>,
    allSkills: List<SkillMetadata>,
    skillManager: SkillManager,
): List<Tool> {
    val available = allSkills.filter { it.name in enabledSkills }
    if (available.isEmpty()) return emptyList()

    val byCategory = available.groupBy { it.category ?: "其他" }

    return listOf(
        Tool(
            name = "use_skill",
            description = "Load a skill by name to access specialized knowledge and workflows.\n\n" +
                "Skills provide pre-built expertise. Available skills are listed in <available_skills>.\n\n" +
                "Usage:\n" +
                "- Use this tool when you need specialized knowledge for a task\n" +
                "- Do NOT use file action=\"list\" or file action=\"search\" to browse skill directories — use this tool instead\n" +
                "- Example: use_skill(name=\"driving-test-master\")\n\n" +
                "When to Use:\n" +
                "- A task matches a skill's description or triggers\n" +
                "- You need domain-specific guidance\n" +
                "- The user mentions a topic with a relevant skill\n\n" +
                "When NOT to Use:\n" +
                "- For general coding tasks without a matching skill",
            systemPrompt = { _, _ ->
                buildString {
                    appendLine("## Skills")
                    appendLine("Skills provide specialized knowledge and workflows. Use `use_skill` to load a skill by name. Do NOT use file action=\"list\" or file action=\"search\" to browse skill directories.")
                    appendLine("<available_skills>")
                    byCategory.forEach { (cat, skills) ->
                        appendLine("  <!-- $cat -->")
                        skills.forEach { s ->
                            append("  - ${s.name}: ${s.description.take(80)}")
                            if (s.triggers.isNotEmpty()) append(" [触发: ${s.triggers.take(3).joinToString()}]")
                            if (s.linkedFiles.isNotEmpty()) {
                                val count = s.linkedFiles.values.sumOf { it.size }
                                append(" (+${count}文件)")
                            }
                            appendLine()
                        }
                    }
                    appendLine("</available_skills>")
                }
            },
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "Skill name")
                        })
                        put("file_path", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional sub-file path from linked_files. Omit for main SKILL.md.")
                        })
                    },
                    required = listOf("name")
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: error("name required")
                if (name !in enabledSkills) error("'$name' not available")

                val filePath = obj["file_path"]?.jsonPrimitive?.content
                val content = if (filePath.isNullOrBlank()) {
                    val body = skillManager.readSkillBody(name) ?: error("Skill '$name' not found")
                    val skill = available.find { it.name == name }
                    buildString {
                        appendLine(body)
                        if (skill != null && skill.linkedFiles.isNotEmpty()) {
                            appendLine()
                            appendLine("--- linked_files ---")
                            skill.linkedFiles.forEach { (dir, files) ->
                                files.forEach { f -> appendLine("$dir/$f") }
                            }
                        }
                    }
                } else {
                    val target = skillManager.resolveSkillFile(name, filePath)
                        ?: error("Path outside skill directory")
                    if (!target.exists()) error("File '$filePath' not found")
                    target.readText()
                }
                listOf(UIMessagePart.Text(content))
            }
        )
    )
}
