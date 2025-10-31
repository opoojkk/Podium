package com.opoojkk.podium.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration

/**
 * 带时间戳识别和点击跳转功能的文本组件
 * 识别文本中的时间戳格式（如 12:34, 1:23:45）并使其可点击
 */
@Composable
fun TimestampText(
    text: String,
    onTimestampClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val annotatedString = buildAnnotatedStringWithTimestamps(
        text = text,
        linkColor = linkColor,
        style = style
    )

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style,
        onClick = { offset ->
            annotatedString.getStringAnnotations(
                tag = "TIMESTAMP",
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                val timestampMs = annotation.item.toLongOrNull()
                if (timestampMs != null) {
                    onTimestampClick(timestampMs)
                }
            }
        }
    )
}

/**
 * 构建包含时间戳注解的文本
 * 支持格式:
 * - MM:SS (例如: 12:34)
 * - H:MM:SS (例如: 1:23:45)
 * - HH:MM:SS (例如: 10:23:45)
 */
private fun buildAnnotatedStringWithTimestamps(
    text: String,
    linkColor: Color,
    style: TextStyle,
): AnnotatedString {
    // 时间戳正则表达式: 匹配 MM:SS 或 H:MM:SS 或 HH:MM:SS
    val timestampRegex = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")

    return buildAnnotatedString {
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
}

/**
 * 解析时间戳字符串为毫秒
 * 支持格式: MM:SS 或 H:MM:SS 或 HH:MM:SS
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
