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

import com.raywenderlich.android.agora.rtm.awaitGetUserAttributes
import com.raywenderlich.android.club.models.MemberInfo
import com.raywenderlich.android.club.models.MemberRole
import io.agora.rtm.RtmAttribute
import io.agora.rtm.RtmChannelMember
import io.agora.rtm.RtmClient

// Local user attributes:
// These are public properties of the current user, readable by other users in the same channel.
// The app uses it to share common state with the room, such as the user's name or
// whether they have raised their hand
private const val ATTR_HAND_RAISED = "hand-raised"
private const val ATTR_MIC_OFF = "microphone-off"
private const val ATTR_ROLE = "role"

/* Conversions between Agora and app */

/**
 * Convert an RtmChannelMember to the app realm's MemberInfo object,
 * parsing all the public attributes in their profile to a new structure
 */
suspend fun RtmChannelMember.asMemberInfo(client: RtmClient): MemberInfo {
    val attributes = client.awaitGetUserAttributes(this.userId)
    return attributes.asMemberInfo(this.userId)
}

/**
 * Create a MemberInfo object with the given [peerId] from a list of attributes.
 */
fun List<RtmAttribute>.asMemberInfo(peerId: String): MemberInfo {
    val map = this.associate { it.key to it.value }

    return MemberInfo(
        agoraId = peerId,
        userName = peerId, // TODO
        role = memberRoleOf(map[ATTR_ROLE]),
        raisedHand = map[ATTR_HAND_RAISED]?.toBooleanStrictOrNull() ?: false,
        microphoneOff = map[ATTR_MIC_OFF]?.toBooleanStrictOrNull() ?: true,
    )
}

fun MemberInfo.asAttributeList() =
    createAttributeList(this.role, this.raisedHand, this.microphoneOff)

fun createAttributeList(
    role: MemberRole,
    isRaisedHand: Boolean,
    isMicrophoneOff: Boolean,
): List<RtmAttribute> =
    listOfNotNull(
        RtmAttribute(ATTR_HAND_RAISED, isRaisedHand.toString()),
        RtmAttribute(ATTR_MIC_OFF, isMicrophoneOff.toString()),
        RtmAttribute(ATTR_ROLE, role.id)
    )

private fun memberRoleOf(value: String?): MemberRole {
    return (MemberRole.values()
        .firstOrNull { it.id == value }
        ?: MemberRole.Audience).also {
        println("memberRoleOf $value --> $it")
    }
}
