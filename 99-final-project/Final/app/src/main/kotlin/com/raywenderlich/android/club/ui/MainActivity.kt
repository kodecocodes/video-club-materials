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

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.raywenderlich.android.agora.rtm.ConnectionState
import com.raywenderlich.android.club.R
import com.raywenderlich.android.club.models.Room
import com.raywenderlich.android.club.controllers.AudioService
import com.raywenderlich.android.club.models.MemberInfo
import com.raywenderlich.android.club.utils.RequestPermission
import com.raywenderlich.android.club.utils.mainViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val requestPermission = RequestPermission(this)
    private val viewModel by lazy { mainViewModel(this) }

    // UI
    private val inputUserName by lazy { findViewById<EditText>(R.id.input_user_name) }
    private val buttonLogin by lazy { findViewById<Button>(R.id.button_login) }
    private val buttonLogout by lazy { findViewById<Button>(R.id.button_logout) }
    private val buttonCreateRoom by lazy { findViewById<Button>(R.id.button_create_room) }
    private val buttonLeaveRoom by lazy { findViewById<Button>(R.id.button_leave_room) }
    private val buttonRaiseHand by lazy { findViewById<Button>(R.id.button_raise_hand) }

    private val rvRooms by lazy { findViewById<RecyclerView>(R.id.list_rooms) }
    private val rvRoomsAdapter = RoomListAdapter()

    private val rvUsers by lazy { findViewById<RecyclerView>(R.id.list_room_users) }
    private val rvUsersAdapter = UserListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hide splash screen
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // Assign random user name
        inputUserName.setText((0..4).map {
            Random.nextInt('a'.code, 'z'.code).toChar()
        }.joinToString(separator = ""))

        // Setup list adapters
        rvRooms.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        rvRooms.adapter = rvRoomsAdapter
        rvUsers.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rvUsers.adapter = rvUsersAdapter

        // Setup listeners
        buttonLogin.setOnClickListener { doLogin() }
        buttonLogout.setOnClickListener { doLogout() }
        buttonCreateRoom.setOnClickListener { createRoom() }
        buttonLeaveRoom.setOnClickListener { closeRoom() }
        buttonRaiseHand.setOnClickListener { toggleHandRaised() }

        // Subscribe to UI state
        viewModel.state
            .onEach { handleState(it) }
            .launchIn(lifecycleScope)

        // React to item clicks in the different RecyclerViews
        rvRoomsAdapter.itemClickEvents
            .onEach { joinRoom(it) }
            .launchIn(lifecycleScope)

        rvUsersAdapter.itemClickEvents
            .onEach { toggleUserRole(it) }
            .launchIn(lifecycleScope)
    }

    private fun handleState(state: MainViewModel.State) {
        // Enable/disable the login buttons depending on the connection to the service
        buttonLogin.isEnabled = state.connectionState == ConnectionState.Disconnected
        buttonLogout.isEnabled = state.connectionState == ConnectionState.Connected

        // Enable/disable the room buttons
        val inRoom = state.connectedRoomInfo != null
        buttonCreateRoom.isEnabled = !inRoom
        buttonLeaveRoom.isEnabled = inRoom
        buttonRaiseHand.isEnabled = inRoom

        // Update list of rooms and disable the list while connected to a room
        rvRooms.isEnabled = !inRoom
        rvRoomsAdapter.submitList(state.openRooms)

        // Keep audio service up-to-date with all pieces of info
        if (state.connectedRoomInfo != null) {
            rvUsersAdapter.submitList(state.connectedRoomMembers)
            AudioService.start(this, state.connectedRoomInfo)
        } else {
            rvUsersAdapter.submitList(emptyList())
            AudioService.stop(this)
        }
    }

    /* Listeners */

    private fun doLogin() {
        val userName = inputUserName.text.toString()
        if (userName.isEmpty()) {
            Toast.makeText(this@MainActivity, "User name required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.login(userName)
    }

    private fun doLogout() {
        viewModel.logout()
    }

    private fun createRoom() {
        ensureAudioPermission {
            viewModel.createRoom()
        }
    }

    private fun joinRoom(room: Room) {
        ensureAudioPermission {
            viewModel.joinRoom(room)
        }
    }

    private fun closeRoom() {
        viewModel.leaveRoom()
    }

    private fun toggleHandRaised() {
        viewModel.toggleHandRaised()
    }

    private fun toggleUserRole(member: MemberInfo) {
        viewModel.toggleRole(member)
    }

    private fun ensureAudioPermission(block: suspend () -> Unit) {
        lifecycleScope.launch {
            val hasPermission = requestPermission(Manifest.permission.RECORD_AUDIO)
            if (hasPermission) {
                block()
            }
        }
    }
}
