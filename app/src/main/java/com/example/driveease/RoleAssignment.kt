package com.example.driveease

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.driveease.databinding.ActivityRoleAssignmentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class RoleAssignment : AppCompatActivity() {

    private lateinit var binding: ActivityRoleAssignmentBinding
    private var selectedRole: String? = null
    private var username: String? = null
    private lateinit var roleCountRef: DatabaseReference
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleAssignmentBinding.inflate(layoutInflater)
        setContentView(binding.root)


        username = intent.getStringExtra("USERNAME")

        // Firebase reference for role counts under admins
        roleCountRef = FirebaseDatabase.getInstance().getReference("admins/role_counts")

        // Initialize dropdown with roles
        val roles = resources.getStringArray(R.array.roles_array)
        val adapter = ArrayAdapter(this, R.layout.dropdown_item, roles)
        binding.roleDropdown.setAdapter(adapter)

        // Role selection listener
        binding.roleDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedRole = roles[position]
        }

        // Login button click listener
        binding.loginButton.setOnClickListener {
            if (validateInputs()) {
                validateRoleAndProceed(selectedRole!!)
            }
        }
    }

    override fun onBackPressed() {
        val intent = Intent(this, Admin_Signin::class.java)
        startActivity(intent)
        finish()
    }



    private fun validateRoleAndProceed(role: String) {
        val adminRef = FirebaseDatabase.getInstance().getReference("admins").child(auth.currentUser!!.uid)
        adminRef.get().addOnSuccessListener { snapshot ->
            val existingRole = snapshot.child("current_role").getValue(String::class.java)
            if (existingRole == null) {
                assignRoleToUser(role)
            } else if (existingRole == role) {
                navigateToAdminPanel()
            } else {
                showToast("⛔ You are already assigned as $existingRole. Multiple roles are not allowed.")
            }
        }.addOnFailureListener {
            showToast("Error checking role assignment")
        }
    }

    // Assign Role and Store User Details
    private fun assignRoleToUser(role: String){
        val adminRef = FirebaseDatabase.getInstance().getReference("admins").child(auth.currentUser!!.uid)
        // Set the current_role for the user
        adminRef.child("current_role").setValue(role).addOnSuccessListener {
            // Retrieve all user details after setting the role
            adminRef.get().addOnSuccessListener {snapshot->
                val userDetails = snapshot.value as Map<*, *>?
                if(userDetails != null){
                    // Store user details under rolesAssigned/{role}/{userId}
                    val roleAssignedRef = FirebaseDatabase.getInstance()
                        .getReference("admins/rolesAssigned/$role/${auth.currentUser!!.uid}")
                    roleAssignedRef.setValue(userDetails).addOnSuccessListener {
                        updateRoleCount(role)
                        navigateToAdminPanel()
                    }.addOnFailureListener{
                        showToast("Failed to store role details")
                    }
                }else{
                    showToast("User details not found")
                }
            }.addOnFailureListener{
                showToast("Failed to retrieve user details")
            }
        }.addOnFailureListener {
            showToast("Failed to assign role")
        }
    }

    // Update Role count
    private fun updateRoleCount(role: String) {
        roleCountRef.child(role).get().addOnSuccessListener { snapshot ->
            val count = snapshot.getValue(Int::class.java) ?: 0
            roleCountRef.child(role).setValue(count + 1)
        }.addOnFailureListener {
            showToast("Failed to update role count")
        }
    }

    private fun navigateToAdminPanel() {
        val intent = Intent(this, AdminPanel::class.java).apply {
            putExtra("SELECTED_ROLE", selectedRole)
            putExtra("USERNAME", username)
        }
        startActivity(intent)
        finish()
    }

    private fun validateInputs(): Boolean {
        return when {
            selectedRole == null -> {
                showToast("Please select a role")
                false
            }
            binding.usernameInput.text.isNullOrEmpty() -> {
                showToast("Please enter username")
                false
            }
            binding.passwordInput.text.isNullOrEmpty() -> {
                showToast("Please enter password")
                false
            }
            else -> true
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}