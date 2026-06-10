package com.pixeleye.welandapola.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.pixeleye.welandapola.model.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class VehicleDetailsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _vehicle = MutableStateFlow<Vehicle?>(null)
    val vehicle = _vehicle.asStateFlow()

    private val _sellerPhone = MutableStateFlow("+94771234567") // Fallback Sri Lankan phone number
    val sellerPhone = _sellerPhone.asStateFlow()

    private val _sellerName = MutableStateFlow("Seller")
    val sellerName = _sellerName.asStateFlow()

    private val _sellerPhotoUrl = MutableStateFlow<String?>(null)
    val sellerPhotoUrl = _sellerPhotoUrl.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked = _isBookmarked.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    companion object {
        private const val TAG = "VehicleDetailsViewModel"
    }

    fun checkIfBookmarked(context: android.content.Context, vehicleId: String) {
        val sharedPrefs = context.getSharedPreferences("fliplk_prefs", android.content.Context.MODE_PRIVATE)
        val bookmarkedSet = sharedPrefs.getStringSet("bookmarked_ids", emptySet()) ?: emptySet()
        _isBookmarked.value = bookmarkedSet.contains(vehicleId)
    }

    fun toggleBookmark(context: android.content.Context, vehicleId: String) {
        val sharedPrefs = context.getSharedPreferences("fliplk_prefs", android.content.Context.MODE_PRIVATE)
        val bookmarkedSet = sharedPrefs.getStringSet("bookmarked_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (bookmarkedSet.contains(vehicleId)) {
            bookmarkedSet.remove(vehicleId)
            _isBookmarked.value = false
        } else {
            bookmarkedSet.add(vehicleId)
            _isBookmarked.value = true
        }
        sharedPrefs.edit().putStringSet("bookmarked_ids", bookmarkedSet).apply()
    }

    fun loadVehicleDetails(vehicleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = db.collection("vehicles")
                    .document(vehicleId)
                    .get()
                    .await()
                
                val veh = snapshot.toObject(Vehicle::class.java)
                if (veh != null) {
                    _vehicle.value = veh
                    if (!veh.sellerPhone.isNullOrBlank()) {
                        _sellerPhone.value = veh.sellerPhone
                    }
                    fetchSellerProfile(veh.sellerUid, veh.sellerPhone)
                } else {
                    _vehicle.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load vehicle details", e)
                _vehicle.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchSellerProfile(sellerUid: String, fallbackPhone: String) {
        viewModelScope.launch {
            try {
                val userSnapshot = db.collection("users").document(sellerUid).get().await()
                val phone = userSnapshot.getString("phoneNumber")
                if (!phone.isNullOrBlank()) {
                    _sellerPhone.value = phone
                } else if (fallbackPhone.isNotEmpty()) {
                    _sellerPhone.value = fallbackPhone
                }
                val name = userSnapshot.getString("displayName")
                if (!name.isNullOrBlank()) {
                    _sellerName.value = name
                }
                val photoUrl = userSnapshot.getString("profilePictureUrl")
                _sellerPhotoUrl.value = photoUrl
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch seller profile details", e)
                if (fallbackPhone.isNotEmpty()) {
                    _sellerPhone.value = fallbackPhone
                }
            }
        }
    }

    fun reportVehicle(vehicleId: String, reason: String, reportedByUid: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val reportData = hashMapOf(
                    "vehicleId" to vehicleId,
                    "reason" to reason,
                    "reportedByUid" to reportedByUid,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("reports").document().set(reportData).await()
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit report", e)
                onComplete(false)
            }
        }
    }
}
