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

package com.raywenderlich.android.club.ui.main

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.CONSUMED
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.raywenderlich.android.club.R
import com.raywenderlich.android.club.controllers.AudioService
import com.raywenderlich.android.club.models.RoomInfo
import com.raywenderlich.android.club.utils.RequestPermission
import com.raywenderlich.android.club.utils.mainViewModel
import com.raywenderlich.android.club.utils.openAppSettings
import com.raywenderlich.android.club.utils.view
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG_BOTTOM_SHEET = "bottom-sheet"

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    /* UI */

    private val buttonStartRoom by view<Button>(R.id.button_start_room)
    private val bottomContainer by view<TextView>(R.id.bottom_container)

    /* Logic */

    private lateinit var viewModel: MainViewModel
    private val requestPermission = RequestPermission(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hide splash screen
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // Setup insets
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBarInsets.top, bottom = systemBarInsets.bottom)
            CONSUMED
        }

        // Setup UI
        buttonStartRoom.setOnClickListener {
            showStartRoomDialog()
        }

        // Initialize ViewModel and listen to whichever room the user is listening to
        viewModel = mainViewModel(this)
        viewModel.state
            .map { it.connectedRoomInfo }
            .distinctUntilChangedBy { it?.roomId }
            .onEach { handleCurrentRoom(it) }
            .launchIn(lifecycleScope)

        // Show initial screen
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.main_fragment_container, MainFragment())
            }
        }
    }

    /* Listeners */

    private fun showStartRoomDialog() {
        // Check if permission is granted first
        ensureAudioPermission {
            CreateRoomBottomSheetFragment.newInstance()
                .show(supportFragmentManager, TAG_BOTTOM_SHEET)
        }
    }

    /* Private */

    private fun handleCurrentRoom(info: RoomInfo?) {
        bottomContainer.text = info?.roomId?.toString() ?: ""
        bottomContainer.isVisible = info != null

        if (info != null) {
            AudioService.start(this, info)

            supportFragmentManager.commit {
                setCustomAnimations(
                    R.anim.slide_in_from_below, R.anim.slide_out_to_below,
                    R.anim.slide_in_from_below, R.anim.slide_out_to_below
                )
                addToBackStack(null)
                add(
                    R.id.room_fragment_container,
                    ActiveRoomBottomSheetFragment.newInstance(info),
                    TAG_BOTTOM_SHEET
                )
            }
        } else {
            AudioService.stop(this)

            with(supportFragmentManager) {
                findFragmentByTag(TAG_BOTTOM_SHEET)?.let { fragment ->
                    popBackStack()
                }
            }
        }
    }

    private fun ensureAudioPermission(block: suspend () -> Unit) {
        lifecycleScope.launch {
            val permission = Manifest.permission.RECORD_AUDIO
            val hasPermission = requestPermission(permission)

            if (hasPermission) {
                block()
            } else {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.dialog_permission_rationale_title)
                        .setMessage(R.string.dialog_permission_rationale_text)
                        .also { builder ->
                            // Insert a button to fix the issue, depending on whether or not
                            // the user has said "Never ask again" before
                            if (shouldShowRequestPermissionRationale(permission)) {
                                builder.setPositiveButton(R.string.button_ok) { d, _ ->
                                    d.dismiss()
                                }
                            } else {
                                builder.setPositiveButton(R.string.button_settings) { d, _ ->
                                    openAppSettings(this@MainActivity)
                                    d.dismiss()
                                }
                            }
                        }
                        .show()
                }
            }
        }
    }

    // UI
//    private val inputUserName by lazy { findViewById<EditText>(R.id.input_user_name) }
//    private val buttonLogin by lazy { findViewById<Button>(R.id.button_login) }
//    private val buttonLogout by lazy { findViewById<Button>(R.id.button_logout) }
//    private val buttonLeaveRoom by lazy { findViewById<Button>(R.id.button_leave_room) }
//    private val buttonRaiseHand by lazy { findViewById<Button>(R.id.button_raise_hand) }
//
//    private val rvRooms by lazy { findViewById<RecyclerView>(R.id.list_rooms) }
//    private val rvRoomsAdapter = RoomListAdapter()
//
//    private val rvUsers by lazy { findViewById<RecyclerView>(R.id.list_room_users) }
//    private val rvUsersAdapter = UserListAdapter()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        // Hide splash screen
//        setTheme(R.style.AppTheme)
//        super.onCreate(savedInstanceState)
//
//        // Assign random user name
//        inputUserName.setText((0..4).map {
//            Random.nextInt('a'.code, 'z'.code).toChar()
//        }.joinToString(separator = ""))
//
//        // Setup list adapters
//        rvRooms.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
//        rvRooms.adapter = rvRoomsAdapter
//        rvUsers.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
//        rvUsers.adapter = rvUsersAdapter
//
//        // Setup listeners
//        buttonLogin.setOnClickListener { doLogin() }
//        buttonLogout.setOnClickListener { doLogout() }
//        buttonCreateRoom.setOnClickListener { createRoom() }
//        buttonLeaveRoom.setOnClickListener { closeRoom() }
//        buttonRaiseHand.setOnClickListener { toggleHandRaised() }
//
//        // Subscribe to UI state
//        viewModel.state
//            .onEach { handleState(it) }
//            .launchIn(lifecycleScope)
//
//        // React to item clicks in the different RecyclerViews
//        rvRoomsAdapter.itemClickEvents
//            .onEach { joinRoom(it) }
//            .launchIn(lifecycleScope)
//
//        rvUsersAdapter.itemClickEvents
//            .onEach { toggleUserRole(it) }
//            .launchIn(lifecycleScope)
//    }
//
//    private fun handleState(state: MainViewModel.State) {
//        // Enable/disable the login buttons depending on the connection to the service
//        buttonLogin.isEnabled = state.connectionState == ConnectionState.Disconnected
//        buttonLogout.isEnabled = state.connectionState == ConnectionState.Connected
//
//        // Enable/disable the room buttons
//        val inRoom = state.connectedRoomInfo != null
//        buttonCreateRoom.isEnabled = !inRoom
//        buttonLeaveRoom.isEnabled = inRoom
//        buttonRaiseHand.isEnabled = inRoom
//
//        // Update list of rooms and disable the list while connected to a room
//        rvRooms.isEnabled = !inRoom
//        rvRoomsAdapter.submitList(state.openRooms)
//
//        // Keep audio service up-to-date with all pieces of info
//        if (state.connectedRoomInfo != null) {
//            rvUsersAdapter.submitList(state.connectedRoomMembers)
//            AudioService.start(this, state.connectedRoomInfo)
//        } else {
//            rvUsersAdapter.submitList(emptyList())
//            AudioService.stop(this)
//        }
//    }
//
//    /* Listeners */
//
//    private fun doLogin() {
//        val userName = inputUserName.text.toString()
//        if (userName.isEmpty()) {
//            Toast.makeText(this@MainActivity, "User name required", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        viewModel.login(userName)
//    }
//
//    private fun doLogout() {
//        viewModel.logout()
//    }
//
//    private fun createRoom() {
//        ensureAudioPermission {
//            viewModel.createRoom()
//        }
//    }
//
//    private fun joinRoom(room: Room) {
//        ensureAudioPermission {
//            viewModel.joinRoom(room)
//        }
//    }
//
//    private fun closeRoom() {
//        viewModel.leaveRoom()
//    }
//
//    private fun toggleHandRaised() {
//        viewModel.toggleHandRaised()
//    }
//
//    private fun toggleUserRole(member: MemberInfo) {
//        viewModel.toggleRole(member)
//    }
}
