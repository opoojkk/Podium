package com.opoojkk.podium.data.repository

/**
 * 重复订阅异常
 * 当用户尝试订阅已存在的播客时抛出
 * 
 * @param podcastTitle 已存在的播客标题
 * @param feedUrl 播客的 RSS 链接
 */
class DuplicateSubscriptionException(
    val podcastTitle: String,
    val feedUrl: String
) : Exception("播客「$podcastTitle」已在订阅列表中")

