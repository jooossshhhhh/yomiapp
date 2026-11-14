package com.yomi.reader.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64 as JavaBase64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.resume

internal object LocalPasswordHasher {
    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = deriveKey(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val saltB64 = JavaBase64.getEncoder().encodeToString(salt)
        val hashB64 = JavaBase64.getEncoder().encodeToString(derived)
        return "$PREFIX\$$ITERATIONS\$$saltB64\$$hashB64"
    }

    fun isHashed(stored: String): Boolean = stored.startsWith("$PREFIX$")

    fun verifyPassword(password: String, stored: String): Boolean {
        if (!isHashed(stored)) return stored == password
        val parts = stored.split('$')
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { JavaBase64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expectedHash = runCatching { JavaBase64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actualHash = deriveKey(password, salt, iterations, expectedHash.size * 8)
        return MessageDigest.isEqual(actualHash, expectedHash)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyBits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

data class GoogleUser(
    val email: String,
    val displayName: String?,
    val idToken: String? = null,
    val photoUrl: String? = null,
    val isGoogleAccount: Boolean = true,
    val googlePhotoUrl: String? = null
)

class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val prefs = context.getSharedPreferences("yomi_auth_prefs", Context.MODE_PRIVATE)
    private val userDbPrefs = context.getSharedPreferences("yomi_registered_users", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<GoogleUser?>(loadSavedUser())
    val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    private val _pendingProfileConsentIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingProfileConsentIntent: StateFlow<PendingIntent?> = _pendingProfileConsentIntent.asStateFlow()

    private fun loadSavedUser(): GoogleUser? {
        if (!prefs.getBoolean("is_signed_in", false)) return null
        val email = prefs.getString("user_email", null) ?: return null
        return GoogleUser(
            email = email,
            displayName = prefs.getString("user_name", null),
            idToken = prefs.getString("user_id_token", null),
            photoUrl = prefs.getString("user_photo", null),
            isGoogleAccount = prefs.getBoolean("is_google_account", false),
            googlePhotoUrl = prefs.getString("google_photo", null)
        )
    }

    /**
     * Native account registration using username and password.
     */
    fun registerWithUsernamePassword(
        username: String,
        password: String,
        displayName: String
    ): Result<GoogleUser> {
        val cleanUser = username.trim().lowercase()
        val cleanPass = password.trim()
        val cleanName = displayName.trim().ifBlank { username.trim() }

        if (cleanUser.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters"))
        }
        if (cleanPass.length < 4) {
            return Result.failure(IllegalArgumentException("Password must be at least 4 characters"))
        }

        if (userDbPrefs.contains("pwd_$cleanUser")) {
            return Result.failure(IllegalArgumentException("Username '$cleanUser' is already registered. Please Sign In."))
        }

        userDbPrefs.edit()
            .putString("pwd_$cleanUser", LocalPasswordHasher.hashPassword(cleanPass))
            .putString("name_$cleanUser", cleanName)
            .apply()

        val savedPhoto = userDbPrefs.getString("photo_$cleanUser", null)
        val newUser = GoogleUser(
            email = if (cleanUser.contains("@")) cleanUser else "$cleanUser@yomi.local",
            displayName = cleanName,
            idToken = null,
            photoUrl = savedPhoto,
            isGoogleAccount = false
        )
        saveUser(newUser)
        return Result.success(newUser)
    }

    /**
     * Native login using username and password.
     */
    fun loginWithUsernamePassword(
        username: String,
        password: String
    ): Result<GoogleUser> {
        val cleanUser = username.trim().lowercase()
        val cleanPass = password.trim()

        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter both username and password"))
        }

        val savedPassword = userDbPrefs.getString("pwd_$cleanUser", null)
        if (savedPassword == null) {
            return Result.failure(
                IllegalArgumentException("No account found for '$cleanUser'. Please switch to Create Account to register.")
            )
        }

        if (!LocalPasswordHasher.verifyPassword(cleanPass, savedPassword)) {
            return Result.failure(IllegalArgumentException("Incorrect password for '$cleanUser'"))
        }

        // Upgrade legacy plaintext entry seamlessly to salted PBKDF2 hash
        if (!LocalPasswordHasher.isHashed(savedPassword)) {
            userDbPrefs.edit()
                .putString("pwd_$cleanUser", LocalPasswordHasher.hashPassword(cleanPass))
                .apply()
        }

        val customDisplayName = userDbPrefs.getString("display_name_$cleanUser", null)
            ?: userDbPrefs.getString("name_$cleanUser", cleanUser)
        val savedPhoto = userDbPrefs.getString("photo_$cleanUser", null)
            ?: userDbPrefs.getString("photo_${if (cleanUser.contains("@")) cleanUser else "$cleanUser@yomi.local"}", null)
        val resolvedPhoto = if (savedPhoto == REMOVED_PHOTO_SENTINEL) null else savedPhoto
        val user = GoogleUser(
            email = if (cleanUser.contains("@")) cleanUser else "$cleanUser@yomi.local",
            displayName = customDisplayName,
            idToken = null,
            photoUrl = resolvedPhoto,
            isGoogleAccount = false
        )
        saveUser(user)
        return Result.success(user)
    }

    suspend fun signInWithGoogle(
        activityContext: Context,
        serverClientId: String = CLIENT_ID
    ): Result<GoogleUser> = withContext(Dispatchers.Main) {
        try {
            val result = try {
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .build()
                val explicitRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()
                credentialManager.getCredential(
                    context = activityContext,
                    request = explicitRequest
                )
            } catch (cancel: GetCredentialCancellationException) {
                throw cancel
            } catch (_: Exception) {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                credentialManager.getCredential(
                    context = activityContext,
                    request = fallbackRequest
                )
            }

            val credential = result.credential
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
            var retrievedPhotoUrl = resolveGoogleProfilePhotoUrl(googleIdToken)

            if (retrievedPhotoUrl.isNullOrBlank()) {
                retrievedPhotoUrl = fetchGoogleProfilePhoto(
                    activity = activityContext as? Activity,
                    email = googleIdToken.id,
                    promptConsentIfNeeded = true,
                    overwriteCurrentPhoto = true
                )
            }

            val accountKey = googleIdToken.id.trim().lowercase()
            val savedAppPhoto = userDbPrefs.getString("photo_$accountKey", null)
            val resolvedPhoto = when (savedAppPhoto) {
                REMOVED_PHOTO_SENTINEL -> null
                null -> retrievedPhotoUrl
                else -> savedAppPhoto
            }
            val savedCustomName = userDbPrefs.getString("display_name_$accountKey", null)
                ?.takeIf { it.isNotBlank() }
            val user = GoogleUser(
                email = googleIdToken.id,
                displayName = savedCustomName ?: googleIdToken.displayName ?: googleIdToken.id.substringBefore("@"),
                idToken = googleIdToken.idToken,
                photoUrl = resolvedPhoto,
                isGoogleAccount = true,
                googlePhotoUrl = retrievedPhotoUrl
            )

            saveUser(user)
            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Sign-in cancelled by user"))
        } catch (e: GetCredentialCustomException) {
            val msg = when {
                e.type.contains("28436", ignoreCase = true) || e.message?.contains("28436") == true ->
                    "Google OAuth Error [28436]: Credential Manager requires a 'Web Application' client ID as the serverClientId in Google Cloud Console."
                e.type.contains("16", ignoreCase = true) || e.message?.contains("16") == true ->
                    "Google OAuth Error [16]: No matching credential found. Ensure your SHA-1 and package name (com.yomi.app) are registered in Google Cloud Console."
                else -> "Google OAuth custom error: ${e.message ?: e.type}"
            }
            Result.failure(Exception(msg))
        } catch (e: GetCredentialException) {
            val msg = if (e.message?.contains("16") == true) {
                "Google OAuth Error [16]: No matching credential found. Ensure a Web Client ID is used in serverClientId and SHA-1 matches in Google Cloud Console."
            } else {
                e.localizedMessage ?: "Google Sign-In failed"
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the Google account's profile picture via OAuth2 userinfo / AuthorizationClient
     * whenever the Credential Manager ID token omitted the `picture` claim.
     */
    suspend fun fetchGoogleProfilePhoto(
        activity: Activity?,
        email: String,
        promptConsentIfNeeded: Boolean = true,
        overwriteCurrentPhoto: Boolean = false
    ): String? {
        // 1. Try AccountManager OAuth2 userinfo.profile silently or via Activity if allowed
        val accountToken = withContext(Dispatchers.IO) {
            runCatching {
                val accountManager = AccountManager.get(context)
                val account = Account(email, "com.google")
                val bundle = accountManager.getAuthToken(
                    account,
                    "oauth2:https://www.googleapis.com/auth/userinfo.profile",
                    null,
                    false,
                    null,
                    null
                ).result
                bundle.getString(AccountManager.KEY_AUTHTOKEN)
            }.getOrNull()
        }
        if (!accountToken.isNullOrBlank()) {
            val url = fetchPictureFromUserInfoEndpoint(accountToken)
            if (!url.isNullOrBlank()) {
                applyFetchedGooglePhoto(email, url, overwriteCurrentPhoto)
                return url
            }
        }

        // 2. Try Google Identity AuthorizationClient (official Credential Manager companion)
        val authContext: Context = activity ?: context
        val client = if (activity != null) {
            Identity.getAuthorizationClient(activity)
        } else {
            Identity.getAuthorizationClient(authContext)
        }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope("profile"),
                    Scope("https://www.googleapis.com/auth/userinfo.profile")
                )
            )
            .setAccount(Account(email, "com.google"))
            .build()

        val authResult = suspendCancellableCoroutine<AuthorizationResult?> { cont ->
            client.authorize(request)
                .addOnSuccessListener { res ->
                    if (cont.isActive) cont.resume(res)
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "AuthorizationClient.authorize failed: ${err.message}")
                    if (cont.isActive) cont.resume(null)
                }
        } ?: return null

        if (authResult.hasResolution()) {
            val pending = authResult.pendingIntent
            if (promptConsentIfNeeded && pending != null) {
                pendingOverwriteOnConsent = overwriteCurrentPhoto
                pendingAccountEmailForConsent = email
                _pendingProfileConsentIntent.value = pending
            }
            return null
        }

        val url = resolvePhotoFromAuthorizationResult(authResult)
        if (!url.isNullOrBlank()) {
            applyFetchedGooglePhoto(email, url, overwriteCurrentPhoto)
        }
        return url
    }

    private var pendingOverwriteOnConsent: Boolean = false
    private var pendingAccountEmailForConsent: String? = null

    fun consumePendingProfileConsentIntent() {
        _pendingProfileConsentIntent.value = null
    }

    suspend fun handleProfileConsentResult(activity: Activity, data: Intent?): Boolean {
        _pendingProfileConsentIntent.value = null
        if (data == null) return false
        return runCatching {
            val authResult = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            val url = resolvePhotoFromAuthorizationResult(authResult)
            val targetEmail = pendingAccountEmailForConsent ?: _currentUser.value?.email
            if (!url.isNullOrBlank() && !targetEmail.isNullOrBlank()) {
                applyFetchedGooglePhoto(targetEmail, url, pendingOverwriteOnConsent)
                true
            } else {
                false
            }
        }.getOrElse {
            Log.w(TAG, "Failed handling Google profile consent result: ${it.message}")
            false
        }
    }

    private suspend fun resolvePhotoFromAuthorizationResult(authResult: AuthorizationResult): String? {
        val accountPhoto = authResult.toGoogleSignInAccount()?.photoUrl?.toString()?.takeIf { it.isNotBlank() }
        if (!accountPhoto.isNullOrBlank()) {
            return upgradeGoogleAvatarResolution(accountPhoto)
        }
        val token = authResult.accessToken
        if (!token.isNullOrBlank()) {
            return fetchPictureFromUserInfoEndpoint(token)
        }
        return null
    }

    private suspend fun fetchPictureFromUserInfoEndpoint(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
            conn.disconnect()
            if (code in 200..299 && !body.isNullOrBlank()) {
                val picture = JSONObject(body).optString("picture").takeIf { it.isNotBlank() }
                picture?.let(::upgradeGoogleAvatarResolution)
            } else {
                Log.w(TAG, "userinfo endpoint returned HTTP $code")
                null
            }
        }.getOrElse {
            Log.w(TAG, "userinfo fetch failed: ${it.message}")
            null
        }
    }

    private suspend fun applyFetchedGooglePhoto(
        email: String,
        url: String,
        overwriteCurrentPhoto: Boolean
    ) {
        withContext(Dispatchers.Main) {
            val current = _currentUser.value ?: return@withContext
            if (!current.email.equals(email, ignoreCase = true)) return@withContext
            val accountKey = email.trim().lowercase()
            val savedPhotoPref = userDbPrefs.getString("photo_$accountKey", null)
            val nextPhotoUrl = when {
                overwriteCurrentPhoto -> url
                savedPhotoPref == REMOVED_PHOTO_SENTINEL -> null
                current.photoUrl.isNullOrBlank() -> url
                else -> current.photoUrl
            }
            saveUser(current.copy(photoUrl = nextPhotoUrl, googlePhotoUrl = url))
        }
    }

    /**
     * Copies a user-selected image into app internal storage and sets it strictly as the local Yomi
     * app profile photo. Never modifies or uploads anything to the user's Google account.
     */
    suspend fun updateProfilePhoto(sourceUri: Uri): Result<GoogleUser> = withContext(Dispatchers.IO) {
        val current = _currentUser.value
            ?: return@withContext Result.failure(IllegalStateException("Please sign in before uploading a profile picture"))

        try {
            val avatarsDir = File(context.filesDir, "profile_avatars").apply { mkdirs() }
            val accountKey = current.email.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_")
            avatarsDir.listFiles { f -> f.name.startsWith("${accountKey}_") }?.forEach { it.delete() }

            val destFile = File(avatarsDir, "${accountKey}_${System.currentTimeMillis()}.jpg")
            val opened = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IllegalStateException("Could not read selected image"))

            opened.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!destFile.exists() || destFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Selected image file was empty"))
            }

            val localUri = Uri.fromFile(destFile).toString()
            userDbPrefs.edit()
                .putString("photo_${current.email.trim().lowercase()}", localUri)
                .apply()

            val updated = current.copy(photoUrl = localUri)
            withContext(Dispatchers.Main) {
                saveUser(updated)
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resetToGooglePhoto(): GoogleUser? {
        val current = _currentUser.value ?: return null
        val googlePhoto = current.googlePhotoUrl ?: return current
        userDbPrefs.edit()
            .remove("photo_${current.email.trim().lowercase()}")
            .apply()
        val updated = current.copy(photoUrl = googlePhoto)
        saveUser(updated)
        return updated
    }

    /**
     * Clears the profile photo inside Yomi only (falls back to initials). Does not modify Google.
     */
    fun removeProfilePhoto(): GoogleUser? {
        val current = _currentUser.value ?: return null
        val accountKey = current.email.trim().lowercase()
        val sanitizedKey = accountKey.replace(Regex("[^a-z0-9._-]"), "_")
        val avatarsDir = File(context.filesDir, "profile_avatars")
        if (avatarsDir.exists()) {
            avatarsDir.listFiles { f -> f.name.startsWith("${sanitizedKey}_") }?.forEach { it.delete() }
        }
        userDbPrefs.edit()
            .putString("photo_$accountKey", REMOVED_PHOTO_SENTINEL)
            .apply()
        val updated = current.copy(photoUrl = null)
        saveUser(updated)
        return updated
    }

    /**
     * Updates the display name stored locally in Yomi without modifying the user's Google Account.
     */
    fun updateDisplayName(newName: String): Result<GoogleUser> {
        val current = _currentUser.value
            ?: return Result.failure(IllegalStateException("Please sign in to edit your profile name"))
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Display name cannot be empty"))
        }
        val accountKey = current.email.trim().lowercase()
        userDbPrefs.edit()
            .putString("display_name_$accountKey", cleanName)
            .apply()
        val updated = current.copy(displayName = cleanName)
        saveUser(updated)
        return Result.success(updated)
    }

    private fun resolveGoogleProfilePhotoUrl(credential: GoogleIdTokenCredential): String? {
        val raw = credential.profilePictureUri?.toString()?.takeIf { it.isNotBlank() }
            ?: extractPictureFromIdToken(credential.idToken)
            ?: return null

        return upgradeGoogleAvatarResolution(raw)
    }

    private fun upgradeGoogleAvatarResolution(url: String): String {
        return url.replace("=s96-c", "=s400-c")
    }

    private fun extractPictureFromIdToken(idToken: String?): String? {
        if (idToken.isNullOrBlank()) return null
        return runCatching {
            val parts = idToken.split('.')
            if (parts.size < 2) return null
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            json.optString("picture").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun signInLocally(email: String, name: String) {
        val user = GoogleUser(
            email = email.trim(),
            displayName = name.trim().ifBlank { email.substringBefore("@") },
            idToken = null,
            photoUrl = null,
            isGoogleAccount = false
        )
        saveUser(user)
    }

    fun saveUser(user: GoogleUser) {
        prefs.edit()
            .putString("user_email", user.email)
            .putString("user_name", user.displayName)
            .putString("user_photo", user.photoUrl)
            .putString("user_id_token", user.idToken)
            .putBoolean("is_google_account", user.isGoogleAccount)
            .putString("google_photo", user.googlePhotoUrl)
            .putBoolean("is_signed_in", true)
            .apply()
        _currentUser.value = user
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _pendingProfileConsentIntent.value = null
        _currentUser.value = null
    }

    /**
     * Obtains an OAuth2 access token for Google Drive API operations using native Android AccountManager.
     */
    suspend fun getDriveAccessToken(accountEmail: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accountManager = AccountManager.get(context)
            val account = Account(accountEmail, "com.google")
            val bundle = accountManager.getAuthToken(
                account,
                "oauth2:https://www.googleapis.com/auth/drive.file",
                null,
                false,
                null,
                null
            ).result
            val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
            if (token != null) {
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("Unable to retrieve Google Drive token for $accountEmail"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GoogleAuthManager"
        private const val REMOVED_PHOTO_SENTINEL = "__REMOVED__"
        const val WEB_CLIENT_ID = "23087873700-l1rv7kbnqkhksi9f9jvsttm8bsjks6io.apps.googleusercontent.com"
        const val ANDROID_CLIENT_ID = "23087873700-or5i6har5llcvtqlfgmd599a52t816qj.apps.googleusercontent.com"
        const val CLIENT_ID = WEB_CLIENT_ID
        const val PROJECT_ID = "yomi-reader"
    }
}
