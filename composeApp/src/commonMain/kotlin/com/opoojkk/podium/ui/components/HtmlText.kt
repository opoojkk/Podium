package com.opoojkk.podium.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * 带 HTML 解析、时间戳识别和链接点击功能的文本组件
 * 支持常见的 HTML 标签并识别时间戳
 */
@Composable
fun HtmlText(
    html: String,
    onTimestampClick: ((Long) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
) {
    val annotatedString = parseHtmlToAnnotatedString(
        html = html,
        linkColor = linkColor,
        baseStyle = style
    )

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        onClick = { offset ->
            // 处理时间戳点击
            if (onTimestampClick != null) {
                annotatedString.getStringAnnotations(
                    tag = "TIMESTAMP",
                    start = offset,
                    end = offset
                ).firstOrNull()?.let { annotation ->
                    val timestampMs = annotation.item.toLongOrNull()
                    if (timestampMs != null) {
                        onTimestampClick(timestampMs)
                    }
                    return@ClickableText
                }
            }

            // 处理 URL 点击
            if (onUrlClick != null) {
                annotatedString.getStringAnnotations(
                    tag = "URL",
                    start = offset,
                    end = offset
                ).firstOrNull()?.let { annotation ->
                    onUrlClick(annotation.item)
                }
            }
        }
    )
}

/**
 * 解析 HTML 字符串为 AnnotatedString
 * 支持的标签:
 * - <p> - 段落（添加换行）
 * - <br> - 换行
 * - <b>, <strong> - 粗体
 * - <i>, <em> - 斜体
 * - <u> - 下划线
 * - <a href="..."> - 链接
 * - <h1> ~ <h6> - 标题
 * - <ul>, <ol>, <li> - 列表
 * - 时间戳识别 (MM:SS, H:MM:SS)
 */

// HTML heading font size multipliers
private const val H1_SIZE_MULTIPLIER = 2.0
private const val H2_SIZE_MULTIPLIER = 1.5
private const val H3_SIZE_MULTIPLIER = 1.3
private const val H4_SIZE_MULTIPLIER = 1.15
private const val H5_SIZE_MULTIPLIER = 1.1
private const val H6_SIZE_MULTIPLIER = 1.05

