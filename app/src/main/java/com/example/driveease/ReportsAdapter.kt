package com.example.driveease

import ReportsDiffCallback
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ReportsAdapter : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {

    private var reportsList = listOf<Report>()

    fun submitList(newList: List<Report>) {
        val diffCallback = ReportsDiffCallback(reportsList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        reportsList = newList
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(reportsList[position])
    }

    override fun getItemCount(): Int = reportsList.size

    class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val reportImage: ImageView = itemView.findViewById(R.id.reportImage)
        private val location: TextView = itemView.findViewById(R.id.location)
        private val dateTime: TextView = itemView.findViewById(R.id.dateTime)
        private val description: TextView = itemView.findViewById(R.id.description)
        private val userEmail: TextView = itemView.findViewById(R.id.userEmail)

        @SuppressLint("SetTextI18n")
        fun bind(report: Report) {
            // Set description
            description.text = report.description ?: "No description available"

            // Get formatted location using getFormattedLocation function
            val formattedLocation = report.getFormattedLocation()

            // Set location: If the formatted location is null or empty, fallback to a default message
            location.text = "Location: ${formattedLocation.takeIf { it.isNotEmpty() } ?: "Location not available"}"

            // Set timestamp
            dateTime.text = "Reported on: ${report.timestamp ?: "Date not available"}"

            // Set user email
            userEmail.text = "Reported by: ${report.userEmail ?: "Unknown user"}"

            // Load image with error handling
            if (!report.imageUrl.isNullOrEmpty()) {
                Glide.with(reportImage.context)
                    .load(report.imageUrl)
                    .placeholder(R.drawable.ic_loading_placeholder)
                    .error(R.drawable.ic_error_placeholder)
                    .into(reportImage)
            } else {
                reportImage.setImageResource(R.drawable.ic_error_placeholder)
            }
        }
    }
}
