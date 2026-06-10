package com.pixeleye.welandapola.model

/**
 * Data class representing a Flashcard in Welandapola
 */
data class FlipCard(
    val id: String = "",
    val frontText: String = "",
    val backText: String = "",
    val imageUrl: String? = null,
    val category: String = "General",
    val createdAt: Long = System.currentTimeMillis()
)
