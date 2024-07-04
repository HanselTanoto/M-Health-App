package com.example.m_health.model.data

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.m_health.R
import com.google.firebase.Timestamp

/**
 * Represents a blood pressure record.
 *
 * This data class defines the structure of a blood pressure record, including its ID, systolic (sys), diastolic (dia),
 * and pulse (pul) values, timestamp (time) of the record, and the category (category) determined based on the blood
 * pressure values.
 *
 * @param id the ID of the blood pressure record.
 * @param sys the systolic blood pressure value.
 * @param dia the diastolic blood pressure value.
 * @param pul the pulse rate value.
 * @param time the timestamp when the blood pressure was recorded.
 * @param category the category of the blood pressure record based on its values.
 */
data class BPRecord(
    val id: String,
    val sys: Int,
    val dia: Int,
    val pul: Int,
    val time: Timestamp,
    val category: String,
) {
    /**
     * Determines the blood pressure record category and its corresponding color based on systolic and diastolic values.
     *
     * This function categorizes blood pressure records into different categories based on systolic (sys) and
     * diastolic (dia) values. It returns a pair containing the category name and the corresponding color resource ID.
     *
     * @param context the context to access resources.
     * @return a pair containing the blood pressure record category and its corresponding color resource ID.
     */
    fun getRecordCategory(context: Context, bpUser:BPUser) : Pair<String, Int> {
        var adjustedSys = sys
        var adjustedDia = dia
        val age = bpUser.getAge()
        if (age <= 1) {
            adjustedSys += 30
            adjustedDia += 20
        } else if (age <= 5) {
            adjustedSys += 25
            adjustedDia += 15
        } else if (age <= 13) {
            adjustedSys += 15
            adjustedDia += 10
        } else if (age <= 19) {
            adjustedSys += 3
            adjustedDia += 3
        } else if (age <= 29) {
            // No adjustment
        } else if (age <= 34) {
            adjustedSys -= 2
            adjustedDia -= 1
        } else if (age <= 39) {
            adjustedSys -= 3
            adjustedDia -= 2
        } else if (age <= 44) {
            adjustedSys -= 5
            adjustedDia -= 3
        } else if (age <= 49) {
            adjustedSys -= 7
            adjustedDia -= 4
        } else if (age <= 54) {
            adjustedSys += 9
            adjustedDia += 5
        } else if (age <= 59) {
            adjustedSys -= 11
            adjustedDia -= 6
        } else { // age > 60
            adjustedSys -= 14
            adjustedDia -= 7
        }
        return when {
            (adjustedSys < 90 || adjustedDia < 60)                            -> "LOW" to ContextCompat.getColor(context, R.color.bp_blue)
            (adjustedSys < 120 && adjustedDia < 80)                           -> "OPTIMAL" to ContextCompat.getColor(context, R.color.bp_green)
            (adjustedSys in 120..129 || adjustedDia in 80..84)    -> "NORMAL" to ContextCompat.getColor(context, R.color.bp_green)
            (adjustedSys in 130..139 || adjustedDia in 85..89)    -> "HIGH NORMAL: PREHYPERTENSION" to ContextCompat.getColor(context, R.color.bp_yellow)
            (adjustedSys in 140..159 || adjustedDia in 90..99)    -> "HIGH: STAGE 1 HYPERTENSION" to ContextCompat.getColor(context, R.color.bp_orange)
            (adjustedSys in 160..179 || adjustedDia in 100..109)  -> "HIGH: STAGE 2 HYPERTENSION" to ContextCompat.getColor(context, R.color.bp_red)
            else /* sys >= 180 || dia >= 110 */             -> "VERY HIGH: STAGE 3 HYPERTENSION" to ContextCompat.getColor(context, R.color.bp_red_dark)
        }
    }
}