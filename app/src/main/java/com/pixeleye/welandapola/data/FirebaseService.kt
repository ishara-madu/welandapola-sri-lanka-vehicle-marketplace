package com.pixeleye.welandapola.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Service class handling Firebase Firestore database operations
 * and Phone Authentication workflows.
 */
class FirebaseService {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirebaseService"
    }

    /**
     * Example: Fetching user profile from Firestore
     */
    fun fetchUserProfile(
        userId: String,
        onSuccess: (Map<String, Any>?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    onSuccess(document.data)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error fetching user profile", exception)
                onFailure(exception)
            }
    }

    /**
     * Example: Getting current logged-in user
     */
    fun getCurrentUser() = auth.currentUser
}
