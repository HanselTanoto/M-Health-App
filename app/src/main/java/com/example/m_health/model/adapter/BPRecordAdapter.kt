package com.example.m_health.model.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.m_health.R
import com.example.m_health.model.data.BPRecord
import com.example.m_health.model.data.BPUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Adapter for displaying blood pressure records in a RecyclerView.
 *
 * This adapter is responsible for displaying blood pressure records in a RecyclerView.
 * It binds the data to the ViewHolder for each record item and handles the creation
 * and recycling of ViewHolder instances.
 *
 * @param context the context of the activity or fragment.
 * @param records the list of blood pressure records to be displayed.
 */
class BPRecordAdapter(
    private val context:Context,
    private val records: MutableList<BPRecord>,
    private val bpUser: BPUser
) : RecyclerView.Adapter<BPRecordAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recordTimestamp: TextView = itemView.findViewById(R.id.record_timestamp)
        val recordSys: TextView = itemView.findViewById(R.id.record_sys_value)
        val recordDia: TextView = itemView.findViewById(R.id.record_dia_value)
        val recordPul: TextView = itemView.findViewById(R.id.record_pul_value)
        val recordMark: TextView = itemView.findViewById(R.id.record_mark)
        val recordCategoryCard: CardView = itemView.findViewById(R.id.record_category_card)
        val recordCategory: TextView = itemView.findViewById(R.id.record_category_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_bp_record, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() : Int {
        return records.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.recordTimestamp.text = formatFirebaseTimestamp(record.time)
        holder.recordSys.text = record.sys.toString()
        holder.recordDia.text = record.dia.toString()
        holder.recordPul.text = record.pul.toString()
        holder.recordCategory.text = record.category
        val categoryColor = record.getRecordCategory(context, bpUser).second
        holder.recordMark.setBackgroundColor(categoryColor)
        holder.recordCategoryCard.setCardBackgroundColor(categoryColor)
    }

    /**
     * Formats a Firebase Timestamp into a readable date and time string.
     *
     * This function takes a Firebase Timestamp object and converts it into a formatted date and time string using the specified
     * date format. The formatted string includes the month abbreviation, day, year, hours, minutes, and seconds.
     *
     * @param timestamp the Firebase Timestamp to be formatted.
     * @return a formatted date and time string representing the Firebase Timestamp.
     */
    private fun formatFirebaseTimestamp(timestamp: com.google.firebase.Timestamp) : String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault())
        val date = Date(timestamp.seconds * 1000 + timestamp.nanoseconds / 1000000)
        return dateFormat.format(date)
    }
}