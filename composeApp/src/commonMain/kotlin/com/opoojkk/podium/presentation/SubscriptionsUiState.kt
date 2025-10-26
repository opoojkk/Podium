package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.Podcast

/**
 * UI model for the subscriptions screen.
 */
data class SubscriptionsUiState(
    val subscriptions: List<Podcast> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val duplicateSubscriptionTitle: String? = null, // 用于显示重复订阅提示
)
