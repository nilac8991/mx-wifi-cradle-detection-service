package com.nilac.zebra.wificradledetection

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.symbol.emdk.EMDKResults
import com.zebra.nilac.emdkloader.EMDKLoader
import com.zebra.nilac.emdkloader.ProfileLoader
import com.zebra.nilac.emdkloader.interfaces.EMDKManagerInitCallBack
import com.zebra.nilac.emdkloader.interfaces.ProfileLoaderResultCallback


class MainService : Service() {

    private var mIsServiceRunning = false

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        mIsServiceRunning = true

        EMDKLoader.getInstance()
            .initEMDKManager(this, object : EMDKManagerInitCallBack {
                override fun onSuccess() {
                    Log.i(
                        TAG,
                        "EMDK Manager successfully initialised, proceeding with service start"
                    )

                    val filters: IntentFilter = IntentFilter().apply {
                        addAction(Intent.ACTION_POWER_DISCONNECTED)
                        addAction(Intent.ACTION_POWER_CONNECTED)
                    }
                    registerReceiver(receiver, filters)

                    createServiceNotification()
                }

                override fun onFailed(message: String) {
                    Log.e(
                        TAG,
                        "Failed to initialise the EMDK Manager, can't start the foreground service"
                    )
                }
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && DISMISS_NOTIFICATION_ACTION == intent.action) {
            createServiceNotification()
        }
        if (!mIsServiceRunning) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        EMDKLoader.getInstance().release()
        unregisterReceiver(receiver)
        mIsServiceRunning = false
    }

    private fun createServiceNotification() {
        val channelId = "com.nilac.zebra.wificradledetection"
        val channelName = "WiFi Toggle Channel"

        // Create Channel
        val notificationChannel = NotificationChannel(
            channelId, channelName,
            NotificationManager.IMPORTANCE_NONE
        )
        notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        // Set Channel
        val manager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(notificationChannel)

        // Build Notification
        val notificationBuilder = NotificationCompat.Builder(
            this,
            channelId
        )

        //Dismiss Intent
        val dismissIntent = Intent(this, MainService::class.java).apply {
            action = DISMISS_NOTIFICATION_ACTION
        }

        val dismissPendingIntent = PendingIntent.getService(
            this,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Return Build Notification object
        val notification = notificationBuilder
            .setContentTitle("Service is active")
            .setSmallIcon(R.drawable.ic_wifi_settings)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setDeleteIntent(dismissPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(7161, notification)
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.d(TAG, "Device has been connected to AC Power")
                    toggleWiFiStateIfNotEnabled()
                }
            }
        }
    }

    private fun toggleWiFiStateIfNotEnabled() {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled) {
            return
        }

        val profile =
            """
            <wap-provisioningdoc>
                <characteristic type="Profile">
                    <parm name="ProfileName" value="WiFiToggle" />
                    <characteristic version="4.3" type="Wi-Fi">
                        <characteristic type="System">
                            <parm name="WiFiAction" value="enable" />
                        </characteristic>
                    </characteristic>
                </characteristic>
            </wap-provisioningdoc>
            """.trimIndent()

        ProfileLoader().processProfile(
            AppConstants.PROFILE_NAME,
            profile,
            object : ProfileLoaderResultCallback {
                override fun onProfileLoaded() {
                    Log.d(TAG, "Profile successfully applied")
                }

                override fun onProfileLoadFailed(errorObject: EMDKResults) {
                    //Nothing to see here
                }

                override fun onProfileLoadFailed(message: String) {
                    Log.d(TAG, "Failed to process the profile")
                }
            })
    }

    companion object {
        const val TAG = "MainService"

        const val DISMISS_NOTIFICATION_ACTION = "DISMISS_NOTIFICATION_ACTION"
    }
}