package com.pixeleye.welandapola.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pixeleye.welandapola.model.Vehicle
import com.pixeleye.welandapola.model.VehicleRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BuyerDashboardViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _trackedCategories = MutableStateFlow<List<VehicleRequest>>(emptyList())
    val trackedCategories = _trackedCategories.asStateFlow()

    private val _vehiclesMap = MutableStateFlow<Map<String, List<Vehicle>>>(emptyMap())
    val vehiclesMap = _vehiclesMap.asStateFlow()

    private val _bookmarkedVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val bookmarkedVehicles = _bookmarkedVehicles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _userProfilePictureUrl = MutableStateFlow<String?>(null)
    val userProfilePictureUrl = _userProfilePictureUrl.asStateFlow()

    private val _userDisplayName = MutableStateFlow<String?>(null)
    val userDisplayName = _userDisplayName.asStateFlow()

    private val _userPhoneNumber = MutableStateFlow<String?>(null)
    val userPhoneNumber = _userPhoneNumber.asStateFlow()

    private val _isProfileUploading = MutableStateFlow(false)
    val isProfileUploading = _isProfileUploading.asStateFlow()

    private var userListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    // Dynamic brand and model lists loaded from Firestore
    private val _brands = MutableStateFlow<List<BrandMetadata>>(emptyList())
    val brands = _brands.asStateFlow()

    // Popular default local fallbacks (Sri Lanka market focused)
    private val defaultBrands = listOf(
        BrandMetadata("bajaj", "Bajaj", listOf("CT100", "Pulsar 150", "Discover 125", "Platina 100", "Three Wheeler", "RE")),
        BrandMetadata("yamaha", "Yamaha", listOf("FZ S", "R15", "RayZR", "FZ25", "Y15ZR", "WR155R")),
        BrandMetadata("honda", "Honda", listOf("Dio", "Hornet 160R", "CB Shine", "CD70", "Super Cub", "Jade")),
        BrandMetadata("suzuki", "Suzuki", listOf("Gixxer", "Intruder", "Access 125", "Burgman Street", "Swift")),
        BrandMetadata("tvs", "TVS", listOf("Apache RTR", "Ntorq 125", "Wego", "XL100", "King"))
    )

    companion object {
        private const val TAG = "BuyerDashboardViewModel"
    }

    init {
        loadDashboardData()
        loadBrandsFromFirestore()
        loadUserProfile()
    }

    private fun loadBrandsFromFirestore() {
        db.collection("brands")
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
                    Log.d(TAG, "Brands loaded successfully: ${list.size} entries.")
                } else {
                    _brands.value = defaultBrands
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load brands from Firestore. Using presets.", e)
                _brands.value = defaultBrands
            }
    }

    fun loadBookmarks(context: android.content.Context) {
        val sharedPrefs = context.getSharedPreferences("fliplk_prefs", android.content.Context.MODE_PRIVATE)
        val bookmarkedSet = sharedPrefs.getStringSet("bookmarked_ids", emptySet()) ?: emptySet()
        
        viewModelScope.launch {
            try {
                val list = mutableListOf<Vehicle>()
                for (id in bookmarkedSet) {
                    val doc = db.collection("vehicles").document(id).get().await()
                    if (doc != null && doc.exists()) {
                        doc.toObject(Vehicle::class.java)?.let { list.add(it) }
                    }
                }
                _bookmarkedVehicles.value = list.sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bookmarked vehicles", e)
            }
        }
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            val currentUser = auth.currentUser
            val buyerId = currentUser?.uid

            if (buyerId == null) {
                Log.d(TAG, "No logged-in user.")
                _trackedCategories.value = emptyList()
                _vehiclesMap.value = emptyMap()
                _isLoading.value = false
                return@launch
            }

            try {
                // Fetch real buyer's tracked requests from Firestore
                val requestsSnapshot = db.collection("vehicle_requests")
                    .whereEqualTo("buyerUid", buyerId)
                    .get()
                    .await()

                val requestsList = requestsSnapshot.toObjects(VehicleRequest::class.java)
                _trackedCategories.value = requestsList
                
                val newMap = mutableMapOf<String, List<Vehicle>>()
                
                for (request in requestsList) {
                    val vehiclesSnapshot = db.collection("vehicles")
                        .whereEqualTo("brand", request.vehicleId)
                        .whereEqualTo("model", request.message)
                        .get()
                        .await()
                    
                    val vehiclesList = vehiclesSnapshot.toObjects(Vehicle::class.java)
                        .sortedByDescending { it.createdAt }
                    newMap[request.id] = vehiclesList
                }
                _vehiclesMap.value = newMap
                Log.d(TAG, "Loaded ${requestsList.size} real category watchlists from Firestore.")
            } catch (e: Exception) {
                Log.e(TAG, "Firestore error fetching buyer requests. Please verify your Firestore rules.", e)
                // Keep lists empty to show actual dashboard state, preventing masking of permissions errors
                _trackedCategories.value = emptyList()
                _vehiclesMap.value = emptyMap()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addCategory(brand: String, model: String) {
        val isAlreadyTracked = _trackedCategories.value.any {
            it.vehicleId.equals(brand, ignoreCase = true) && it.message.equals(model, ignoreCase = true)
        }
        if (isAlreadyTracked) {
            Log.d(TAG, "Category $brand $model is already tracked.")
            return
        }
        viewModelScope.launch {
            val currentUser = auth.currentUser
            val buyerId = currentUser?.uid
            
            if (buyerId == null) {
                Log.e(TAG, "Cannot add category: No authenticated user.")
                return@launch
            }

            // Real Firestore database insertion
            val newRequest = VehicleRequest(
                id = db.collection("vehicle_requests").document().id,
                vehicleId = brand,
                buyerUid = buyerId,
                sellerUid = "",
                message = model,
                status = "ACTIVE"
            )

            try {
                db.collection("vehicle_requests").document(newRequest.id).set(newRequest).await()
                
                // Fetch matching vehicles in Firestore
                val vehiclesSnapshot = db.collection("vehicles")
                    .whereEqualTo("brand", brand)
                    .whereEqualTo("model", model)
                    .get()
                    .await()
                val realVehiclesList = vehiclesSnapshot.toObjects(Vehicle::class.java)
                    .sortedByDescending { it.createdAt }

                val currentList = _trackedCategories.value.toMutableList()
                currentList.add(newRequest)
                _trackedCategories.value = currentList

                val currentMap = _vehiclesMap.value.toMutableMap()
                currentMap[newRequest.id] = realVehiclesList
                _vehiclesMap.value = currentMap
                Log.d(TAG, "Real watchlist category saved in Firestore: ${newRequest.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write category tracking to Firestore. Please check security rules.", e)
            }
        }
    }

    fun removeCategory(requestId: String) {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            val buyerId = currentUser?.uid

            if (buyerId == null) {
                Log.e(TAG, "Cannot remove category: No authenticated user.")
                return@launch
            }

            try {
                db.collection("vehicle_requests").document(requestId).delete().await()

                _trackedCategories.value = _trackedCategories.value.filter { it.id != requestId }
                val currentMap = _vehiclesMap.value.toMutableMap()
                currentMap.remove(requestId)
                _vehiclesMap.value = currentMap
                Log.d(TAG, "Real category watchlist deleted from Firestore: $requestId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove category watchlist from Firestore. Please check security rules.", e)
            }
        }
    }

    fun loadUserProfile() {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: return

        userListenerRegistration?.remove()
        userListenerRegistration = db.collection("users").document(uid)
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    _userProfilePictureUrl.value = documentSnapshot.getString("profilePictureUrl")
                    _userDisplayName.value = documentSnapshot.getString("displayName")
                    val profilePhone = documentSnapshot.getString("phoneNumber")
                    if (!profilePhone.isNullOrBlank()) {
                        // Profile has a phone number — use it directly
                        _userPhoneNumber.value = profilePhone
                    } else {
                        // No profile phone — fall back to most recent vehicle post's sellerPhone
                        fetchPhoneFromLatestPost(uid)
                    }
                } else {
                    _userProfilePictureUrl.value = currentUser.photoUrl?.toString()
                    _userDisplayName.value = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "User"
                    // No user document — try to get phone from most recent post
                    fetchPhoneFromLatestPost(uid)
                }
            }
    }

    private fun fetchPhoneFromLatestPost(uid: String) {
        db.collection("vehicles")
            .whereEqualTo("sellerUid", uid)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val latestPhone = querySnapshot.documents
                    .firstOrNull()
                    ?.getString("sellerPhone")
                    ?.takeIf { it.isNotBlank() }
                // Set null if no post or no phone — UI will hide the phone row
                _userPhoneNumber.value = latestPhone
                Log.d(TAG, "Phone from latest post: $latestPhone")
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Failed to fetch phone from latest vehicle post", err)
                _userPhoneNumber.value = null
            }
    }

    fun uploadProfilePicture(uri: android.net.Uri, context: android.content.Context) {
        val uid = auth.currentUser?.uid ?: return
        val oldUrl = _userProfilePictureUrl.value
        _isProfileUploading.value = true
        
        com.pixeleye.welandapola.data.CloudinaryManager.initialize(context)
        com.pixeleye.welandapola.data.CloudinaryManager.uploadImage(
            uri = uri,
            onSuccess = { optimizedUrl ->
                db.collection("users").document(uid)
                    .update("profilePictureUrl", optimizedUrl)
                    .addOnSuccessListener {
                        Log.d(TAG, "Profile picture URL updated in Firestore: $optimizedUrl")
                        _isProfileUploading.value = false
                        // Delete the old profile picture if it was a Cloudinary image
                        if (!oldUrl.isNullOrEmpty()) {
                            com.pixeleye.welandapola.data.CloudinaryManager.deleteImage(oldUrl)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update profile picture URL in Firestore, trying set", e)
                        val updateMap = hashMapOf("profilePictureUrl" to optimizedUrl)
                        db.collection("users").document(uid)
                            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d(TAG, "Profile picture URL set in Firestore: $optimizedUrl")
                                _isProfileUploading.value = false
                                // Delete the old profile picture if it was a Cloudinary image
                                if (!oldUrl.isNullOrEmpty()) {
                                    com.pixeleye.welandapola.data.CloudinaryManager.deleteImage(oldUrl)
                                }
                            }
                            .addOnFailureListener { err ->
                                Log.e(TAG, "Failed to set profile picture URL in Firestore", err)
                                _isProfileUploading.value = false
                            }
                    }
            },
            onFailure = { error ->
                Log.e(TAG, "Cloudinary profile upload failed: $error")
                _isProfileUploading.value = false
            }
        )
    }

    fun updateUserProfileDetails(
        newName: String,
        newPhone: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        val updateMap = hashMapOf<String, Any>(
            "displayName" to newName,
            "phoneNumber" to newPhone
        )

        db.collection("users").document(uid)
            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "User profile details successfully updated in Firestore.")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update user profile details in Firestore", e)
                onFailure(e)
            }
    }

    fun changePassword(
        oldPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) {
            onFailure(Exception("User not authenticated."))
            return
        }

        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, oldPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        Log.d(TAG, "Password updated successfully.")
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update password.", e)
                        onFailure(e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Re-authentication failed.", e)
                onFailure(e)
            }
    }

    override fun onCleared() {
        super.onCleared()
        userListenerRegistration?.remove()
    }
}
