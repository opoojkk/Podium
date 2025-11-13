package com.opoojkk.podium.data.util

/**
 * XML 实体编码和解码工具类
 * 用于统一处理 XML 特殊字符的转义和反转义
 */
object XmlUtils {

    private val namedEntities = mapOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&amp;" to "&"
    )

    private val numericEntityRegex = Regex("&#(x?[0-9a-fA-F]+);")

    /**
     * 将 XML 编码的字符串解码为原始字符串
     * 支持命名实体（如 &lt;）和数字实体（如 &#38; 或 &#x26;）
     *
     * @param raw 需要解码的字符串
     * @return 解码后的字符串
     */
    fun decodeEntities(raw: String): String {
        if (!raw.contains('&')) return raw

        // 先处理命名实体
        val namedDecoded = namedEntities.entries.fold(raw) { acc, (entity, value) ->
            acc.replace(entity, value)
        }

        // 再处理数字实体
        return numericEntityRegex.replace(namedDecoded) { match ->
            val value = match.groupValues[1]
            val codePoint = if (value.startsWith("x", ignoreCase = true)) {
                value.substring(1).toIntOrNull(16)
            } else {
                value.toIntOrNull()
            }

            codePoint?.let { cp -> codePointToString(cp) } ?: match.value
        }
    }

    /**
     * 将原始字符串编码为 XML 安全的字符串
     * 转义特殊字符：& < > " '
     *
     * @param raw 需要编码的字符串
     * @return 编码后的字符串
     */
    fun encodeEntities(raw: String): String =
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

    /**
     * 将 Unicode 码点转换为字符串
     * 支持基本多文种平面（BMP）和补充平面字符
     *
     * @param codePoint Unicode 码点
     * @return 对应的字符串，如果码点无效则返回 null
     */
    private fun codePointToString(codePoint: Int): String? = when {
        codePoint < 0 -> null
        codePoint <= 0xFFFF -> runCatching {
            codePoint.toChar().toString()
        }.getOrNull()
        codePoint <= 0x10FFFF -> {
            // 处理补充平面字符（使用代理对）
            val high = ((codePoint - 0x10000) shr 10) + 0xD800
            val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
            if (high in 0xD800..0xDBFF && low in 0xDC00..0xDFFF) {
                buildString(2) {
                    append(high.toChar())
                    append(low.toChar())
                }
            } else {
                null
            }
        }
        else -> null
    }
}
