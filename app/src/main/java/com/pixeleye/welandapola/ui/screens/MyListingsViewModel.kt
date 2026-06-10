package com.pixeleye.welandapola.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pixeleye.welandapola.model.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface MyListingsState {
    object Idle : MyListingsState
    object Loading : MyListingsState
    data class Error(val message: String) : MyListingsState
    object Success : MyListingsState
}

class MyListingsViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _listings = MutableStateFlow<List<Vehicle>>(emptyList())
    val listings = _listings.asStateFlow()

    private val _uiState = MutableStateFlow<MyListingsState>(MyListingsState.Idle)
    val uiState = _uiState.asStateFlow()

    // Loaded brands for editing
    private val _brands = MutableStateFlow<List<BrandMetadata>>(emptyList())
    val brands = _brands.asStateFlow()

    private val defaultBrands = listOf(
        BrandMetadata("bajaj", "Bajaj", listOf("CT100", "Pulsar 150", "Discover 125", "Platina 100", "Three Wheeler", "RE")),
        BrandMetadata("yamaha", "Yamaha", listOf("FZ S", "R15", "RayZR", "FZ25", "Y15ZR", "WR155R")),
        BrandMetadata("honda", "Honda", listOf("Dio", "Hornet 160R", "CB Shine", "CD70", "Super Cub", "Jade")),
        BrandMetadata("suzuki", "Suzuki", listOf("Gixxer", "Intruder", "Access 125", "Burgman Street", "Swift")),
        BrandMetadata("tvs", "TVS", listOf("Apache RTR", "Ntorq 125", "Wego", "XL100", "King"))
    )

    companion object {
        private const val TAG = "MyListingsViewModel"
    }

    init {
        loadSellerListings()
        loadBrandsFromFirestore()
    }

    fun loadSellerListings() {
        val currentUser = auth.currentUser
        val sellerUid = currentUser?.uid
        if (sellerUid == null) {
            _listings.value = emptyList()
            _uiState.value = MyListingsState.Idle
            return
        }

        _uiState.value = MyListingsState.Loading

        firestore.collection("vehicles")
            .whereEqualTo("sellerUid", sellerUid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot != null) {
                    val list = querySnapshot.toObjects(Vehicle::class.java)
                    _listings.value = list.sortedByDescending { it.createdAt }
                    _uiState.value = MyListingsState.Idle
                    Log.d(TAG, "Loaded ${list.size} seller listings.")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load seller listings", e)
                _uiState.value = MyListingsState.Error(e.localizedMessage ?: "Failed to load listings")
            }
    }

    private fun loadBrandsFromFirestore() {
        firestore.collection("brands")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    val list = querySnapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name") ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val models = doc.get("models") as? List<String> ?: emptyList()
                        BrandMetadata(doc.id, name, models)
                    }
                    _brands.value = list
                } else {
                    _brands.value = defaultBrands
                }
            }
            .addOnFailureListener {
                _brands.value = defaultBrands
            }
    }

    fun deleteListing(vehicleId: String) {
        viewModelScope.launch {
            _uiState.value = MyListingsState.Loading
            try {
                // Fetch the vehicle document to get image URLs before deleting
                val doc = firestore.collection("vehicles").document(vehicleId).get().await()
                val vehicle = doc.toObject(Vehicle::class.java)

                // Delete all images from Cloudinary
                if (vehicle != null) {
                    val allImageUrls = vehicle.imageUrls.toMutableList()
                    if (vehicle.imageUrl.isNotBlank() && vehicle.imageUrl !in allImageUrls) {
                        allImageUrls.add(vehicle.imageUrl)
                    }
                    if (allImageUrls.isNotEmpty()) {
                        Log.d(TAG, "Deleting ${allImageUrls.size} images from Cloudinary for vehicle: $vehicleId")
                        com.pixeleye.welandapola.data.CloudinaryManager.deleteImages(allImageUrls)
                    }
                }

                // Delete the Firestore document
                firestore.collection("vehicles").document(vehicleId)
                    .delete()
                    .await()
                
                // Update local list
                _listings.value = _listings.value.filter { it.id != vehicleId }
                _uiState.value = MyListingsState.Idle
                Log.d(TAG, "Deleted listing successfully: $vehicleId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete listing", e)
                _uiState.value = MyListingsState.Error(e.localizedMessage ?: "Failed to delete listing")
            }
        }
    }

    private fun isValidSriLankanPhoneNumber(phone: String): Boolean {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        val localRegex = Regex("^0[0-9]{9}$")
        val intlWithPlusRegex = Regex("^\\+94[0-9]{9}$")
        val intlRegex = Regex("^94[0-9]{9}$")
        
        return cleaned.matches(localRegex) || cleaned.matches(intlWithPlusRegex) || cleaned.matches(intlRegex)
    }

    fun updateListing(
        vehicleId: String,
        title: String,
        brand: String,
        model: String,
        year: Int,
        price: Double,
        description: String,
        sellerPhone: String,
        sellerWhatsapp: String,
        imageUrls: List<String>,
        originalImageUrls: List<String> = emptyList()
    ) {
        // Mobile number validation
        if (!isValidSriLankanPhoneNumber(sellerPhone)) {
            _uiState.value = MyListingsState.Error("Invalid mobile format. Please use: 0771234567 or +94771234567")
            return
        }

        // WhatsApp number validation (if not empty)
        if (sellerWhatsapp.isNotEmpty() && !isValidSriLankanPhoneNumber(sellerWhatsapp)) {
            _uiState.value = MyListingsState.Error("Invalid WhatsApp format. Please use: 0771234567 or +94771234567")
            return
        }

        viewModelScope.launch {
            _uiState.value = MyListingsState.Loading
            try {
                val docRef = firestore.collection("vehicles").document(vehicleId)
                val primaryImageUrl = imageUrls.firstOrNull() ?: ""
                val updates = mapOf(
                    "title" to title.trim(),
                    "brand" to brand.trim(),
                    "model" to model.trim(),
                    "year" to year,
                    "price" to price,
                    "description" to description.trim(),
                    "sellerPhone" to sellerPhone.trim(),
                    "sellerWhatsapp" to sellerWhatsapp.trim(),
                    "imageUrl" to primaryImageUrl,
                    "imageUrls" to imageUrls
                )
                
                docRef.update(updates).await()

                // Delete removed images from Cloudinary
                val removedUrls = originalImageUrls.filter { it !in imageUrls }
                if (removedUrls.isNotEmpty()) {
                    Log.d(TAG, "Deleting ${removedUrls.size} removed images from Cloudinary for vehicle: $vehicleId")
                    com.pixeleye.welandapola.data.CloudinaryManager.deleteImages(removedUrls)
                }

                // Update local state
                _listings.value = _listings.value.map {
                    if (it.id == vehicleId) {
                        it.copy(
                            title = title.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            year = year,
                            price = price,
                            description = description.trim(),
                            sellerPhone = sellerPhone.trim(),
                            sellerWhatsapp = sellerWhatsapp.trim(),
                            imageUrl = primaryImageUrl,
                            imageUrls = imageUrls
                        )
                    } else it
                }
                _uiState.value = MyListingsState.Idle
                Log.d(TAG, "Updated listing successfully: $vehicleId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update listing", e)
                _uiState.value = MyListingsState.Error(e.localizedMessage ?: "Failed to update listing")
            }
        }
    }
}
