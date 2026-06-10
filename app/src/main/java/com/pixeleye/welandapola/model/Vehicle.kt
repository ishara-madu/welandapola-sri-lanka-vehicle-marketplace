package com.pixeleye.welandapola.model

import java.util.Date

/**
 * Data class representing a Vehicle entry.
 * All fields have default values to enable smooth Firebase Firestore serialization.
 */
data class Vehicle(
    val id: String = "",
    val title: String = "",
    val brand: String = "",
    val model: String = "",
    val year: Int = 0,
    val price: Double = 0.0,
    val imageUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val description: String = "",
    val sellerUid: String = "",
    val sellerPhone: String = "",
    val sellerWhatsapp: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = true,
    val expireAt: Date? = null
)