private fun parseHtmlToAnnotatedString(
    html: String,
    linkColor: Color,
    baseStyle: TextStyle,
): AnnotatedString {
    if (html.isBlank()) return AnnotatedString("")

    // 解码 HTML 实体
    var text = decodeHtmlEntities(html)

    return buildAnnotatedString {
        // 用于追踪当前的样式栈
        val styleStack = mutableListOf<SpanStyle>()
        var currentIndex = 0

        // HTML 标签正则表达式
        val htmlTagRegex = Regex("""<(/?)(\w+)([^>]*)>""")

        htmlTagRegex.findAll(text).forEach { matchResult ->
            val fullMatch = matchResult.value
            val isClosing = matchResult.groupValues[1] == "/"
            val tagName = matchResult.groupValues[2].lowercase()
            val attributes = matchResult.groupValues[3]

            // 添加标签之前的文本
            if (currentIndex < matchResult.range.first) {
                val content = text.substring(currentIndex, matchResult.range.first)
                appendWithTimestamps(content, linkColor)
            }

            // 处理标签
            when (tagName) {
                "br" -> {
                    append("\n")
                }
                "p" -> {
                    if (isClosing && length > 0) {
                        append("\n\n")
                    }
                }
                "div" -> {
                    if (isClosing && length > 0) {
                        append("\n")
                    }
                }
                "b", "strong" -> {
                    if (!isClosing) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        styleStack.add(SpanStyle(fontWeight = FontWeight.Bold))
                    } else if (styleStack.isNotEmpty()) {
                        pop()
                        styleStack.removeLastOrNull()
                    }
                }
                "i", "em" -> {
                    if (!isClosing) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        styleStack.add(SpanStyle(fontStyle = FontStyle.Italic))
                    } else if (styleStack.isNotEmpty()) {
                        pop()
                        styleStack.removeLastOrNull()
                    }
                }
                "u" -> {
                    if (!isClosing) {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        styleStack.add(SpanStyle(textDecoration = TextDecoration.Underline))
                    } else if (styleStack.isNotEmpty()) {
                        pop()
                        styleStack.removeLastOrNull()
                    }
                }
                "a" -> {
                    if (!isClosing) {
                        // 提取 href 属性
                        val hrefRegex = Regex("""href=["']([^"']+)["']""")
                        val href = hrefRegex.find(attributes)?.groupValues?.get(1)
                        if (href != null) {
                            pushStringAnnotation(tag = "URL", annotation = href)
                        }
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        styleStack.add(SpanStyle(color = linkColor))
                    } else if (styleStack.isNotEmpty()) {
                        pop() // pop style
                        pop() // pop annotation
                        styleStack.removeLastOrNull()
                    }
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    if (!isClosing) {
                        val fontSize = when (tagName) {
                            "h1" -> baseStyle.fontSize * H1_SIZE_MULTIPLIER
                            "h2" -> baseStyle.fontSize * H2_SIZE_MULTIPLIER
                            "h3" -> baseStyle.fontSize * H3_SIZE_MULTIPLIER
                            "h4" -> baseStyle.fontSize * H4_SIZE_MULTIPLIER
                            "h5" -> baseStyle.fontSize * H5_SIZE_MULTIPLIER
                            else -> baseStyle.fontSize * H6_SIZE_MULTIPLIER
                        }
                        pushStyle(
                            SpanStyle(
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        styleStack.add(SpanStyle(fontSize = fontSize))
                    } else {
                        if (styleStack.isNotEmpty()) {
                            pop()
                            styleStack.removeLastOrNull()
                        }
                        if (length > 0) {
                            append("\n\n")
                        }
                    }
                }
                "ul", "ol" -> {
                    if (isClosing && length > 0) {
                        append("\n")
                    }
                }
                "li" -> {
                    if (!isClosing) {
                        append("\n  • ")
                    }
                }
            }

            currentIndex = matchResult.range.last + 1
        }

        // 添加剩余的文本
        if (currentIndex < text.length) {
            val remainingText = text.substring(currentIndex)
            appendWithTimestamps(remainingText, linkColor)
        }

        // 弹出所有未关闭的样式
        repeat(styleStack.size) {
            pop()
        }
    }
}

/**
 * AnnotatedString.Builder 扩展函数：追加文本并识别时间戳
 */
private fun AnnotatedString.Builder.appendWithTimestamps(
    text: String,
    linkColor: Color,
) {
    val timestampRegex = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    var lastIndex = 0

    timestampRegex.findAll(text).forEach { matchResult ->
        val range = matchResult.range

        // 添加时间戳之前的普通文本
        if (lastIndex < range.first) {
            append(text.substring(lastIndex, range.first))
        }

        // 解析时间戳
        val timestampMs = parseTimestamp(matchResult.value)

        // 添加时间戳文本，带链接样式
        pushStringAnnotation(
            tag = "TIMESTAMP",
            annotation = timestampMs.toString()
        )
        pushStyle(
            SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline
            )
        )
        append(matchResult.value)
        pop()
        pop()

        lastIndex = range.last + 1
    }

    // 添加剩余的文本
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

/**
 * 解析时间戳字符串为毫秒
 */
private fun parseTimestamp(timestamp: String): Long {
    val parts = timestamp.split(":")
    return when (parts.size) {
        2 -> {
            // MM:SS
            val minutes = parts[0].toLongOrNull() ?: 0
            val seconds = parts[1].toLongOrNull() ?: 0
            (minutes * 60 + seconds) * 1000
        }
        3 -> {
            // H:MM:SS or HH:MM:SS
            val hours = parts[0].toLongOrNull() ?: 0
            val minutes = parts[1].toLongOrNull() ?: 0
            val seconds = parts[2].toLongOrNull() ?: 0
            (hours * 3600 + minutes * 60 + seconds) * 1000
        }
        else -> 0L
    }
}

/**
 * 解码常见的 HTML 实体
 */
private fun decodeHtmlEntities(html: String): String {
    return html
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&#8217;", "'")
        .replace("&#8220;", """)
        .replace("&#8221;", """)
        .replace("&#8211;", "–")
        .replace("&#8212;", "—")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace("&rsquo;", "'")
        .replace("&lsquo;", "'")
        .replace("&rdquo;", """)
        .replace("&ldquo;", """)
}
