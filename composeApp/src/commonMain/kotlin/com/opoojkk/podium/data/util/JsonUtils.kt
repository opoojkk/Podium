package com.opoojkk.podium.data.util

/**
 * JSON 字符串转义和反转义工具类
 * 用于手动处理 JSON 字符串的编码和解码
 */
object JsonUtils {

    /**
     * 将字符串编码为 JSON 格式的字符串字面量（包含引号）
     * 处理特殊字符的转义
     *
     * @param value 需要编码的字符串
     * @return JSON 格式的字符串（带引号）
     */
    fun encodeString(value: String): String {
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
                        // 控制字符编码为 \uXXXX 格式
                        append("\\u${ch.code.toString(16).padStart(4, '0')}")
                    } else {
                        append(ch)
                    }
                }
            }
            append('"')
        }
    }

    /**
     * 解码 JSON 字符串中的转义序列
     * 注意：此方法不处理引号，假定输入已去除外层引号
     *
     * @param value 需要解码的字符串
     * @return 解码后的字符串
     */
    fun decodeString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\b", "\b")
    }

    /**
     * 转义字符串用于 JSON 值（不包含外层引号）
     * 与 encodeString 的区别是不添加引号
     *
     * @param value 需要转义的字符串
     * @return 转义后的字符串（不带引号）
     */
    fun escapeString(value: String): String {
        return buildString {
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
        }
    }
}
