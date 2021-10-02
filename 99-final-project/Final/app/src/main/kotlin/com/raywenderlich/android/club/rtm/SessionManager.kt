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

package com.raywenderlich.android.club.rtm

import android.content.Context
import com.raywenderlich.android.agora.rtm.awaitLogin
import com.raywenderlich.android.agora.rtm.joinChannel
import com.raywenderlich.android.club.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.agora.rtm.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

/**
 * Management object for the user's session, connected to the Agora.io RTM SDK.
 *
 */
class SessionManager(
    context: Context,
    appId: String = BuildConfig.AGORA_APP_ID,
    baseServerUrl: String = BuildConfig.SERVER_BASE_URL,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val eventListener = object : RtmClientListener {
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            println("onConnectionStateChanged($state, $reason)")
        }

        override fun onMessageReceived(message: RtmMessage, peerId: String) {
            println("onMessageReceived($message, $peerId)")
        }

        override fun onImageMessageReceivedFromPeer(message: RtmImageMessage, peerId: String) {
        }

        override fun onFileMessageReceivedFromPeer(message: RtmFileMessage, peerId: String) {
        }

        override fun onMediaUploadingProgress(
            progress: RtmMediaOperationProgress,
            requestId: Long
        ) {
        }

        override fun onMediaDownloadingProgress(
            progress: RtmMediaOperationProgress,
            requestId: Long
        ) {
        }

        override fun onTokenExpired() {
            println("onTokenExpired()")
        }

        override fun onPeersOnlineStatusChanged(peersStatus: MutableMap<String, Int>) {
            println("onPeersOnlineStatusChanged($peersStatus)")
        }
    }

    private val rtmClient =
        RtmClient.createInstance(context, appId, eventListener)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val serverApi = Retrofit.Builder()
        .baseUrl(baseServerUrl)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create<ServerApi>()

    suspend fun login(userId: String): Session = withContext(dispatcher) {
        // The app's login flow consists of a few distinct steps:
        // - Obtain a token from our own backend server
        // - Log into the Agora RTM system with this token
        // - Join the common 'lobby' channel of the RTM system
        val tokenResponse = serverApi.createRtmToken(userId)

        rtmClient.awaitLogin(tokenResponse.token, userId)

        val lobbyChannel = rtmClient.joinChannel("lobby") { message, member ->
            println("onMessageReceived from $member: $message")
        }

        // Return a session object for the caller;
        // this can be used to log them out at a later time
        Session(lobbyChannel)
    }
}
