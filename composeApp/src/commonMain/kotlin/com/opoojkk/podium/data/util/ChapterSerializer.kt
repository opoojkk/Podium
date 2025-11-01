package com.opoojkk.podium.data.util

import com.opoojkk.podium.data.model.Chapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/**
 * Utility object for serializing and deserializing Chapter lists to/from JSON.
 * Now directly uses the Chapter class which is @Serializable.
 */
object ChapterSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val listSerializer = ListSerializer(Chapter.serializer())

    /**
     * Serialize a list of chapters to JSON string.
     */
    fun serialize(chapters: List<Chapter>): String {
        return json.encodeToString(listSerializer, chapters)
    }

    /**
     * Deserialize a JSON string to a list of chapters.
     */
    fun deserialize(chaptersJson: String?): List<Chapter> {
        if (chaptersJson.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(listSerializer, chaptersJson)
        } catch (e: Exception) {
            println("⚠️ ChapterSerializer: Failed to deserialize chapters: ${e.message}")
            emptyList()
        }
    }
}
