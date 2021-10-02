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

package com.raywenderlich.android.agora.rtm

import io.agora.rtm.*
import kotlin.coroutines.suspendCoroutine

/**
 * Suspending variants of several methods of the [RtmClient] class.
 */

suspend fun RtmClient.awaitLogin(token: String, userId: String) {
    suspendCoroutine<Void> { continuation ->
        login(token, userId, continuation.asResultCallback())
    }
}

suspend fun RtmClient.awaitLogout() {
    suspendCoroutine<Void> { continuation ->
        logout(continuation.asResultCallback())
    }
}

suspend fun RtmClient.joinChannel(
    channelId: String,
    onMessage: (RtmMessage, RtmChannelMember) -> Unit
): RtmChannel {
    // First create the channel...
    val channel = createChannel(channelId, object : RtmChannelListener {
        override fun onMessageReceived(message: RtmMessage, member: RtmChannelMember) {
            onMessage(message, member)
        }

        override fun onMemberJoined(member: RtmChannelMember) {
            println("onMemberJoined $member")
        }

        override fun onMemberLeft(member: RtmChannelMember) {
            println("onMemberLeft $member")
        }

        override fun onMemberCountUpdated(count: Int) {
            println("onMemberCountUpdated($count)")
        }

        override fun onAttributesUpdated(attributes: MutableList<RtmChannelAttribute>) {
            println("onAttributesUpdated $attributes")
        }

        override fun onImageMessageReceived(message: RtmImageMessage, member: RtmChannelMember) {
        }

        override fun onFileMessageReceived(p0: RtmFileMessage?, p1: RtmChannelMember?) {
        }
    })

    // ...and then join it
    channel.awaitJoin()

    return channel
}
