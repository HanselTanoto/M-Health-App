package com.example.m_health.ui.profile

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.m_health.LoginActivity
import com.example.m_health.databinding.CardAddBpUserBinding
import com.example.m_health.databinding.CardEditBpUserBinding
import com.example.m_health.databinding.FragmentProfileBinding
import com.example.m_health.model.FirestoreDataViewModel
import com.example.m_health.model.adapter.BPUserAdapter
import com.example.m_health.model.data.BPUser
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private var _addBPUserCard: CardAddBpUserBinding? = null
    private var _editBPUserCard: CardEditBpUserBinding? = null
    private val binding get() = _binding!!      // This property is only valid between onCreateView and onDestroyView.
    private val addBPUserCard get() = _addBPUserCard!!
    private val editBPUserCard get() = _editBPUserCard!!


    private lateinit var safeContext: Context
    private lateinit var firestoreDataViewModel: FirestoreDataViewModel
    private lateinit var auth: FirebaseAuth

    // UI Components
    private lateinit var userNameInfo: TextView
    private lateinit var userEmailInfo: TextView
    private lateinit var userUIDInfo: TextView
    private lateinit var userWeightInfo: TextView
    private lateinit var userHeightInfo: TextView
    private lateinit var userAgeInfo: TextView
    private lateinit var userBirthdateInfo: TextView
    private lateinit var userBMIInfo: TextView
    private lateinit var userBMICategoryInfoCard: CardView
    private lateinit var userBMICategoryInfo: TextView
    private lateinit var logoutButton: Button
    private lateinit var editButton: Button
    private lateinit var addButton: Button
    private lateinit var bpUserRecyclerView: RecyclerView

    private lateinit var authUser: BPUser
    private lateinit var bpUserList: MutableList<BPUser>
    private lateinit var bpUserListAdapter: BPUserAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        safeContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root
        _addBPUserCard = CardAddBpUserBinding.inflate(inflater, container, false)
        _editBPUserCard = CardEditBpUserBinding.inflate(inflater, container, false)

        firestoreDataViewModel = ViewModelProvider(this)[FirestoreDataViewModel::class.java]
        auth = FirebaseAuth.getInstance()

        // UI Binding
        userNameInfo            = binding.userName
        userEmailInfo           = binding.userEmail
        userUIDInfo             = binding.userId
        userWeightInfo          = binding.userWeight
        userHeightInfo          = binding.userHeight
        userAgeInfo             = binding.userAge
        userBirthdateInfo       = binding.userBirthdate
        userBMIInfo             = binding.userBmi
        userBMICategoryInfoCard = binding.userBmiCategoryCard
        userBMICategoryInfo     = binding.userBmiCategory
        logoutButton            = binding.logoutButton
        editButton              = binding.editUserButton
        addButton               = binding.addUserButton
        bpUserRecyclerView      = binding.bpUserRecyclerView

        firestoreDataViewModel.authUser.observe(viewLifecycleOwner) {
            if (it != null) {
                authUser = it
                val name = it.name
                userNameInfo.text = name.substring(0, name.indexOf("(")).trim()
                userWeightInfo.text = it.weight.toString()
                userHeightInfo.text = it.height.toString()
                userAgeInfo.text = it.getAge().toString()
                userBirthdateInfo.text = formatFirebaseTimestamp(it.birthdate)
                val bmi = it.getBMI()
                val bmiCategory = it.getBMICategory(safeContext)
                userBMIInfo.text = bmi.toString()
                userBMICategoryInfo.text = bmiCategory.first
                userBMICategoryInfoCard.setCardBackgroundColor(bmiCategory.second)
            }
        }

        firestoreDataViewModel.firebaseUser.observe(viewLifecycleOwner) {
            if (it != null) {
                val uid = "UID ${it.uid}"
                userEmailInfo.text = it.email ?: "Email"
                userUIDInfo.text = uid
            }
        }

        bpUserList = mutableListOf()
        firestoreDataViewModel.bpUserList.observe(viewLifecycleOwner) { item ->
            bpUserList = item.filter { it.id != "U01" }.toMutableList()
            Log.d(TAG, "User Count: ${bpUserList.size}")
            bpUserListAdapter = BPUserAdapter(safeContext, bpUserList, ::editBPUser, ::deleteBPUser)
            bpUserRecyclerView.adapter = bpUserListAdapter
        }
        bpUserRecyclerView.layoutManager = LinearLayoutManager(safeContext)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logoutButton.setOnClickListener {
            logout()
        }

        editButton.setOnClickListener {
            editBPUser(authUser)
        }

        addButton.setOnClickListener {
            addNewBPUser()
        }
    }

    override fun onResume() {
        super.onResume()
        firestoreDataViewModel.getBPUserData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun logout() {
        Log.i(TAG, "Logging out...")
        auth.signOut()
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    private fun editBPUser(bpUser: BPUser) {
        val editBPUserDialog = AlertDialog.Builder(safeContext)
        val editBPUserLayout = editBPUserCard.root
        if (editBPUserLayout.parent != null) {
            (editBPUserLayout.parent as ViewGroup).removeView(editBPUserLayout)
        }
        editBPUserCard.weightInput.setText(bpUser.weight.toString())
        editBPUserCard.heightInput.setText(bpUser.height.toString())
        editBPUserDialog.setView(editBPUserLayout)
        editBPUserDialog.setTitle("Edit User")
        editBPUserDialog.setPositiveButton("Confirm") { dialog, _ ->
            val weight = editBPUserCard.weightInput.text.toString()
            val height = editBPUserCard.heightInput.text.toString()
            if (weight.isNotEmpty() && height.isNotEmpty()) {
                Log.i(TAG, "Editing user data...")
                firestoreDataViewModel.updateBPUserData(bpUser.id, weight.toInt(), height.toInt())
                Snackbar.make(requireView(), "User edited successfully", Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Snackbar.make(requireView(), "Please fill out all fields", Snackbar.LENGTH_SHORT).show()
            }
        }
        editBPUserDialog.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        editBPUserDialog.show()
    }

    private fun deleteBPUser(bpUser: BPUser) {
        val deleteBPUserDialog = AlertDialog.Builder(safeContext)
        deleteBPUserDialog.setTitle("Delete User")
        deleteBPUserDialog.setPositiveButton("Confirm") { dialog, _ ->
            Log.i(TAG, "Deleting user data...")
            firestoreDataViewModel.deleteBPUserData(bpUser.id)
            Snackbar.make(requireView(), "User deleted successfully", Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        deleteBPUserDialog.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        deleteBPUserDialog.show()
    }

    private fun addNewBPUser() {
        val addBPUserDialog = AlertDialog.Builder(safeContext)
        val addBPUserLayout = addBPUserCard.root
        if (addBPUserLayout.parent != null) {
            (addBPUserLayout.parent as ViewGroup).removeView(addBPUserLayout)
        }
        addBPUserDialog.setView(addBPUserLayout)
        addBPUserDialog.setTitle("Add New User")
        addBPUserDialog.setPositiveButton("Confirm") { dialog, _ ->
            val name = addBPUserCard.nameInput.text.toString()
            val weight = addBPUserCard.weightInput.text.toString()
            val height = addBPUserCard.heightInput.text.toString()
            val datePicker = addBPUserCard.birthdateInput
            val calendar = Calendar.getInstance()
            calendar.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
            val birthdate = Timestamp(calendar.time)
            if (name.isNotEmpty() && weight.isNotEmpty() && height.isNotEmpty()) {
                Log.i(TAG, "Adding $name as new user...")
                firestoreDataViewModel.setBPUserData(name, weight.toInt(), height.toInt(), birthdate)
                Snackbar.make(requireView(), "New user added successfully", Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Snackbar.make(requireView(), "Please fill out all fields", Snackbar.LENGTH_SHORT).show()
            }
        }
        addBPUserDialog.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        addBPUserDialog.show()
    }

    private fun formatFirebaseTimestamp(timestamp: Timestamp) : String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val date = timestamp.toDate()
        return dateFormat.format(date)
    }

    companion object {
        private const val TAG = "Profile"
    }
}