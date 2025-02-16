package com.example.driveease

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.driveease.databinding.ActivityAdminPanelBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.*

class AdminPanel : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityAdminPanelBinding
    private val database = FirebaseDatabase.getInstance()
    private val roleCountRef = database.getReference("admins/role_counts")
    private val reportsRef = database.getReference("pothole_reports")

    private var roleCountListener: ValueEventListener? = null
    private var reportsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide() // hide action bar
        
        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)


        setupFirebaseListeners()
        setupNavigationDrawer()
    }

    private fun setupNavigationDrawer() {
        // Set up the toolbar button to open the navigation drawer
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Set up navigation menu item click listener
        binding.navigationView.setNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            binding.navigationView.menu.findItem(R.id.nav_dashboard).itemId -> {
                Toast.makeText(this, "Dashboard Selected", Toast.LENGTH_SHORT).show()
            }
            binding.navigationView.menu.findItem(R.id.nav_officer).itemId -> {
                startActivity(Intent(this, OfficerActivity::class.java))
            }
            binding.navigationView.menu.findItem(R.id.nav_sdo).itemId -> {
                Toast.makeText(this, "SDO Selected", Toast.LENGTH_SHORT).show()
            }
            binding.navigationView.menu.findItem(R.id.nav_worker).itemId -> {
                Toast.makeText(this, "Worker Selected", Toast.LENGTH_SHORT).show()
            }
            binding.navigationView.menu.findItem(R.id.nav_reports).itemId -> {
                Toast.makeText(this, "Reports Selected", Toast.LENGTH_SHORT).show()
            }
            binding.navigationView.menu.findItem(R.id.nav_location).itemId -> {
                Toast.makeText(this, "Location Selected", Toast.LENGTH_SHORT).show()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun setupFirebaseListeners() {
        // Listen for role counts
        roleCountListener = roleCountRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val officerCount = snapshot.child("Officer").getValue(Int::class.java) ?: 0
                val sdoCount = snapshot.child("Sub-divisional Officer (SDO)").getValue(Int::class.java) ?: 0
                val workerCount = snapshot.child("Worker").getValue(Int::class.java) ?: 0

                binding.dashboardOfficersCount.text = officerCount.toString()
                binding.dashboardSdosCount.text = sdoCount.toString()
                binding.dashboardWorkersCount.text = workerCount.toString()
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Failed to load role counts: ${error.message}")
            }
        })

        // Listen for reports count
        reportsListener = reportsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.dashboardReportsCount.text = snapshot.childrenCount.toString()
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Failed to load reports: ${error.message}")
            }
        })
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        roleCountListener?.let { roleCountRef.removeEventListener(it) }
        reportsListener?.let { reportsRef.removeEventListener(it) }
        super.onDestroy()
    }

    override fun onBackPressed() {
        startActivity(Intent(this, RoleAssignment::class.java))
        finish()
    }
}
