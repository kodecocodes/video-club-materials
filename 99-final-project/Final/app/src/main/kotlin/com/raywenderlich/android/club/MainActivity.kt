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

package com.raywenderlich.android.club

import android.Manifest
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.raywenderlich.android.club.service.AudioService
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    // UI
    private val joinButton by lazy { findViewById<Button>(R.id.button_join) }
    private val leaveButton by lazy { findViewById<Button>(R.id.button_leave) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hide splash screen
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // Setup listeners
        joinButton.setOnClickListener { joinChannel() }
        leaveButton.setOnClickListener { leaveChannel() }
    }

    override fun onDestroy() {
        super.onDestroy()
        leaveChannel()
    }

    /* Listeners */

    private fun joinChannel() =
        lifecycleScope.launch {
            val hasPermission = awaitPermission(Manifest.permission.RECORD_AUDIO)
            if (hasPermission) {
                AudioService.start(this@MainActivity)

                // TODO Toggle these automatically through a flow exposed by the service
                joinButton.isEnabled = false
                leaveButton.isEnabled = true
            }
        }

    private fun leaveChannel() {
        AudioService.stop(this)

        // TODO Toggle these automatically through a flow exposed by the service
        leaveButton.isEnabled = false
        joinButton.isEnabled = true
    }

    /* Private */

    private suspend fun awaitPermission(permission: String) =
        suspendCoroutine<Boolean> { continuation ->
            if (ContextCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED) {
                // Already have permission
                continuation.resume(true)

            } else {
                val launcher = registerForActivityResult(RequestPermission()) { granted ->
                    continuation.resume(granted)
                }

                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    // Show explanation UI for the permission
                    AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_permission_rationale_title)
                        .setMessage(R.string.dialog_permission_rationale_text)
                        .setPositiveButton(R.string.button_ok) { _, _ ->
                            launcher.launch(permission)
                        }
                        .show()

                } else {
                    // Request the permission
                    launcher.launch(permission)
                }
            }
        }
}
