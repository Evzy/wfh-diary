package dev.wfhdiary.app.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dev.wfhdiary.app.BuildConfig
import kotlinx.coroutines.tasks.await
import java.util.UUID

private fun humanizeGetCredentialException(e: GetCredentialException): Throwable {
    val msg = e.message.orEmpty()
    val lower = msg.lowercase()
    val looksLike16 =
        Regex("""(^|[^\d])16([:\],]|\s|$)""").containsMatchIn(msg) ||
            lower.contains("re-auth") ||
            lower.contains("reauth")
    if (!looksLike16) return e
    return IllegalStateException(
        "Google sign-in failed (code 16 / re-auth). Register this APK signing key in Firebase Console " +
            "for Android app dev.wfhdiary.app (SHA-1 / SHA-256), then sync google-services.json.",
        e,
    )
}

object GoogleFirebaseSignIn {
    suspend fun signIn(activity: Activity): Result<Unit> {
        if (!BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
            return Result.failure(IllegalStateException("Missing app/google-services.json — add file and Sync Gradle."))
        }
        val serverClientId = BuildConfig.FIREBASE_WEB_CLIENT_ID.trim()
        if (serverClientId.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "Set FIREBASE_WEB_CLIENT_ID in local.properties (OAuth 2.0 Web client id).",
                ),
            )
        }

        return try {
            val signInWithGoogleOption =
                GetSignInWithGoogleOption.Builder(serverClientId = serverClientId)
                    .setNonce(UUID.randomUUID().toString())
                    .build()
            val request =
                GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .setPreferImmediatelyAvailableCredentials(false)
                    .build()
            val credentialManager = CredentialManager.create(activity.applicationContext)
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(IllegalStateException("Unexpected credential type"))
            }
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
            Result.success(Unit)
        } catch (e: NoCredentialException) {
            Result.failure(
                IllegalStateException(
                    "Google sign-in could not start. Update Play services, add a Google account, " +
                        "and register signing SHA-1 in Firebase for release/debug builds.",
                    e,
                ),
            )
        } catch (e: GetCredentialException) {
            Result.failure(humanizeGetCredentialException(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        if (BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
            FirebaseAuth.getInstance().signOut()
        }
    }
}
