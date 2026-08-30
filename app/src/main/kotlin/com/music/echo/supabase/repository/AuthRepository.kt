package com.music.echo.supabase.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result wrapper for auth operations — avoids leaking exception types to UI.
 */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Lightweight view of the current authenticated session, suitable for UI.
 */
data class EpsilonSession(
    val userId: String,
    val email: String?,
    val accessToken: String,
    val expiresAtMillis: Long,
)

/**
 * Wraps Supabase Auth. All auth operations (sign up, sign in, sign out,
 * password reset, email verification) flow through here.
 *
 * The repository exposes the current session as a StateFlow so ViewModels can
 * react to login/logout transitions without polling.
 *
 * NOTE: existing YouTube Music cookie-based login (LoginScreen +
 * AccountSettingsViewModel) is intentionally NOT touched. Supabase Auth is an
 * additional, independent identity surface for cloud-synced user data.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: Auth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _session = MutableStateFlow<EpsilonSession?>(null)
    val session: StateFlow<EpsilonSession?> = _session.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Observe the Supabase session status and update our StateFlow
        scope.launch {
            try {
                auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val s = status.session
                            _session.value = EpsilonSession(
                                userId = s.user?.id.orEmpty(),
                                email = s.user?.email,
                                accessToken = s.accessToken,
                                expiresAtMillis = s.expiresAt.epochSeconds * 1000,
                            )
                        }
                        is SessionStatus.NotAuthenticated -> _session.value = null
                        is SessionStatus.RefreshFailure -> {
                            Timber.w("Supabase session refresh failed: ${status.cause}")
                            _session.value = null
                        }
                        SessionStatus.Initializing -> Unit
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe Supabase session")
            }
        }
    }

    val isLoggedIn: StateFlow<Boolean> = session
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        _isLoading.value = true
        return try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            AuthResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Sign up failed")
            AuthResult.Error(e.message ?: "Sign up failed")
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        _isLoading.value = true
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            AuthResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Sign in failed")
            AuthResult.Error(e.message ?: "Sign in failed")
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signOut() {
        _isLoading.value = true
        try {
            auth.signOut()
        } catch (e: Exception) {
            Timber.e(e, "Sign out failed")
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun resetPassword(email: String): AuthResult {
        return try {
            auth.resetPasswordForEmail(email)
            AuthResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Password reset failed")
            AuthResult.Error(e.message ?: "Password reset failed")
        }
    }

    suspend fun resendVerificationEmail(email: String): AuthResult {
        return try {
            auth.resendEmail(OtpType.Email.SIGNUP, email)
            AuthResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Resend verification failed")
            AuthResult.Error(e.message ?: "Resend verification failed")
        }
    }

    /**
     * Refresh from disk — call this on app start to restore a persisted session.
     */
    suspend fun loadSession() {
        try {
            auth.loadFromStorage()
        } catch (e: Exception) {
            Timber.w(e, "Failed to load Supabase session from storage")
        }
    }
}
