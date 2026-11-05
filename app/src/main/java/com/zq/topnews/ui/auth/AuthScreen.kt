package com.zq.topnews.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zq.topnews.data.AuthRepository
import com.zq.topnews.data.AuthResult
import com.zq.topnews.data.AuthUser
import com.zq.topnews.data.AuthValidation
import com.zq.topnews.data.DemoAccount

private enum class AuthMode(val label: String) {
    LOGIN("登录"),
    REGISTER("注册"),
}

/**
 * Login / registration form backed by [AuthRepository]. Successful auth closes
 * the screen through [onAuthenticated]; registration signs the user in directly.
 */
@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onAuthenticated: (AuthUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    var modeName by rememberSaveable { mutableStateOf(AuthMode.LOGIN.name) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var nicknameError by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val mode = AuthMode.valueOf(modeName)
    val focusManager = LocalFocusManager.current

    fun clearErrors() {
        nicknameError = null
        accountError = null
        passwordError = null
        confirmError = null
        formError = null
    }

    fun switchMode(next: AuthMode) {
        if (next == mode) return
        modeName = next.name
        confirmPassword = ""
        clearErrors()
    }

    fun submit() {
        formError = null
        nicknameError =
            if (mode == AuthMode.REGISTER) AuthValidation.nicknameError(nickname) else null
        accountError = AuthValidation.accountError(account)
        passwordError =
            if (mode == AuthMode.REGISTER) {
                AuthValidation.passwordError(password)
            } else {
                AuthValidation.loginPasswordError(password)
            }
        confirmError =
            if (mode == AuthMode.REGISTER) {
                AuthValidation.confirmPasswordError(password, confirmPassword)
            } else {
                null
            }
        if (
            nicknameError != null || accountError != null ||
            passwordError != null || confirmError != null
        ) {
            return
        }

        focusManager.clearFocus()
        val result = when (mode) {
            AuthMode.LOGIN -> authRepository.login(account, password)
            AuthMode.REGISTER ->
                authRepository.register(nickname, account, password, confirmPassword)
        }
        when (result) {
            is AuthResult.Success -> onAuthenticated(result.user)
            is AuthResult.Failure -> formError = result.message
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "欢迎来到 TopNews",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "登录后同步收藏、评论和历史记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            TabRow(
                selectedTabIndex = mode.ordinal,
                containerColor = Color.Transparent,
                divider = {},
            ) {
                AuthMode.entries.forEach { entry ->
                    Tab(
                        selected = entry == mode,
                        onClick = { switchMode(entry) },
                        text = {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight =
                                    if (entry == mode) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            if (mode == AuthMode.REGISTER) {
                AuthField(
                    value = nickname,
                    onValueChange = {
                        nickname = it
                        nicknameError = null
                        formError = null
                    },
                    label = "昵称",
                    placeholder = "2-16 个字符",
                    error = nicknameError,
                )
                Spacer(Modifier.height(12.dp))
            }

            AuthField(
                value = account,
                onValueChange = {
                    account = it
                    accountError = null
                    formError = null
                },
                label = "账号",
                placeholder = "手机号或邮箱",
                error = accountError,
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(12.dp))

            AuthField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                    formError = null
                },
                label = "密码",
                placeholder = if (mode == AuthMode.REGISTER) "6-32 位字符" else "请输入密码",
                error = passwordError,
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible },
                imeAction = if (mode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done,
                onDone = { submit() },
            )

            if (mode == AuthMode.REGISTER) {
                Spacer(Modifier.height(12.dp))
                AuthField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmError = null
                        formError = null
                    },
                    label = "确认密码",
                    placeholder = "请再次输入密码",
                    error = confirmError,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    imeAction = ImeAction.Done,
                    onDone = { submit() },
                )
            }

            if (formError != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = formError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
            ) {
                Text(
                    text = if (mode == AuthMode.LOGIN) "登录" else "注册并登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (mode == AuthMode.LOGIN) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "演示账号：${DemoAccount.ACCOUNT} / ${DemoAccount.PASSWORD}",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "当前为本地 mock 登录，账号与登录状态仅保存在本机。",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: () -> Unit = {},
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        shape = RoundedCornerShape(12.dp),
        visualTransformation =
            if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { onDone() },
        ),
        trailingIcon = if (isPassword) {
            {
                TextButton(onClick = onTogglePassword) {
                    Text(
                        text = if (passwordVisible) "隐藏" else "显示",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        } else {
            null
        },
    )
}
