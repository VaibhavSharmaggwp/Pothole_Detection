package com.example.driveease

import android.app.Activity
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.location.Location
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.driveease.databinding.ActivityPotholeMainBinding
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import android.net.ConnectivityManager
import android.content.Context
import java.io.EOFException


class Pothole_main : AppCompatActivity() {

    private lateinit var binding: ActivityPotholeMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var currentPhotoPath: String
    private lateinit var placesClient: PlacesClient
    private var selectedImageUri: Uri? = null
    private var currentLocation: Location? = null
    private var userId: Int = -1

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            updateLocationWithGoogleMaps()
        } else {
            Toast.makeText(this, "Location permissions required", Toast.LENGTH_SHORT).show()
            showLocationError("Location permissions denied")
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = Uri.fromFile(File(currentPhotoPath))
            binding.previewImage.setImageURI(selectedImageUri)
            binding.btnCamera.isEnabled = false
            Toast.makeText(this, "Image captured successfully!", Toast.LENGTH_SHORT).show()
            binding.tvLocation.setText("Fetching your location...")
            requestLocationPermission()
        } else {
            Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser == null) {
            val intent = Intent(this, SignActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPotholeMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firebaseAuth = FirebaseAuth.getInstance()

        userId = intent.getIntExtra("userId", -1)
        if (userId == -1) {
            Log.e("PotholeMain", "Invalid userId received")
            Toast.makeText(this, "User ID not found, please sign in again", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SignActivity::class.java))
            finish()
            return
        }

        Places.initialize(applicationContext, getString(R.string.my_map_api_key))
        placesClient = Places.createClient(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupClickListeners()
        testConnection()
    }

    private fun setupClickListeners() {
        binding.apply {
            btnCamera.setOnClickListener {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            btnSubmit.setOnClickListener {
                submitReport()
            }
            btnReview.setOnClickListener {
                val intent = Intent(this@Pothole_main, ReportActivity::class.java)
                intent.putExtra("userId", userId)
                startActivity(intent)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocationWithGoogleMaps() {
        binding.tvLocation.setText("Fetching your location...")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val placeFields = listOf(Place.Field.LAT_LNG, Place.Field.NAME, Place.Field.ADDRESS)
            val request = FindCurrentPlaceRequest.newInstance(placeFields)

            placesClient.findCurrentPlace(request)
                .addOnSuccessListener { response ->
                    val placeLikelihood = response.placeLikelihoods.firstOrNull()
                    placeLikelihood?.let { likelihood ->
                        val place = likelihood.place
                        place.latLng?.let { latLng ->
                            val location = Location("GoogleMapsAPI").apply {
                                latitude = latLng.latitude
                                longitude = latLng.longitude
                            }
                            currentLocation = location
                            val coordinates = "Lat: ${latLng.latitude}, Lng: ${latLng.longitude}"
                            val address = place.address ?: "Address not available"
                            val locationText = "Coordinates: $coordinates\nAddress: $address"
                            binding.tvLocation.setText(locationText)
                            Toast.makeText(this, "Location updated", Toast.LENGTH_SHORT).show()
                        } ?: showLocationError("Invalid coordinates")
                    } ?: showLocationError("No location data available")
                }
                .addOnFailureListener { exception ->
                    Log.e("PotholeMain", "Location error: ${exception.message}")
                    showLocationError(exception.message ?: "Unknown error")
                }
        }
    }

    private fun showLocationError(message: String) {
        Toast.makeText(this, "Location error: $message", Toast.LENGTH_SHORT).show()
        binding.tvLocation.setText(getString(R.string.location_not_available))
        currentLocation = null
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openCamera() {
        if (firebaseAuth.currentUser != null) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
                intent.resolveActivity(packageManager)?.also {
                    val photoFile = createImageFile()
                    photoFile?.also {
                        val photoURI = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider",
                            it
                        )
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                        cameraLauncher.launch(intent)
                    } ?: run {
                        Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val intent = Intent(this, SignActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(null)
            File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            ).apply {
                currentPhotoPath = absolutePath
            }
        } catch (e: Exception) {
            Log.e("PotholeMain", "Error creating image file: ${e.message}")
            null
        }
    }

    private fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    private fun formatTime(timestamp: Long): String {
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }

    private fun submitReport() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please capture an image", Toast.LENGTH_LONG).show()
            return
        }
        if (firebaseAuth.currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SignActivity::class.java))
            finish()
            return
        }
        val description = binding.etDescription.text.toString().trim()
        val severity = when (binding.severityChipGroup.checkedChipId) {
            R.id.chipLow -> "low"
            R.id.chipMedium -> "medium"
            R.id.chipHigh -> "high"
            else -> ""
        }
        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
            return
        }
        if (severity.isEmpty()) {
            Toast.makeText(this, "Please select a severity", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentLocation == null) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_LONG).show()
            return
        }
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.dimBackground.visibility = android.view.View.VISIBLE
        binding.btnSubmit.isEnabled = false
        val latitude = currentLocation?.latitude ?: 0.0
        val longitude = currentLocation?.longitude ?: 0.0
        val imageUri = selectedImageUri!!
        sendToBackend(userId, latitude, longitude, imageUri, description, severity)
    }

    private fun sendToBackend(
        userId: Int,
        latitude: Double,
        longitude: Double,
        imageUri: Uri,
        description: String,
        severity: String
    ) {
        try {
            if (description.isBlank()) {
                Toast.makeText(this, "Description cannot be empty", Toast.LENGTH_SHORT).show()
                cleanupLoadingState()
                return
            }
            if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
                Log.e("SendToBackend", "Invalid coordinates: latitude=$latitude, longitude=$longitude")
                Toast.makeText(this, "Invalid location data", Toast.LENGTH_SHORT).show()
                cleanupLoadingState()
                return
            }
            val filePath = getRealPathFromURI(imageUri)
            if (filePath == null) {
                Log.e("SendToBackend", "Failed to get file path from URI")
                Toast.makeText(this, "Failed to access image file", Toast.LENGTH_SHORT).show()
                cleanupLoadingState()
                return
            }
            val file = File(filePath)
            if (!file.exists() || file.length() == 0L) {
                Log.e("SendToBackend", "File does not exist or is empty: ${file.absolutePath}")
                Toast.makeText(this, "Image file is missing or empty", Toast.LENGTH_SHORT).show()
                cleanupLoadingState()
                return
            }
            Log.d("SendToBackend", "Sending report: userId=$userId, lat=$latitude, lng=$longitude, desc=$description, severity=$severity, file=${file.absolutePath}")
            val userIdBody = userId.toString().toRequestBody("text/plain".toMediaType())
            val latBody = latitude.toString().toRequestBody("text/plain".toMediaType())
            val lngBody = longitude.toString().toRequestBody("text/plain".toMediaType())
            val descBody = description.trim().toRequestBody("text/plain".toMediaType())
            val severityBody = severity.toRequestBody("text/plain".toMediaType())
            val requestFile = RequestBody.create("image/jpeg".toMediaType(), file)
            val imagePart = MultipartBody.Part.createFormData("image", file.name, requestFile)
            RetrofitClient.api.reportPothole(
                userId = userIdBody,
                latitude = latBody,
                longitude = lngBody,
                description = descBody,
                severity = severityBody,
                image = imagePart
            ).enqueue(object : Callback<ReportResponse> {
                override fun onResponse(
                    call: Call<ReportResponse>,
                    response: Response<ReportResponse>
                ) {
                    if (response.isSuccessful) {
                        val reportResponse = response.body()
                        Log.d("SendToBackend", "Success: ${reportResponse?.message}")
                        Toast.makeText(
                            this@Pothole_main,
                            reportResponse?.message ?: "Report submitted successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        saveServerResponseToFirebase(
                            userId = userId,
                            status = "Success",
                            message = reportResponse?.message ?: "Report sent successfully"
                        )
                        if (reportResponse?.message == "Pothole Request Saved!") {
                            uploadImageAndSaveToFirebase(
                                userId = userId,
                                latitude = latitude,
                                longitude = longitude,
                                description = description,
                                severity = severity,
                                address = binding.tvLocation.text.toString(),
                                imageUri = imageUri
                            )
                        } else {
                            Log.d("SendToBackend", "Server response not 'Pothole Request Saved!', skipping Firebase save")
                            clearForm()
                            cleanupLoadingState()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "No error details"
                        Log.e("SendToBackend", "Server error: Code=${response.code()}, Body=$errorBody")
                        Toast.makeText(
                            this@Pothole_main,
                            "Failed to submit report: Server error (Code: ${response.code()})",
                            Toast.LENGTH_LONG
                        ).show()
                        saveServerResponseToFirebase(
                            userId = userId,
                            status = "Error",
                            message = "Server error: ${response.code()} - $errorBody"
                        )
                        cleanupLoadingState()
                    }
                }
                override fun onFailure(call: Call<ReportResponse>, t: Throwable) {
                    val errorMessage = when (t) {
                        is EOFException -> "Server returned empty response. Please check server status."
                        else -> t.message ?: "Unknown network error"
                    }
                    Log.e("SendToBackend", "Network error: $errorMessage", t)
                    Toast.makeText(this@Pothole_main, "Network error: $errorMessage", Toast.LENGTH_LONG).show()
                    saveServerResponseToFirebase(
                        userId = userId,
                        status = "Failure",
                        message = "Network error: $errorMessage"
                    )
                    cleanupLoadingState()
                }
            })
        } catch (e: Exception) {
            Log.e("SendToBackend", "Exception: ${e.message}", e)
            Toast.makeText(this, "Error submitting report: ${e.message}", Toast.LENGTH_LONG).show()
            saveServerResponseToFirebase(userId, "Exception", "Exception: ${e.message}")
            cleanupLoadingState()
        }
    }

    private fun uploadImageAndSaveToFirebase(
        userId: Int,
        latitude: Double,
        longitude: Double,
        description: String,
        severity: String,
        address: String,
        imageUri: Uri
    ) {
        try {
            val timestamp = System.currentTimeMillis()
            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef = storageRef.child("pothole_images/${UUID.randomUUID()}.jpg")

            imageRef.putFile(imageUri)
                .addOnSuccessListener { taskSnapshot ->
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        saveToFirebase(
                            userId = userId,
                            latitude = latitude,
                            longitude = longitude,
                            description = description,
                            severity = severity,
                            address = address,
                            imageUrl = uri.toString(),
                            timestamp = timestamp
                        )
                    }.addOnFailureListener { e ->
                        Log.e("FirebaseStorage", "Failed to get download URL: ${e.message}")
                        Toast.makeText(this, "Failed to get image URL: ${e.message}", Toast.LENGTH_LONG).show()
                        cleanupLoadingState()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseStorage", "Failed to upload image: ${e.message}")
                    Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()
                    cleanupLoadingState()
                }
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Exception uploading image: ${e.message}", e)
            Toast.makeText(this, "Error uploading image: ${e.message}", Toast.LENGTH_LONG).show()
            cleanupLoadingState()
        }
    }

    private fun saveToFirebase(
        userId: Int,
        latitude: Double,
        longitude: Double,
        description: String,
        severity: String,
        address: String,
        imageUrl: String,
        timestamp: Long
    ) {
        try {
            val databaseRef = FirebaseDatabase.getInstance().reference.child("pothole_reports")
            val newReportRef = databaseRef.push()
            val newId = newReportRef.key ?: UUID.randomUUID().toString()

            val potholeReport = PotholeReport(
                id = newId,
                userId = firebaseAuth.currentUser?.uid ?: userId.toString(),
                userEmail = firebaseAuth.currentUser?.email ?: "",
                imageUrl = imageUrl,
                description = description,
                severity = severity,
                latitude = latitude,
                longitude = longitude,
                address = address,
                date = formatDate(timestamp),
                time = formatTime(timestamp),
                status = "in-progress"
            )

            newReportRef.setValue(potholeReport)
                .addOnSuccessListener {
                    Log.d("SaveToFirebase", "Report saved to Firebase with ID: $newId")
                    Toast.makeText(this, "Report saved to Firebase!", Toast.LENGTH_SHORT).show()
                    fixOldReports()
                    clearForm()
                    cleanupLoadingState()
                }
                .addOnFailureListener { e ->
                    Log.e("SaveToFirebase", "Failed to save to Firebase: ${e.message}")
                    Toast.makeText(this, "Failed to save to Firebase: ${e.message}", Toast.LENGTH_LONG).show()
                    clearForm()
                    cleanupLoadingState()
                }
        } catch (e: Exception) {
            Log.e("SaveToFirebase", "Exception saving to Firebase: ${e.message}", e)
            Toast.makeText(this, "Error saving to Firebase: ${e.message}", Toast.LENGTH_LONG).show()
            clearForm()
            cleanupLoadingState()
        }
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        try {
            if ("file".equals(uri.scheme, ignoreCase = true)) {
                return uri.path
            }
            if ("content".equals(uri.scheme, ignoreCase = true)) {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    if (cursor.moveToFirst()) {
                        return cursor.getString(columnIndex)
                    }
                }
            }
            if (::currentPhotoPath.isInitialized && currentPhotoPath.isNotEmpty()) {
                return currentPhotoPath
            }
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val tempFile = File(cacheDir, "temp_upload_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                return tempFile.absolutePath
            }
            return null
        } catch (e: Exception) {
            Log.e("RealPathFromURI", "Error getting file path: ${e.message}", e)
            return null
        }
    }

    private fun testConnection() {
        RetrofitClient.api.ping().enqueue(object : Callback<PingResponse> {
            override fun onResponse(call: Call<PingResponse>, response: Response<PingResponse>) {
                if (response.isSuccessful) {
                    val pingResponse = response.body()
                    Log.d("TestConnection", "Server response: ${response.body()}")
                    Toast.makeText(this@Pothole_main, "Server online: ${pingResponse?.ping}", Toast.LENGTH_SHORT).show()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error details"
                    Log.e("TestConnection", "Server error: Code=${response.code()}, Body=$errorBody")
                    Toast.makeText(this@Pothole_main, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<PingResponse>, t: Throwable) {
                Log.e("TestConnection", "Connection failed: ${t.message}", t)
                Toast.makeText(this@Pothole_main, "Connection failed: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fixOldReports() {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("pothole_reports")
        databaseRef.get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val report = child.getValue(PotholeReport::class.java)
                val firebaseKey = child.key
                if (report != null && (report.id.isEmpty() || report.id == "") && firebaseKey != null) {
                    child.ref.child("id").setValue(firebaseKey)
                }
            }
            Log.d("FixOldReports", "Finished updating missing IDs.")
        }.addOnFailureListener {
            Log.e("FixOldReports", "Failed to fetch reports: ${it.message}")
        }
    }

    private fun saveServerResponseToFirebase(userId: Int, status: String, message: String) {
        try {
            val responseData = mapOf(
                "status" to status,
                "message" to message
            )
            val dbRef = FirebaseDatabase.getInstance().getReference("responses")
            dbRef.child(userId.toString()).setValue(responseData)
                .addOnSuccessListener {
                    Log.d("FirebaseResponse", "Server response saved under responses/$userId")
                }
                .addOnFailureListener { error ->
                    Log.e("FirebaseResponse", "Error saving response: ${error.message}")
                }
        } catch (e: Exception) {
            Log.e("FirebaseResponse", "Exception saving response: ${e.message}")
        }
    }

    private fun clearForm() {
        binding.apply {
            previewImage.setImageBitmap(null)
            etDescription.text?.clear()
            severityChipGroup.clearCheck()
            tvLocation.setText(getString(R.string.location))
            btnCamera.isEnabled = true
        }
        selectedImageUri = null
        currentLocation = null
    }

    private fun cleanupLoadingState() {
        binding.progressBar.visibility = android.view.View.INVISIBLE
        binding.dimBackground.visibility = android.view.View.INVISIBLE
        binding.btnSubmit.isEnabled = true
    }
}