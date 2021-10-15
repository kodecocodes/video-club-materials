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

package com.raywenderlich.android.club.controllers.util

import com.raywenderlich.android.agora.rtm.DefaultRtmChannelListener
import com.raywenderlich.android.club.models.MemberInfo
import io.agora.rtm.RtmChannelMember
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Listener for any ongoing room, handling messages
 * to and from other audience members and the broadcaster
 */
class RoomChannelListener(
    private val client: RtmClient,
    private val coroutineScope: CoroutineScope
) : DefaultRtmChannelListener() {

    val membersFlow = MutableStateFlow(emptyList<MemberInfo>())

    override fun onMessageReceived(message: RtmMessage, member: RtmChannelMember) {
        println("onMessageReceived in '${member.channelId}' from ${member.userId}: ${message.text}")
    }

    override fun onMemberJoined(member: RtmChannelMember) {
        coroutineScope.launch {
            val info = member.asMemberInfo(client)
            updateMember(info)
        }
    }

    override fun onMemberLeft(member: RtmChannelMember) {
        // Remove the user from the internal list of users
        membersFlow.update { members ->
            members.toMutableList().apply {
                removeAll { it.agoraId == member.userId }
            }
        }
    }

    fun updateMember(info: MemberInfo) {
        // Remove the user from the internal list of users
        // and add the updated object back
        membersFlow.update { members ->
            members.toMutableList().apply {
                removeAll { it.agoraId == info.agoraId }
                add(info)
            }
        }
    }
}
