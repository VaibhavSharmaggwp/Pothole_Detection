package com.example.driveease

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.driveease.databinding.ActivityReportBinding
import com.google.firebase.database.*

class ReportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportBinding
    private lateinit var adapter: ReportsAdapter
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var reportsReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize database
        firebaseDatabase = FirebaseDatabase.getInstance()
        reportsReference = firebaseDatabase.getReference("pothole_reports")

        // Setup RecyclerView
        setupRecyclerView()

        // Setup search functionality
        setupSearch()
    }

    private fun setupRecyclerView() {
        adapter = ReportsAdapter()
        binding.reportsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.reportsRecyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val searchText = v.text.toString().trim()
                if (searchText.isNotEmpty()) {
                    fetchReportsByUserEmail(searchText) // Fetch reports based on email
                    true
                } else {
                    fetchAllReports() // Fetch all reports if search text is empty
                    true
                }
            } else {
                false
            }
        }
    }

    private fun fetchReportsByUserEmail(userEmail: String) {
        // Convert email to lowercase for consistent searching
        val normalizedEmail = userEmail.toLowerCase()

        reportsReference
            .orderByChild("userEmail")
            .equalTo(normalizedEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val reportsList = mutableListOf<Report>()

                    for (childSnapshot in snapshot.children) {
                        try {
                            val date = childSnapshot.child("date").getValue(String::class.java) ?: ""
                            val time = childSnapshot.child("time").getValue(String::class.java) ?: ""
                            val timestamp = if (date.isNotEmpty() && time.isNotEmpty()) "$date $time" else "Date not available"

                            val report = Report(
                                userId = childSnapshot.child("userId").getValue(String::class.java),
                                userEmail = childSnapshot.child("userEmail").getValue(String::class.java),
                                description = childSnapshot.child("description").getValue(String::class.java),
                                lat = childSnapshot.child("lat").getValue(String::class.java),
                                long = childSnapshot.child("long").getValue(String::class.java),
                                imageUrl = childSnapshot.child("imageUrl").getValue(String::class.java),
                                timestamp = timestamp,
                                location = childSnapshot.child("location").getValue(String::class.java)
                                    ?: if (!childSnapshot.child("lat").getValue(String::class.java).isNullOrEmpty() &&
                                        !childSnapshot.child("long").getValue(String::class.java).isNullOrEmpty()
                                    ) {
                                        "${childSnapshot.child("lat").getValue(String::class.java)}, ${childSnapshot.child("long").getValue(String::class.java)}"
                                    } else {
                                        "Location not available"
                                    }
                            )
                            reportsList.add(report)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            continue
                        }
                    }

                    if (reportsList.isNotEmpty()) {
                        reportsList.sortByDescending { it.timestamp } // Sort by timestamp
                        adapter.submitList(reportsList)
                    } else {
                        Toast.makeText(
                            this@ReportActivity,
                            "No reports found for $userEmail",
                            Toast.LENGTH_SHORT
                        ).show()
                        adapter.submitList(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@ReportActivity,
                        "Failed to fetch reports: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun fetchAllReports() {
        reportsReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reportsList = mutableListOf<Report>()

                for (childSnapshot in snapshot.children) {
                    try {
                        val date = childSnapshot.child("date").getValue(String::class.java) ?: ""
                        val time = childSnapshot.child("time").getValue(String::class.java) ?: ""
                        val timestamp = if (date.isNotEmpty() && time.isNotEmpty()) "$date $time" else "Date not available"

                        val report = Report(
                            userId = childSnapshot.child("userId").getValue(String::class.java),
                            userEmail = childSnapshot.child("userEmail").getValue(String::class.java),
                            description = childSnapshot.child("description").getValue(String::class.java),
                            lat = childSnapshot.child("lat").getValue(String::class.java),
                            long = childSnapshot.child("long").getValue(String::class.java),
                            imageUrl = childSnapshot.child("imageUrl").getValue(String::class.java),
                            timestamp = timestamp,
                            location = childSnapshot.child("location").getValue(String::class.java)
                                ?: if (!childSnapshot.child("lat").getValue(String::class.java).isNullOrEmpty() &&
                                    !childSnapshot.child("long").getValue(String::class.java).isNullOrEmpty()
                                ) {
                                    "${childSnapshot.child("lat").getValue(String::class.java)}, ${childSnapshot.child("long").getValue(String::class.java)}"
                                } else {
                                    "Location not available"
                                }
                        )
                        reportsList.add(report)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        continue
                    }
                }

                if (reportsList.isNotEmpty()) {
                    reportsList.sortByDescending { it.timestamp } // Sort by timestamp
                    adapter.submitList(reportsList)
                } else {
                    Toast.makeText(
                        this@ReportActivity,
                        "No valid reports available",
                        Toast.LENGTH_SHORT
                    ).show()
                    adapter.submitList(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@ReportActivity,
                    "Failed to fetch reports: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}
