package com.example.lab_week_08

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.*
import com.example.lab_week_08.worker.FirstWorker
import com.example.lab_week_08.worker.SecondWorker
import com.example.lab_week_08.worker.ThirdWorker

class MainActivity : AppCompatActivity() {

    private val workManager = WorkManager.getInstance(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- Permissions ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        // --- Constraints ---
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val id = "001"

        // 1. Define all requests
        val firstRequest = OneTimeWorkRequest
            .Builder(FirstWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(FirstWorker.INPUT_DATA_ID, id))
            .build()

        val secondRequest = OneTimeWorkRequest
            .Builder(SecondWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(SecondWorker.INPUT_DATA_ID, id))
            .build()

        // 🔑 NEW: Define ThirdWorker request here (but DO NOT enqueue it yet)
        val thirdRequest = OneTimeWorkRequest
            .Builder(ThirdWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(ThirdWorker.INPUT_DATA_ID, id))
            .build()


        // --- Work Chain (1 -> 2) ---
        // The chain now stops after the SecondWorker.
        workManager.beginWith(firstRequest)
            .then(secondRequest)
            .enqueue()

        // --- LiveData Observers (Control Flow) ---

        // 🔑 1. Observer to start the ThirdWorker
        // This observer monitors the completion of the FIRST Notification Service.
        NotificationService.trackingCompletion.observe(this) { Id ->
            showResult("Process for Notification Channel ID $Id is done!")

            // 🔑 CORE FIX: When the service (and its countdown) is DONE,
            // we manually enqueue the ThirdWorker.
            workManager.enqueue(thirdRequest) // -> Triggers Step 4
        }

        // 2. Observer to start the Second Notification Service
        SecondNotificationService.trackingCompletion.observe(this) { Id ->
            showResult("Process for Second Notification Channel ID $Id is done!")
            // No more work needs to be triggered after this.
        }

        // 3. Observer for First Worker
        workManager.getWorkInfoByIdLiveData(firstRequest.id)
            .observe(this) { info ->
                if (info?.state?.isFinished == true) {
                    showResult("First process is done")
                }
            }

        // 4. Observer for Second Worker (Triggers the FIRST NotificationService)
        workManager.getWorkInfoByIdLiveData(secondRequest.id)
            .observe(this) { info ->
                if (info?.state?.isFinished == true) {
                    showResult("Second process is done")
                    launchNotificationService() // -> Triggers Step 3
                }
            }

        // 5. Observer for Third Worker (Triggers the SECOND NotificationService)
        workManager.getWorkInfoByIdLiveData(thirdRequest.id)
            .observe(this) { info ->
                if (info?.state?.isFinished == true) {
                    showResult("Third process is done")
                    launchSecondNotificationService() // -> Triggers Step 5
                }
            }
    }

    // --- Private Helper Methods ---

    private fun getIdInputData(idKey: String, idValue: String) =
        Data.Builder()
            .putString(idKey, idValue)
            .build()

    private fun showResult(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun launchNotificationService() {
        val serviceIntent = Intent(this, NotificationService::class.java).apply {
            putExtra(EXTRA_ID, "001")
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun launchSecondNotificationService() {
        val serviceIntent = Intent(this, SecondNotificationService::class.java).apply {
            putExtra(EXTRA_ID, "002")
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    companion object{
        const val EXTRA_ID = "Id"
    }
}