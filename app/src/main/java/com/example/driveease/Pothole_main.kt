package com.example.driveease

import android.app.Activity

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.location.Location
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.driveease.databinding.ActivityPotholeMainBinding
//import com.google.firebase.Firebase
//import com.google.firebase.storage.storage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import java.io.File

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


class Pothole_main : AppCompatActivity() {

    private lateinit var binding: ActivityPotholeMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var currentPhotoPath: String
    private var selectedImageUri: Uri? = null
    private var currentLocation: Location?= null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){isGranted->
        if(isGranted){
            openCamera()
        }
        else{
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }

    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){permissions->
        if(permissions.all { it.value }){
            updateLocation()
        }
        else{
            Toast.makeText(this, "Location permissions required", Toast.LENGTH_SHORT).show()
        }

    }
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){result->
        if(result.resultCode == Activity.RESULT_OK){
            selectedImageUri = Uri.fromFile(File(currentPhotoPath))
            binding.previewImage.setImageURI(selectedImageUri)
        }
    }
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){result->
        if(result.resultCode == Activity.RESULT_OK){
            selectedImageUri = result.data?.data
            binding.previewImage.setImageURI(selectedImageUri)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPotholeMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firebaseAuth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupClickListeners()
    }
    private fun setupClickListeners(){
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
            btnSubmit.setOnClickListener{
                submitReport()
            }
        }
    }
    private fun openCamera() {
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
    }
    private fun createImageFile(): File{
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
    private fun openGallery(){
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
    private fun requestLocationPermission(){
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    private fun updateLocation(){
        if(ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ){
            fusedLocationClient.lastLocation.addOnSuccessListener { location->
                location?.let {
                    currentLocation = it
                    binding.tvLocation.text = "Location: ${it.latitude}, ${it.longitude}"
                }

            }
        }

    }
    private fun submitReport(){
        if(selectedImageUri == null){
            Toast.makeText(this, "Please select an image", Toast.LENGTH_LONG).show()
            return
        }
        val currentUser = firebaseAuth.currentUser
        if(currentUser == null){
            Toast.makeText(this, "Please login first ", Toast.LENGTH_LONG).show()
            return
        }

        val description = binding.etDescription.text.toString()
        val severity = when(binding.severityChipGroup.checkedChipId){
            R.id.chipLow -> "Low"
            R.id.chipMedium -> "Medium"
            R.id.chipHigh -> "High"
            else -> "Not specified"
        }
        // upload image to firebase store
        val storageRef = FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("potholes/${UUID.randomUUID()}")

        imageRef.putFile(selectedImageUri!!)
            .addOnSuccessListener {taskSnapshot->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener {downloadUrl->
                    // create pothole report
                    val potholeReport = PotholeReport(
                        userId = currentUser.uid,
                        userEmail = currentUser.email ?: "",
                        imageUrl = downloadUrl.toString(),
                        description = description,
                        severity = severity,
                        latitude = currentLocation?.latitude ?: 0.0,
                        longitude = currentLocation?.longitude ?: 0.0,
                        timestamp = System.currentTimeMillis()
                    )
                    // Save to Firebase Realtime Database
                    FirebaseDatabase.getInstance().reference
                        .child("pothole_reports")
                        .push()
                        .setValue(potholeReport)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Report submitted successfully", Toast.LENGTH_LONG).show()
                            clearForm()
                        }
                        .addOnFailureListener{e->
                            Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
                        }


                }

            }
            .addOnFailureListener{e->
                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()

            }
    }
    private fun clearForm(){
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
