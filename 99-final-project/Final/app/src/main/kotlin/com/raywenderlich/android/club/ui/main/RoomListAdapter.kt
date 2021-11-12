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

import android.content.Context
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.DynamicDrawableSpan.ALIGN_BASELINE
import android.text.style.DynamicDrawableSpan.ALIGN_BOTTOM
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.raywenderlich.android.club.R
import com.raywenderlich.android.club.models.Room
import com.raywenderlich.android.club.utils.profileImageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.random.Random

private val callback = object : DiffUtil.ItemCallback<Room>() {
    override fun areItemsTheSame(oldItem: Room, newItem: Room): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Room, newItem: Room): Boolean {
        return oldItem.roomId == newItem.roomId
    }
}

class RoomListAdapter : ListAdapter<Room, RoomViewHolder>(callback) {

    private val _itemClickEvents = MutableSharedFlow<Room>(extraBufferCapacity = 1)
    val itemClickEvents: Flow<Room> = _itemClickEvents

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = getItem(position)
        val context = holder.itemView.context

        holder.textRoomName.text = room.name
        holder.textHostNames.text = hostNamesAsSpannable(context, room)
        holder.textMemberCount.text = memberCountAsSpannable(context, room)

        holder.imageHost.setImageResource(room.host.profileImageRes())
        val coHost = room.coHosts.firstOrNull()
        if (coHost != null) {
            holder.imageCoHost.setImageResource(coHost.profileImageRes())
        } else {
            holder.imageCoHost.setImageDrawable(null)
        }

        holder.itemView.setOnClickListener {
            _itemClickEvents.tryEmit(room)
        }
    }

    private fun hostNamesAsSpannable(context: Context, room: Room): CharSequence {
        // Concatenate the name of the room's host & up to 4 co-hosts
        // and combine them with some speech bubble icons'
        return buildSpannedString {
            append(room.host.name)
            append("  ")
            setSpan(
                ImageSpan(context, R.drawable.ic_message, ALIGN_BASELINE),
                length - 1,
                length,
                SPAN_EXCLUSIVE_EXCLUSIVE
            )

            for (coHost in room.coHosts.take(4)) {
                append('\n')
                append(coHost.name)
                append("  ")
                setSpan(
                    ImageSpan(context, R.drawable.ic_message, ALIGN_BASELINE),
                    length - 1,
                    length,
                    SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun memberCountAsSpannable(context: Context, room: Room): CharSequence {
        // Format the member count with an icon
        return buildSpannedString {
            append(room.memberCount.toString())
            append("  ")
            setSpan(
                ImageSpan(context, R.drawable.ic_account, ALIGN_BOTTOM),
                length - 1,
                length,
                SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}

class RoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val textRoomName = view.findViewById<TextView>(R.id.text_room_name)
    val textHostNames = view.findViewById<TextView>(R.id.text_host_names)
    val textMemberCount = view.findViewById<TextView>(R.id.text_member_count)

    val imageHost = view.findViewById<ImageView>(R.id.image_host_profile)
    val imageCoHost = view.findViewById<ImageView>(R.id.image_co_host_profile)
}
