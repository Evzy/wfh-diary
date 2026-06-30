package dev.wfhdiary.app.notification

import com.google.firebase.auth.FirebaseAuth
import dev.wfhdiary.app.BuildConfig
import dev.wfhdiary.app.WfhDiaryApplication
import kotlinx.coroutines.tasks.await

object FirebaseTokenHelper {
    suspend fun ensureToken(app: WfhDiaryApplication) {
        if (!BuildConfig.GOOGLE_SERVICES_CONFIGURED) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val token = user.getIdToken(false).await().token
        app.firebaseIdTokenHolder.setToken(token)
    }
}
