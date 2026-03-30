package gr.usee.app.ui.screens

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import gr.usee.app.BuildConfig
import java.util.Locale
import gr.usee.app.R
import gr.usee.app.auth.AuthRepository
import gr.usee.app.auth.LoginCredentials
import gr.usee.app.auth.LoginResult
import gr.usee.app.auth.SavedCredential
import gr.usee.app.auth.SecureCredentialsStore
import gr.usee.app.i18n.SupportedLanguages
import gr.usee.app.ui.theme.UseeOfficialAppTheme
import kotlinx.coroutines.delay
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

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var selectedLanguageCode by rememberSaveable {
        mutableStateOf(prefs.getString("selected_language", SupportedLanguages.currentLanguageCode()) ?: SupportedLanguages.currentLanguageCode())
    }
    val updatePolicy = remember { currentUpdatePolicy() }
    var showUpdateDialog by rememberSaveable { mutableStateOf(updatePolicy.isUpdateAvailable) }

    val localizedContext = remember(selectedLanguageCode) {
        val locale = Locale.forLanguageTag(selectedLanguageCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }

    CompositionLocalProvider(LocalContext provides localizedContext) {
        if (showUpdateDialog) {
            ForceUpdateDialog(
                isStoreUpdate = updatePolicy.isStoreUpdate,
                onUpdateFromStore = {
                    openPlayStoreListing(localizedContext)
                },
                onSilentRestart = {
                    restartApplication(localizedContext)
                }
            )

            if (!updatePolicy.isStoreUpdate) {
                LaunchedEffect(updatePolicy.isStoreUpdate) {
                    delay(SILENT_UPDATE_WAIT_MS)
                    showUpdateDialog = false
                    restartApplication(localizedContext)
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (authenticatedUsername == null) {
                LoginRoute(
                    authRepository = authRepository,
                    onLoginSuccess = { username -> authenticatedUsername = username },
                    selectedLanguageCode = selectedLanguageCode,
                    onLanguageChange = { code ->
                        selectedLanguageCode = code
                        prefs.edit().putString("selected_language", code).apply()
                    }
                )
            } else {
                HelloScreen(
                    username = authenticatedUsername.orEmpty(),
                    onLogout = { authenticatedUsername = null }
                )
            }
        }
    }
}

/**
 * Forced update popup shown when a newer app version is marked as available.
 *
 * @author Georgia Chatzimarkaki
 */
@Composable
private fun ForceUpdateDialog(
    isStoreUpdate: Boolean,
    onUpdateFromStore: () -> Unit,
    onSilentRestart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stringResource(id = R.string.update_dialog_title))
        },
        text = {
            Text(
                text = if (isStoreUpdate) {
                    stringResource(id = R.string.update_dialog_store_message)
                } else {
                    stringResource(id = R.string.update_dialog_silent_message)
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isStoreUpdate) {
                        onUpdateFromStore()
                    } else {
                        onSilentRestart()
                    }
                }
            ) {
                Text(
                    text = if (isStoreUpdate) {
                        stringResource(id = R.string.update_dialog_update_now)
                    } else {
                        stringResource(id = R.string.update_dialog_restart_now)
                    }
                )
            }
        }
    )
}

