package com.example.driveease

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.driveease.databinding.ActivitySignBinding
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*

class SignActivity : AppCompatActivity() {
    private val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
    private lateinit var binding: ActivitySignBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val signInEmail: EditText = findViewById(R.id.signInEmail)
        val signInPassword: EditText = findViewById(R.id.signInPassword)
        val signInPasswordLayout: TextInputLayout = findViewById(R.id.signInPasswordLayout)
        val signInBtn: Button = findViewById(R.id.signInBtn)
        val signInProgressBar: ProgressBar = findViewById(R.id.signInProgressBar)

        val signUpText = binding.signUpText
        val adminLoginText = binding.adminLogin

        signUpText.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        adminLoginText.setOnClickListener {
            startActivity(Intent(this, Admin_Signin::class.java))
        }

        signInBtn.setOnClickListener {
            signInProgressBar.visibility = View.VISIBLE

            signInPasswordLayout.isPasswordVisibilityToggleEnabled = true
            val email = signInEmail.text.toString()
            val password = signInPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                if (email.isEmpty()) {
                    signInEmail.error = "Enter your email address"
                }
                if (password.isEmpty()) {
                    signInPassword.error = "Enter your password"
                    signInPasswordLayout.isPasswordVisibilityToggleEnabled = false
                }
                signInProgressBar.visibility = View.GONE
                Toast.makeText(this, "Enter valid details", Toast.LENGTH_LONG).show()

            } else if (!email.matches(emailPattern.toRegex())) {
                signInProgressBar.visibility = View.GONE
                signInEmail.error = "Enter valid email address"
                Toast.makeText(this, "Enter valid email address", Toast.LENGTH_LONG).show()

            } else if (password.length < 6) {
                signInPasswordLayout.isPasswordVisibilityToggleEnabled = false
                signInProgressBar.visibility = View.GONE
                signInPassword.error = "Enter password more than 6 characters"
                Toast.makeText(this, "Enter password more than 6 characters", Toast.LENGTH_LONG)
                    .show()

            } else {
                val userRequest = UserSignRequest(email, password)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = UserSigninRetrofitClient.api.SignIn(userRequest)
                        withContext(Dispatchers.Main) {
                            signInProgressBar.visibility = View.GONE
                            Toast.makeText(this@SignActivity, response.message, Toast.LENGTH_LONG).show()

                            // Pass userId to Pothole_main
                            val intent = Intent(this@SignActivity, Pothole_main::class.java)
                            intent.putExtra("userId", response.id)
                            startActivity(intent)
                            finish() // Close SignActivity to prevent going back
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            signInProgressBar.visibility = View.GONE
                            Toast.makeText(this@SignActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}