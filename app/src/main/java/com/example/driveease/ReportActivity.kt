package com.example.driveease

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.driveease.databinding.ActivityReportBinding
import kotlinx.coroutines.*
import retrofit2.HttpException
import java.io.IOException

class ReportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportBinding
    private lateinit var adapter: ReportsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup RecyclerView
        setupRecyclerView()

        // Get userId from Intent
        val userId = intent.getIntExtra("userId", -1)
        if (userId == -1) {
            Toast.makeText(this, "Invalid user ID", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Fetch potholes for the user
        fetchPotholes(userId)
    }

    private fun setupRecyclerView() {
        adapter = ReportsAdapter()
        binding.reportsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.reportsRecyclerView.adapter = adapter
    }

    private fun fetchPotholes(userId: Int) {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = UserSigninRetrofitClient.api.fetchPotholesByUserId(userId)
                val reports = response.potholes.map { pothole ->
                    Report(
                        id = pothole.id,
                        description = pothole.description,
                        imageUrl = pothole.image_url,
                        severity = pothole.severity,
                        createdAt = pothole.created_at,
                        zoneName = pothole.zone_name,
                        status = pothole.status
                    )
                }
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (reports.isNotEmpty()) {
                        adapter.submitList(reports)
                    } else {
                        Toast.makeText(this@ReportActivity, "No pothole reports found", Toast.LENGTH_SHORT).show()
                        adapter.submitList(emptyList())
                    }
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ReportActivity, "Error: ${e.message()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ReportActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ReportActivity, "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}