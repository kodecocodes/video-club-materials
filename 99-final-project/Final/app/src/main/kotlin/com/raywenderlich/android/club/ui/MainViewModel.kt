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

package com.raywenderlich.android.club.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raywenderlich.android.agora.rtm.ConnectionState
import com.raywenderlich.android.club.controllers.SessionManager
import com.raywenderlich.android.club.models.MemberInfo
import com.raywenderlich.android.club.models.Room
import com.raywenderlich.android.club.models.RoomInfo
import com.raywenderlich.android.club.models.RoomSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * The MainViewModel is connected to the MainActivity
 * and holds a life-long reference to the app's sessions.
 */
class MainViewModel(private val sessionManager: SessionManager) : ViewModel() {

    data class State(
        val connectionState: ConnectionState = ConnectionState.Disconnected,
        val openRooms: List<Room> = emptyList(),
        val connectedRoomInfo: RoomInfo? = null,
        val connectedRoomMembers: List<MemberInfo> = emptyList()
    )

    private val _state = MutableStateFlow(State())
    val state: Flow<State> = _state

    private var currentMembersJob: Job? = null

    init {
        // Forward connection state events and open rooms from the SessionManager
        sessionManager.connectionStateEvents
            .onEach { newState ->
                _state.update {
                    it.copy(connectionState = newState)
                }
            }
            .launchIn(viewModelScope)

        sessionManager.openRoomEvents
            .onEach { rooms ->
                _state.update {
                    it.copy(openRooms = rooms)
                }
            }
            .launchIn(viewModelScope)

        sessionManager.connectedRoomEvents
            .onEach { session ->
                updateSessionState(session)
            }
            .launchIn(viewModelScope)
    }

    private fun updateSessionState(session: RoomSession?) {
        if (session == null) {
            // Session was closed; remove subscription to any previous room
            currentMembersJob?.cancel()
            _state.update {
                it.copy(
                    connectedRoomInfo = null,
                    connectedRoomMembers = emptyList()
                )
            }

        } else {
            val currentRoomInfo = _state.value.connectedRoomInfo
            if (session.info.roomId != currentRoomInfo?.roomId) {
                // First room joined or switched to a new room;
                // remove previous subscription to members of the room
                // and create a fresh one to forward member events to the UI
                currentMembersJob?.cancel()
                currentMembersJob = session.memberEvents
                    .onEach { members ->
                        _state.update { it.copy(connectedRoomMembers = members) }
                    }
                    .launchIn(viewModelScope)
            }

            // Always update the room info object
            _state.update { it.copy(connectedRoomInfo = session.info) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        logout()
    }

    fun login(userName: String) {
        viewModelScope.launch {
            sessionManager.login(userName)
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.logout()
        }
    }

    fun createRoom() {
        viewModelScope.launch {
            sessionManager.createRoom()
        }
    }

    fun joinRoom(room: Room) {
        viewModelScope.launch {
            sessionManager.joinRoom(room)
        }
    }

    fun leaveRoom() {
        viewModelScope.launch {
            sessionManager.leaveRoom()
        }
    }

    fun toggleHandRaised() {
        viewModelScope.launch {
            sessionManager.toggleHandRaised()
        }
    }

    fun toggleRole(member: MemberInfo) {
        viewModelScope.launch {
            sessionManager.toggleRole(member)
        }
    }
}
