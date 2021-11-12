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

import android.content.Context
import com.raywenderlich.android.agora.rtm.*
import com.raywenderlich.android.club.BuildConfig
import com.raywenderlich.android.club.api.ServerApi
import com.raywenderlich.android.club.controllers.util.ClientListenerImpl
import com.raywenderlich.android.club.controllers.util.RoomChannelListener
import com.raywenderlich.android.club.controllers.util.asAttributeList
import com.raywenderlich.android.club.controllers.util.asMemberInfo
import com.raywenderlich.android.club.models.*
import io.agora.rtm.RtmChannel
import io.agora.rtm.RtmChannelMember
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmMessage
import io.agora.rtm.RtmStatusCode.ConnectionState.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.random.Random

/**
 * Access to the Agora SDKs in a structured manner, aggregating all interactions with
 * the system into a small set of observable flows. Internally, this class keeps track
 * of the user's sign-in status and observes some properties of the available and connected rooms.
 */
class SessionManager(
    context: Context,
    appId: String,
    private val serverApi: ServerApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private companion object {
        // The name of the default channel that every signed-in user is thrown into at first.
        // Through exchanged messages in this channel, the available open rooms are broadcast
        // to all users. When any user creates or closes their own room, this information
        // is spread to all other users waiting in the lobby channel.
        private const val LOBBY_CHANNEL_ID = "_lobby"
    }

    /**
     * Data holder for an RTM channel and the corresponding
     * authentication token for its audio stream
     */
    private data class RoomChannelConnection(
        val room: Room,
        val channel: RtmChannel,
        val myInfo: MemberInfo,
        val token: Token,
        val listener: RoomChannelListener
    )

    /* Private logic */

    private val _connectionStateEvents = MutableStateFlow(CONNECTION_STATE_DISCONNECTED)
    private val _openRoomEvents = MutableStateFlow(emptySet<Room>())
    private val _connectedRoomEvents = MutableStateFlow<RoomSession?>(null)

    // Access to Agora RTM resources
    private val client by lazy {
        RtmClient.createInstance(
            context,
            appId,
            ClientListenerImpl(
                coroutineScope = coroutineScope,
                onConnectionState = { _connectionStateEvents.value = it },
                onMessage = ::handleClientMessage
            )
        )
    }
    private val coroutineScope = CoroutineScope(dispatcher)

    // Currently logged-in user; set to non-null after login
    private val currentUserEvents = MutableStateFlow<User?>(null)
    private val currentUser get() = currentUserEvents.value

    // Reference to lobby channel; set to non-null after login
    private var lobbyChannel: RtmChannel? = null

    // Reference to current room; set to non-null after creating/joining one
    private var roomConnection: RoomChannelConnection? = null
        set(value) {
            field = value

            // Send connection info to subscribers of the flow
            if (value == null) {
                _connectedRoomEvents.tryEmit(null)
            } else {
                _connectedRoomEvents.tryEmit(
                    RoomSession(
                        info = RoomInfo(
                            roomId = RoomId(value.channel.id),
                            token = value.token,
                            userId = requireNotNull(currentUser?.id),
                            isBroadcaster = value.myInfo.role != MemberRole.Audience
                        ),
                        memberEvents = value.listener.membersFlow
                    )
                )
            }
        }

    /* Public API */

    /**
     * A Flow of events representing the connection status of the user to the Agora system.
     */
    val connectionStateEvents: Flow<LoginState> =
        combine(_connectionStateEvents, currentUserEvents) { rtmState, user ->
            // Convert the Agora session status & knowledge about the logged-in user
            // into a dedicated state object for consumers
            when (rtmState) {
                CONNECTION_STATE_DISCONNECTED -> LoginState.Disconnected
                CONNECTION_STATE_RECONNECTING -> LoginState.Reconnecting
                CONNECTION_STATE_ABORTED -> LoginState.Aborted
                CONNECTION_STATE_CONNECTING -> LoginState.Connecting
                CONNECTION_STATE_CONNECTED -> user?.let { LoginState.Connected(it) }
                else -> null
            }
        }.filterNotNull()

    /**
     * A Flow of events representing the current list of open rooms that a user can join.
     */
    val openRoomEvents: Flow<List<Room>> = _openRoomEvents.map { it.distinct() }

    /**
     * A Flow of events representing the room that the user is currently connected to.
     * This can emit the same room multiple times in a row (e.g. when the user's role
     * in the channel is being updated or their token is renewed). When this emits null,
     * the user decided to leave a room, or the broadcast was finished.
     */
    val connectedRoomEvents: Flow<RoomSession?> = _connectedRoomEvents

    /**
     * Log into the system with the provided [userName]. This will allow
     */
    suspend fun login(userName: String) {
        withContext(dispatcher) {
            // Log out any current session
            logout()

            // Allocate a new user ID and perform the login on the server side
            val userId = UserId(Random.nextInt(0, Int.MAX_VALUE))
            val token = serverApi.createRtmToken(userName)

            // Sign into Agora RTM system and hold onto the credentials
            client.awaitLogin(token.value, userName)
            currentUserEvents.value = User(userId, userName)

            // Finally, log into the common "lobby" channel,
            // which will be used to communicate open rooms to all logged-in users
            lobbyChannel = client.awaitJoinChannel(LOBBY_CHANNEL_ID, lobbyChannelListener)
        }
    }

    /**
     *
     */
    suspend fun logout() {
        if (currentUser == null) return

        withContext(dispatcher) {
            // If active, leave the active room
            leaveRoom()

            // Leave the lobby
            lobbyChannel?.let { lobby ->
                lobby.awaitLeave()
                lobby.release()
                _openRoomEvents.value = emptySet()
                lobbyChannel = null
            }

            // Sign out of the system completely
            client.awaitLogout()
            currentUserEvents.value = null
        }
    }

    /**
     *
     */
    suspend fun createRoom(roomName: String) {
        val currentUser = currentUser ?: return

        // Create a new channel with a random ID and then join it
        val roomId = UUID.randomUUID().toString()
        val room = Room(
            host = currentUser,
            coHosts = emptyList(),
            name = roomName,
            roomId = RoomId(roomId),
            memberCount = 1,
        )
        joinRoom(room)
    }

    /**
     *
     */
    suspend fun joinRoom(room: Room) {
        val currentUser = currentUser ?: return

        withContext(dispatcher) {
            // Leave any currently active room
            leaveRoom()

            // Create and join the messaging channel for the room
            val isBroadcaster = currentUser.id == room.host.id
            val channelListener = RoomChannelListener(client, coroutineScope)
            val channel = client.awaitJoinChannel(room.roomId.value, channelListener)

            val role = if (isBroadcaster) MemberRole.Host else MemberRole.Audience
            val memberInfo = MemberInfo(
                agoraId = currentUser.name,
                userName = currentUser.name,
                role = role,
                raisedHand = false
            )

            // Initialize local listener's understanding of the channel's members
            // (for new rooms: just the host for now, otherwise all current members)
            channelListener.membersFlow.value = if (isBroadcaster) {
                listOf(memberInfo)
            } else {
                channel.awaitGetMembers().map { it.asMemberInfo(client) }
            }

            // Obtain authentication token to the audio streams of the room
            val token = serverApi.createRtcToken(currentUser.id, room.roomId, isBroadcaster)

            roomConnection = RoomChannelConnection(
                room = room,
                channel = channel,
                myInfo = memberInfo,
                token = token,
                listener = channelListener
            )

            // If the room was just created by the current user,
            // broadcast its availability to users in the lobby
            if (isBroadcaster) {
                _openRoomEvents.update { it + room }
                sendUpdatedRoomListToLobbyUsers()
            }
        }
    }

    /**
     *
     */
    suspend fun leaveRoom() {
        if (currentUser == null) return

        withContext(dispatcher) {
            roomConnection?.let { connection ->
                // If this user was the host of the room,
                // notify its disappearance to users in the lobby
                // and throw everybody out
                if (connection.myInfo.role == MemberRole.Host) {
                    _openRoomEvents.update { it - connection.room }
                    sendUpdatedRoomListToLobbyUsers(wait = true)

                    client.awaitSendMessageToChannelMembers(
                        channel = connection.channel,
                        message = client.createRoomClosedMessage(connection.room)
                    )
                }

                client.awaitClearLocalUserAttributes()

                connection.channel.awaitLeave()
                connection.channel.release()
                roomConnection = null
            }
        }
    }

    suspend fun toggleHandRaised() {
        val currentUser = currentUser ?: return
        val connection = roomConnection ?: return

        val newInfo = connection.myInfo.copy(raisedHand = !connection.myInfo.raisedHand)

        withContext(dispatcher) {
            // Update a local user attribute to reflect the updated "hand raised" state
            val attributes = newInfo.asAttributeList()
            client.awaitAddOrUpdateLocalUserAttributes(attributes)

            // Local: Update knowledge of the connection to reflect the requested state
            val updatedConnection = connection.copy(myInfo = newInfo)
            updatedConnection.listener.updateMember(newInfo)
            roomConnection = updatedConnection

            // Remote: Broadcast to the room that our attributes have changed
            client.awaitSendMessageToChannelMembers(
                channel = connection.channel,
                client.createUserUpdatedMessage(currentUser)
            )
        }
    }

    suspend fun toggleRole(member: MemberInfo) {
        // Roles can only be assigned by the original host of the room,
        // and only for members who have raised their hand
        val currentUser = currentUser ?: return
        val connection = roomConnection ?: return

        if (connection.room.host.id != currentUser.id) return
        if (!member.raisedHand) return

        withContext(dispatcher) {
            // Flip between audience and co-host
            val newState = if (member.role == MemberRole.Audience) {
                MemberRole.CoHost
            } else {
                MemberRole.Audience
            }

            // Send a message to this specific member and let them know about their new role
            client.awaitSendMessageToPeer(
                userId = member.agoraId,
                message = client.createRoleChangedMessage(connection.room.roomId, newState)
            )
        }
    }

    /* Private */

    // Receiver of remote messages from other users
    private suspend fun handleClientMessage(message: Sendable, peerId: String) {
        when (message.kind) {
            // List of available rooms has been updated
            Sendable.Kind.RoomList -> {
                val rooms = message.decodeBody<RoomList>()
                _openRoomEvents.update { rooms.rooms.toSet() }
            }

            // The current room was closed by the host; stop listening to it
            Sendable.Kind.RoomClosed -> {
                val connection = roomConnection ?: return
                val room = message.decodeBody<Room>()

                if (connection.room.roomId == room.roomId) {
                    leaveRoom()
                }
            }

            Sendable.Kind.RoleChanged -> {
                val user = currentUser ?: return
                val connection = roomConnection ?: return

                // First verify that the permission is for the correct room
                val body = message.decodeBody<UserRoleChanged>()
                if (body.roomId != connection.room.roomId) return

                // Now get a new token and store the knowledge in the local roomConnection
                // to update the audio service and engage with the updated permission
                val newToken = serverApi.createRtcToken(user.id, body.roomId, body.isBroadcaster)
                val newInfo = connection.myInfo.copy(
                    role = if (body.isBroadcaster) MemberRole.CoHost else MemberRole.Audience
                )

                roomConnection = connection.copy(
                    myInfo = newInfo,
                    token = newToken
                )

                // Announce the update to our role via the user attributes &
                // send a message to other users in the room
                client.awaitAddOrUpdateLocalUserAttributes(newInfo.asAttributeList())
                client.awaitSendMessageToChannelMembers(
                    channel = connection.channel,
                    client.createUserUpdatedMessage(user)
                )
            }

            // User has updated their public information
            Sendable.Kind.UserUpdated -> {
                val connection = roomConnection ?: return

                // Fetch attributes for the updated user
                val user = message.decodeBody<UserUpdated>()
                val attributes = client.awaitGetUserAttributes(user.name)

                val updatedMember = attributes.asMemberInfo(peerId)
                connection.listener.updateMember(updatedMember)
            }
        }
    }

    private suspend fun sendUpdatedRoomListToLobbyUsers(wait: Boolean = false) {
        val lobbyChannel = lobbyChannel ?: return
        val message = client.createRoomListMessage()

        if (wait) {
            client.awaitSendMessageToChannelMembers(lobbyChannel, message, NO_OPTIONS)
        } else {
            client.sendMessageToChannelMembers(lobbyChannel, message, NO_OPTIONS)
        }
    }

    /* Agora Listeners */

    /**
     * Listener for the "lobby" channel, handling the synchronization of open rooms
     */
    private val lobbyChannelListener = object : DefaultRtmChannelListener() {
        override fun onMessageReceived(message: RtmMessage, member: RtmChannelMember) {
            println("onMessageReceived in lobby from ${member.userId}: ${message.text}")
        }

        override fun onMemberJoined(member: RtmChannelMember) {
            // Provide any member of the lobby with the current list of open rooms
            val message = client.createRoomListMessage()
            client.sendMessageToPeer(member.userId, message, NO_OPTIONS, null)
        }
    }

    /* Serialized messages between clients */

    private fun RtmClient.createRoomListMessage(): RtmMessage {
        val rooms = _openRoomEvents.value.toList()
        return createSendableMessage(
            kind = Sendable.Kind.RoomList,
            bodyText = Json.encodeToString(RoomList(rooms))
        )
    }

    private fun RtmClient.createRoomClosedMessage(room: Room): RtmMessage {
        return createSendableMessage(
            kind = Sendable.Kind.RoomClosed,
            bodyText = Json.encodeToString(room)
        )
    }

    private fun RtmClient.createRoleChangedMessage(roomId: RoomId, role: MemberRole): RtmMessage {
        val isBroadcaster = role != MemberRole.Audience
        return createSendableMessage(
            kind = Sendable.Kind.RoleChanged,
            bodyText = Json.encodeToString(UserRoleChanged(roomId, isBroadcaster))
        )
    }

    private fun RtmClient.createUserUpdatedMessage(user: User): RtmMessage {
        return createSendableMessage(
            kind = Sendable.Kind.UserUpdated,
            bodyText = Json.encodeToString(UserUpdated(user.name))
        )
    }
}
