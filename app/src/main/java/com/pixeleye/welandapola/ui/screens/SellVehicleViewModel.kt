package com.pixeleye.welandapola.ui.screens

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pixeleye.welandapola.data.CloudinaryManager
import com.pixeleye.welandapola.model.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SellVehicleState {
    object Idle : SellVehicleState
    object Submitting : SellVehicleState
    object Success : SellVehicleState
    data class Error(val message: String) : SellVehicleState
}

data class BrandMetadata(
    val id: String = "",
    val name: String = "",
    val models: List<String> = emptyList()
)

class SellVehicleViewModel : ViewModel() {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _brand = MutableStateFlow("")
    val brand = _brand.asStateFlow()

    private val _model = MutableStateFlow("")
    val model = _model.asStateFlow()

    private val _year = MutableStateFlow("")
    val year = _year.asStateFlow()

    private val _price = MutableStateFlow("")
    val price = _price.asStateFlow()

    // Local image URIs selected from gallery (NOT yet uploaded)
    private val _pendingImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImageUris = _pendingImageUris.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    // Required Seller Mobile Number
    private val _sellerPhone = MutableStateFlow("")
    val sellerPhone = _sellerPhone.asStateFlow()

    // Optional WhatsApp Number
    private val _sellerWhatsapp = MutableStateFlow("")
    val sellerWhatsapp = _sellerWhatsapp.asStateFlow()

    private val _uiState = MutableStateFlow<SellVehicleState>(SellVehicleState.Idle)
    val uiState = _uiState.asStateFlow()

    // Dynamic brand and model lists loaded from Firestore
    private val _brands = MutableStateFlow<List<BrandMetadata>>(emptyList())
    val brands = _brands.asStateFlow()

    // Available models based on selected brand
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels = _availableModels.asStateFlow()

    // Popular default local fallbacks (Sri Lanka market focused)
    private val defaultBrands = listOf(
        BrandMetadata("bajaj", "Bajaj", listOf("CT100", "Pulsar 150", "Discover 125", "Platina 100", "Three Wheeler", "RE")),
        BrandMetadata("yamaha", "Yamaha", listOf("FZ S", "R15", "RayZR", "FZ25", "Y15ZR", "WR155R")),
        BrandMetadata("honda", "Honda", listOf("Dio", "Hornet 160R", "CB Shine", "CD70", "Super Cub", "Jade")),
        BrandMetadata("suzuki", "Suzuki", listOf("Gixxer", "Intruder", "Access 125", "Burgman Street", "Swift")),
        BrandMetadata("tvs", "TVS", listOf("Apache RTR", "Ntorq 125", "Wego", "XL100", "King"))
    )

    companion object {
        private const val TAG = "SellVehicleViewModel"
    }