@Composable
private fun LoginRoute(
    authRepository: AuthRepository,
    onLoginSuccess: (String) -> Unit,
    selectedLanguageCode: String,
    onLanguageChange: (String) -> Unit
) {
    var uiState by rememberSaveable(stateSaver = LoginUiState.Saver) { mutableStateOf(LoginUiState()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialsStore = remember(context) { SecureCredentialsStore(context.applicationContext) }
    var savedCredentials by remember { mutableStateOf(credentialsStore.getAll()) }
    val usernameRequiredMessage = stringResource(id = R.string.login_username_required_error)
    val passwordRequiredMessage = stringResource(id = R.string.login_password_required_error)

    fun submit(username: String = uiState.username, password: String = uiState.password) {
        val candidateState = uiState.copy(username = username, password = password)
        val validatedState = candidateState.validated(
            usernameRequiredMessage = usernameRequiredMessage,
            passwordRequiredMessage = passwordRequiredMessage
        )
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
                    credentialsStore.save(
                        username = validatedState.username,
                        password = validatedState.password
                    )
                    savedCredentials = credentialsStore.getAll()
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
        onSubmit = { submit() },
        savedCredentials = savedCredentials,
        onQuickLogin = { credential -> submit(credential.username, credential.password) },
        selectedLanguageCode = selectedLanguageCode,
        onLanguageChange = onLanguageChange
    )
}

@Composable
private fun LoginScreen(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    savedCredentials: List<SavedCredential>,
    onQuickLogin: (SavedCredential) -> Unit,
    selectedLanguageCode: String,
    onLanguageChange: (String) -> Unit
) {
    Scaffold { innerPadding ->
        val context = LocalContext.current
        val logoSize = 150.dp
        val logoHalfHeight = 75.dp
        val horizontalPagePadding = 24.dp
        val availableWidth = LocalConfiguration.current.screenWidthDp.dp - (horizontalPagePadding * 2)
        val formWidth = if (availableWidth < 700.dp) availableWidth else 600.dp
        var languageMenuExpanded by remember { mutableStateOf(false) }
        var quickLoginExpanded by remember { mutableStateOf(false) }
        val selectedLanguage = SupportedLanguages.byCode(selectedLanguageCode) ?: SupportedLanguages.default

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
                    .padding(bottom = 28.dp)
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

                        Box(modifier = Modifier.fillMaxWidth()) {
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
                                trailingIcon = {
                                    if (savedCredentials.isNotEmpty()) {
                                        Text(
                                            text = "▼",
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .clickable(enabled = !uiState.isLoading) {
                                                    quickLoginExpanded = true
                                                },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next,
                                    autoCorrectEnabled = false
                                )
                            )

                            DropdownMenu(
                                expanded = quickLoginExpanded,
                                onDismissRequest = { quickLoginExpanded = false },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                savedCredentials.forEach { credential ->
                                    DropdownMenuItem(
                                        text = { Text(text = credential.username) },
                                        onClick = {
                                            quickLoginExpanded = false
                                            onQuickLogin(credential)
                                        }
                                    )
                                }
                            }
                        }

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

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-12).dp, y = (logoHalfHeight + 14.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable(enabled = !uiState.isLoading) {
                                    languageMenuExpanded = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = selectedLanguage.flagEmoji)
                        }

                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false }
                        ) {
                            SupportedLanguages.all.forEach { language ->
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(text = language.flagEmoji)
                                            Text(text = language.nativeName)
                                        }
                                    },
                                    onClick = {
                                        languageMenuExpanded = false
                                        onLanguageChange(language.code)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(id = R.string.app_version, BuildConfig.VERSION_NAME),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )

            Text(
                text = stringResource(id = R.string.privacy_policy_link_label),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .clickable(enabled = !uiState.isLoading) {
                        openExternalUrl(
                            context = context,
                            url = PRIVACY_POLICY_URL
                        )
                    },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Opens an external browser for a URL used by legal links (e.g. privacy policy).
 *
 * @author Georgia Chatzimarkaki
 */
private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

/**
 * Opens this app's listing page in Play Store.
 *
 * @author Georgia Chatzimarkaki
 */
private fun openPlayStoreListing(context: Context) {
    val packageName = context.packageName
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(webIntent)
    }
}

/**
 * Restarts the application process after a silent update completes.
 *
 * @author Georgia Chatzimarkaki
 */
private fun restartApplication(context: Context) {
    val packageManager = context.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launchIntent)
    Runtime.getRuntime().exit(0)
}

/**
 * Computes the update strategy based on BuildConfig values.
 *
 * @author Georgia Chatzimarkaki
 */
private fun currentUpdatePolicy(): UpdatePolicy {
    val isUpdateAvailable = BuildConfig.LATEST_AVAILABLE_VERSION_CODE > BuildConfig.VERSION_CODE
    return UpdatePolicy(
        isUpdateAvailable = isUpdateAvailable,
        isStoreUpdate = BuildConfig.IS_STORE_UPDATE
    )
}

private data class UpdatePolicy(
    val isUpdateAvailable: Boolean,
    val isStoreUpdate: Boolean
)

private const val SILENT_UPDATE_WAIT_MS = 30_000L

private const val PRIVACY_POLICY_URL = "https://usee.gr/privacy-policy"

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

private fun LoginUiState.validated(
    usernameRequiredMessage: String,
    passwordRequiredMessage: String
): LoginUiState {
    val trimmedUsername = username.trim()
    return copy(
        username = trimmedUsername,
        usernameError = if (trimmedUsername.isBlank()) usernameRequiredMessage else null,
        passwordError = if (password.isBlank()) passwordRequiredMessage else null
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
            onSubmit = {},
            savedCredentials = emptyList(),
            onQuickLogin = {},
            selectedLanguageCode = "en",
            onLanguageChange = {}
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
