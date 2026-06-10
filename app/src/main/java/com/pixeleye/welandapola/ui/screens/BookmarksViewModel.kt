package com.pixeleye.welandapola.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.pixeleye.welandapola.model.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BookmarksViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    
    private val _bookmarkedVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val bookmarkedVehicles = _bookmarkedVehicles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    companion object {
        private const val TAG = "BookmarksViewModel"
    }

    fun loadBookmarks(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val sharedPrefs = context.getSharedPreferences("fliplk_prefs", Context.MODE_PRIVATE)
            val bookmarkedSet = sharedPrefs.getStringSet("bookmarked_ids", emptySet()) ?: emptySet()
            
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
            } finally {
                _isLoading.value = false
            }
        }
    }
}
