package com.opoojkk.podium.data.model

/**
 * Builds a share text for the episode including podcast title, episode title, and audio URL.
 */
fun Episode.buildShareText(): String = buildString {
    append(podcastTitle)
    append(" - ")
    append(title)
    append("\n\n")
    append("链接：")
    append(audioUrl)
}
