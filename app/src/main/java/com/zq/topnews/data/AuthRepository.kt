package com.zq.topnews.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** The signed-in account. The mock implementation only needs these two fields. */
data class AuthUser(val account: String, val nickname: String)

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data class Failure(val message: String) : AuthResult
}

/**
 * Authentication boundary of the app. [MockAuthRepository] is the current
 * implementation; a future backend client can replace it without touching the UI.
 */
interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>

    fun login(account: String, password: String): AuthResult

    fun register(
        nickname: String,
        account: String,
        password: String,
        confirmPassword: String,
    ): AuthResult

    fun logout()
}

/** Input rules shared by the form and the mock repository. */
object AuthValidation {
    private val ACCOUNT_PATTERN = Regex("^[A-Za-z0-9._@+-]{3,30}$")

    fun nicknameError(value: String): String? = when {
        value.isBlank() -> "请输入昵称"
        value.trim().length !in 2..16 -> "昵称需为 2-16 个字符"
        else -> null
    }

    fun accountError(value: String): String? = when {
        value.isBlank() -> "请输入账号"
        !ACCOUNT_PATTERN.matches(value.trim()) -> "请输入 3-30 位手机号或邮箱"
        else -> null
    }

    fun passwordError(value: String): String? = when {
        value.isBlank() -> "请输入密码"
        value.length !in 6..32 -> "密码需为 6-32 位字符"
        else -> null
    }

    fun loginPasswordError(value: String): String? =
        if (value.isBlank()) "请输入密码" else null

    fun confirmPasswordError(password: String, confirm: String): String? = when {
        confirm.isBlank() -> "请再次输入密码"
        confirm != password -> "两次输入的密码不一致"
        else -> null
    }
}

/** The demo account shown on the login screen; it is not persisted. */
object DemoAccount {
    const val ACCOUNT = "demo"
    const val PASSWORD = "123456"
    const val NICKNAME = "头条体验官"
}

/** Simple key-value abstraction so the mock repository stays unit-testable. */
interface AuthStore {
    fun read(): String?
    fun write(value: String?)
}

class SharedPreferencesAuthStore(context: Context) : AuthStore {
    private val prefs =
        context.getSharedPreferences("topnews_auth", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(value: String?) {
        prefs.edit {
            if (value == null) remove(KEY) else putString(KEY, value)
        }
    }

    private companion object {
        const val KEY = "state"
    }
}

/** Used by previews and unit tests; keeps the auth state for one process only. */
class InMemoryAuthStore(initialValue: String? = null) : AuthStore {
    var value: String? = initialValue
        private set

    override fun read(): String? = value

    override fun write(value: String?) {
        this.value = value
    }
}

/**
 * Offline mock auth: accounts and the current session live in [store].
 * Passwords are stored as a salted SHA-256 hash rather than plain text.
 */
class MockAuthRepository(
    private val store: AuthStore,
    private val random: SecureRandom = SecureRandom(),
) : AuthRepository {

    private val accounts = LinkedHashMap<String, StoredAccount>()
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    init {
        restore()
    }

    override fun login(account: String, password: String): AuthResult {
        AuthValidation.accountError(account)?.let { return AuthResult.Failure(it) }
        if (password.isBlank()) return AuthResult.Failure("请输入密码")

        val normalized = account.trim()
        if (normalized.equals(DemoAccount.ACCOUNT, ignoreCase = true)) {
            return if (password == DemoAccount.PASSWORD) {
                enter(AuthUser(DemoAccount.ACCOUNT, DemoAccount.NICKNAME))
            } else {
                AuthResult.Failure("账号或密码错误")
            }
        }

        val stored = accounts[keyOf(normalized)]
            ?: return AuthResult.Failure("账号或密码错误")
        if (stored.passwordHash != hash(password, stored.salt)) {
            return AuthResult.Failure("账号或密码错误")
        }
        return enter(AuthUser(stored.account, stored.nickname))
    }

    override fun register(
        nickname: String,
        account: String,
        password: String,
        confirmPassword: String,
    ): AuthResult {
        AuthValidation.nicknameError(nickname)?.let { return AuthResult.Failure(it) }
        AuthValidation.accountError(account)?.let { return AuthResult.Failure(it) }
        AuthValidation.passwordError(password)?.let { return AuthResult.Failure(it) }
        AuthValidation.confirmPasswordError(password, confirmPassword)
            ?.let { return AuthResult.Failure(it) }

        val normalized = account.trim()
        if (normalized.equals(DemoAccount.ACCOUNT, ignoreCase = true)) {
            return AuthResult.Failure("该账号为演示账号，请使用其他账号")
        }
        if (accounts.containsKey(keyOf(normalized))) {
            return AuthResult.Failure("该账号已注册，请直接登录")
        }

        val salt = randomSalt()
        val stored = StoredAccount(
            account = normalized,
            nickname = nickname.trim(),
            salt = salt,
            passwordHash = hash(password, salt),
        )
        accounts[keyOf(normalized)] = stored
        return enter(AuthUser(stored.account, stored.nickname))
    }

    override fun logout() {
        _currentUser.value = null
        persist()
    }

    private fun enter(user: AuthUser): AuthResult {
        _currentUser.value = user
        persist()
        return AuthResult.Success(user)
    }

    private fun persist() {
        val json = JSONObject()
        val list = JSONArray()
        accounts.values.forEach { account ->
            list.put(
                JSONObject()
                    .put("account", account.account)
                    .put("nickname", account.nickname)
                    .put("salt", account.salt)
                    .put("hash", account.passwordHash),
            )
        }
        json.put("accounts", list)
        _currentUser.value?.let { json.put("current", it.account) }
        store.write(json.toString())
    }

    private fun restore() {
        val raw = store.read() ?: return
        runCatching {
            val json = JSONObject(raw)
            val list = json.optJSONArray("accounts") ?: JSONArray()
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val stored = StoredAccount(
                    account = item.optString("account"),
                    nickname = item.optString("nickname"),
                    salt = item.optString("salt"),
                    passwordHash = item.optString("hash"),
                )
                if (stored.account.isNotBlank() && stored.passwordHash.isNotBlank()) {
                    accounts[keyOf(stored.account)] = stored
                }
            }
            when {
                json.optString("current")
                    .equals(DemoAccount.ACCOUNT, ignoreCase = true) -> {
                    _currentUser.value = AuthUser(DemoAccount.ACCOUNT, DemoAccount.NICKNAME)
                }
                else -> accounts[keyOf(json.optString("current"))]?.let {
                    _currentUser.value = AuthUser(it.account, it.nickname)
                }
            }
        }
    }

    private fun keyOf(account: String): String = account.lowercase(Locale.ROOT)

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    private fun hash(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + password).toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte) }

    private data class StoredAccount(
        val account: String,
        val nickname: String,
        val salt: String,
        val passwordHash: String,
    )
}
