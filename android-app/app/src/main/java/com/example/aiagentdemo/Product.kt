package com.example.aiagentdemo

data class Product(
    val name: String,
    val price: Int,
    val category: String,
    val imageRes: Int,
    val quantity: Int = 1,

    val brand: String? = null,
    val isBrandAutoSelected: Boolean = false,

    // 🔥 NEW (BACKEND DRIVEN)
    val recommendationReason: String? = null,
    val alternatives: List<Product> = emptyList()
)
