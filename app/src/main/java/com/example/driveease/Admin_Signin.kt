package com.example.driveease

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.driveease.databinding.ActivityAdminSigninBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class Admin_Signin : AppCompatActivity() {
    private lateinit var binding: ActivityAdminSigninBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001  // Request code for Google Sign-In

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminSigninBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Initialize Google Sign-In client
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))  // Ensure this matches your Firebase config
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)

        // Sign In button click
        binding.adminSignInBtn.setOnClickListener {
            val email = binding.adminEmail.text.toString().trim()
            val password = binding.adminPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                signInWithEmailPassword(email, password)
            }
        }

        // Google Sign-In button click
        binding.adminGoogleSignInBtn.setOnClickListener {
            signInWithGoogle()
        }

        // Redirect to Sign-Up page
        binding.adminSignUpText.setOnClickListener {
            val intent = Intent(this, Admin_SignUp::class.java)
            startActivity(intent)
        }
    }

    // Sign in with email and password
    private fun signInWithEmailPassword(email: String, password: String) {
        binding.adminSignInProgressBar.visibility = android.view.View.VISIBLE
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.adminSignInProgressBar.visibility = android.view.View.GONE
                if (task.isSuccessful) {
                    // Successfully signed in
                    Toast.makeText(this, "Sign-in successful", Toast.LENGTH_SHORT).show()
                    // Redirect to admin panel or next screen
                    val intent = Intent(this, AdminPanel::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Sign-in failed
                    Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Google Sign-In functionality
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    // Handle the result of Google Sign-In
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Firebase Authentication with Google
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        if (account != null) {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Google sign-in successful", Toast.LENGTH_SHORT).show()
                        // Redirect to admin panel or next screen
                        val intent = Intent(this, AdminPanel::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this, "Google sign-in failed: Account is null", Toast.LENGTH_SHORT).show()
        }
    }

    // Input Validation
    private fun validateInputs(email: String, password: String): Boolean {
        if (TextUtils.isEmpty(email)) {
            binding.adminEmailLayout.error = "Email is required"  // Set error on TextInputLayout
            binding.adminEmailLayout.isErrorEnabled = true   // Enable the error state
            return false
        } else {
            binding.adminEmailLayout.isErrorEnabled = false  // Disable error state if input is valid
        }

        if (TextUtils.isEmpty(password)) {
            binding.adminPasswordLayout.error = "Password is required"  // Set error on TextInputLayout
            binding.adminPasswordLayout.isErrorEnabled = true   // Enable the error state
            return false
        } else {
            binding.adminPasswordLayout.isErrorEnabled = false  // Disable error state if input is valid
        }

        return true
    }
}