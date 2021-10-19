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

package com.raywenderlich.android.club.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raywenderlich.android.club.controllers.SessionManager
import com.raywenderlich.android.club.controllers.persistence.SettingsRepository
import com.raywenderlich.android.club.utils.EventFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val settingsRepository: SettingsRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    data class State(
        val userName: String = "",
        val isLoading: Boolean = false
    )

    sealed class Event {
        object LoginFailure : Event()
        object LoginSuccess : Event()
    }

    private val _state = MutableStateFlow(State())
    val state: Flow<State> = _state

    private val _events = EventFlow<Event>()
    val events: Flow<Event> = _events

    init {
        // If a user name was already chosen before, log in right away and redirect the user after
        settingsRepository.getPersistedUserName()?.let { userName ->
            doLogin(userName)
        }
    }

    fun doLogin(userName: String) {
        if (userName.isEmpty()) {
            _events.tryEmit(Event.LoginFailure)
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(userName = userName, isLoading = true)
            }

            // Perform the login into the Agora system and store the user name persistently
            // (for a real app, you'd want to use AccountManager instead of shared preferences for that)
            sessionManager.login(userName)
            settingsRepository.setPersistedUserName(userName)

            _events.tryEmit(Event.LoginSuccess)
        }
    }
}
