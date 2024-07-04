package com.example.m_health

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.m_health.databinding.ActivityRegisterBinding
import com.example.m_health.model.FirestoreDataViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreDataViewModel: FirestoreDataViewModel

    private lateinit var birthdate: Date

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestoreDataViewModel = ViewModelProvider(this)[FirestoreDataViewModel::class.java]

        birthdate = Date()

        binding.registerButton.setOnClickListener {
            register()
        }

        binding.registerFooterText2.setOnClickListener {
            goToLogin()
        }

        binding.birthdateInput.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun register() {
        val name = binding.nameInput.text.toString()
        val email = binding.emailInput.text.toString()
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.passwordInput.text.toString()
        val weight = binding.weightInput.text.toString()
        val height = binding.heightInput.text.toString()
        val date = binding.birthdateInput.text.toString()

        if (
            name.isNotEmpty() &&
            email.isNotEmpty() &&
            password.isNotEmpty() &&
            confirmPassword.isNotEmpty() &&
            weight.isNotEmpty() &&
            height.isNotEmpty() &&
            date.isNotEmpty()
        ) {
            if (password == confirmPassword) {
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Registration successful! Please login to continue", Toast.LENGTH_LONG).show()
                        firestoreDataViewModel.setBPUserData(
                            "$name (You)",
                            weight.toInt(),
                            height.toInt(),
                            Timestamp(birthdate)
                        )
                        goToLogin()
                        finish()
                    } else {
                        Snackbar.make(binding.root, it.exception?.message.toString(), Snackbar.LENGTH_LONG).show()
                    }
                }
            } else {
                Snackbar.make(binding.root, "Password and password confirmation do not match", Snackbar.LENGTH_LONG).show()
            }
        } else {
            Snackbar.make(binding.root, "Please fill out all fields", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val datePickerDialog = DatePickerDialog(
            this,
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                calendar.set(Calendar.YEAR, selectedYear)
                calendar.set(Calendar.MONTH, selectedMonth)
                calendar.set(Calendar.DAY_OF_MONTH, selectedDay)
                birthdate = calendar.time
                binding.birthdateInput.setText(dateFormat.format(calendar.time))
            },
            year, month, day
        )
        datePickerDialog.show()
    }
}