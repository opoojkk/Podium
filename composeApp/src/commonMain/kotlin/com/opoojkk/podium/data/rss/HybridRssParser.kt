package com.opoojkk.podium.data.rss

/**
 * 混合 RSS 解析器
 * 首先尝试使用 Rust 解析器（高性能），失败时回退到 Kotlin 解析器（最大兼容性）
 *
 * @param rustParser Rust 实现的解析器
 * @param kotlinParser Kotlin 实现的解析器（备用）
 */
internal class HybridRssParser(
    private val rustParser: RssParser,
    private val kotlinParser: RssParser = SimpleRssParser()
) : RssParser {

    override fun parse(feedUrl: String, xml: String): PodcastFeed {
        // 首先尝试 Rust 解析器以获得更好的性能
        val rustResult = try {
            rustParser.parse(feedUrl, xml)
        } catch (e: Exception) {
            // Rust 解析器抛出异常，使用备用解析器
            null
        }

        // 如果 Rust 解析器成功，使用其结果
        if (rustResult != null) {
            println("✓ RSS parsed with Rust parser (high-performance)")
            return rustResult
        }

        // 回退到 Kotlin 解析器以获得最大兼容性
        println("⟲ Falling back to Kotlin parser for this feed")
        return kotlinParser.parse(feedUrl, xml)
    }
}
