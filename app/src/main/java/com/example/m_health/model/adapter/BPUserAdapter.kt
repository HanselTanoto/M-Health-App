package com.example.m_health.model.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.m_health.R
import com.example.m_health.model.data.BPUser


/**
 * Adapter for displaying BPUser data in a RecyclerView.
 *
 * This adapter is responsible for displaying BPUser data in a RecyclerView.
 * It binds the data to the ViewHolder for each user item and handles the creation
 * and recycling of ViewHolder instances. It also provides callbacks for handling
 * edit and delete actions on user items.
 *
 * @param context the context of the activity or fragment.
 * @param bpUserList the list of blood pressure users to be displayed.
 * @param onEditClick a callback function to handle edit actions on user items.
 * @param onDeleteClick a callback function to handle delete actions on user items.
 */
class BPUserAdapter(
    private val context:Context,
    private val bpUserList: MutableList<BPUser>,
    private val onEditClick: (BPUser) -> Unit,
    private val onDeleteClick: (BPUser) -> Unit
) : RecyclerView.Adapter<BPUserAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userNameInfo: TextView = itemView.findViewById(R.id.bp_user_name)
        val userIdInfo: TextView = itemView.findViewById(R.id.bp_user_id)
        val userAgeInfo: TextView = itemView.findViewById((R.id.bp_user_age))
        val userWeightInfo: TextView = itemView.findViewById(R.id.bp_user_weight)
        val userHeightInfo: TextView = itemView.findViewById(R.id.bp_user_height)
        val userBMIInfo: TextView = itemView.findViewById(R.id.bp_user_bmi)
        val userBMICategoryInfoCard: CardView = itemView.findViewById(R.id.bp_user_bmi_category_card)
        val userBMICategoryInfo: TextView = itemView.findViewById(R.id.bp_user_bmi_category)
        val editButton: Button = itemView.findViewById(R.id.bp_user_edit_button)
        val deleteButton: Button = itemView.findViewById(R.id.bp_user_delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_bp_user, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() : Int {
        return bpUserList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bpUser = bpUserList[position]
        holder.userNameInfo.text = bpUser.name
        holder.userIdInfo.text = bpUser.id
        holder.userAgeInfo.text = bpUser.getAge().toString()
        holder.userWeightInfo.text = bpUser.weight.toString()
        holder.userHeightInfo.text = bpUser.height.toString()
        val bmi = bpUser.getBMI()
        val userBMICategory = bpUser.getBMICategory(context)
        holder.userBMIInfo.text = bmi.toString()
        holder.userBMICategoryInfo.text = userBMICategory.first
        holder.userBMICategoryInfoCard.setCardBackgroundColor(userBMICategory.second)
        holder.editButton.setOnClickListener {
            onEditClick(bpUser)
        }
        holder.deleteButton.setOnClickListener {
            onDeleteClick(bpUser)
        }
    }
}