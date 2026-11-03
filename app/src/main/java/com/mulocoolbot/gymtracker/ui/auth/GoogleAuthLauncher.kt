@file:Suppress("DEPRECATION")

package com.mulocoolbot.gymtracker.ui.auth

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

@Suppress("DEPRECATION")
@Composable
fun rememberGoogleAuthLauncher(
    onSuccess: (email: String, displayName: String?) -> Unit,
    onErrorOrCancel: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedEmail = result.data
                ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                ?.trim()
                .orEmpty()
            if (selectedEmail.isNotEmpty()) {
                onSuccess(selectedEmail, null)
            } else {
                onErrorOrCancel()
            }
        } else {
            onErrorOrCancel()
        }
    }

    val launchNativeAccountPicker = {
        runCatching {
            val pickerIntent = AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .build()
            )
            accountPickerLauncher.launch(pickerIntent)
        }.onFailure {
            onErrorOrCancel()
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email?.trim().orEmpty()
            if (email.isNotEmpty()) {
                onSuccess(email, account?.displayName)
            } else {
                launchNativeAccountPicker()
            }
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.DEVELOPER_ERROR ||
                e.statusCode == CommonStatusCodes.INTERNAL_ERROR ||
                e.statusCode == 12500
            ) {
                launchNativeAccountPicker()
            } else {
                onErrorOrCancel()
            }
        } catch (_: Throwable) {
            onErrorOrCancel()
        }
    }

    return {
        runCatching {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build()
            val client = GoogleSignIn.getClient(context, options)
            client.signOut()
            googleSignInLauncher.launch(client.signInIntent)
        }.onFailure {
            launchNativeAccountPicker()
        }
    }
}

