package com.example.m_health.model.data

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.m_health.R
import com.google.firebase.Timestamp
import java.util.Calendar


/**
 * Represents a user's blood pressure data.
 *
 * This data class defines the structure of a user's blood pressure data, including the
 * user's ID, name, weight, height, birthdate, and a list of blood pressure records.
 *
 * @param id the ID of the user.
 * @param name the name of the user.
 * @param weight the weight of the user.
 * @param height the height of the user.
 * @param birthdate the birthdate of the user.
 * @param records a list of blood pressure records associated with the user (default empty list).
 */
data class BPUser(
    val id: String,
    val name: String,
    val weight: Int,
    val height: Int,
    val birthdate: Timestamp,
    var records: MutableList<BPRecord> = mutableListOf()
) {
    /**
     * Calculates the age based on the user's birthdate.
     *
     * This function calculates the age of the user based on their birthdate.
     * It uses the current date to determine the age accurately,
     * considering the birthday's day of the year.
     *
     * @return the age of the user.
     */
    fun getAge() : Int {
        val now = Calendar.getInstance()
        val birthDay = Calendar.getInstance()
        birthDay.time = birthdate.toDate()
        var age = now.get(Calendar.YEAR) - birthDay.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < birthDay.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        return age
    }

    /**
     * Calculates the Body Mass Index (BMI) based on the user's weight and height.
     *
     * This function calculates the Body Mass Index (BMI) of the user based on their weight in kilograms and height in meters.
     * It uses the formula BMI = weight / (height * height) where weight is in kilograms and height is in meters.
     * The BMI value is formatted to one decimal point for precision.
     *
     * @return the Body Mass Index (BMI) of the user.
     */
    fun getBMI() : Float {
        val w = weight.toFloat()
        val h = height.toFloat() / 100
        val bmi = w / (h * h)
        return String.format("%.1f", bmi).toFloat()
    }

    /**
     * Determines the BMI category based on the user's Body Mass Index (BMI).
     *
     * This function calculates the Body Mass Index (BMI) of the user using the getBMI() function
     * and then categorizes the BMI into different categories based on standard BMI ranges.
     * It returns a pair containing the BMI category name and the corresponding
     * color resource ID based on the provided context.
     *
     * @param context the context to access resources.
     * @return a pair containing the BMI category name and its corresponding color resource ID.
     */
    fun getBMICategory(context: Context) : Pair<String, Int> {
        val bmi = getBMI()
        return when {
            bmi < 18.5 -> "Underweight"     to ContextCompat.getColor(context, R.color.bp_blue)
            bmi < 24.9 -> "Normal"          to ContextCompat.getColor(context, R.color.bp_green)
            bmi < 29.9 -> "Overweight"      to ContextCompat.getColor(context, R.color.bp_yellow)
            bmi < 34.9 -> "Obese Class I"   to ContextCompat.getColor(context, R.color.bp_orange)
            bmi < 39.9 -> "Obese Class II"  to ContextCompat.getColor(context, R.color.bp_red)
            else       -> "Obese Class III" to ContextCompat.getColor(context, R.color.bp_red_dark)
        }
    }
}