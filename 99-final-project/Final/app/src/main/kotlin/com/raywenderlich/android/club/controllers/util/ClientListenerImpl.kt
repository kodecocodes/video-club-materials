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

import com.raywenderlich.android.agora.rtm.ConnectionState
import com.raywenderlich.android.agora.rtm.DefaultRtmClientListener
import com.raywenderlich.android.club.models.Sendable
import io.agora.rtm.RtmMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * A special variant of Agora's RtmClientListener interface which invokes callbacks
 * related to incoming messages using the given [coroutineScope], allowing for them
 * to be suspending functions.
 */
class ClientListenerImpl(
    private val coroutineScope: CoroutineScope,
    private val onConnectionState: (ConnectionState) -> Unit,
    private val onMessage: suspend (Sendable, String) -> Unit
) : DefaultRtmClientListener() {

    override fun onConnectionStateChanged(state: Int, reason: Int) {
        onConnectionState(ConnectionState.fromCode(state))
    }

    override fun onMessageReceived(message: RtmMessage, peerId: String) {
        // Decode the incoming message
        val data = runCatching { Json.decodeFromString<Sendable>(message.text) }.getOrNull()
            ?: run {
                println("onMessageReceived() got unknown message from $peerId: ${message.text}")
                return
            }

        coroutineScope.launch {
            onMessage(data, peerId)
        }
    }
}
