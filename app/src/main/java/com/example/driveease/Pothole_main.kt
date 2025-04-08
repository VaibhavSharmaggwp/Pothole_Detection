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
import android.view.View
import android.view.WindowInsetsAnimation
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.io.File
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.squareup.okhttp.Call
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.Response

import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


class Pothole_main : AppCompatActivity() {

    private lateinit var binding: ActivityPotholeMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var currentPhotoPath: String
    private lateinit var placesClient: PlacesClient
    private var selectedImageUri: Uri? = null
    private var currentLocation: Location? = null

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

            // Automatically start location fetch
            binding.tvLocation.setText("Fetching your location...")
            requestLocationPermission()
        } else {
            Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()

        // Only redirect if user is not logged in
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

        // Initialize Places API
        Places.initialize(applicationContext, getString(R.string.my_map_api_key))
        placesClient = Places.createClient(this)



        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupClickListeners()
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
                startActivity(intent)
            }

            // When image is clicked, fetch location
//            previewImage.setOnClickListener {
//                tvLocation.setText("Fetching your location...") // Show loading text
//                requestLocationPermission() // Start location fetch
//            }
        }
    }


    @SuppressLint("SetTextI18n")
    private fun updateLocationWithGoogleMaps() {
        binding.tvLocation.setText("Fetching your location...")  // Set fetching text

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
                            Toast.makeText(this, "Location updated successfully", Toast.LENGTH_SHORT).show()
                        } ?: run {
                            showLocationError("Invalid coordinates")
                        }
                    } ?: run {
                        showLocationError("No location data available")
                    }
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
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
                intent.resolveActivity(packageManager)?.also {
                    val photoFile = createImageFile()
                    photoFile.also {
                        val photoURI = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider",
                            it
                        )
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                        cameraLauncher.launch(intent)
                    }
                }
            }
        } else {
            val intent = Intent(this, SignActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }


    private fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = Date(timestamp)
        return format.format(date) // Returns the formatted date string
    }
    private fun formatTime(timestamp: Long): String {
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val date = Date(timestamp)
        return format.format(date) // Returns the formatted time string
    }


    private fun submitReport() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image", Toast.LENGTH_LONG).show()
            return
        }

        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_LONG).show()
            return
        }

        val description = binding.etDescription.text.toString()
        val severity = when (binding.severityChipGroup.checkedChipId) {
            R.id.chipLow -> "Low"
            R.id.chipMedium -> "Medium"
            R.id.chipHigh -> "High"
            else -> "Not specified"
        }

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.dimBackground.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        val imageUri = selectedImageUri!!
        val storageRef = FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("potholes/${UUID.randomUUID()}")

        // 1. Upload to Firebase Storage
        imageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val timestamp = System.currentTimeMillis()

                    val latitude = currentLocation?.latitude ?: 0.0
                    val longitude = currentLocation?.longitude ?: 0.0

                    // 2. Upload to Firebase Database
                    val databaseRef = FirebaseDatabase.getInstance().reference.child("pothole_reports")
                    val newReportRef = databaseRef.push()
                    val newId = newReportRef.key ?: UUID.randomUUID().toString()

                    val potholeReport = PotholeReport(
                        id = newId,
                        userId = currentUser.uid,
                        userEmail = currentUser.email ?: "",
                        imageUrl = downloadUrl.toString(),
                        description = description,
                        severity = severity,
                        latitude = latitude,
                        longitude = longitude,
                        address = binding.tvLocation.text?.toString() ?: "",
                        date = formatDate(timestamp),
                        time = formatTime(timestamp)
                    )

                    newReportRef.setValue(potholeReport)
                        .addOnSuccessListener {
                            // 3. Now send to external backend server via Retrofit
                            sendToBackend(userId = 19, latitude, longitude, imageUri)

                            Toast.makeText(this, "Report submitted to Firebase!", Toast.LENGTH_SHORT).show()
                            fixOldReports()
                            clearForm()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to submit to Firebase: ${e.message}", Toast.LENGTH_LONG).show()
                            binding.progressBar.visibility = View.INVISIBLE
                            binding.dimBackground.visibility = View.INVISIBLE
                            binding.btnSubmit.isEnabled = true
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.INVISIBLE
                binding.dimBackground.visibility = View.INVISIBLE
                binding.btnSubmit.isEnabled = true
            }
    }


    private fun sendToBackend(userId: Int, latitude: Double, longitude: Double, imageUri: Uri) {
        // Show progress UI
        binding.progressBar.visibility = View.VISIBLE
        binding.dimBackground.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        try {
            val contentResolver = contentResolver
            val inputStream = contentResolver.openInputStream(imageUri)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()

            if (imageBytes == null) {
                Toast.makeText(this, "Failed to read image for server upload", Toast.LENGTH_SHORT).show()
                return
            }

            // Prepare image part
            val requestBody = imageBytes.toRequestBody("image/*".toMediaTypeOrNull())
            val requestFile = MultipartBody.Part.createFormData("image", "pothole.jpg", requestBody)

            // Prepare text fields
            val userIdBody = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val latBody = latitude.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val lngBody = longitude.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            // Send request
            RetrofitClient.api.reportPothole(userIdBody, latBody, lngBody, requestFile)
                .enqueue(object : retrofit2.Callback<Void> {
                    override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@Pothole_main, "Data sent to server!", Toast.LENGTH_SHORT).show()
                            // Save success response to Firebase
                            saveServerResponseToFirebase(
                                userId = userId.toString(), // Keep as 19
                                status = "Success",
                                message = "Report successfully sent to server (Code: ${response.code()})"
                            )
                        } else {
                            Toast.makeText(this@Pothole_main, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
                            // Save error response to Firebase
                            saveServerResponseToFirebase(
                                userId = userId.toString(), // Keep as 19
                                status = "Error",
                                message = "Server returned error: ${response.code()}"
                            )
                        }

                        // Hide progress UI
                        binding.progressBar.visibility = View.INVISIBLE
                        binding.dimBackground.visibility = View.INVISIBLE
                        binding.btnSubmit.isEnabled = true
                    }

                    override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                        Toast.makeText(this@Pothole_main, "Failed to send data: ${t.message}", Toast.LENGTH_SHORT).show()
                        // Save failure response to Firebase
                        saveServerResponseToFirebase(
                            userId = userId.toString(), // Keep as 19
                            status = "Failure",
                            message = "Network error: ${t.message ?: "Unknown error"}"
                        )

                        // Hide progress UI
                        binding.progressBar.visibility = View.INVISIBLE
                        binding.dimBackground.visibility = View.INVISIBLE
                        binding.btnSubmit.isEnabled = true
                    }
                })
        } catch (e: Exception) {
            Toast.makeText(this, "Exception while sending to server: ${e.message}", Toast.LENGTH_SHORT).show()
            // Save exception response to Firebase
            saveServerResponseToFirebase(
                userId = userId.toString(), // Keep as 19
                status = "Exception",
                message = "Exception occurred: ${e.message ?: "Unknown error"}"
            )

            // Hide progress UI
            binding.progressBar.visibility = View.INVISIBLE
            binding.dimBackground.visibility = View.INVISIBLE
            binding.btnSubmit.isEnabled = true
        }
    }

    private fun fixOldReports(){
        val databaseRef  = FirebaseDatabase.getInstance().reference.child("pothole_reports")
        databaseRef.get().addOnSuccessListener {snapshot->
            for(child in snapshot.children){
                val report = child.getValue(PotholeReport::class.java)
                val firebaseKey = child.key

                if(report!= null && (report.id.isEmpty() || report.id == "") && firebaseKey!= null){
                    // update missing firebase key with random generated key
                    child.ref.child("id").setValue(firebaseKey)
                }
            }
            Log.d("FixOldReports", "Finished updating missing IDs.")
        }.addOnFailureListener{
            Log.e("FixOldReports", "Failed to fetch reports: ${it.message}")
        }
    }

    fun saveServerResponseToFirebase(
        userId: String,
        status: String,
        message: String
    ){
        val responseData = mapOf(
            "status" to status,
            "message" to message
        )

        val dbRef = FirebaseDatabase.getInstance().getReference("responses")
        dbRef.child(userId).setValue(responseData)
            .addOnSuccessListener {
                Log.d("FirebaseResponse", "Server response saved under responses/$userId")
            }
            .addOnFailureListener{error->
                Log.e("FirebaseResponse", "Error saving response: ${error.message}")
            }
    }




    private fun clearForm() {
        binding.apply {
            previewImage.setImageBitmap(null)
            etDescription.text?.clear()
            severityChipGroup.clearCheck()
            tvLocation.setText(getString(R.string.location))
        }
        selectedImageUri = null
        currentLocation = null

        // Hide dim background after clearing form
        binding.dimBackground.visibility = View.INVISIBLE
        binding.btnSubmit.isEnabled = true
    }
}

