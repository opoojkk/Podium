package com.opoojkk.podium.data.util

import com.opoojkk.podium.data.model.Chapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
private data class ChapterDto(
    val startTimeMs: Long,
    val title: String,
    val imageUrl: String? = null,
    val url: String? = null,
)

object ChapterSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(chapters: List<Chapter>): String {
        val dtos = chapters.map { ChapterDto(it.startTimeMs, it.title, it.imageUrl, it.url) }
        return json.encodeToString(dtos)
    }

    fun deserialize(chaptersJson: String?): List<Chapter> {
        if (chaptersJson.isNullOrBlank()) return emptyList()
        return try {
            val dtos = json.decodeFromString<List<ChapterDto>>(chaptersJson)
            dtos.map { Chapter(it.startTimeMs, it.title, it.imageUrl, it.url) }
        } catch (e: Exception) {
            println("Failed to deserialize chapters: ${e.message}")
            emptyList()
        }
    }
}
