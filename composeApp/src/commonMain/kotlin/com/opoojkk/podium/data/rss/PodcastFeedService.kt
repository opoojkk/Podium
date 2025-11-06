package com.opoojkk.podium.data.rss

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType

class PodcastFeedService(
    private val httpClient: HttpClient,
    private val parser: RssParser,
) {
    suspend fun fetch(feedUrl: String): PodcastFeed {
        val response = httpClient.get(feedUrl) {
            contentType(ContentType.Application.Xml)
        }
        val xml = response.body<String>()
        return parser.parse(feedUrl, xml)
    }
}
