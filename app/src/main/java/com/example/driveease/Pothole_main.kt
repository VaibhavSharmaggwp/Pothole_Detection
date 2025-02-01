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
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import android.view.View
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
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = Uri.fromFile(File(currentPhotoPath))
            binding.previewImage.setImageURI(selectedImageUri)
        } else {
            val intent = Intent(this, SignActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val storageDir = getExternalFilesDir(null)
                    val tempFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)

                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    selectedImageUri = FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.provider",
                        tempFile
                    )

                    binding.previewImage.setImageURI(selectedImageUri)
                } catch (e: Exception) {
                    Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPotholeMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Places API
        Places.initialize(applicationContext, getString(R.string.my_map_api_key))
        placesClient = Places.createClient(this)

        firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser == null) {
            val intent = Intent(this, SignActivity::class.java)
            startActivity(intent)
            finish()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.apply {
            btnCamera.setOnClickListener {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            btnGallery.setOnClickListener {
                openGallery()
            }
            btnUpdateLocation.setOnClickListener {
                requestLocationPermission()
            }
            btnSubmit.setOnClickListener {
                submitReport()
            }
            btnReview.setOnClickListener {
                val intent = Intent(this@Pothole_main, ReportActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun updateLocationWithGoogleMaps() {
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
                        val location = Location("GoogleMapsAPI").apply {
                            latitude = place.latLng?.latitude ?: 0.0
                            longitude = place.latLng?.longitude ?: 0.0
                        }
                        currentLocation = location
                        val address = place.address ?: ""
                        binding.tvLocation.text = "Location: ${location.latitude}, ${location.longitude}\n$address"
                        Toast.makeText(this, "Location updated successfully", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(this, "Could not determine location. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("PotholeMain", "Error getting location: ${exception.message}")
                    Toast.makeText(this, "Error getting location: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
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

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        try {
            galleryLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening gallery: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    private fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) // Format as date (yyyy-MM-dd)
        val date = Date(timestamp)
        return format.format(date) // Returns the formatted date string
    }
    private fun formatTime(timestamp: Long): String {
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault()) // Format as time (HH:mm:ss)
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

        val storageRef = FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("potholes/${UUID.randomUUID()}")

        // show Progress bar and dim the background
        binding.progressBar.visibility = View.VISIBLE
        binding.dimBackground.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        imageRef.putFile(selectedImageUri!!)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val timestamp = System.currentTimeMillis()
                    val date = formatDate(timestamp)
                    val time = formatTime(timestamp)
                    val potholeReport = PotholeReport(
                        userId = currentUser.uid,
                        userEmail = currentUser.email ?: "",
                        imageUrl = downloadUrl.toString(),
                        description = description,
                        severity = severity,
                        latitude = currentLocation?.latitude ?: 0.0,
                        longitude = currentLocation?.longitude ?: 0.0,
                        address = binding.tvLocation.text.toString(), // store location
                        date = date,  // store date
                        time = time  // store time

                    )

                    FirebaseDatabase.getInstance().reference
                        .child("pothole_reports")
                        .push()
                        .setValue(potholeReport)
                        .addOnSuccessListener {
                            Toast.makeText(this, "com.example.driveease.Report submitted successfully", Toast.LENGTH_LONG).show()
                            binding.progressBar.visibility = View.INVISIBLE
                            binding.dimBackground.visibility = View.INVISIBLE
                            binding.btnSubmit.isEnabled = true
                            clearForm()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
                            binding.progressBar.visibility = View.INVISIBLE
                            binding.dimBackground.visibility = View.INVISIBLE
                            binding.btnSubmit.isEnabled = true
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.INVISIBLE
                binding.dimBackground.visibility = View.INVISIBLE
                binding.btnSubmit.isEnabled = true
            }
    }


    private fun clearForm() {
        binding.apply {
            previewImage.setImageBitmap(null)
            etDescription.text?.clear()
            severityChipGroup.clearCheck()
            tvLocation.text = getString(R.string.current_location)
        }
        selectedImageUri = null
        currentLocation = null
    }
}
