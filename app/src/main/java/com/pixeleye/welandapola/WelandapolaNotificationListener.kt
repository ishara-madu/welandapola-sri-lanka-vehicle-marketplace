package com.pixeleye.welandapola

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.pixeleye.welandapola.model.Vehicle
import com.pixeleye.welandapola.model.VehicleRequest

object WelandapolaNotificationListener {
    private const val TAG = "WelandapolaNotification"
    private const val CHANNEL_ID = "welandapola_alerts"
    
    private var isListening = false
    private val startTime = System.currentTimeMillis()

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Welandapola Category Alerts"
            val descriptionText = "Alerts for tracked vehicle categories on Welandapola"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel registered successfully.")
        }
    }

    fun startListeningForNewVehicles(context: Context) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val buyerUid = currentUser.uid

        if (isListening) return
        isListening = true

        val db = FirebaseFirestore.getInstance()

        Log.d(TAG, "Starting notifications observer for buyer: $buyerUid")

        // 1. Listen to buyer's tracked requests in real time
        db.collection("vehicle_requests")
            .whereEqualTo("buyerUid", buyerUid)
            .addSnapshotListener { requestsSnapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to requests: ", e)
                    return@addSnapshotListener
                }

                val trackedRequests = requestsSnapshot?.toObjects(VehicleRequest::class.java) ?: emptyList()
                if (trackedRequests.isEmpty()) {
                    Log.d(TAG, "No tracked categories found for buyer $buyerUid")
                    return@addSnapshotListener
                }

                Log.d(TAG, "Updated tracked categories for notifications: ${trackedRequests.size} categories.")

                // 2. Listen to newly created vehicles
                db.collection("vehicles")
                    .whereGreaterThanOrEqualTo("createdAt", startTime)
                    .addSnapshotListener { vehiclesSnapshot, ve ->
                        if (ve != null) {
                            Log.e(TAG, "Error listening to vehicles: ", ve)
                            return@addSnapshotListener
                        }

                        if (vehiclesSnapshot != null) {
                            for (change in vehiclesSnapshot.documentChanges) {
                                if (change.type == DocumentChange.Type.ADDED) {
                                    val vehicle = change.document.toObject(Vehicle::class.java)
                                    // Check if the added vehicle matches any tracked brand & model
                                    val match = trackedRequests.any { req ->
                                        req.vehicleId.equals(vehicle.brand, ignoreCase = true) &&
                                        req.message.equals(vehicle.model, ignoreCase = true)
                                    }
                                    if (match && vehicle.sellerUid != buyerUid) {
                                        triggerSystemNotification(context, vehicle)
                                    }
                                }
                            }
                        }
                    }
            }
    }

    @SuppressLint("MissingPermission")
    private fun triggerSystemNotification(context: Context, vehicle: Vehicle) {
        val sharedPrefs = context.getSharedPreferences("WelandapolaPrefs", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPrefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications are disabled by user preference. Skipping.")
            return
        }

        initNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("nav_to_vehicle_id", vehicle.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            vehicle.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New tracked listing on Welandapola!")
            .setContentText("A new ${vehicle.brand} ${vehicle.model} was just added for LKR ${String.format("%,.0f", vehicle.price)}!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(vehicle.id.hashCode(), builder.build())
            Log.d(TAG, "Triggered notification for vehicle: ${vehicle.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger system notification: ", e)
        }
    }
}
