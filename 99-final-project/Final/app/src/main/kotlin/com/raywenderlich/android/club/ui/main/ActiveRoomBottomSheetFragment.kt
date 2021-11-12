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

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.recyclerview.widget.RecyclerView
import com.raywenderlich.android.club.R
import com.raywenderlich.android.club.utils.view
import com.raywenderlich.android.club.utils.viewLifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ActiveRoomBottomSheetFragment : Fragment(R.layout.fragment_active_room) {

    companion object {
        fun newInstance() = ActiveRoomBottomSheetFragment()
    }

    /* UI */

    private val textRoomName by view<TextView>(R.id.text_room_name)
    private val rvUsers by view<RecyclerView>(R.id.rv_users)
    private val buttonLeaveRoom by view<Button>(R.id.button_leave_room)

    /* Logic */

    private lateinit var viewModel: MainViewModel
    private val rvUsersAdapter = UserListAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Connect to activity's ViewModel and subscribe to its state
        viewModel = ViewModelProvider(requireActivity()).get()
        viewModel.state
            .onEach { handleState(it) }
            .launchIn(viewLifecycleScope)

        // Setup UI
        rvUsers.adapter = rvUsersAdapter

        buttonLeaveRoom.setOnClickListener {
            viewModel.leaveRoom()
        }
    }

    private fun handleState(state: MainViewModel.State) {
        textRoomName.text = state.connectedRoom?.roomName
        rvUsersAdapter.submitList(state.connectedRoomMembers)
    }
}
