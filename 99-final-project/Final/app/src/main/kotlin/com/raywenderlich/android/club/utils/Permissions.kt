package com.raywenderlich.android.club.utils

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A way to await a permission from inside a coroutine utilizing an Activity contract.
 * Based on https://stackoverflow.com/questions/65277180/could-registerforactivityresult-be-used-in-a-coroutine
 */
class RequestPermission(activity: ComponentActivity) {
    private var continuation: CancellableContinuation<Boolean>? = null

    @SuppressLint("MissingPermission")
    private val requestFineLocationPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            continuation?.resume(isGranted)
            continuation = null
        }

    suspend operator fun invoke(permission: String) =
        suspendCancellableCoroutine<Boolean> { continuation ->
            this.continuation = continuation
            requestFineLocationPermissionLauncher.launch(permission)

            continuation.invokeOnCancellation {
                this.continuation = null
            }
        }
}
