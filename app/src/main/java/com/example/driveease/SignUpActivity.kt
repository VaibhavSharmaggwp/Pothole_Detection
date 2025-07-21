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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            startActivity(Intent(this, SignActivity::class.java))
        }

        binding.signupBtn.setOnClickListener {
            val name = binding.signUpName.text.toString().trim()
            val email = binding.signUpEmail.text.toString().trim()
            val phone = binding.signUpPhone.text.toString().trim()
            val password = binding.signUpPassword.text.toString().trim()
            val cPassword = binding.signUpCPassword.text.toString().trim()

            binding.signUpProgressBar.visibility = View.VISIBLE
            binding.signupBtn.isEnabled = false

            // Input validation
            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || cPassword.isEmpty()) {
                displayInputErrors(name, email, phone, password, cPassword)
                return@setOnClickListener
            }

            if (!email.matches(emailPattern.toRegex())) {
                showError("Enter a valid email address", binding.signUpEmail)
                return@setOnClickListener
            }

            if (phone.length != 10) {
                showError("Enter a valid phone number", binding.signUpPhone)
                return@setOnClickListener
            }

            if (password.length < 6) {
                showError("Password must be at least 6 characters", binding.signUpPassword)
                return@setOnClickListener
            }

            if (password != cPassword) {
                showError("Passwords do not match", binding.signUpCPassword)
                return@setOnClickListener
            }

            // Send data to server
            val userRequest = UserRequest(name, email, phone, password)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = UserRetrofitClient.api.signUp(userRequest)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SignUpActivity, "Server response: ${response.message}", Toast.LENGTH_LONG).show()

                        // Proceed to Firebase only if server responded successfully
                        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                val userId = authTask.result.user?.uid
                                if (userId != null) {
                                    val user = User(name, email, phone, userId)
                                    val dbRef = database.reference.child("users").child(userId)

                                    dbRef.setValue(user).addOnCompleteListener { dbTask ->
                                        binding.signUpProgressBar.visibility = View.GONE
                                        binding.signupBtn.isEnabled = true

                                        if (dbTask.isSuccessful) {
                                            Toast.makeText(this@SignUpActivity, "Sign-up successful! Please log in.", Toast.LENGTH_LONG).show()
                                            startActivity(Intent(this@SignUpActivity, SignActivity::class.java))
                                            finish()
                                        } else {
                                            Toast.makeText(this@SignUpActivity, "Database error. Try again.", Toast.LENGTH_LONG).show()
                                        }
                                    }.addOnFailureListener { e ->
                                        showError("DB Error: ${e.message}")
                                    }
                                } else {
                                    showError("User ID is null")
                                }
                            } else {
                                val exception = authTask.exception
                                if (exception is FirebaseAuthUserCollisionException) {
                                    showError("This email is already registered.")
                                } else {
                                    showError("Firebase signup failed: ${exception?.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showError("Server error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun displayInputErrors(name: String, email: String, phone: String, password: String, cPassword: String) {
        if (name.isEmpty()) binding.signUpName.error = "Enter your name"
        if (email.isEmpty()) binding.signUpEmail.error = "Enter your email"
        if (phone.isEmpty()) binding.signUpPhone.error = "Enter your phone"
        if (password.isEmpty()) binding.signUpPassword.error = "Enter your password"
        if (cPassword.isEmpty()) binding.signUpCPassword.error = "Re-enter your password"
        Toast.makeText(this, "Please fill all fields", Toast.LENGTH_LONG).show()
        binding.signUpProgressBar.visibility = View.GONE
        binding.signupBtn.isEnabled = true
    }

    private fun showError(message: String, errorField: View? = null) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.signUpProgressBar.visibility = View.GONE
        binding.signupBtn.isEnabled = true
        if (errorField != null && errorField is androidx.appcompat.widget.AppCompatEditText) {
            errorField.error = message
        }
    }
}
