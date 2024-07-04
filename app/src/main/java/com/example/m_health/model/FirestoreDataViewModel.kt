package com.example.m_health.model

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.m_health.model.data.BPRecord
import com.example.m_health.model.data.BPUser
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.firestore
import java.util.Locale


/** ViewModel class for managing Firestore data and authentication. */
class FirestoreDataViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** LiveData representing the currently authenticated user as FirebaseUser. */
    private val _firebaseUser = MutableLiveData<FirebaseUser>()
    val firebaseUser: LiveData<FirebaseUser> = _firebaseUser

    /** LiveData representing the currently authenticated user as BPUser. */
    private val _authUser = MutableLiveData<BPUser>()
    val authUser: LiveData<BPUser> = _authUser

    /** LiveData representing the list of BPUser objects fetched from Firestore. */
    private val _bpUserList = MutableLiveData<MutableList<BPUser>>().apply {
        value = mutableListOf()
    }
    val bpUserList: LiveData<MutableList<BPUser>> = _bpUserList

    init {
        fetchUserCredentials()
        getBPUserData()
    }

    /**
     * Fetches the current user's credentials from Firebase authentication.
     *
     * This function retrieves the current user's credentials from Firebase authentication
     * and updates the `_firebaseUser` LiveData with the fetched user information.
     */
    private fun fetchUserCredentials() {
        val currentUser: FirebaseUser? = auth.currentUser
        _firebaseUser.value = currentUser
    }

    /**
     * Retrieves associated list of BPUser data from Firestore.
     *
     * This function fetches list of BPUser data from Firestore that is associated with current authenticated user's UID.
     * It retrieves user documents and their corresponding blood pressure records, constructs BPUser
     * objects, and updates the LiveData objects `_authUser` and `_bpUserList` with the fetched data.
     * If an error occurs during retrieval, appropriate error logs are generated.
     */
    fun getBPUserData() {
        val db = Firebase.firestore
        val uid = firebaseUser.value?.uid ?: ""
        // Log.d(TAG, "UID: $uid")
        if (uid.isEmpty()) {
            Log.w(TAG, "Error getting bp user documents: uid is undefined")
            return
        }
        val bpUserList = mutableListOf<BPUser>()
        db.collection(uid).get().addOnSuccessListener { documents ->
            for (document in documents) {
                val bpUser = BPUser(
                    id          = document.id,
                    name        = document.getString("name").toString(),
                    weight      = document.getLong("weight")?.toInt() ?: 0,
                    height      = document.getLong("height")?.toInt() ?: 0,
                    birthdate   = document.getTimestamp("birthdate") ?: Timestamp.now()
                )
                if (document.getString("id").toString() == "U01") {
                    _authUser.value = bpUser
                }
                // Log.d(TAG, "${document.id} => ${document.data}")
                db.collection(uid).document(document.id).collection("bpvalue").get().addOnSuccessListener { nestedDocuments ->
                    for (nestedDocument in nestedDocuments) {
                        bpUser.records.add(
                            BPRecord(
                                id          = nestedDocument.id,
                                sys         = nestedDocument.getLong("sys")?.toInt() ?: 0,
                                dia         = nestedDocument.getLong("dia")?.toInt() ?: 0,
                                pul         = nestedDocument.getLong("pul")?.toInt() ?: 0,
                                time        = nestedDocument.getTimestamp("timestamp") ?: Timestamp.now(),
                                category    = nestedDocument.getString("category").toString(),
                            )
                        )
                    }
                    bpUser.records = bpUser.records.sortedBy { it.time }.toMutableList()
                    bpUserList.add(bpUser)
                    _bpUserList.value = bpUserList.sortedBy { it.id }.toMutableList()
                }.addOnFailureListener { exception ->
                    Log.w(TAG, "Error getting bp record documents.", exception)
                }
            }
        }.addOnFailureListener { exception ->
            Log.w(TAG, "Error getting bp user documents.", exception)
        }
    }

    /**
     * Sets BPUser data in Firestore.
     *
     * This function adds a new BPUser document to Firestore based on the current user's UID.
     * It generates a new user ID, constructs the user data, and sets the document in Firestore.
     * After successful addition, it retrieves updated user data using `getBPUserData`.
     *
     * @param name the name of the user.
     * @param weight the body weight of the user.
     * @param height the body height of the user.
     * @param birthdate the birthdate of the user as a Timestamp.
     */
    fun setBPUserData(name: String, weight: Int, height: Int, birthdate: Timestamp) {
        val db = Firebase.firestore
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        // Log.d(TAG, "UID: $uid")
        if (uid.isEmpty()) {
            Log.w(TAG, "Error adding user document: uid is undefined")
            return
        }
        val newBPUserId = generateNewUserId(_bpUserList.value)
        val newBPUser = hashMapOf(
            "id" to newBPUserId,
            "name" to capitalizeWords(name),
            "weight" to weight,
            "height" to height,
            "birthdate" to birthdate
        )
        db.collection(uid).document(newBPUserId).set(newBPUser).addOnSuccessListener {
            Log.i(TAG, "User's DocumentSnapshot added with ID: $newBPUserId")
            getBPUserData()
        }.addOnFailureListener { e ->
            Log.w(TAG, "Error adding user document", e)
        }
    }

    /**
     * Sets blood pressure record data in Firestore.
     *
     * This function adds a new blood pressure record document to Firestore for a given BPUser.
     * It generates a new record ID, constructs the record data, and sets the document in Firestore.
     * After successful addition, it retrieves updated user data using `getBPUserData`.
     *
     * @param bpUser the BPUser for which to add the record.
     * @param sys the systolic blood pressure value.
     * @param dia the diastolic blood pressure value.
     * @param pul the pulse rate value.
     * @param time the timestamp of the record.
     */
    fun setBPRecordData(bpUser: BPUser, sys: Int, dia: Int, pul: Int, time: Timestamp) {
        val db = Firebase.firestore
        val uid = firebaseUser.value?.uid ?: ""
        // Log.d(TAG, "UID: $uid")
        if (uid.isEmpty()) {
            Log.w(TAG, "Error adding record document: uid is undefined")
            return
        }
        val bpRecordCount = bpUser.records.size
        val newBPRecordId = String.format("%s-R%03d", bpUser.id, bpRecordCount+1)
        val newBPRecord = hashMapOf(
            "sys" to sys,
            "dia" to dia,
            "pul" to pul,
            "timestamp" to time,
            "category" to getRecordCategory(sys, dia, bpUser)
        )
        db.collection(uid).document(bpUser.id).collection("bpvalue").document(newBPRecordId)
            .set(newBPRecord).addOnSuccessListener {
                Log.i(TAG, "Record's DocumentSnapshot added with ID: $newBPRecordId")
                getBPUserData()
            }.addOnFailureListener { e ->
                Log.w(TAG, "Error adding record document", e)
            }
    }

    /**
     * Updates BPUser data in Firestore.
     *
     * This function updates the weight and height fields of a BPUser document in Firestore.
     * It constructs the update data, performs the update operation, and retrieves updated user
     * data using `getBPUserData` after successful update.
     *
     * @param id the ID of the user document to update.
     * @param weight the new weight value to update.
     * @param height the new height value to update.
     */
    fun updateBPUserData(id: String, weight: Int, height: Int) {
        val db = Firebase.firestore
        val uid = firebaseUser.value?.uid ?: ""
        // Log.d(TAG, "UID: $uid")
        if (uid.isEmpty()) {
            Log.w(TAG, "Error adding user document: uid is undefined")
            return
        }
        val updates = hashMapOf(
            "weight" to weight,
            "height" to height,
        )
        db.collection(uid).document(id).update(updates as Map<String, Any>).addOnSuccessListener{
            Log.i(TAG, "User's DocumentSnapshot with ID: $id has been updated")
            getBPUserData()
        }.addOnFailureListener { e ->
            Log.w(TAG, "Error adding user document", e)
        }
    }

    /**
     * Deletes BPUser data from Firestore.
     *
     * This function deletes a BPUser document from Firestore based on the provided ID.
     * After successful deletion, it retrieves updated user data using `getBPUserData`.
     *
     * @param id the ID of the user document to delete.
     */
    fun deleteBPUserData(id: String) {
        val db = Firebase.firestore
        val uid = firebaseUser.value?.uid ?: ""
        // Log.d(TAG, "UID: $uid")
        if (uid.isEmpty()) {
            Log.w(TAG, "Error adding user document: uid is undefined")
            return
        }
        db.collection(uid).document(id).delete().addOnSuccessListener{
            Log.i(TAG, "User's DocumentSnapshot with ID: $id has been deleted")
            getBPUserData()
        }.addOnFailureListener { e ->
            Log.w(TAG, "Error adding user document", e)
        }
    }

    /**
     * Generates a new user ID based on the existing BPUser list.
     *
     * This function generates a new user ID by examining the existing BPUser list and finding the highest
     * numeric part in the user IDs. It then increments this number by 1 and formats the new ID accordingly.
     * If the provided BPUser list is null or empty, it generates a new ID starting from 1.
     *
     * @param bpUserList the list of BPUsers to analyze for generating the new ID.
     * @return a new user ID.
     */
    private fun generateNewUserId(bpUserList: MutableList<BPUser>?) : String {
        var highestIdNumber = 0
        if (bpUserList != null) {
            for (bpUser in bpUserList) {
                val id = bpUser.id
                if (id.startsWith("U")) {
                    val numberPart = id.substring(1).toIntOrNull()
                    if (numberPart != null && numberPart > highestIdNumber) {
                        highestIdNumber = numberPart
                    }
                }
            }
        }
        return String.format("U%02d", highestIdNumber.plus(1))
    }

    /**
     * Determines the blood pressure record category based on systolic and diastolic values.
     *
     * This function categorizes blood pressure records into different categories based on systolic (sys) and
     * diastolic (dia) values. It returns a string representing the category.
     *
     * @param sys the systolic blood pressure value.
     * @param dia the diastolic blood pressure value.
     * @return the category of the blood pressure record.
     */
    fun getRecordCategory(sys: Int, dia: Int, bpUser: BPUser) : String {
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
            (adjustedSys < 90 || adjustedDia < 60) -> "LOW"
            (adjustedSys < 120 && adjustedDia < 80) -> "OPTIMAL"
            (adjustedSys in 120..129 || adjustedDia in 80..84) -> "NORMAL"
            (adjustedSys in 130..139 || adjustedDia in 85..89) -> "HIGH NORMAL: PREHYPERTENSION"
            (adjustedSys in 140..159 || adjustedDia in 90..99) -> "HIGH: STAGE 1 HYPERTENSION"
            (adjustedSys in 160..179 || adjustedDia in 100..109) -> "HIGH: STAGE 2 HYPERTENSION"
            // sys >= 180 || dia >= 110
            else -> "VERY HIGH: STAGE 3 HYPERTENSION"
        }
    }

    /**
     * Capitalizes the first letter of each word in a given input string.
     *
     * This function splits the input string into words, capitalizes the first letter of each word, and then joins
     * the capitalized words back into a single string. It returns the resulting string with each word capitalized.
     *
     * @param input the input string to capitalize.
     * @return the input string with each word capitalized.
     */
    private fun capitalizeWords(input: String) : String {
        val words = input.split(" ")
        val capitalizedWords = words.map { word ->
            word.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault())
            else char.toString()
        } }
        return capitalizedWords.joinToString(" ")
    }

    companion object {
        private const val TAG = "FirestoreDataViewModel"
    }
}