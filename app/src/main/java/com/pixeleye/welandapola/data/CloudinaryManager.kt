package com.pixeleye.welandapola.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.pixeleye.welandapola.BuildConfig

/**
 * Helper class for managing media uploads and configurations
 * using the Cloudinary Android SDK.
 */
object CloudinaryManager {

    private var isInitialized = false
    private const val TAG = "CloudinaryManager"

    // Read from the dynamically injected BuildConfig
    var cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
    var apiKey = BuildConfig.CLOUDINARY_API_KEY
    var apiSecret = BuildConfig.CLOUDINARY_API_SECRET

    /**
     * Initializes Cloudinary MediaManager.
     * Call this in your Application class or MainActivity.
     */
    fun initialize(context: Context) {
        if (!isInitialized) {
            try {
                val config = mapOf(
                    "cloud_name" to cloudName,
                    "api_key" to apiKey,
                    "api_secret" to apiSecret
                )
                MediaManager.init(context, config)
                isInitialized = true
                Log.d(TAG, "Cloudinary MediaManager initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Cloudinary MediaManager", e)
            }
        }
    }

    /**
     * Asynchronously uploads an image file Uri to Cloudinary.
     * Triggers onSuccess with the secure Cloudinary image URL.
     */
    fun uploadImage(
        uri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (cloudName == "your_cloud_name" || cloudName.isEmpty()) {
            Log.e(TAG, "Cloudinary is not configured.")
            onFailure("Cloudinary is not configured.")
            return
        }

        try {
            MediaManager.get().upload(uri)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        Log.d(TAG, "Upload started: $requestId")
                    }

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        val progress = (bytes.toFloat() / totalBytes) * 100
                        Log.d(TAG, "Upload progress for $requestId: $progress%")
                    }

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val secureUrl = resultData["secure_url"] as? String ?: ""
                        val optimizedUrl = getOptimizedUrl(secureUrl)
                        Log.d(TAG, "Upload success! Optimized URL: $optimizedUrl")
                        onSuccess(optimizedUrl)
                    }

                    override fun onError(requestId: String, error: ErrorInfo?) {
                        val errorDescription = error?.description ?: "Unknown Cloudinary error"
                        Log.e(TAG, "Upload error for $requestId: $errorDescription")
                        onFailure(errorDescription)
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo?) {
                        Log.w(TAG, "Upload rescheduled: $requestId")
                        onFailure("Upload rescheduled")
                    }
                })
                .dispatch()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Cloudinary upload", e)
            onFailure(e.localizedMessage ?: "Failed to upload image")
        }
    }

    /**
     * Converts a raw Cloudinary URL into an optimized delivery URL
     * using the "f_auto,q_auto" transforms (Automatic format & quality compression).
     */
    fun getOptimizedUrl(url: String): String {
        if (url.contains("cloudinary.com") && url.contains("/upload/")) {
            return url.replace("/upload/", "/upload/f_auto,q_auto/")
        }
        return url
    }

    /**
     * Extracts the Cloudinary public ID from a full secure URL.
     * Handles URLs with or without transformation segments like f_auto,q_auto.
     * Example URL: https://res.cloudinary.com/CLOUD/image/upload/f_auto,q_auto/v123456/abc123.jpg
     * Returns: "abc123"
     */
    fun extractPublicId(url: String): String? {
        if (!url.contains("cloudinary.com")) return null
        try {
            // Remove query parameters
            val cleanUrl = url.split("?").first()
            // Find the /upload/ segment and take everything after it
            val uploadIndex = cleanUrl.indexOf("/upload/")
            if (uploadIndex == -1) return null
            val afterUpload = cleanUrl.substring(uploadIndex + "/upload/".length)
            // Split remaining path by /
            val segments = afterUpload.split("/")
            // Filter out transformation segments (contain commas like f_auto,q_auto)
            // and version segments (start with 'v' followed by digits)
            val meaningfulSegments = segments.filter { segment ->
                !segment.contains(",") && !segment.matches(Regex("^v\\d+$"))
            }
            if (meaningfulSegments.isEmpty()) return null
            // Join remaining segments and strip file extension from the last one
            val lastSegment = meaningfulSegments.last().substringBeforeLast(".")
            val prefix = meaningfulSegments.dropLast(1)
            return (prefix + lastSegment).joinToString("/")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract public ID from URL: $url", e)
            return null
        }
    }

    /**
     * Deletes an image from Cloudinary by its URL.
     * Uses the Cloudinary Admin API with HTTP Basic Auth.
     */
    fun deleteImage(imageUrl: String) {
        val publicId = extractPublicId(imageUrl)
        if (publicId == null) {
            Log.w(TAG, "Cannot delete non-Cloudinary image: $imageUrl")
            return
        }

        if (cloudName.isEmpty() || cloudName == "your_cloud_name") {
            Log.w(TAG, "Cloudinary not configured. Skipping delete for: $publicId")
            return
        }

        // Use a background thread for the HTTP delete call
        Thread {
            try {
                val apiUrl = "https://api.cloudinary.com/v1_1/$cloudName/image/destroy"
                val url = java.net.URL(apiUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                // HTTP Basic Auth with api_key:api_secret
                val credentials = "$apiKey:$apiSecret"
                val encoded = android.util.Base64.encodeToString(
                    credentials.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                connection.setRequestProperty("Authorization", "Basic $encoded")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val body = "public_id=${java.net.URLEncoder.encode(publicId, "UTF-8")}"
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    Log.d(TAG, "Successfully deleted Cloudinary image: $publicId (HTTP $responseCode)")
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                    Log.e(TAG, "Failed to delete Cloudinary image: $publicId (HTTP $responseCode) - $errorBody")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Exception deleting Cloudinary image: $publicId", e)
            }
        }.start()
    }

    /**
     * Deletes multiple images from Cloudinary by their URLs.
     */
    fun deleteImages(imageUrls: List<String>) {
        imageUrls.forEach { deleteImage(it) }
    }
}