    init {
        loadBrandsFromFirestore()
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
                    Log.d(TAG, "Brands loaded from Firestore successfully: ${list.size} entries.")
                    updateAvailableModels()
                } else {
                    Log.w(TAG, "Firestore 'brands' collection is empty. Initializing with local fallbacks.")
                    _brands.value = defaultBrands
                    updateAvailableModels()
                    seedDefaultBrandsToFirestore()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load brands from Firestore. Using local presets.", e)
                _brands.value = defaultBrands
                updateAvailableModels()
            }
    }

    private fun seedDefaultBrandsToFirestore() {
        viewModelScope.launch {
            for (brand in defaultBrands) {
                firestore.collection("brands").document(brand.id)
                    .set(mapOf("name" to brand.name, "models" to brand.models))
                    .addOnSuccessListener {
                        Log.d(TAG, "Seeded brand to Firestore: ${brand.name}")
                    }
            }
        }
    }

    private fun updateAvailableModels() {
        val selectedBrand = _brand.value
        val match = _brands.value.firstOrNull { it.name.equals(selectedBrand, ignoreCase = true) }
        _availableModels.value = match?.models ?: emptyList()
        // Reset model selection if it does not belong to the selected brand
        if (_model.value.isNotEmpty() && !(_availableModels.value.contains(_model.value))) {
            _model.value = ""
        }
    }

    fun onTitleChange(value: String) { _title.value = value }

    fun onBrandChange(value: String) {
        _brand.value = value
        updateAvailableModels()
    }

    fun onModelChange(value: String) {
        _model.value = value
    }

    fun onYearChange(value: String) { _year.value = value }
    fun onPriceChange(value: String) { _price.value = value }
    fun onDescriptionChange(value: String) { _description.value = value }
    fun onSellerPhoneChange(value: String) { _sellerPhone.value = value }
    fun onSellerWhatsappChange(value: String) { _sellerWhatsapp.value = value }

    /**
     * Adds selected local image URIs to the pending list (no upload yet).
     */
    fun addPendingImages(uris: List<Uri>) {
        val remaining = 5 - _pendingImageUris.value.size
        if (remaining <= 0) {
            _uiState.value = SellVehicleState.Error("You can add a maximum of 5 images.")
            return
        }
        _pendingImageUris.value = _pendingImageUris.value + uris.take(remaining)
    }

    fun removePendingImage(index: Int) {
        val current = _pendingImageUris.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _pendingImageUris.value = current
        }
    }

    private fun isValidSriLankanPhoneNumber(phone: String): Boolean {
        // Strip spaces, dashes, parentheses
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        val localRegex = Regex("^0[0-9]{9}$")
        val intlWithPlusRegex = Regex("^\\+94[0-9]{9}$")
        val intlRegex = Regex("^94[0-9]{9}$")
        
        return cleaned.matches(localRegex) || cleaned.matches(intlWithPlusRegex) || cleaned.matches(intlRegex)
    }

    fun submitVehicleListing() {
        val titleStr = _title.value.trim()
        val brandStr = _brand.value.trim()
        val modelStr = _model.value.trim()
        val yearInt = _year.value.trim().toIntOrNull()
        val priceDouble = _price.value.trim().toDoubleOrNull()
        val pendingUris = _pendingImageUris.value
        val descriptionStr = _description.value.trim()
        val phoneStr = _sellerPhone.value.trim()
        val whatsappStr = _sellerWhatsapp.value.trim()

        if (titleStr.isEmpty() || brandStr.isEmpty() || modelStr.isEmpty() || yearInt == null || priceDouble == null || phoneStr.isEmpty()) {
            _uiState.value = SellVehicleState.Error("Please fill out all required fields, including your mobile number.")
            return
        }

        // Format validation for mobile number
        if (!isValidSriLankanPhoneNumber(phoneStr)) {
            _uiState.value = SellVehicleState.Error("Invalid mobile format. Please use: 0771234567 or +94771234567")
            return
        }

        // Format validation for WhatsApp (if provided)
        if (whatsappStr.isNotEmpty() && !isValidSriLankanPhoneNumber(whatsappStr)) {
            _uiState.value = SellVehicleState.Error("Invalid WhatsApp format. Please use: 0771234567 or +94771234567")
            return
        }

        _uiState.value = SellVehicleState.Submitting

        viewModelScope.launch {
            val sellerUid = auth.currentUser?.uid
            if (sellerUid == null) {
                _uiState.value = SellVehicleState.Error("You must be signed in to submit a listing.")
                return@launch
            }

            // Step 1: Upload all pending images to Cloudinary
            val uploadedUrls = mutableListOf<String>()
            var uploadError: String? = null

            if (pendingUris.isNotEmpty()) {
                val remaining = pendingUris.size
                var completed = 0
                val uploadedUrlsArray = arrayOfNulls<String>(remaining)

                for (i in pendingUris.indices) {
                    val uri = pendingUris[i]
                    CloudinaryManager.uploadImage(
                        uri = uri,
                        onSuccess = { url ->
                            synchronized(uploadedUrlsArray) {
                                uploadedUrlsArray[i] = url
                                completed++
                                if (completed >= remaining) {
                                    val orderedUrls = uploadedUrlsArray.filterNotNull()
                                    // All uploads done — publish to Firestore
                                    publishToFirestore(sellerUid, titleStr, brandStr, modelStr, yearInt, priceDouble, descriptionStr, phoneStr, whatsappStr, orderedUrls)
                                }
                            }
                        },
                        onFailure = { error ->
                            synchronized(uploadedUrlsArray) {
                                if (uploadError == null) {
                                    uploadError = error
                                    _uiState.value = SellVehicleState.Error("Image upload failed: $error")
                                }
                            }
                        }
                    )
                }
            } else {
                // No images to upload — publish directly
                publishToFirestore(sellerUid, titleStr, brandStr, modelStr, yearInt, priceDouble, descriptionStr, phoneStr, whatsappStr, emptyList())
            }
        }
    }

    private fun publishToFirestore(
        sellerUid: String,
        title: String,
        brand: String,
        model: String,
        year: Int,
        price: Double,
        description: String,
        phone: String,
        whatsapp: String,
        imageUrls: List<String>
    ) {
        val docRef = firestore.collection("vehicles").document()
        val primaryImageUrl = imageUrls.firstOrNull() ?: ""

        val vehicle = Vehicle(
            id = docRef.id,
            title = title,
            brand = brand,
            model = model,
            year = year,
            price = price,
            imageUrl = primaryImageUrl,
            imageUrls = imageUrls,
            description = description,
            sellerUid = sellerUid,
            sellerPhone = phone,
            sellerWhatsapp = whatsapp,
            createdAt = System.currentTimeMillis(),
            isAvailable = true,
            expireAt = java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
        )

        docRef.set(vehicle)
            .addOnSuccessListener {
                Log.d(TAG, "Vehicle published successfully: ${docRef.id}")
                // Sync seller phone number to users collection
                val userUpdateMap = hashMapOf<String, Any>(
                    "phoneNumber" to phone
                )
                firestore.collection("users").document(sellerUid)
                    .set(userUpdateMap, com.google.firebase.firestore.SetOptions.merge())
                    .addOnCompleteListener {
                        _uiState.value = SellVehicleState.Success
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to publish vehicle", e)
                _uiState.value = SellVehicleState.Error(
                    e.localizedMessage ?: "Failed to list vehicle. Please try again."
                )
            }
    }

    fun resetState() {
        _title.value = ""
        _brand.value = ""
        _model.value = ""
        _year.value = ""
        _price.value = ""
        _pendingImageUris.value = emptyList()
        _description.value = ""
        _sellerPhone.value = ""
        _sellerWhatsapp.value = ""
        _uiState.value = SellVehicleState.Idle
        updateAvailableModels()
    }
}
