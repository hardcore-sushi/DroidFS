package sushi.hardcore.droidfs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat

class KeepAliveService: Service() {
    internal class NotificationDetails(
        val channel: String,
        val title: String,
        val text: String,
        val action: NotificationAction,
    ) : Parcelable {
        internal class NotificationAction(
            val icon: Int,
            val title: String,
            val action: String,
        )

        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            NotificationAction(
                parcel.readInt(),
                parcel.readString()!!,
                parcel.readString()!!,
            )
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            with (parcel) {
                writeString(channel)
                writeString(title)
                writeString(text)
                writeInt(action.icon)
                writeString(action.title)
                writeString(action.action)
            }
        }

        override fun describeContents() = 0

        companion object CREATOR : Parcelable.Creator<NotificationDetails> {
            override fun createFromParcel(parcel: Parcel) = NotificationDetails(parcel)
            override fun newArray(size: Int) = arrayOfNulls<NotificationDetails>(size)
        }

    }

    companion object {
        const val ACTION_START = "start"

        /**
         * If [startForeground] is called before notification permission is granted,
         * the notification won't appear.
         *
         * This action can be used once the permission is granted, to make the service
         * call [startForeground] again in order to properly show the notification.
         */
        const val ACTION_FOREGROUND = "foreground"
        const val NOTIFICATION_CHANNEL_ID = "KeepAlive"
    }

    private val notificationManager by lazy {
        NotificationManagerCompat.from(this)
    }
    private var notification: Notification? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_START) {
            val notificationDetails = IntentCompat.getParcelableExtra(intent, "notification", NotificationDetails::class.java)!!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        notificationDetails.channel,
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notificationDetails.title)
                .setContentText(notificationDetails.text)
                .addAction(NotificationCompat.Action(
                    notificationDetails.action.icon,
                    notificationDetails.action.title,
                    PendingIntent.getBroadcast(
                        this,
                        0,
                        Intent(this, NotificationBroadcastReceiver::class.java).apply {
                            action = notificationDetails.action.action
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ))
                .build()
        }
        ServiceCompat.startForeground(this, startId, notification!!, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC // is there a better use case flag?
        } else {
            0
        })
        return START_NOT_STICKY
    }
}