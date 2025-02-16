package com.example.driveease

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.driveease.databinding.ActivityAdminSigninBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Admin_Signin : AppCompatActivity() {
    private lateinit var binding: ActivityAdminSigninBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminSigninBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)

        binding.adminSignInBtn.setOnClickListener {
            val email = binding.adminEmail.text.toString().trim()
            val password = binding.adminPassword.text.toString().trim()
            if (validateInputs(email, password)) signInWithEmailPassword(email, password)
        }

        binding.adminGoogleSignInBtn.setOnClickListener { signInWithGoogle() }
        binding.adminSignUpText.setOnClickListener {
            startActivity(Intent(this, Admin_SignUp::class.java))
        }
    }

    private fun signInWithEmailPassword(email: String, password: String) {
        binding.adminSignInProgressBar.visibility = View.VISIBLE
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                checkUserAccess(auth.currentUser?.uid ?: "")
            } else {
                handleError(task.exception?.message)
            }
        }
    }

    private fun signInWithGoogle() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                account?.let { firebaseAuthWithGoogle(it) }
            } catch (e: ApiException) {
                handleError("Google sign-in failed: ${e.message}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                checkUserAccess(auth.currentUser?.uid ?: "")
            } else {
                handleError(task.exception?.message)
            }
        }
    }

    private fun checkUserAccess(uid: String) {
        val potholeReportsRef = FirebaseDatabase.getInstance().getReference("pothole_reports")

        // Step 1: Check if user exists in "pothole_reports" (Regular user)
        potholeReportsRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // If the user exists in pothole_reports, they are a regular user
                    auth.signOut()
                    googleSignInClient.signOut()
                    Toast.makeText(
                        this@Admin_Signin,
                        "⛔ You cannot access the admin panel as a regular user.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }

                // Step 2: If not in pothole_reports, proceed to check if they are an admin
                val adminRef = FirebaseDatabase.getInstance().getReference("admins")
                adminRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        binding.adminSignInProgressBar.visibility = View.GONE
                        if (snapshot.exists()) {
                            val role = snapshot.child("role").value.toString() // Fetch role

                            // Redirect to RoleAssignment and pass the role
                            startActivity(Intent(this@Admin_Signin, RoleAssignment::class.java).apply {
                                putExtra("LOGIN_TYPE", "ADMIN")
                                putExtra("USER_ROLE", role) // Pass the role
                                putExtra("USERNAME", snapshot.child("username").value.toString()) // Pass username
                            })
                            finish()
                        } else {
                            auth.signOut()
                            googleSignInClient.signOut()
                            Toast.makeText(
                                this@Admin_Signin,
                                "⛔ You don't have admin privileges",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        binding.adminSignInProgressBar.visibility = View.GONE
                        handleError("Database error: ${error.message}")
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                binding.adminSignInProgressBar.visibility = View.GONE
                handleError("Database error: ${error.message}")
            }
        })
    }


    private fun validateInputs(email: String, password: String): Boolean {
        var valid = true
        if (TextUtils.isEmpty(email)) {
            binding.adminEmailLayout.error = "Email required"
            valid = false
        } else binding.adminEmailLayout.isErrorEnabled = false

        if (TextUtils.isEmpty(password)) {
            binding.adminPasswordLayout.error = "Password required"
            valid = false
        } else binding.adminPasswordLayout.isErrorEnabled = false

        return valid
    }

    private fun handleError(message: String?) {
        binding.adminSignInProgressBar.visibility = View.GONE
        Toast.makeText(this, message ?: "Authentication failed", Toast.LENGTH_LONG).show()
    }
}
