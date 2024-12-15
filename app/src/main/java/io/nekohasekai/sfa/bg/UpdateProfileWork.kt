package io.nekohasekai.sfa.bg

import android.content.Context
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec







class UpdateProfileWork {

    companion object {
        private const val WORK_NAME = "UpdateProfile"
        private const val TAG = "UpdateProfileWork"
        const val BASE_URL = "https://raw.githubusercontent.com/rtlvpn/junk/main/"

        suspend fun reconfigureUpdater() {
            runCatching {
                reconfigureUpdater0()
            }.onFailure {
                Log.e(TAG, "reconfigureUpdater", it)
            }
        }
        fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            return digest.fold("", { str, it -> str + "%02x".format(it) })
        }
            
        private suspend fun reconfigureUpdater0() {
            val remoteProfiles = ProfileManager.list()
                .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) {
                WorkManager.getInstance(Application.application).cancelUniqueWork(WORK_NAME)
                return
            }

            var minDelay =
                remoteProfiles.minByOrNull { it.typed.autoUpdateInterval }!!.typed.autoUpdateInterval.toLong()
            val nowSeconds = System.currentTimeMillis() / 1000L
            val minInitDelay =
                remoteProfiles.minOf { (it.typed.autoUpdateInterval * 60) - (nowSeconds - (it.typed.lastUpdated.time / 1000L)) }
            if (minDelay < 15) minDelay = 15
            WorkManager.getInstance(Application.application).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(UpdateTask::class.java, minDelay, TimeUnit.MINUTES)
                    .apply {
                        if (minInitDelay > 0) setInitialDelay(minInitDelay, TimeUnit.SECONDS)
                        setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                    }
                    .build()
            )
        }

    }


    class UpdateTask(
        appContext: Context, params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            val remoteProfiles = ProfileManager.list()
                .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) return Result.success()
            var success = true
            val sharedPref = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val androidIdKey = "android_id"
            var androidId = sharedPref.getString(androidIdKey, null)
            if (androidId == null) {
                androidId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
                with(sharedPref.edit()) {
                    putString(androidIdKey, androidId)
                    apply()
                }
            }

            for (profile in remoteProfiles) {
                val lastSeconds =
                    (System.currentTimeMillis() - profile.typed.lastUpdated.time) / 1000L
                if (lastSeconds < profile.typed.autoUpdateInterval * 60) {
                    continue
                }
                try {
                 
                    val encryptedContent = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                    // The key is now the secret text itself
                    val keySpec = SecretKeySpec(profile.name.toByteArray(), "AES")

                    // Decrypt the AES
                    val json = JSONObject(encryptedContent)
                    val iv = Base64.decode(json.getString("iv"), Base64.DEFAULT)
                    val ct = Base64.decode(json.getString("ciphertext"), Base64.DEFAULT)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    val ivSpec = IvParameterSpec(iv)
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                    val decryptedContent = String(cipher.doFinal(ct))
                    val timestampRegex = "(?<=//)\\d+(?=$)".toRegex()
                    val timestampStr = timestampRegex.find(decryptedContent)?.value
                    val timestampLong = timestampStr?.toLong()
                    with (sharedPref.edit()) {
                        if (timestampLong != null) {
                            putLong(profile.name + "stamp", timestampLong)
                        }

                        apply()
                    }

                    Libbox.checkConfig(decryptedContent)
                    File(profile.typed.path).writeText(decryptedContent)
                    profile.typed.lastUpdated = Date()
                    ProfileManager.update(profile)

                    val usageDataKey = "${profile.name}-usage"
                    val usageData = sharedPref.getLong(usageDataKey, 0L)
                    if (usageData > 0) {
                        // Assume you have a Firestore collection named "usageData"
                        val db = Firebase.firestore
                        val usageDoc = db.collection("usageData").document(profile.name)
                        val usageIncrement = hashMapOf<String, Any>(
                            "usage" to hashMapOf(
                                androidId to FieldValue.increment(usageData)
                            )
                        )
                        db.collection("keys").document(profile.name)
                            .set(usageIncrement, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d("Firebase", "DocumentSnapshot successfully written!")
                                with(sharedPref.edit()) {
                                    putLong(usageDataKey, 0L) // Reset to 0
                                    apply()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Error sending usage data to Firestore for profile: ${profile.name}", e)
                            }

                    }


//mac tonight
// Check if Android ID has been sent before
                    val androidIdSentKey = "${profile.name}_android_id_sent"
                    val androidIdSent = sharedPref.getBoolean(androidIdSentKey, false)

// If Android ID has not been sent before, add it to Firestore
                    if (!androidIdSent) {
                        val db = Firebase.firestore

                        // Get the document with ID as profile.name
                        val docRef = db.collection("keys").document(profile.name)

                        // Get the current macs field
                        docRef.get().addOnSuccessListener { document ->
                            if (document != null) {
                                // Retrieve the macs field, defaulting to an empty HashMap if it doesn't exist
                                val macs = document.get("macs") as? HashMap<String, Boolean> ?: hashMapOf()

                                // Check if the Android ID is already in the macs field
                                    // If not, add it
                                macs[androidId.toString()] = true

                                // Update the macs field in the document
                                docRef.update("macs", macs)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "DocumentSnapshot successfully updated!")

                                            // Update the flag in SharedPreferences
                                        with(sharedPref.edit()) {
                                            putBoolean(androidIdSentKey, true)
                                            apply()
                                        }
                                    }
                                .addOnFailureListener { e -> Log.w(TAG, "Error updating document", e) }

                                // Retrieve the providers field, defaulting to an empty HashMap if it doesn't exist
                                val providers = document.get("providers") as? HashMap<String, String> ?: hashMapOf()

                                // Check if the Android ID is already in the providers field
                                if (!providers.containsKey(androidId)) {
                                    // Fetch the provider name using TelephonyManager
                                    val telephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                                    val carrierName = telephonyManager.networkOperatorName

                                    // Add the Android ID with the provider names to the providers field
                                    providers[androidId.toString()] = carrierName

                                    // Update the providers field in the document
                                    docRef.update("providers", providers)
                                        .addOnSuccessListener {
                                            Log.d(TAG, "DocumentSnapshot successfully updated with providers!")
                                        }
                                        .addOnFailureListener { e -> Log.w(TAG, "Error updating providers", e) }
                                }
                            }
                        }
                    }





                } catch (e: Exception) {
                    Log.e(TAG, "update profile ${profile.name}", e)
                    success = false
                }
            }
            return if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        }

    }


}