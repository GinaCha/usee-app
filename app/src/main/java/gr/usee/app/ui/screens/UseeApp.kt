package gr.usee.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import gr.usee.app.R
import gr.usee.app.auth.AuthRepository
import gr.usee.app.auth.LoginCredentials
import gr.usee.app.auth.LoginResult
import gr.usee.app.ui.theme.UseeOfficialAppTheme
import kotlinx.coroutines.launch

/**
 * Root app composable.
 *
 * The app starts on the login screen and switches to the hello component only after a
 * successful backend login request returns.
 * 
 * @author Georgia Chatzimarkaki
 */
@Composable
fun UseeApp(
    authRepository: AuthRepository = remember { AuthRepository() }
) {
    var authenticatedUsername by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (authenticatedUsername == null) {
            LoginRoute(
                authRepository = authRepository,
                onLoginSuccess = { username -> authenticatedUsername = username }
            )
        } else {
            HelloScreen(
                username = authenticatedUsername.orEmpty(),
                onLogout = { authenticatedUsername = null }
            )
        }
    }
}

@Composable
private fun LoginRoute(
    authRepository: AuthRepository,
    onLoginSuccess: (String) -> Unit
) {
    var uiState by rememberSaveable(stateSaver = LoginUiState.Saver) { mutableStateOf(LoginUiState()) }
    val scope = rememberCoroutineScope()

    fun submit() {
        // Validate locally first so the user gets immediate feedback before the network call.
        val validatedState = uiState.validated()
        uiState = validatedState

        if (!validatedState.isSubmittable) {
            return
        }

        scope.launch {
            uiState = validatedState.copy(isLoading = true, errorMessage = null)

            when (
                val result = authRepository.login(
                    LoginCredentials(
                        username = validatedState.username,
                        password = validatedState.password
                    )
                )
            ) {
                is LoginResult.Success -> {
                    uiState = uiState.copy(isLoading = false, errorMessage = null)
                    onLoginSuccess(result.displayName)
                }

                is LoginResult.Failure -> {
                    uiState = uiState.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    LoginScreen(
        uiState = uiState,
        onUsernameChange = { username ->
            uiState = uiState.copy(
                username = username,
                usernameError = null,
                errorMessage = null
            )
        },
        onPasswordChange = { password ->
            uiState = uiState.copy(
                password = password,
                passwordError = null,
                errorMessage = null
            )
        },
        onSubmit = ::submit
    )
}

@Composable
private fun LoginScreen(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Scaffold { innerPadding ->
        val logoSize = 150.dp
        val logoHalfHeight = 75.dp
        val horizontalPagePadding = 24.dp
        val availableWidth = LocalConfiguration.current.screenWidthDp.dp - (horizontalPagePadding * 2)
        val formWidth = if (availableWidth < 700.dp) availableWidth else 600.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = horizontalPagePadding, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(formWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = logoHalfHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .shadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp))
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(start = 20.dp, end = 20.dp, top = logoHalfHeight + 12.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.login_title),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(id = R.string.login_subtitle),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.username,
                            onValueChange = onUsernameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(id = R.string.login_username_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            enabled = !uiState.isLoading,
                            isError = uiState.usernameError != null,
                            supportingText = uiState.usernameError?.let { message ->
                                { Text(text = message) }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                                autoCorrectEnabled = false
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(id = R.string.login_password_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            enabled = !uiState.isLoading,
                            isError = uiState.passwordError != null,
                            supportingText = uiState.passwordError?.let { message ->
                                { Text(text = message) }
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() })
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = uiState.errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Button(
                            onClick = onSubmit,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.isSubmittable
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(text = stringResource(id = R.string.login_button))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-70).dp)
                            .size(logoSize + 10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                    )

                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(id = R.string.login_logo_content_description),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-80).dp)
                            .size(logoSize+ 40.dp)

                    )
                }
            }
        }
    }
}

@Composable
private fun HelloScreen(
    username: String,
    onLogout: () -> Unit
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
                Text(
                    text = stringResource(id = R.string.hello_title, username),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.hello_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                TextButton(onClick = onLogout) {
                    Text(text = stringResource(id = R.string.logout_button))
                }
            }
        }
    }
}

private data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null
) {
    val isSubmittable: Boolean
        get() = !isLoading && username.trim().isNotBlank() && password.isNotBlank()

    companion object {
        /**
         * Stores the login state using Bundle-friendly primitives so the form survives
         * configuration changes and process recreation.
         */
        val Saver: Saver<LoginUiState, Any> = Saver(
            save = { state ->
                listOf(
                    state.username,
                    state.password,
                    state.isLoading,
                    state.errorMessage,
                    state.usernameError,
                    state.passwordError
                )
            },
            restore = { restored ->
                val values = restored as? List<*> ?: return@Saver LoginUiState()
                LoginUiState(
                    username = values.getOrNull(0) as? String ?: "",
                    password = values.getOrNull(1) as? String ?: "",
                    isLoading = values.getOrNull(2) as? Boolean ?: false,
                    errorMessage = values.getOrNull(3) as? String,
                    usernameError = values.getOrNull(4) as? String,
                    passwordError = values.getOrNull(5) as? String
                )
            }
        )
    }
}

private fun LoginUiState.validated(): LoginUiState {
    val trimmedUsername = username.trim()
    return copy(
        username = trimmedUsername,
        usernameError = if (trimmedUsername.isBlank()) "Username is required." else null,
        passwordError = if (password.isBlank()) "Password is required." else null
    )
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    UseeOfficialAppTheme {
        LoginScreen(
            uiState = LoginUiState(),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HelloScreenPreview() {
    UseeOfficialAppTheme {
        HelloScreen(
            username = "gina",
            onLogout = {}
        )
    }
}
