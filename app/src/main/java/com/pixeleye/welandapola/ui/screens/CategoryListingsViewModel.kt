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

class CategoryListingsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _listings = MutableStateFlow<List<Vehicle>>(emptyList())
    val listings = _listings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    companion object {
        private const val TAG = "CategoryListingsViewModel"
    }

    fun loadCategoryListings(brand: String, model: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = db.collection("vehicles")
                    .whereEqualTo("brand", brand)
                    .whereEqualTo("model", model)
                    .get()
                    .await()

                val list = snapshot.toObjects(Vehicle::class.java)
                _listings.value = list.sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load category listings from Firestore", e)
                _listings.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
