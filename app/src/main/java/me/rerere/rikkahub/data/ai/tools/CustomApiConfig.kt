package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.Serializable

@Serializable
data class CustomApiConfig(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val method: String = "POST",
    val description: String = "",
)

val DEFAULT_CUSTOM_API_CONFIGS = emptyList<CustomApiConfig>()
