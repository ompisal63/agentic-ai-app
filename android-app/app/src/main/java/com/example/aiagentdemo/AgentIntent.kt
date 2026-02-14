package com.example.aiagentdemo

data class AgentIntent(
    val intent: String,
    val confidence: Float,
    val items: List<CartItem>,
    val missing: List<String> = emptyList()
)

