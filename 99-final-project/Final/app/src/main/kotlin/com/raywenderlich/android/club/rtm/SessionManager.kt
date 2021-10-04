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

package com.raywenderlich.android.club.rtm

import android.content.Context
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.raywenderlich.android.agora.rtm.*
import com.raywenderlich.android.agora.rtm.ConnectionState.Disconnected
import com.raywenderlich.android.club.BuildConfig
import com.raywenderlich.android.club.models.*
import com.raywenderlich.android.club.models.rtm.Sendable
import com.raywenderlich.android.club.models.rtm.createSendableMessage
import io.agora.rtm.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BODY
import retrofit2.Retrofit
import retrofit2.create
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Management object for the user's session, connected to the Agora.io RTM SDK.
 */
class SessionManager(
    context: Context,
    appId: String = BuildConfig.AGORA_APP_ID,
    baseServerUrl: String = BuildConfig.SERVER_BASE_URL,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        // The name of the default channel that every signed-in user is thrown into at first.
        // Through exchanged messages in this channel, the available open rooms are broadcast
        // to all users. When any user creates or closes their own room, this information
        // is spread to all other users waiting in the lobby channel.
        private const val LOBBY_CHANNEL_ID = "_lobby"
    }

    private val eventListener = object : RtmClientListener {
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            // Broadcast the new value via a Flow
            _connectionStateEvents.value = ConnectionState.fromCode(state)
        }

        override fun onMessageReceived(message: RtmMessage, peerId: String) {
            // Unpack the body first and determine what to do with the content based on its kind
            val body = Json.decodeFromString<Sendable>(message.text)
            when (body.kind) {
                Sendable.Kind.RoomOpened -> {
                    // Append the new room to the list of open rooms
                    val room = body.decodeBody<Room>()
                    _openRoomEvents.update { rooms -> rooms + room }
                }
                Sendable.Kind.RoomClosed -> {
                    // Remove the new room from the list of open rooms
                    val room = body.decodeBody<Room>()
                    _openRoomEvents.update { rooms -> rooms - room }
                }
                Sendable.Kind.RoomList -> {
                    // Replace the list of rooms with the received data
                    val data = body.decodeBody<RoomList>()
                    _openRoomEvents.update { data.rooms.toSet() }
                }
            }
        }

        override fun onImageMessageReceivedFromPeer(message: RtmImageMessage, peerId: String) {
        }

        override fun onFileMessageReceivedFromPeer(message: RtmFileMessage, peerId: String) {
        }

        override fun onMediaUploadingProgress(
            progress: RtmMediaOperationProgress,
            requestId: Long
        ) {
        }

        override fun onMediaDownloadingProgress(
            progress: RtmMediaOperationProgress,
            requestId: Long
        ) {
        }

        override fun onTokenExpired() {
            println("onTokenExpired()")
        }

        override fun onPeersOnlineStatusChanged(peersStatus: MutableMap<String, Int>) {
            println("onPeersOnlineStatusChanged($peersStatus)")
        }
    }

    // Access to Agora.io RTM system
    private val rtmClient =
        RtmClient.createInstance(context, appId, eventListener)

    // Access to app server
    @OptIn(ExperimentalSerializationApi::class)
    private val serverApi = Retrofit.Builder()
        .baseUrl(baseServerUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor { Log.i("OkHttp", it) }.setLevel(BODY))
                .build()
        )
        .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create<ServerApi>()

    /* Public */

    private val _connectionStateEvents = MutableStateFlow(Disconnected)
    private val _openRoomEvents = MutableStateFlow(emptySet<Room>())

    /**
     * A Flow of events representing the connection status of the user to the Agora system.
     */
    val connectionStateEvents: Flow<ConnectionState> = _connectionStateEvents

    /**
     * A Flow of events representing the current list of open rooms that a user can join.
     */
    val openRoomEvents: Flow<List<Room>> = _openRoomEvents.map { it.distinct() }

    /**
     * Log into the system with the provided [userName].
     * This method returns a [Session] object with which the caller can interact with
     * the system at a later time.
     */
    suspend fun login(userName: String): LoginSession = withContext(dispatcher) {
        // The app's login flow consists of a few distinct steps:
        // - Obtain a token from our own backend server
        // - Log into the Agora RTM system with this token
        // - Join the common 'lobby' channel of the RTM system
        val user = User(
            id = UserId(Random.nextInt(0, Int.MAX_VALUE)),
            name = userName
        )
        val tokenResponse = serverApi.createRtmToken(user.name)

        rtmClient.awaitLogin(tokenResponse.token.value, user.name)

        val lobbyChannel = rtmClient.joinChannel(
            channelId = LOBBY_CHANNEL_ID,
            onMessage = { message, member ->
                println("onMessageReceived in lobby from $member: $message")
            },
            onMemberEvent = { member, joined ->
                if (joined) {
                    // Provide any new member of the lobby with the list of open rooms
                    val rooms = _openRoomEvents.value.toList()
                    if (rooms.isNotEmpty()) {
                        val message = rtmClient.createSendableMessage(
                            kind = Sendable.Kind.RoomList,
                            bodyText = Json.encodeToString(RoomList(rooms))
                        )

                        rtmClient.sendMessageToPeer(
                            member.userId,
                            message,
                            SendMessageOptions(),
                            null
                        )
                    }
                }
            })

        DefaultLoginSession(user, lobbyChannel)
    }

    /* Private */

    private suspend fun notifyChannelMembers(
        channel: RtmChannel,
        kind: Sendable.Kind,
        body: String
    ) = withContext(dispatcher) {
        // Create a generic message to send over,
        // validating that the type is matching the requested [kind]
        val message = rtmClient.createSendableMessage(kind, body)
        rtmClient.awaitSendMessageToChannelMembers(channel, message)
    }

    /* Inner classes */

    private inner class DefaultLoginSession(
        private val user: User,
        private val lobbyChannel: RtmChannel
    ) : LoginSession {
        // Gatekeeper to access some of the session's functionality,
        // ensuring that it is still alive. After calling close(),
        // no more calls can happen on this session and an error should be thrown
        private val loginActive = AtomicBoolean(true)

        /**
         * Close the login session. After this call,
         * no future calls will be acknowledged on it anymore.
         * This also goes for any rooms created via [createRoom].
         */
        override suspend fun close() = withContext(dispatcher) {
            if (loginActive.getAndSet(false)) {
                lobbyChannel.awaitLeave()
                lobbyChannel.release()
                rtmClient.awaitLogout()
            }
        }

        /**
         * Create a new room for broadcasting. This will also immediately join that room as the broadcaster.
         * This method returns a [RoomSession] object with which the caller can interact with
         * the room at a later time.
         */
        override suspend fun createRoom(): RoomSession = withContext(dispatcher) {
            // Create a new channel with a random ID
            val channelId = UUID.randomUUID().toString()
            val channel = rtmClient.joinChannel(
                channelId = channelId,
                onMessage = { message, member ->
                    println("onMessageReceived in room '$channelId' from ${member.userId}: ${message.text}")
                },
                onMemberEvent = { member, joined ->

                })
            val room = Room(hostId = user.id, roomId = RoomId(channelId))

            // Obtain auth token to broadcast to the new room
            val tokenResponse = serverApi.createRtcToken(user.id, room.roomId, isCreator = true)

            // Broadcast the availability of the new room to everybody in the lobby
            notifyChannelMembers(
                channel = lobbyChannel,
                kind = Sendable.Kind.RoomOpened,
                body = Json.encodeToString(room)
            )

            // Keep track of the new room from the perspective of the creator, too
            _openRoomEvents.update { rooms -> rooms + room }

            DefaultRoomSession(user, room, tokenResponse.token, isCreator = true, channel)
        }

        override suspend fun joinRoom(room: Room): RoomSession = withContext(dispatcher) {
            val channel = rtmClient.joinChannel(
                channelId = room.roomId.value,
                onMessage = { message, member ->
                    println("onMessageReceived in room '${room.roomId}' from ${member.userId}: ${message.text}")
                },
                onMemberEvent = { member, joined ->

                })

            // Obtain auth token to broadcast to that room
            val tokenResponse = serverApi.createRtcToken(user.id, room.roomId, isCreator = false)
            DefaultRoomSession(user, room, tokenResponse.token, isCreator = false, channel)
        }

        private inner class DefaultRoomSession(
            override val user: User,
            override val room: Room,
            override val token: Token,
            override val isCreator: Boolean,
            private val roomChannel: RtmChannel
        ) : RoomSession {
            // Gatekeeper to access some of the session's functionality,
            // ensuring that it is still alive. After calling close(),
            // no more calls can happen on this session and an error should be thrown
            private val roomActive = AtomicBoolean(true)

            override suspend fun close() = withContext(dispatcher) {
                if (loginActive.get() && roomActive.getAndSet(false)) {
                    // Broadcast the closure of the room to everybody in the lobby
                    notifyChannelMembers(
                        channel = lobbyChannel,
                        kind = Sendable.Kind.RoomClosed,
                        body = Json.encodeToString(room)
                    )

                    // Lose track of this room from the perspective of the creator, too
                    _openRoomEvents.update { rooms -> rooms - room }

                    roomChannel.awaitLeave()
                    roomChannel.release()
                }
            }
        }
    }
}
