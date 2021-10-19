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

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.raywenderlich.android.club.R
import com.raywenderlich.android.club.ui.main.MainActivity
import com.raywenderlich.android.club.utils.loginViewModel
import com.raywenderlich.android.club.utils.view
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Login screen of the app, prompting for a user name before redirecting to the Main screen.
 */
class LoginActivity : AppCompatActivity(R.layout.activity_login) {

    /* UI */

    private val buttonLogin by view<Button>(R.id.button_login)
    private val inputUserName by view<EditText>(R.id.input_user_name)
    private val progressBar by view<ProgressBar>(R.id.progress_bar)

    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hide splash screen
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // Initialize ViewModel and subscribe to its state and events
        viewModel = loginViewModel(this)

        viewModel.state
            .onEach { handleState(it) }
            .launchIn(lifecycleScope)

        viewModel.events
            .onEach { handleEvent(it) }
            .launchIn(lifecycleScope)

        // Setup listeners
        buttonLogin.setOnClickListener { doLogin() }
    }

    /* Private */

    private fun doLogin() {
        val userName = inputUserName.text.toString()
        viewModel.doLogin(userName)
    }

    private fun handleState(state: LoginViewModel.State) {
        inputUserName.setText(state.userName)

        buttonLogin.isEnabled = !state.isLoading
        inputUserName.isEnabled = !state.isLoading
        progressBar.isVisible = state.isLoading
    }

    private fun handleEvent(event: LoginViewModel.Event) {
        when (event) {
            // Redirect to main screen
            is LoginViewModel.Event.LoginSuccess -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }

            // Show error message
            is LoginViewModel.Event.LoginFailure -> {
                Toast.makeText(this, R.string.toast_login_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
