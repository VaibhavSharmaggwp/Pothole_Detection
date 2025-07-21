package com.example.driveease

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
        private val dateTime: TextView = itemView.findViewById(R.id.dateTime)
        private val description: TextView = itemView.findViewById(R.id.description)
        private val severity: TextView = itemView.findViewById(R.id.severity)
        private val status: TextView = itemView.findViewById(R.id.status)
        private val zoneName: TextView = itemView.findViewById(R.id.zoneName)

        @SuppressLint("SetTextI18n")
        fun bind(report: Report) {
            // Description
            description.text = report.description?.takeIf { it.isNotEmpty() } ?: "No description available"

            // Zone Name (Location)
            zoneName.text = "Zone: ${report.getFormattedLocation()}"

            // Timestamp
            dateTime.text = "Reported on: ${report.getFormattedTimestamp()}"

            // Severity
            severity.text = "Severity: ${report.severity?.takeIf { it.isNotEmpty() } ?: "Not specified"}"

            // Status
            status.text = "Status: ${report.status?.takeIf { it.isNotEmpty() } ?: "Unknown"}"

            // Image
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