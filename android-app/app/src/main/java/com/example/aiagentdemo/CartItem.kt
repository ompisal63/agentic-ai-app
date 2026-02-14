package com.example.aiagentdemo

data class CartItem(
    val name: String,
    val quantity: Int,
    val price: Int,
    val recommended: Boolean = false,
    val reason: String? = null,
    val alternatives: List<AltItem>? = null
)

data class AltItem(
    val name: String,
    val price: Int
)



