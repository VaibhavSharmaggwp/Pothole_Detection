package com.example.driveease

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.driveease.databinding.ActivityAdminSigninBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Admin_Signin : AppCompatActivity() {
    private lateinit var binding: ActivityAdminSigninBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminSigninBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.adminSignInBtn.setOnClickListener {
            val email = binding.adminEmail.text.toString().trim()
            val password = binding.adminPassword.text.toString().trim()
            if (validateInputs(email, password)) signInWithEmailPassword(email, password)
        }

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

    private fun checkUserAccess(uid: String) {
        val potholeReportsRef = FirebaseDatabase.getInstance().getReference("pothole_reports")

        // Step 1: Check if user exists in "pothole_reports" (Regular user)
        potholeReportsRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Regular user trying to access admin panel
                    auth.signOut()
                    binding.adminSignInProgressBar.visibility = View.GONE
                    Toast.makeText(
                        this@Admin_Signin,
                        "⛔ You cannot access the admin panel as a regular user.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }

                // Step 2: Check admin role in "admins/{uid}/current_role"
                val adminRef = FirebaseDatabase.getInstance().getReference("admins").child(uid)
                adminRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        binding.adminSignInProgressBar.visibility = View.GONE
                        if (!snapshot.exists()) {
                            // No admin privileges
                            auth.signOut()
                            Toast.makeText(
                                this@Admin_Signin,
                                "⛔ You don't have admin privileges",
                                Toast.LENGTH_LONG
                            ).show()
                            return
                        }

                        val role = snapshot.child("current_role").getValue(String::class.java)
                        val username = snapshot.child("username").getValue(String::class.java) ?: "Admin"

                        if (role.isNullOrEmpty()) {
                            // New admin, redirect to RoleAssignment
                            startActivity(Intent(this@Admin_Signin, RoleAssignment::class.java).apply {
                                putExtra("LOGIN_TYPE", "ADMIN")
                                putExtra("USERNAME", username)
                            })
                            finish()
                        } else {
                            // Role already assigned, redirect to appropriate activity
                            when (role) {
                                "Officer" -> {
                                    startActivity(Intent(this@Admin_Signin, AdminPanel::class.java).apply {
                                        putExtra("SELECTED_ROLE", role)
                                        putExtra("USERNAME", username)
                                    })
                                    finish()
                                }
                                "Sub-divisional Officer (SDO)" -> {
                                    startActivity(Intent(this@Admin_Signin, SDOScreen::class.java).apply {
                                        putExtra("SELECTED_ROLE", role)
                                        putExtra("USERNAME", username)
                                    })
                                    finish()
                                }
                                "Worker" -> {
                                    startActivity(Intent(this@Admin_Signin, Worker_Activity::class.java).apply {
                                        putExtra("SELECTED_ROLE", role)
                                        putExtra("USERNAME", username)
                                    })
                                    finish()
                                }
                                else -> {
                                    // Invalid role
                                    auth.signOut()
                                    Toast.makeText(
                                        this@Admin_Signin,
                                        "⛔ Invalid role assigned",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
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