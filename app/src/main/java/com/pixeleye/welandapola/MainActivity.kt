package com.pixeleye.welandapola

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pixeleye.welandapola.ui.screens.BuyerDashboardScreen
import com.pixeleye.welandapola.ui.screens.BookmarksScreen
import com.pixeleye.welandapola.ui.screens.CategoryListingsScreen
import com.pixeleye.welandapola.ui.screens.HomeScreen
import com.pixeleye.welandapola.ui.screens.LoginScreen
import com.pixeleye.welandapola.ui.screens.MyListingsScreen
import com.pixeleye.welandapola.ui.screens.SellVehicleScreen
import com.pixeleye.welandapola.ui.screens.VehicleDetailsScreen
import com.pixeleye.welandapola.ui.screens.PosterProfileScreen
import com.pixeleye.welandapola.ui.theme.AutoMatchTheme

class MainActivity : ComponentActivity() {

    private val pendingVehicleId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Cloudinary MediaManager
        com.pixeleye.welandapola.data.CloudinaryManager.initialize(applicationContext)

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
            }
        }

        // Initialize Notification Alerts and Real-Time Listener
        WelandapolaNotificationListener.initNotificationChannel(applicationContext)
        WelandapolaNotificationListener.startListeningForNewVehicles(applicationContext)

        // Intercept notification clicks from cold launch
        intent.getStringExtra("nav_to_vehicle_id")?.let {
            pendingVehicleId.value = it
            intent.removeExtra("nav_to_vehicle_id")
        }

        setContent {
            AutoMatchTheme {
                val navController = rememberNavController()
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val sharedPrefs = getSharedPreferences("WelandapolaPrefs", android.content.Context.MODE_PRIVATE)
                val hasSeenWelcome = sharedPrefs.getBoolean("has_seen_welcome", false)
                val startDestination = if (currentUser != null) {
                    if (hasSeenWelcome) "buy" else "home"
                } else {
                    "login"
                }

                // Reactive listener for pending deep link notification clicks
                val pendingId by pendingVehicleId
                LaunchedEffect(pendingId) {
                    if (!pendingId.isNullOrBlank()) {
                        pendingVehicleId.value = null // reset immediately to prevent loops
                        navController.safeNavigate("vehicle_details/$pendingId")
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    // Start listening for notifications on login success
                                    WelandapolaNotificationListener.startListeningForNewVehicles(applicationContext)
                                    val hasSeenWelcomePrefs = getSharedPreferences("WelandapolaPrefs", android.content.Context.MODE_PRIVATE)
                                    val seenWelcome = hasSeenWelcomePrefs.getBoolean("has_seen_welcome", false)
                                    val destination = if (seenWelcome) "buy" else "home"
                                    navController.safeNavigate(destination) {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onSellClicked = {
                                    navController.safeNavigate("sell")
                                },
                                onBuyClicked = {
                                    navController.safeNavigate("buy")
                                }
                            )
                        }
                        composable("sell") {
                            SellVehicleScreen(
                                onBackClicked = {
                                    navController.safePopBackStack()
                                }
                            )
                        }
                        composable("buy") {
                            BuyerDashboardScreen(
                                onBackClicked = {
                                    navController.safePopBackStack()
                                },
                                onVehicleClicked = { vehicleId ->
                                    navController.safeNavigate("vehicle_details/$vehicleId")
                                },
                                onSeeAllClicked = { brand, model ->
                                    navController.safeNavigate("category_listings/$brand/$model")
                                },
                                onPostAdClicked = {
                                    navController.safeNavigate("sell")
                                },
                                onManageListingsClicked = {
                                    navController.safeNavigate("my_listings")
                                },
                                onBookmarksClicked = {
                                    navController.safeNavigate("bookmarks")
                                },
                                onLogoutClicked = {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                    navController.safeNavigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("bookmarks") {
                            BookmarksScreen(
                                onBackClicked = {
                                    navController.safePopBackStack()
                                },
                                onVehicleClicked = { vehicleId ->
                                    navController.safeNavigate("vehicle_details/$vehicleId")
                                }
                            )
                        }
                        composable("category_listings/{brand}/{model}") { backStackEntry ->
                            val brand = backStackEntry.arguments?.getString("brand") ?: ""
                            val model = backStackEntry.arguments?.getString("model") ?: ""
                            CategoryListingsScreen(
                                brand = brand,
                                model = model,
                                onBackClicked = {
                                    navController.safePopBackStack()
                                },
                                onVehicleClicked = { vehicleId ->
                                    navController.safeNavigate("vehicle_details/$vehicleId")
                                }
                            )
                        }
                        composable("my_listings") {
                            MyListingsScreen(
                                onBackClicked = {
                                    navController.safePopBackStack()
                                }
                            )
                        }
                        composable("vehicle_details/{vehicleId}") { backStackEntry ->
                            val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: ""
                            VehicleDetailsScreen(
                                vehicleId = vehicleId,
                                onBackClicked = {
                                    navController.safePopBackStack()
                                },
                                onPosterProfileClicked = { sellerUid ->
                                    navController.safeNavigate("poster_profile/$sellerUid")
                                }
                            )
                        }
                        composable("poster_profile/{sellerUid}") { backStackEntry ->
                            val sellerUid = backStackEntry.arguments?.getString("sellerUid") ?: ""
                            PosterProfileScreen(
                                sellerUid = sellerUid,
                                onBackClicked = {
                                    navController.safePopBackStack()
                                },
                                onVehicleClicked = { id ->
                                    navController.safeNavigate("vehicle_details/$id")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Intercept notification clicks from hot background resume
        intent.getStringExtra("nav_to_vehicle_id")?.let {
            pendingVehicleId.value = it
            intent.removeExtra("nav_to_vehicle_id")
        }
    }
}

fun NavController.safeNavigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route, builder)
    }
}

fun NavController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED && previousBackStackEntry != null) {
        popBackStack()
    }
}