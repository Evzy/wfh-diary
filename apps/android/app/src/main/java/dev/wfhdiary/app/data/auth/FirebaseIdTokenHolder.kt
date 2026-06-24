package dev.wfhdiary.app.data.auth

import java.util.concurrent.atomic.AtomicReference

class FirebaseIdTokenHolder {
    private val tokenRef = AtomicReference<String?>(null)

    fun setToken(token: String?) {
        tokenRef.set(token)
    }

    fun getToken(): String? = tokenRef.get()
}
