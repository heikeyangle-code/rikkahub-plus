package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.Serializable

@Serializable
data class CustomApiHeader(
    val key: String = "",
    val value: String = "",
)

@Serializable
data class CustomApiConfig(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val method: String = "POST",
    val headers: List<CustomApiHeader> = emptyList(),
    val description: String = "",
)

val DEFAULT_CUSTOM_API_CONFIGS = emptyList<CustomApiConfig>()
