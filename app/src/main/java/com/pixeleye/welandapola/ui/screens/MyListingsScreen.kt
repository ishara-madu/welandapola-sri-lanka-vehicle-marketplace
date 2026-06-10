package com.pixeleye.welandapola.ui.screens

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pixeleye.welandapola.model.Vehicle
import com.pixeleye.welandapola.data.CloudinaryManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyListingsViewModel = viewModel()
) {
    val listings by viewModel.listings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val brandsList by viewModel.brands.collectAsState()

    var activeDeleteVehicleId by remember { mutableStateOf<String?>(null) }
    var activeEditVehicle by remember { mutableStateOf<Vehicle?>(null) }

    // State parameters for Edit Dialog
    var editTitle by remember { mutableStateOf("") }
    var editBrand by remember { mutableStateOf("") }
    var editModel by remember { mutableStateOf("") }
    var editYear by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editWhatsapp by remember { mutableStateOf("") }
    var editImageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var originalEditImageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    // New local images selected during edit (not yet uploaded)
    var pendingEditUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isEditSaving by remember { mutableStateOf(false) }

    // Fullscreen Image Overlay State
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    // Launcher for editing gallery images (stores locally, uploads on save)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val totalCurrent = editImageUrls.size + pendingEditUris.size
        val remainingSlots = 5 - totalCurrent
        if (remainingSlots > 0) {
            pendingEditUris = pendingEditUris + uris.take(remainingSlots)
        }
    }

    // Deletion Modal Dialog
    if (activeDeleteVehicleId != null) {
        AlertDialog(
            onDismissRequest = { activeDeleteVehicleId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Listing?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Are you sure you want to permanently delete this listing from Welandapola? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeDeleteVehicleId?.let { id ->
                            viewModel.deleteListing(id)
                        }
                        activeDeleteVehicleId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDeleteVehicleId = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    // Edit Modal Dialog
    if (activeEditVehicle != null) {
        val brandNames = brandsList.map { it.name }
        val selectedBrandMetadata = brandsList.firstOrNull { it.name.equals(editBrand, ignoreCase = true) }
        val availableModels = selectedBrandMetadata?.models ?: emptyList()

        AlertDialog(
            onDismissRequest = { activeEditVehicle = null },
            title = { Text("Edit Vehicle Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Update the vehicle catalog listing instantly. Brand & model list matches the admin configuration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Photo gallery edit section
                    val totalEditImages = editImageUrls.size + pendingEditUris.size
                    Text(
                        text = "Vehicle Photos ($totalEditImages/5)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Existing Cloudinary URLs
                        editImageUrls.forEachIndexed { index, url ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE9ECEF))
                            ) {
                                AsyncImage(
                                    model = CloudinaryManager.getOptimizedUrl(url),
                                    contentDescription = "Edit Photo $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (index == 0 && pendingEditUris.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.85f))
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Cover",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .align(Alignment.TopEnd)
                                        .clickable {
                                            editImageUrls = editImageUrls.filterIndexed { idx, _ -> idx != index }
                                        }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // Pending local URIs (new images not yet uploaded)
                        pendingEditUris.forEachIndexed { index, uri ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE9ECEF))
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "New Photo $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .align(Alignment.TopEnd)
                                        .clickable {
                                            pendingEditUris = pendingEditUris.filterIndexed { idx, _ -> idx != index }
                                        }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove New Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        if (totalEditImages < 5) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF8F9FA))
                                    .clickable { imagePickerLauncher.launch("image/*") }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Add Photo",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Listing Title *") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Edit Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SearchableDropdownField(
                                label = "Brand *",
                                value = editBrand,
                                options = brandNames,
                                onOptionSelected = {
                                    editBrand = it
                                    editModel = "" // reset model selection
                                },
                                leadingIcon = Icons.Default.Build
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            SearchableDropdownField(
                                label = "Model *",
                                value = editModel,
                                options = availableModels,
                                enabled = editBrand.isNotEmpty(),
                                onOptionSelected = { editModel = it },
                                leadingIcon = Icons.Default.DirectionsCar,
                                disabledMessage = "Select Brand First"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = editYear,
                            onValueChange = { editYear = it },
                            label = { Text("Year *") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = "Year") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = editPrice,
                            onValueChange = { editPrice = it },
                            label = { Text("Price (LKR) *") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = "Price") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // seller mobile number section (Required)
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Mobile Number *") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // seller whatsapp number section (Optional)
                    OutlinedTextField(
                        value = editWhatsapp,
                        onValueChange = { editWhatsapp = it },
                        label = { Text("WhatsApp Number (Optional)") },
                        leadingIcon = { Icon(Icons.Default.Send, contentDescription = "WhatsApp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = "Description") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val yearInt = editYear.toIntOrNull()
                        val priceDouble = editPrice.toDoubleOrNull()
                        val vehicleId = activeEditVehicle?.id

                        if (vehicleId != null && editTitle.isNotBlank() && editBrand.isNotBlank() && editModel.isNotBlank() && yearInt != null && priceDouble != null && editPhone.isNotBlank()) {
                            if (pendingEditUris.isNotEmpty()) {
                                isEditSaving = true
                                val remaining = pendingEditUris.size
                                var uploadsCompleted = 0
                                val uploadedUrlsArray = arrayOfNulls<String>(remaining)
                                for (i in pendingEditUris.indices) {
                                    val uri = pendingEditUris[i]
                                    com.pixeleye.welandapola.data.CloudinaryManager.uploadImage(
                                        uri = uri,
                                        onSuccess = { url ->
                                            synchronized(uploadedUrlsArray) {
                                                uploadedUrlsArray[i] = url
                                                uploadsCompleted++
                                                if (uploadsCompleted == remaining) {
                                                    val orderedUploaded = uploadedUrlsArray.filterNotNull()
                                                    val combinedUrls = editImageUrls + orderedUploaded
                                                    viewModel.updateListing(
                                                        vehicleId = vehicleId,
                                                        title = editTitle,
                                                        brand = editBrand,
                                                        model = editModel,
                                                        year = yearInt,
                                                        price = priceDouble,
                                                        description = editDescription,
                                                        sellerPhone = editPhone,
                                                        sellerWhatsapp = editWhatsapp,
                                                        imageUrls = combinedUrls,
                                                        originalImageUrls = originalEditImageUrls
                                                    )
                                                    isEditSaving = false
                                                    activeEditVehicle = null
                                                }
                                            }
                                        },
                                        onFailure = {
                                            synchronized(uploadedUrlsArray) {
                                                uploadsCompleted++
                                                if (uploadsCompleted == remaining) {
                                                    val orderedUploaded = uploadedUrlsArray.filterNotNull()
                                                    val combinedUrls = editImageUrls + orderedUploaded
                                                    viewModel.updateListing(
                                                        vehicleId = vehicleId,
                                                        title = editTitle,
                                                        brand = editBrand,
                                                        model = editModel,
                                                        year = yearInt,
                                                        price = priceDouble,
                                                        description = editDescription,
                                                        sellerPhone = editPhone,
                                                        sellerWhatsapp = editWhatsapp,
                                                        imageUrls = combinedUrls,
                                                        originalImageUrls = originalEditImageUrls
                                                    )
                                                    isEditSaving = false
                                                    activeEditVehicle = null
                                                }
                                            }
                                        }
                                    )
                                }
                            } else {
                                viewModel.updateListing(
                                    vehicleId = vehicleId,
                                    title = editTitle,
                                    brand = editBrand,
                                    model = editModel,
                                    year = yearInt,
                                    price = priceDouble,
                                    description = editDescription,
                                    sellerPhone = editPhone,
                                    sellerWhatsapp = editWhatsapp,
                                    imageUrls = editImageUrls,
                                    originalImageUrls = originalEditImageUrls
                                )
                                activeEditVehicle = null
                            }
                        }
                    },
                    enabled = !isEditSaving,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isEditSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Text("Save Changes")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { activeEditVehicle = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Listings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8F9FA),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8F9FA))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (listings.isEmpty() && uiState !is MyListingsState.Loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Car",
                                tint = Color.LightGray,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No listings posted yet.",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Go to the 'Sell Vehicle' page to publish your first vehicle list!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(listings) { vehicle ->
                            SellerListingCard(
                                vehicle = vehicle,
                                onImageClicked = { fullscreenImageUrl = it },
                                onEditClicked = {
                                    activeEditVehicle = vehicle
                                    editTitle = vehicle.title
                                    editBrand = vehicle.brand
                                    editModel = vehicle.model
                                    editYear = vehicle.year.toString()
                                    editPrice = vehicle.price.toString()
                                    editDescription = vehicle.description
                                    editPhone = vehicle.sellerPhone
                                    editWhatsapp = vehicle.sellerWhatsapp
                                    editImageUrls = if (vehicle.imageUrls.isNotEmpty()) vehicle.imageUrls else listOf(vehicle.imageUrl)
                                    originalEditImageUrls = editImageUrls
                                    pendingEditUris = emptyList()
                                },
                                onDeleteClicked = {
                                    activeDeleteVehicleId = vehicle.id
                                }
                            )
                        }
                    }
                }
            }

            // Error Toast Message overlay
            AnimatedVisibility(
                visible = uiState is MyListingsState.Error,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) {
                val state = uiState
                if (state is MyListingsState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Error",
                                tint = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = state.message, color = Color(0xFFC62828))
                        }
                    }
                }
            }

            if (uiState is MyListingsState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Fullscreen image overlay (dismissible on click)
    fullscreenImageUrl?.let { url ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { fullscreenImageUrl = null },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = CloudinaryManager.getOptimizedUrl(url),
                contentDescription = "Fullscreen view",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
            )

            IconButton(
                onClick = { fullscreenImageUrl = null },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun SellerListingCard(
    vehicle: Vehicle,
    onImageClicked: (String) -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Listing image thumbnail
                AsyncImage(
                    model = CloudinaryManager.getOptimizedUrl(vehicle.imageUrl),
                    contentDescription = vehicle.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImageClicked(CloudinaryManager.getOptimizedUrl(vehicle.imageUrl)) }
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicle.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${vehicle.brand} • ${vehicle.model} • ${vehicle.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "LKR ${String.format("%,.0f", vehicle.price)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    val relativeTime = DateUtils.getRelativeTimeSpanString(
                        vehicle.createdAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Listed $relativeTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFF1F3F5))
            )

            // Edit & Delete Action Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAFAFA))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Button
                IconButton(onClick = onEditClicked) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Listing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Delete Button
                IconButton(onClick = onDeleteClicked) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Listing",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
