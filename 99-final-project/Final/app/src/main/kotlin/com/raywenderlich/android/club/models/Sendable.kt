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

package com.raywenderlich.android.club.models

import com.raywenderlich.android.club.models.Room
import com.raywenderlich.android.club.models.RoomList
import com.raywenderlich.android.club.models.UserRoleChanged
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmMessage
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

/**
 * Helper functions for creating [Sendable] messages
 * from the context of a RTM client.
 */
@OptIn(InternalSerializationApi::class)
fun RtmClient.createSendableMessage(kind: Sendable.Kind, bodyText: String): RtmMessage {
    val sendable = Sendable(kind, bodyText)
    val encoded = Json.encodeToString(sendable)
    return createMessage(encoded)
}

/**
 * Base class for messages that can be sent to peers with the Agora RTM SDK.
 * They are serialized to JSON format before sending and will be re-assembled
 * on the receiving end. The [kind] of a sendable message can be used
 * to determine the reaction of the receiver
 */
@Serializable
data class Sendable(
    @SerialName("kind")
    val kind: Kind,
    @SerialName("body")
    private val bodyText: String
) {
    @Serializable
    enum class Kind(val bodyClass: KClass<out Any>) {
        @SerialName("room-opened")
        RoomOpened(Room::class),

        @SerialName("room-closed")
        RoomClosed(Room::class),

        @SerialName("room-list")
        RoomList(com.raywenderlich.android.club.models.RoomList::class),

        @SerialName("role-changed")
        RoleChanged(UserRoleChanged::class),
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(InternalSerializationApi::class)
    fun <T : Any> decodeBody(): T {
        return Json.decodeFromString(kind.bodyClass.serializer(), bodyText) as T
    }
}
