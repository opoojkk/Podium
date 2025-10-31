package com.opoojkk.podium.data.subscription

import com.opoojkk.podium.data.model.Podcast
import kotlinx.datetime.Instant

/**
 * Service for exporting podcast subscriptions to various standard formats.
 * Supports OPML 2.0 and JSON formats for maximum compatibility.
 */
class SubscriptionExporter {

    /**
     * Export subscriptions as OPML 2.0 format.
     * OPML (Outline Processor Markup Language) is the standard format for podcast subscriptions.
     */
    fun exportAsOpml(
        podcasts: List<Podcast>,
        title: String = "Podium Subscriptions",
        ownerName: String? = null,
        ownerEmail: String? = null,
    ): String {
        val dateCreated = formatRfc822Date(kotlinx.datetime.Clock.System.now())

        val outlines = podcasts.joinToString(separator = "\n") { podcast ->
            buildOpmlOutline(podcast)
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<opml version="2.0">""")
            appendLine("""  <head>""")
            appendLine("""    <title>${escapeXml(title)}</title>""")
            appendLine("""    <dateCreated>$dateCreated</dateCreated>""")
            if (ownerName != null) {
                appendLine("""    <ownerName>${escapeXml(ownerName)}</ownerName>""")
            }
            if (ownerEmail != null) {
                appendLine("""    <ownerEmail>${escapeXml(ownerEmail)}</ownerEmail>""")
            }
            appendLine("""  </head>""")
            appendLine("""  <body>""")
            append(outlines)
            if (outlines.isNotEmpty()) appendLine()
            appendLine("""  </body>""")
            append("""</opml>""")
        }
    }

    /**
     * Export subscriptions as JSON format.
     * This format is easier to parse and can include more metadata than OPML.
     */
    fun exportAsJson(podcasts: List<Podcast>): String {
        val subscriptions = podcasts.map { podcast ->
            SubscriptionJson(
                title = podcast.title,
                feedUrl = podcast.feedUrl,
                description = podcast.description,
                artworkUrl = podcast.artworkUrl,
                lastUpdated = podcast.lastUpdated.toString(),
                autoDownload = podcast.autoDownload,
            )
        }

        val export = SubscriptionExportJson(
            version = "1.0",
            exportedAt = kotlinx.datetime.Clock.System.now().toString(),
            subscriptions = subscriptions,
        )

        return buildJsonString(export)
    }

    private fun buildOpmlOutline(podcast: Podcast): String {
        val title = escapeXml(podcast.title)
        val feedUrl = escapeXml(podcast.feedUrl)
        val description = escapeXml(podcast.description)
        val artworkUrl = podcast.artworkUrl?.let { escapeXml(it) }

        return buildString {
            append("""    <outline type="rss"""")
            append(""" text="$title"""")
            append(""" title="$title"""")
            append(""" xmlUrl="$feedUrl"""")
            if (description.isNotEmpty()) {
                append(""" description="$description"""")
            }
            if (artworkUrl != null) {
                append(""" imageUrl="$artworkUrl"""")
            }
            append(" />")
        }
    }

    private fun formatRfc822Date(instant: Instant): String {
        // RFC 822 format: "Wed, 02 Oct 2024 15:00:00 GMT"
        // For simplicity, we'll use ISO format which is widely accepted
        return instant.toString()
    }

    private fun escapeXml(raw: String): String =
        buildString(raw.length + 16) {
            raw.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }

    private fun buildJsonString(export: SubscriptionExportJson): String {
        return buildString {
            appendLine("{")
            appendLine("""  "version": "${export.version}",""")
            appendLine("""  "exportedAt": "${export.exportedAt}",""")
            appendLine("""  "subscriptions": [""")

            export.subscriptions.forEachIndexed { index, sub ->
                appendLine("    {")
                appendLine("""      "title": ${jsonString(sub.title)},""")
                appendLine("""      "feedUrl": ${jsonString(sub.feedUrl)},""")
                appendLine("""      "description": ${jsonString(sub.description)},""")
                if (sub.artworkUrl != null) {
                    appendLine("""      "artworkUrl": ${jsonString(sub.artworkUrl)},""")
                }
                appendLine("""      "lastUpdated": "${sub.lastUpdated}",""")
                append("""      "autoDownload": ${sub.autoDownload}""")
                appendLine()
                if (index < export.subscriptions.size - 1) {
                    appendLine("    },")
                } else {
                    appendLine("    }")
                }
            }

            appendLine("  ]")
            append("}")
        }
    }

    private fun jsonString(value: String): String {
        return buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 32) {
                        append("\\u${ch.code.toString(16).padStart(4, '0')}")
                    } else {
                        append(ch)
                    }
                }
            }
            append('"')
        }
    }

    private data class SubscriptionExportJson(
        val version: String,
        val exportedAt: String,
        val subscriptions: List<SubscriptionJson>,
    )

    private data class SubscriptionJson(
        val title: String,
        val feedUrl: String,
        val description: String,
        val artworkUrl: String?,
        val lastUpdated: String,
        val autoDownload: Boolean,
    )
}
