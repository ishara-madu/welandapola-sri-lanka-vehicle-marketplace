package com.pixeleye.welandapola.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    object Success : AuthState
    data class Error(val message: String) : AuthState
}

class LoginViewModel : ViewModel() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _isSignUpMode = MutableStateFlow(false)
    val isSignUpMode = _isSignUpMode.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    companion object {
        private const val TAG = "LoginViewModel"
    }

    fun onEmailChange(value: String) {
        _email.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun toggleMode() {
        _isSignUpMode.value = !_isSignUpMode.value
        _authState.value = AuthState.Idle
    }

    private fun saveUserDataToFirestore() {
        val user = auth.currentUser ?: return
        val docRef = firestore.collection("users").document(user.uid)
        
        docRef.get().addOnSuccessListener { document ->
            val userMap = hashMapOf<String, Any>(
                "uid" to user.uid,
                "email" to (user.email ?: "")
            )
            
            val defaultDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: user.phoneNumber ?: "User"
            
            if (!document.exists()) {
                userMap["createdAt"] = System.currentTimeMillis()
                userMap["displayName"] = defaultDisplayName
                val authPhone = user.phoneNumber
                if (!authPhone.isNullOrEmpty()) {
                    userMap["phoneNumber"] = authPhone
                }
            } else {
                val existingName = document.getString("displayName")
                if (existingName.isNullOrEmpty()) {
                    userMap["displayName"] = defaultDisplayName
                }
                
                val existingPhone = document.getString("phoneNumber")
                val authPhone = user.phoneNumber
                if (existingPhone.isNullOrEmpty() && !authPhone.isNullOrEmpty()) {
                    userMap["phoneNumber"] = authPhone
                }
            }
            
            val existingPhoto = document.getString("profilePictureUrl")
            if (existingPhoto.isNullOrEmpty()) {
                val googlePhoto = user.photoUrl?.toString()
                if (!googlePhoto.isNullOrEmpty()) {
                    userMap["profilePictureUrl"] = googlePhoto
                }
            }
            
            docRef.set(userMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "User data successfully saved to Firestore.")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save user data to Firestore", e)
                }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check user document in Firestore", e)
        }
    }

    fun performAuthAction() {
        val emailStr = _email.value.trim()
        val passwordStr = _password.value.trim()

        if (emailStr.isEmpty() || passwordStr.isEmpty()) {
            _authState.value = AuthState.Error("Please fill in both email and password.")
            return
        }

        if (passwordStr.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters long.")
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            if (_isSignUpMode.value) {
                // Register User
                auth.createUserWithEmailAndPassword(emailStr, passwordStr)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "createUserWithEmail:success")
                            saveUserDataToFirestore()
                            _authState.value = AuthState.Success
                        } else {
                            Log.e(TAG, "createUserWithEmail:failure", task.exception)
                            _authState.value = AuthState.Error(
                                task.exception?.localizedMessage ?: "Registration failed. Try again."
                            )
                        }
                    }
            } else {
                // Login User
                auth.signInWithEmailAndPassword(emailStr, passwordStr)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "signInWithEmail:success")
                            saveUserDataToFirestore()
                            _authState.value = AuthState.Success
                        } else {
                            Log.e(TAG, "signInWithEmail:failure", task.exception)
                            _authState.value = AuthState.Error(
                                task.exception?.localizedMessage ?: "Incorrect email or password."
                            )
                        }
                    }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithGoogle:success")
                    saveUserDataToFirestore()
                    _authState.value = AuthState.Success
                } else {
                    Log.e(TAG, "signInWithGoogle:failure", task.exception)
                    _authState.value = AuthState.Error(
                        task.exception?.localizedMessage ?: "Google Sign-in failed."
                    )
                }
            }
    }

    fun setAuthError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
