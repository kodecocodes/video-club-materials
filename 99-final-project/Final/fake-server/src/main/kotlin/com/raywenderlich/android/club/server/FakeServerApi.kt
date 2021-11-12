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

package com.raywenderlich.android.club.server

import com.raywenderlich.android.club.api.ServerApi
import com.raywenderlich.android.club.models.RoomId
import com.raywenderlich.android.club.models.Token
import com.raywenderlich.android.club.models.UserId
import io.agora.media.RtcTokenBuilder
import io.agora.rtm.RtmTokenBuilder
import kotlinx.coroutines.delay
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * A fake implementation of the [ServerApi] interface, used to facilitate the contents of this course,
 * which focuses on Android apps rather than server development. It does not perform any actual HTTP calls,
 * but rather simulates network delays and creates authentication tokens on the client side.
 *
 * **DO NOT DO THIS FOR A PRODUCTION APP.**
 *
 * This should never be used in a real production app, as it requires the secret Agora credentials
 * in order to create valid authentication tokens. These secrets MUST be kept outside an app
 * because otherwise, they could compromise your service. Again, the only purpose for this is
 * to cut down on the scope of this course. Instead of using this class, deploy your own
 * backend server and use libraries like Retrofit to interact with it from the Android side.
 */
@ExperimentalTime
class FakeServerApi(
    private val agoraAppId: String,
    private val agoraAppCertificate: String,
    private val tokenExpiration: Duration
) : ServerApi {

    private val rtmBuilder = RtmTokenBuilder()
    private val rtcBuilder = RtcTokenBuilder()

    override suspend fun createRtmToken(userName: String): Token {
        simulateNetworkDelay()

        val value = rtmBuilder.buildToken(
            /* appId = */ agoraAppId,
            /* appCertificate = */ agoraAppCertificate,
            /* uid = */ userName,
            /* role = */ RtmTokenBuilder.Role.Rtm_User,
            /* privilegeTs = */ createExpirationTimestamp()
        )

        return Token(value)
    }

    override suspend fun createRtcToken(
        userId: UserId,
        roomId: RoomId,
        isBroadcaster: Boolean
    ): Token {
        simulateNetworkDelay()

        val value = rtcBuilder.buildTokenWithUid(
            /* appId = */ agoraAppId,
            /* appCertificate = */ agoraAppCertificate,
            /* channelName = */ roomId.value,
            /* uid = */ userId.value,
            /* role = */ if (isBroadcaster) {
                RtcTokenBuilder.Role.Role_Publisher
            } else {
                RtcTokenBuilder.Role.Role_Subscriber
            },
            /* privilegeTs = */ createExpirationTimestamp()
        )

        return Token(value)
    }

    /* Private */

    private suspend fun simulateNetworkDelay() {
        delay(Random.nextLong(200, 1000))
    }

    private fun createExpirationTimestamp(): Int {
        // Java's Calendar API returns milliseconds,
        // but Agora expiration timestamps need seconds
        val currentTime = Calendar.getInstance().timeInMillis / 1_000
        val endTime = currentTime + tokenExpiration.inWholeSeconds

        // Strangely, the Agora Authentication SDK accepts timestamps only as integers
        return endTime.toInt()
    }
}
