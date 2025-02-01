package com.example.driveease

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.driveease.databinding.ActivityAdminSignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class Admin_SignUp : AppCompatActivity() {
    private lateinit var binding: ActivityAdminSignUpBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminSignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Sign In text click
        binding.adminSignInText.setOnClickListener {
            val intent = Intent(this, Admin_Signin::class.java)
            startActivity(intent)
        }

        // Sign Up button click
        binding.adminSignUpBtn.setOnClickListener {
            val name = binding.adminSignUpName.text.toString().trim()
            val email = binding.adminSignUpEmail.text.toString().trim()
            val phone = binding.adminSignUpPhone.text.toString().trim()
            val password = binding.adminSignUpPassword.text.toString().trim()
            val confirmPassword = binding.adminSignUpCPassword.text.toString().trim()

            // Validate input fields
            if (validateInputs(name, email, phone, password, confirmPassword)) {
                binding.adminSignUpProgressBar.visibility = android.view.View.VISIBLE

                // Firebase Authentication: Sign up with email and password
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        binding.adminSignUpProgressBar.visibility = android.view.View.GONE
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid ?: ""
                            val database = FirebaseDatabase.getInstance().reference
                            val adminRef = database.child("admins").child(userId)

                            val adminData = AdminData(name, email, phone, "admin") // Adding role as "admin"
                            adminRef.setValue(adminData).addOnCompleteListener {
                                if (it.isSuccessful) {
                                    // Go to next screen
                                    Toast.makeText(this, "Admin signed up successfully!", Toast.LENGTH_LONG).show()
                                    val intent = Intent(this, Admin_Signin::class.java)
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this, "Failed to save admin data", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    // Input Validation
    private fun validateInputs(name: String, email: String, phone: String, password: String, confirmPassword: String): Boolean {
        // Name validation
        if (TextUtils.isEmpty(name)) {
            binding.adminSignUpNameLayout.error = "Name is required"
            return false
        } else {
            binding.adminSignUpNameLayout.isErrorEnabled = false
        }

        // Email validation
        if (TextUtils.isEmpty(email)) {
            binding.adminSignUpEmailLayout.error = "Email is required"
            return false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.adminSignUpEmailLayout.error = "Enter a valid email"
            return false
        } else {
            binding.adminSignUpEmailLayout.isErrorEnabled = false
        }

        // Phone validation
        if (TextUtils.isEmpty(phone)) {
            binding.adminSignUpPhoneLayout.error = "Phone number is required"
            return false
        } else {
            binding.adminSignUpPhoneLayout.isErrorEnabled = false
        }

        // Password validation
        if (TextUtils.isEmpty(password)) {
            binding.adminSignUpPasswordLayout.error = "Password is required"
            return false
        } else if (password.length < 6) {
            binding.adminSignUpPasswordLayout.error = "Password should be at least 6 characters"
            return false
        } else {
            binding.adminSignUpPasswordLayout.isErrorEnabled = false
        }

        // Confirm Password validation
        if (TextUtils.isEmpty(confirmPassword)) {
            binding.adminSignUpCPasswordLayout.error = "Confirm your password"
            return false
        } else if (confirmPassword != password) {
            binding.adminSignUpCPasswordLayout.error = "Passwords do not match"
            return false
        } else {
            binding.adminSignUpCPasswordLayout.isErrorEnabled = false
        }

        return true
    }
}

data class AdminData(
    val name: String,
    val email: String,
    val phone: String,
    val role: String
)
