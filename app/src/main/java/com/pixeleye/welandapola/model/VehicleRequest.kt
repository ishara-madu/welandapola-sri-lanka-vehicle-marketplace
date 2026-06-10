package com.pixeleye.welandapola.model

/**
 * Data class representing a buyer's inquiry or purchase request for a vehicle.
 * All fields have default values to enable smooth Firebase Firestore serialization.
 */
data class VehicleRequest(
    val id: String = "",
    val vehicleId: String = "",
    val buyerUid: String = "",
    val sellerUid: String = "",
    val message: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED
    val createdAt: Long = System.currentTimeMillis()
)
