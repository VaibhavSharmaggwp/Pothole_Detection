package com.example.driveease

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.example.driveease.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.FirebaseDatabase

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
    private lateinit var binding: ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        binding.signInText.setOnClickListener {
            val intent = Intent(this, SignActivity::class.java)
            startActivity(intent)
        }

        binding.signupBtn.setOnClickListener {
            val name = binding.signUpName.text.toString()
            val email = binding.signUpEmail.text.toString()
            val phone = binding.signUpPhone.text.toString()
            val password = binding.signUpPassword.text.toString()
            val cPassword = binding.signUpCPassword.text.toString()

            // Start progress bar and disable button to prevent multiple clicks
            binding.signUpProgressBar.visibility = View.VISIBLE
            binding.signupBtn.isEnabled = false

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || cPassword.isEmpty()) {
                displayInputErrors(name, email, phone, password, cPassword)
            } else if (!email.matches(emailPattern.toRegex())) {
                binding.signUpProgressBar.visibility = View.GONE
                binding.signupBtn.isEnabled = true
                binding.signUpEmail.error = "Enter a valid email address"
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_LONG).show()
            } else if (phone.length != 10) {
                binding.signUpProgressBar.visibility = View.GONE
                binding.signupBtn.isEnabled = true
                binding.signUpPhone.error = "Enter a valid number"
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_LONG).show()
            } else if (password.length < 6) {
                binding.signUpProgressBar.visibility = View.GONE
                binding.signupBtn.isEnabled = true
                binding.signUpPassword.error = "Enter a password with more than 6 characters"
                Toast.makeText(this, "Enter a password with more than 6 characters", Toast.LENGTH_LONG).show()
            } else if (password != cPassword) {
                binding.signUpProgressBar.visibility = View.GONE
                binding.signupBtn.isEnabled = true
                binding.signUpCPassword.error = "Passwords do not match"
                Toast.makeText(this, "Passwords do not match! Try again", Toast.LENGTH_LONG).show()
            } else {
                // Proceed with Firebase authentication and data storage
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        val userId = authTask.result.user?.uid
                        if (userId != null) {
                            // Store user data in database
                            val user = User(name, email, phone, userId)
                            val databaseRef = database.reference.child("users").child(userId)

                            databaseRef.setValue(user).addOnCompleteListener { dbTask ->
                                binding.signUpProgressBar.visibility = View.GONE
                                binding.signupBtn.isEnabled = true

                                if (dbTask.isSuccessful) {
                                    Toast.makeText(this, "Sign-up successful! Please log in.", Toast.LENGTH_LONG).show()

                                    // Redirect to SignActivity (sign-in page)
                                    val intent = Intent(this, SignActivity::class.java)
                                    startActivity(intent)
                                    finish()  // Close SignUpActivity to prevent returning to it
                                } else {
                                    Toast.makeText(this, "Database error. Please try again.", Toast.LENGTH_LONG).show()
                                }
                            }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to store data: ${e.message}", Toast.LENGTH_LONG).show()
                                    binding.signUpProgressBar.visibility = View.GONE
                                    binding.signupBtn.isEnabled = true
                                }
                        } else {
                            Toast.makeText(this, "User ID is null. Please try again.", Toast.LENGTH_LONG).show()
                            binding.signUpProgressBar.visibility = View.GONE
                            binding.signupBtn.isEnabled = true
                        }
                    } else {
                        // Check for specific error types
                        val exception = authTask.exception
                        if (exception is FirebaseAuthUserCollisionException) {
                            Toast.makeText(this, "This email is already registered.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Sign-up failed: ${exception?.message}", Toast.LENGTH_LONG).show()
                        }
                        binding.signUpProgressBar.visibility = View.GONE
                        binding.signupBtn.isEnabled = true
                    }
                }
            }
        }
    }

    private fun displayInputErrors(name: String, email: String, phone: String, password: String, cPassword: String) {
        if (name.isEmpty()) binding.signUpName.error = "Enter your name"
        if (email.isEmpty()) binding.signUpEmail.error = "Enter your email address"
        if (phone.isEmpty()) binding.signUpPhone.error = "Enter your phone number"
        if (password.isEmpty()) binding.signUpPassword.error = "Enter your password"
        if (cPassword.isEmpty()) binding.signUpCPassword.error = "Re-enter your password"
        Toast.makeText(this, "Enter valid details", Toast.LENGTH_LONG).show()
        binding.signUpProgressBar.visibility = View.GONE
        binding.signupBtn.isEnabled = true
    }
}
