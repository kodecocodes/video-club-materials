/*
 * Copyright (c) 2021 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * This project and source code may use libraries or frameworks that are
 * released under various Open-Source licenses. Use of those libraries and
 * frameworks are governed by their own individual licenses.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.club.controllers

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.raywenderlich.android.club.BuildConfig
import com.raywenderlich.android.club.ui.MainActivity
import com.raywenderlich.android.club.R
import com.raywenderlich.android.club.models.RoomInfo
import com.raywenderlich.android.club.utils.lazyWithReset
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.IRtcEngineEventHandler.ClientRole.CLIENT_ROLE_AUDIENCE
import io.agora.rtc.IRtcEngineEventHandler.ClientRole.CLIENT_ROLE_BROADCASTER
import io.agora.rtc.RtcEngine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AudioService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_COMMAND = "command"
        private const val EXTRA_ROOM_INFO = "room-info"

        private const val COMMAND_START = "start"
        private const val COMMAND_STOP = "stop"

        private fun intent(context: Context): Intent =
            Intent(context, AudioService::class.java)

        /**
         * Start the audio service or switch to the given room's [info].
         * This will start the service with a persistent notification in the foreground.
         * It won't do anything if this room is already being listened to. If other properties
         * inside the info object are different, the service will switch to acknowledge them.
         */
        fun start(context: Context, info: RoomInfo) {
            ContextCompat.startForegroundService(context, intent(context).apply {
                putExtra(EXTRA_COMMAND, COMMAND_START)
                putExtra(EXTRA_ROOM_INFO, Json.encodeToString(info))
            })
        }

        /**
         * Stop the current audio service session
         */
        fun stop(context: Context) {
            // Send a command to the service to make it stop itself
            context.startService(intent(context).apply {
                putExtra(EXTRA_COMMAND, COMMAND_STOP)
            })
        }
    }

    private val binder by lazy { RtcBinderImpl() }

    // Connection to Agora RTC SDK for audio communication.
    // This value is evaluated lazily and can be reset once the engine is destroyed
    private var rtcEngine = lazyWithReset {
        RtcEngine.create(
            this,
            BuildConfig.AGORA_APP_ID,
            object : IRtcEngineEventHandler() {

                override fun onError(err: Int) {
                    println("onError: $err ${RtcEngine.getErrorDescription(err)}()")
                }

                override fun onConnectionStateChanged(state: Int, reason: Int) {
                    println("onConnectionStateChanged($state, $reason)")
                }

                override fun onConnectionLost() {
                    println("onConnectionLost()")
                }

                override fun onLeaveChannel(stats: RtcStats?) {
                    println("onLeaveChannel()")
                }
            })
    }

    // Persistent notification
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val notificationChannelId by lazy { getString(R.string.notification_channel_audio) }

    override fun onBind(p0: Intent?): RtcBinder = binder

    override fun onCreate() {
        super.onCreate()

        // Set up a notification channel on Android 8 and above
        // (this is a persistent, one-time thing)
        if (Build.VERSION.SDK_INT >= 26) {
            if (notificationManager.getNotificationChannel(notificationChannelId) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        /* id = */ notificationChannelId,
                        /* name = */ getString(R.string.notification_channel_name),
                        /* importance = */ NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This is called whenever the service receives an intent.
        // Check if the 'stop' command is sent and handle it accordingly
        return when (intent?.getStringExtra(EXTRA_COMMAND)) {
            COMMAND_START -> {
                val infoJson = requireNotNull(intent.getStringExtra(EXTRA_ROOM_INFO))
                val info = Json.decodeFromString<RoomInfo>(infoJson)
                doStart(info)
                START_STICKY
            }
            COMMAND_STOP -> {
                doStop(startId)
                START_NOT_STICKY
            }
            else -> {
                println("Unknown command for AudioService: $intent")
                super.onStartCommand(intent, flags, startId)
            }
        }
    }

    /* Private */

    private var currentInfo: RoomInfo? = null

    private fun doStart(info: RoomInfo) {
        // Now start/update the service in foreground mode,
        // connecting it to a persistent notification
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Switch to a new room if its ID is now different
        // (i.e. "switch to a new channel")
        val rtcEngine = this.rtcEngine.value

        val currentInfo = this.currentInfo
        if (currentInfo != null) {
            when {
                // In case the given info is identical to the current one, do nothing
                currentInfo == info -> {
                    println("RTC service: Nothing to do, no change")
                    return
                }

                // When switching to a new channel
                currentInfo.roomId != info.roomId -> {
                    println("Switching RTC to a new room: ${info.roomId}")
                    rtcEngine.switchChannel(info.token.value, info.roomId.value)
                    rtcEngine.setIsBroadcaster(info.isBroadcaster)
                }

                // Otherwise, update the token for this room (it's possible that the role changed)
                else -> {
                    println("Updating RTC token for existing session to ${info.token} (broadcaster=${info.isBroadcaster})")
                    rtcEngine.renewToken(info.token.value)
                    rtcEngine.setIsBroadcaster(info.isBroadcaster)
                }
            }
        } else {
            // Joining the first channel
            println("Joining RTC room: ${info.roomId}")
            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            rtcEngine.setIsBroadcaster(info.isBroadcaster)
            rtcEngine.joinChannel(info.token.value, info.roomId.value, "", info.userId.value)
        }

        // Hold onto the new room info for later
        this.currentInfo = info
    }

    private fun doStop(id: Int) {
        // Stop foreground mode and the notification
        stopSelf(id)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)

        leaveCurrentRoom()
        RtcEngine.destroy()
        rtcEngine.reset()
    }

    private fun leaveCurrentRoom() {
        currentInfo?.let { info ->
            println("Leaving channel ${info.roomId}")
            rtcEngine.value.leaveChannel()
            currentInfo = null
        }
    }

    private fun createNotification(): Notification {
        // The intent to start when tapping on the notification
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, notificationChannelId)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun RtcEngine.setIsBroadcaster(enabled: Boolean) {
        this.setClientRole(
            if (enabled) {
                CLIENT_ROLE_BROADCASTER
            } else {
                CLIENT_ROLE_AUDIENCE
            }
        )
    }

    /* Binder */

    interface RtcBinder : IBinder {

    }

    private class RtcBinderImpl : Binder(), RtcBinder {

    }
}
