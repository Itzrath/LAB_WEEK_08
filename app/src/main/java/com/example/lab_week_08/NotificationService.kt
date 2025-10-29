package com.example.lab_week_08

import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class NotificationService : Service() {

    // TAG for logging any errors
    private val TAG = "NotificationService"

    // 1. Class Properties (HandlerThread setup)
    private lateinit var serviceHandler: Handler
    private lateinit var handlerThread: HandlerThread

    // --- Service Lifecycle Methods ---
    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize the background HandlerThread ONLY once in onCreate
        handlerThread = HandlerThread("SecondThread").apply { start() }
        serviceHandler = Handler(handlerThread.looper)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Always clean up the Looper when the service is destroyed.
        serviceHandler.looper.quitSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Return value: START_NOT_STICKY is best for one-off tasks
        val returnValue = START_NOT_STICKY

        // 1. Get the ID and check for null safely
        val id = intent?.getStringExtra(EXTRA_ID)
        if (id == null) {
            Log.e(TAG, "Channel ID (EXTRA_ID) not provided, stopping service.")
            stopSelf()
            return returnValue
        }

        // 2. Start Foreground Service and get the builder object
        val notificationBuilder = startForegroundServiceSetup()

        // 3. Post the work (counting, notifying, stopping) to the background thread
        serviceHandler.post {
            try {
                // Actual work: Count down
                countDownFromTenToZero(notificationBuilder)

                // Update LiveData (must use Main Looper)
                notifyCompletion(id)

                // 4. Stop the foreground state and the service
                stopForeground(true) // 'true' means also remove the notification
                stopSelf() // Stop and destroy the service

            } catch (e: Exception) {
                // Log the crash on the background thread
                Log.e(TAG, "CRASH in Work Thread: ${e.message}", e)
                stopSelf()
            }
        }

        return returnValue
    }

    // --- Foreground Service Setup ---

    // Renamed to include 'Setup' to differentiate it from the service lifecycle method
    private fun startForegroundServiceSetup(): NotificationCompat.Builder {
        val pendingIntent = getPendingIntent()
        val channelId = createNotificationChannel()
        val notificationBuilder = getNotificationBuilder(pendingIntent, channelId)

        // This is the CRUCIAL call that places the service in the foreground
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        return notificationBuilder
    }

    // --- Helper Methods ---

    private fun getPendingIntent(): PendingIntent {
        // Note: Build.VERSION_CODES.S is API 31, which is what FLAG_IMMUTABLE requires.
        // It's safe to keep the logic for future compatibility.
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            FLAG_IMMUTABLE else 0

        return PendingIntent.getActivity(
            this, 0, Intent(
                this,
                MainActivity::class.java
            ), flag
        )
    }

    private fun createNotificationChannel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "001"
            val channelName = "001 Channel"
            val channelPriority = NotificationManager.IMPORTANCE_DEFAULT

            val channel = NotificationChannel(
                channelId,
                channelName,
                channelPriority
            )

            val service = requireNotNull(
                ContextCompat.getSystemService(this,
                    NotificationManager::class.java)
            )

            service.createNotificationChannel(channel)
            channelId
        } else {
            "default_channel" // Fallback ID for pre-Oreo devices
        }
    }

    private fun getNotificationBuilder(pendingIntent: PendingIntent, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Second worker process is done")
            .setContentText("Check it out!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Recommended for ongoing/passive status

    private fun countDownFromTenToZero(notificationBuilder: NotificationCompat.Builder) {
        // Gets the notification manager
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Count down from 10 to 0
        for (i in 10 downTo 0) {
            // This blocking call is safe because it's running on the serviceHandler thread.
            Thread.sleep(1000L)

            // Updates the notification content text
            notificationBuilder.setContentText("$i seconds until last warning")
                .setSilent(true)

            // Notify the notification manager about the content update
            notificationManager.notify(
                NOTIFICATION_ID,
                notificationBuilder.build()
            )
        }
    }

    // Update the LiveData with the returned channel id through the Main Thread
    private fun notifyCompletion(Id: String) {
        Handler(Looper.getMainLooper()).post {
            // Note: Use postValue if you were unsure if you were on the main thread,
            // but here we are ensuring we are on the main looper, so .value is okay.
            mutableID.value = Id
        }
    }

    // --- Companion Object ---
    companion object {
        const val NOTIFICATION_ID = 0xCA7
        const val EXTRA_ID = "Id"

        private val mutableID = MutableLiveData<String>()
        val trackingCompletion: LiveData<String> = mutableID
    }
}