package dev.wfhdiary.app.ui.auth

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dev.wfhdiary.app.BuildConfig
import dev.wfhdiary.app.data.auth.GoogleFirebaseSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val requiresSignIn: Boolean = BuildConfig.GOOGLE_SERVICES_CONFIGURED,
    val authChecked: Boolean = !BuildConfig.GOOGLE_SERVICES_CONFIGURED,
    val isSignedIn: Boolean = !BuildConfig.GOOGLE_SERVICES_CONFIGURED,
    val userLabel: String? = null,
    val signInBusy: Boolean = false,
    val errorMessage: String = "",
) {
    val showLoginScreen: Boolean
        get() = requiresSignIn && authChecked && !isSignedIn

    val showLoading: Boolean
        get() = requiresSignIn && !authChecked
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    init {
        if (BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
            val auth = FirebaseAuth.getInstance()
            fun syncFromAuth() {
                val user = auth.currentUser
                _uiState.update {
                    it.copy(
                        authChecked = true,
                        isSignedIn = user != null,
                        userLabel = user?.email?.takeIf { email -> email.isNotBlank() } ?: user?.uid,
                    )
                }
            }
            syncFromAuth()
            auth.addAuthStateListener { syncFromAuth() }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (!BuildConfig.GOOGLE_SERVICES_CONFIGURED) return
        viewModelScope.launch {
            _uiState.update { it.copy(signInBusy = true, errorMessage = "") }
            val result = withContext(Dispatchers.Main) { GoogleFirebaseSignIn.signIn(activity) }
            _uiState.update { it.copy(signInBusy = false) }
            result.exceptionOrNull()?.let { e ->
                _uiState.update {
                    it.copy(errorMessage = e.message?.trim().orEmpty().ifEmpty { e.javaClass.simpleName })
                }
            }
        }
    }

    fun signOut() {
        GoogleFirebaseSignIn.signOut()
        _uiState.update { it.copy(errorMessage = "") }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AuthViewModel(application) as T
            }
    }
}
