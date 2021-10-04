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

package com.raywenderlich.android.club

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
import com.raywenderlich.android.club.models.Room
import com.raywenderlich.android.club.rtc.AudioService
import com.raywenderlich.android.club.rtm.LoginSession
import com.raywenderlich.android.club.rtm.RoomSession
import com.raywenderlich.android.club.utils.RequestPermission
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val requestPermission = RequestPermission(this)
    private val sessionManager by lazy { app.sessionManager }

    // UI
    private val inputUserName by lazy { findViewById<EditText>(R.id.input_user_name) }
    private val buttonJoin by lazy { findViewById<Button>(R.id.button_join) }
    private val buttonLeave by lazy { findViewById<Button>(R.id.button_leave) }
    private val buttonCreateRoom by lazy { findViewById<Button>(R.id.button_create_room) }
    private val buttonCloseRoom by lazy { findViewById<Button>(R.id.button_close_room) }

    private val listRooms by lazy { findViewById<RecyclerView>(R.id.list_rooms) }
    private val listAdapter = RoomListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hide splash screen
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // Assign random user name
        inputUserName.setText((0..4).map {
            Random.nextInt('a'.code, 'z'.code).toChar()
        }.joinToString(separator = ""))

        // Setup list adapter
        listRooms.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        listRooms.adapter = listAdapter

        // Setup listeners
        buttonJoin.setOnClickListener { joinChannel() }
        buttonLeave.setOnClickListener { leaveChannel() }
        buttonCreateRoom.setOnClickListener { createRoom() }
        buttonCloseRoom.setOnClickListener { closeRoom() }

        // Subscribe to connection states and update UI accordingly
        sessionManager.connectionStateEvents
            .onEach { updateUI(it) }
            .launchIn(lifecycleScope)

        // Keep list of open rooms up-to-date at all times
        sessionManager.openRoomEvents
            .onEach { println("Open Rooms: $it") }
            .onEach { listAdapter.submitList(it) }
            .launchIn(lifecycleScope)

        // React to item clicks in the RecyclerView
        listAdapter.itemClickEvents
            .onEach { joinRoom(it) }
            .launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        super.onDestroy()
        leaveChannel()
    }

    /* Listeners */

    private var loginSession: LoginSession? = null
    private var roomSession: RoomSession? = null

    private fun joinChannel() =
        lifecycleScope.launch {
            val userName = inputUserName.text.toString()
            if (userName.isEmpty()) {
                Toast.makeText(this@MainActivity, "User name required", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // TODO MVVM
            loginSession = app.sessionManager.login(userName)
        }

    private fun leaveChannel() =
        lifecycleScope.launch {
            loginSession?.close()
        }

    private fun createRoom() =
        lifecycleScope.launch {
            val loginSession = loginSession ?: return@launch

            val hasPermission = requestPermission(Manifest.permission.RECORD_AUDIO)
            if (hasPermission) {
                val roomSession = loginSession.createRoom()
                AudioService.start(this@MainActivity, roomSession)

                // TODO Toggle these automatically through a flow exposed by the service
                buttonCreateRoom.isEnabled = false
                buttonCloseRoom.isEnabled = true
                this@MainActivity.roomSession = roomSession
            }
        }

    private suspend fun joinRoom(room: Room) {
        val loginSession = loginSession ?: return

        val hasPermission = requestPermission(Manifest.permission.RECORD_AUDIO)
        if (hasPermission) {
            val roomSession = loginSession.joinRoom(room)
            AudioService.start(this@MainActivity, roomSession)

            // TODO Toggle these automatically through a flow exposed by the service
            buttonCreateRoom.isEnabled = false
            buttonCloseRoom.isEnabled = true
            this.roomSession = roomSession
        }
    }

    private fun closeRoom() =
        lifecycleScope.launch {
            roomSession?.close()
            AudioService.stop(this@MainActivity)

            // TODO Toggle these automatically through a flow exposed by the service
            buttonCreateRoom.isEnabled = true
            buttonCloseRoom.isEnabled = false
        }

    /* Private */

    private fun updateUI(connectionState: ConnectionState) {
        buttonJoin.isEnabled = connectionState == ConnectionState.Disconnected
        buttonLeave.isEnabled = connectionState == ConnectionState.Connected
    }
}
