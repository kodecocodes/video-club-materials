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
val NO_OPTIONS = SendMessageOptions()

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

suspend fun RtmClient.awaitJoinChannel(
    channelId: String,
    listener: RtmChannelListener
): RtmChannel {
    val channel = createChannel(channelId, listener)
    channel.awaitJoin()
    return channel
}

suspend fun RtmClient.awaitSendMessageToPeer(
    userId: String,
    message: RtmMessage,
    options: SendMessageOptions = NO_OPTIONS
) {
    suspendCoroutine<Void> { continuation ->
        sendMessageToPeer(userId, message, options, continuation.asResultCallback())
    }
}

suspend fun RtmClient.awaitSendMessageToChannelMembers(
    channel: RtmChannel,
    message: RtmMessage,
    options: SendMessageOptions = NO_OPTIONS
) {
    val members = channel.awaitGetMembers()

    // Send the message to all of them in parallel,
    // wait until it was delivered to all of them
    for (member in members) {
        // A peer may be unreachable, but that's OK
        runCatching { awaitSendMessageToPeer(member.userId, message, options) }
    }
}

fun RtmClient.sendMessageToChannelMembers(
    channel: RtmChannel,
    message: RtmMessage,
    options: SendMessageOptions = NO_OPTIONS
) {
    channel.getMembers(object : ResultCallback<List<RtmChannelMember>> {
        override fun onSuccess(members: List<RtmChannelMember>) {
            for (member in members) {
                // A peer may be unreachable, but that's OK
                runCatching { sendMessageToPeer(member.userId, message, options, null) }
            }
        }

        override fun onFailure(p0: ErrorInfo?) {
        }
    })
}

suspend fun RtmClient.awaitAddOrUpdateLocalUserAttributes(attributes: List<RtmAttribute>) {
    suspendCoroutine<Void> { continuation ->
        addOrUpdateLocalUserAttributes(attributes, continuation.asResultCallback())
    }
}

suspend fun RtmClient.awaitClearLocalUserAttributes() {
    suspendCoroutine<Void> { continuation ->
        clearLocalUserAttributes(continuation.asResultCallback())
    }
}

suspend fun RtmClient.awaitGetUserAttributes(peerId: String): List<RtmAttribute> =
    suspendCoroutine { continuation ->
        getUserAttributes(peerId, continuation.asResultCallback())
    }
