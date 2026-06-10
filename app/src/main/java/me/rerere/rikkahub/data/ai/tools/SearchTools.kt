package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import java.time.LocalDate
import kotlin.uuid.Uuid

fun createSearchTools(settings: Settings): Set<Tool> {
    return buildSet {
        add(
            Tool(
                name = "search_web",
                                description = "Search the web for up-to-date information.\n\n" +
                                    "When to use:\n" +
                                    "- Current events, recent data, or information beyond your knowledge cutoff\n" +
                                    "- Research topics requiring multiple sources\n\n" +
                                    "When NOT to use:\n" +
                                    "- Reading a specific URL (use web_fetch)\n" +
                                    "- GitHub operations (use github_tool)\n\n" +
                                    "Args:\n" +
                                    "- query: Search keywords. Generate focused queries, run multiple searches if needed.\n\n" +
                                    "CRITICAL: After using search results, you MUST include a \"Sources:\" section.\n" +
                                    "Format: [citation,domain](id) after each cited sentence.\n" +
                                    "Today is ${LocalDate.now().toLocalString(true)}." +
                parameters = {
                    val options = settings.searchServices.getOrElse(
                        index = settings.searchServiceSelected,
                        defaultValue = { SearchServiceOptions.DEFAULT })
                    val service = SearchService.getService(options)
                    service.parameters(options)
                },
                execute = {
                    val options = settings.searchServices.getOrElse(
                        index = settings.searchServiceSelected,
                        defaultValue = { SearchServiceOptions.DEFAULT })
                    val service = SearchService.getService(options)
                    val result = service.search(
                        params = it.jsonObject,
                        commonOptions = settings.searchCommonOptions,
                        serviceOptions = options,
                    )
                    val results =
                        JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject.let { json ->
                            val map = json.toMutableMap()
                            map["items"] =
                                JsonArray(map["items"]!!.jsonArray.mapIndexed { index, item ->
                                    JsonObject(item.jsonObject.toMutableMap().apply {
                                        put("id", JsonPrimitive(Uuid.random().toString().take(6)))
                                        put("index", JsonPrimitive(index + 1))
                                    })
                                })
                            JsonObject(map)
                        }
                    listOf(UIMessagePart.Text(results.toString()))
                }
            )
        )

        val options = settings.searchServices.getOrElse(
            index = settings.searchServiceSelected,
            defaultValue = { SearchServiceOptions.DEFAULT })
        val service = SearchService.getService(options)
        if (service.scrapingParameters(options) != null) {
            add(
                Tool(
                    name = "scrape_web",
                    description = "Scrape a URL for detailed page content.\n\n" +
                        "When to use:\n" +
                        "- Read full content of a specific URL (not search results)\n" +
                        "- Extract text from web pages for analysis\n\n" +
                        "When NOT to use:\n" +
                        "- Web search (use search_web)\n\n" +
                        "Args:\n" +
                        "- url: URL to scrape",
                    parameters = {
                        val options = settings.searchServices.getOrElse(
                            index = settings.searchServiceSelected,
                            defaultValue = { SearchServiceOptions.DEFAULT })
                        val service = SearchService.getService(options)
                        service.scrapingParameters(options)
                    },
                    execute = {
                        val options = settings.searchServices.getOrElse(
                            index = settings.searchServiceSelected,
                            defaultValue = { SearchServiceOptions.DEFAULT })
                        val service = SearchService.getService(options)
                        val result = service.scrape(
                            params = it.jsonObject,
                            commonOptions = settings.searchCommonOptions,
                            serviceOptions = options,
                        )
                        val payload = JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject
                        listOf(UIMessagePart.Text(payload.toString()))
                    }
                ))
        }
    }
}
