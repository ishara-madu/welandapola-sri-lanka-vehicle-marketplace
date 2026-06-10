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

class PosterProfileViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _displayName = MutableStateFlow("Seller")
    val displayName = _displayName.asStateFlow()

    private val _profilePictureUrl = MutableStateFlow<String?>(null)
    val profilePictureUrl = _profilePictureUrl.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()

    private val _joinedDate = MutableStateFlow<Long?>(null)
    val joinedDate = _joinedDate.asStateFlow()

    private val _listings = MutableStateFlow<List<Vehicle>>(emptyList())
    val listings = _listings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    companion object {
        private const val TAG = "PosterProfileViewModel"
    }

    fun loadPosterProfile(sellerUid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch profile
                val userSnapshot = db.collection("users").document(sellerUid).get().await()
                if (userSnapshot.exists()) {
                    _displayName.value = userSnapshot.getString("displayName") ?: "Seller"
                    _profilePictureUrl.value = userSnapshot.getString("profilePictureUrl")
                    _email.value = userSnapshot.getString("email") ?: ""
                    _phoneNumber.value = userSnapshot.getString("phoneNumber") ?: ""
                    _joinedDate.value = userSnapshot.getLong("createdAt")
                } else {
                    _displayName.value = "Seller"
                    _profilePictureUrl.value = null
                    _email.value = ""
                    _phoneNumber.value = ""
                    _joinedDate.value = null
                }

                // Fetch listings
                val vehiclesSnapshot = db.collection("vehicles")
                    .whereEqualTo("sellerUid", sellerUid)
                    .get()
                    .await()

                val list = vehiclesSnapshot.toObjects(Vehicle::class.java)
                // Filter only available vehicles and sort descending by creation date
                val activeList = list.filter { it.isAvailable }.sortedByDescending { it.createdAt }
                _listings.value = activeList

                if (_phoneNumber.value.isBlank()) {
                    val fallbackPhone = list.sortedByDescending { it.createdAt }
                        .firstOrNull { !it.sellerPhone.isNullOrBlank() }
                        ?.sellerPhone
                    if (!fallbackPhone.isNullOrBlank()) {
                        _phoneNumber.value = fallbackPhone
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load poster profile and listings", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
