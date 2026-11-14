package com.yomi.reader.ui.auth

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.yomi.reader.auth.GoogleUser
import com.yomi.reader.data.local.PreferencesManager
import com.yomi.reader.ui.components.YOMI_BRAND_NAME
import com.yomi.reader.ui.components.YOMI_TAGLINE
import com.yomi.reader.ui.components.YomiLogo
import com.yomi.reader.ui.settings.ProfileSettingsContent
import com.yomi.reader.ui.theme.yomiPaperBackground

private val GoogleBlue = Color(0xFF4285F4)
private val GoogleBlueDark = Color(0xFF1A73E8)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)
private val ValidationGreen = Color(0xFF1E8E3E)
private val ValidationAmber = Color(0xFFF59E0B)

@Composable
fun AuthScreen(
    currentUser: GoogleUser?,
    preferencesManager: PreferencesManager,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onLocalSignIn: (username: String, password: String, isNewAccount: Boolean, name: String, photoUri: Uri?) -> Unit,
    onGoogleSignInClick: () -> Unit = {},
    onUploadPhoto: (Uri) -> Unit = {},
    onResetToGooglePhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    onUpdateDisplayName: (String) -> Unit = {},
    onSignOut: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onPickDriveFiles: (List<Uri>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .yomiPaperBackground(),
        color = Color.Transparent
    ) {
        if (currentUser != null) {
            ProfileSettingsContent(
                user = currentUser,
                preferencesManager = preferencesManager,
                onUploadPhoto = onUploadPhoto,
                onResetToGooglePhoto = onResetToGooglePhoto,
                onRemovePhoto = onRemovePhoto,
                onUpdateDisplayName = onUpdateDisplayName,
                onSignOut = onSignOut,
                onPickDriveFiles = onPickDriveFiles,
                onBack = onContinueAsGuest
            )
        } else {
            AuthFormContent(
                isLoading = isLoading,
                errorMessage = errorMessage,
                onClearError = onClearError,
                onLocalSignIn = onLocalSignIn,
                onGoogleSignInClick = onGoogleSignInClick,
                onBack = onContinueAsGuest
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AuthFormContent(
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onLocalSignIn: (username: String, password: String, isNewAccount: Boolean, name: String, photoUri: Uri?) -> Unit,
    onGoogleSignInClick: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var nameTouched by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var confirmTouched by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }

    val nameValidation = remember(name) { AuthFormValidator.validateFullName(name) }
    val emailValidation = remember(email) { AuthFormValidator.validateUsernameOrEmail(email) }
    val signInPasswordValidation = remember(password) { AuthFormValidator.validateSignInPassword(password) }
    val passwordAnalysis = remember(password) { AuthFormValidator.analyzePassword(password) }
    val confirmValidation = remember(password, confirmPassword) {
        AuthFormValidator.validateConfirmPassword(password, confirmPassword)
    }

    val isSignUpTab = selectedTab == 1
    val isFormValid = if (isSignUpTab) {
        nameValidation.isValid &&
            emailValidation.isValid &&
            passwordAnalysis.isValidForSignUp &&
            confirmValidation.isValid
    } else {
        emailValidation.isValid && signInPasswordValidation.isValid
    }

    val scrollState = rememberScrollState()
    val passwordSectionRequester = remember { BringIntoViewRequester() }
    var isPasswordFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isSignUpTab, isPasswordFocused, password.isNotEmpty(), imeVisible) {
        if (isSignUpTab && isPasswordFocused && password.isNotEmpty()) {
            delay(100)
            passwordSectionRequester.bringIntoView()
            delay(260)
            passwordSectionRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back Navigation Button at top
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Welcome to Yomi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // App Logo
        YomiLogo(size = 84.dp, shapeRadius = 24.dp)

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = YOMI_BRAND_NAME,
            style = MaterialTheme.typography.headlineMedium,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = YOMI_TAGLINE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            FeatureBadge(label = "PDF Manager")
            Spacer(modifier = Modifier.width(8.dp))
            FeatureBadge(label = "Google Drive")
            Spacer(modifier = Modifier.width(8.dp))
            FeatureBadge(label = "Auto-Convert to PDF")
        }

        Spacer(modifier = Modifier.height(22.dp))

        if (!errorMessage.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onClearError,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tabs: Sign In / Create Account
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    if (selectedTab != 0) {
                        selectedTab = 0
                        confirmPassword = ""
                        confirmTouched = false
                        attemptedSubmit = false
                        onClearError()
                    }
                },
                text = { Text("Sign In", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    if (selectedTab != 1) {
                        selectedTab = 1
                        attemptedSubmit = false
                        onClearError()
                    }
                },
                text = { Text("Create Account", fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Full Name (Create Account only)
        if (isSignUpTab) {
            var nameHadFocus by remember { mutableStateOf(false) }
            val showNameError = (nameTouched || attemptedSubmit) && !nameValidation.isValid

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameTouched = true
                },
                label = { Text("Full Name") },
                placeholder = { Text("Enter your full name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                trailingIcon = {
                    FieldStatusTrailingIcon(
                        isValid = name.isNotEmpty() && nameValidation.isValid,
                        isError = showNameError
                    )
                },
                isError = showNameError,
                supportingText = if (showNameError && nameValidation.errorMessage != null) {
                    { Text(nameValidation.errorMessage) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            nameHadFocus = true
                        } else if (nameHadFocus) {
                            nameTouched = true
                        }
                    }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Username or Email
        var emailHadFocus by remember { mutableStateOf(false) }
        val showEmailError = (emailTouched || attemptedSubmit) && !emailValidation.isValid

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailTouched = true
            },
            label = { Text("Username or Email") },
            placeholder = { Text("Enter username or email") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            trailingIcon = {
                FieldStatusTrailingIcon(
                    isValid = email.isNotEmpty() && emailValidation.isValid,
                    isError = showEmailError
                )
            },
            isError = showEmailError,
            supportingText = if (showEmailError && emailValidation.errorMessage != null) {
                { Text(emailValidation.errorMessage) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        emailHadFocus = true
                    } else if (emailHadFocus) {
                        emailTouched = true
                    }
                }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Password / Enter Password + Inline Password Strength Checker
        var passwordHadFocus by remember { mutableStateOf(false) }
        val showPasswordError = if (isSignUpTab) {
            (passwordTouched || attemptedSubmit) && !passwordAnalysis.isValidForSignUp
        } else {
            (passwordTouched || attemptedSubmit) && !signInPasswordValidation.isValid
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(passwordSectionRequester)
        ) {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordTouched = true
                },
                label = { Text(if (isSignUpTab) "Enter Password" else "Password") },
                placeholder = { Text("Enter your password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                isError = showPasswordError,
                supportingText = if (!isSignUpTab && showPasswordError && signInPasswordValidation.errorMessage != null) {
                    { Text(signInPasswordValidation.errorMessage) }
                } else {
                    null
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isSignUpTab) ImeAction.Next else ImeAction.Done
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        isPasswordFocused = focus.isFocused
                        if (focus.isFocused) {
                            passwordHadFocus = true
                        } else if (passwordHadFocus) {
                            passwordTouched = true
                        }
                    }
            )

            // Hidden when empty; expands smoothly & stays scrolled above keyboard while typing
            AnimatedVisibility(visible = isSignUpTab && password.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PasswordStrengthChecker(
                        analysis = passwordAnalysis,
                        passwordEmpty = false
                    )
                }
            }
        }

        // Confirm Password (Create Account only)
        if (isSignUpTab) {
            Spacer(modifier = Modifier.height(12.dp))

            var confirmHadFocus by remember { mutableStateOf(false) }
            val showConfirmFeedback = confirmTouched || attemptedSubmit || confirmPassword.isNotEmpty()
            val showConfirmError = showConfirmFeedback && !confirmValidation.isValid
            val showConfirmMatch = confirmPassword.isNotEmpty() && confirmValidation.isValid

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    confirmTouched = true
                },
                label = { Text("Confirm Password") },
                placeholder = { Text("Re-enter your password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showConfirmMatch) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Passwords match",
                                tint = ValidationGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) "Hide confirm password" else "Show confirm password"
                            )
                        }
                    }
                },
                isError = showConfirmError,
                supportingText = when {
                    showConfirmError && confirmValidation.errorMessage != null -> {
                        { Text(confirmValidation.errorMessage) }
                    }
                    showConfirmMatch -> {
                        {
                            Text(
                                text = "Passwords match",
                                color = ValidationGreen
                            )
                        }
                    }
                    else -> null
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            confirmHadFocus = true
                        } else if (confirmHadFocus) {
                            confirmTouched = true
                        }
                    }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Action Button: Sign In or Create Account
        Button(
            onClick = {
                attemptedSubmit = true
                if (isFormValid) {
                    val displayName = if (isSignUpTab && name.isNotBlank()) {
                        name.trim()
                    } else {
                        email.trim().substringBefore("@")
                    }
                    onLocalSignIn(
                        email.trim(),
                        password.trim(),
                        isSignUpTab,
                        displayName,
                        null
                    )
                }
            },
            enabled = isFormValid && !isLoading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = if (isSignUpTab) "Create Account" else "Sign In",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "  or  ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sign in with Google button (below Sign In / Create Account, styled with Google's color palette)
        GooglePaletteSignInButton(
            isLoading = isLoading,
            onClick = onGoogleSignInClick
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun FieldStatusTrailingIcon(
    isValid: Boolean,
    isError: Boolean
) {
    when {
        isError -> Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Invalid input",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        isValid -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Valid input",
            tint = ValidationGreen,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PasswordStrengthChecker(
    analysis: PasswordAnalysis,
    passwordEmpty: Boolean
) {
    val accentColor by animateColorAsState(
        targetValue = when (analysis.strength) {
            PasswordStrength.EMPTY -> MaterialTheme.colorScheme.outlineVariant
            PasswordStrength.WEAK -> GoogleRed
            PasswordStrength.FAIR -> ValidationAmber
            PasswordStrength.GOOD -> GoogleBlueDark
            PasswordStrength.STRONG -> ValidationGreen
        },
        label = "passwordStrengthColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = if (!passwordEmpty) accentColor.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password strength",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = analysis.strength.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (passwordEmpty) MaterialTheme.colorScheme.onSurfaceVariant else accentColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(4) { index ->
                val active = !passwordEmpty && index < analysis.strength.level
                val barColor by animateColorAsState(
                    targetValue = if (active) accentColor
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    label = "strengthBar$index"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PasswordRuleItem(
                    label = "8+ characters",
                    met = analysis.hasMinLength,
                    hasInput = !passwordEmpty,
                    modifier = Modifier.weight(1f)
                )
                PasswordRuleItem(
                    label = "Uppercase (A-Z)",
                    met = analysis.hasUppercase,
                    hasInput = !passwordEmpty,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PasswordRuleItem(
                    label = "Lowercase (a-z)",
                    met = analysis.hasLowercase,
                    hasInput = !passwordEmpty,
                    modifier = Modifier.weight(1f)
                )
                PasswordRuleItem(
                    label = "Number (0-9)",
                    met = analysis.hasDigit,
                    hasInput = !passwordEmpty,
                    modifier = Modifier.weight(1f)
                )
            }
            PasswordRuleItem(
                label = "Special char (!@#\$...) for Strong",
                met = analysis.hasSpecialChar,
                hasInput = !passwordEmpty,
                isBonus = true
            )
            AnimatedVisibility(visible = !passwordEmpty && !analysis.hasNoSpaces) {
                PasswordRuleItem(
                    label = "No spaces allowed",
                    met = false,
                    hasInput = true,
                    forceError = true
                )
            }
        }
    }
}

@Composable
private fun PasswordRuleItem(
    label: String,
    met: Boolean,
    hasInput: Boolean,
    isBonus: Boolean = false,
    forceError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tint = when {
        forceError -> GoogleRed
        met -> ValidationGreen
        hasInput && !isBonus -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                forceError -> Icons.Default.Close
                met -> Icons.Default.Check
                else -> Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (met || forceError) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun GooglePaletteSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color(0xFFF8F9FA),
            disabledContentColor = Color(0xFF5F6368)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 1.5.dp,
            pressedElevation = 0.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = GoogleBlue
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Connecting to Google...",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F1F1F)
            )
        } else {
            GoogleLogoIcon()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF1F1F1F))) {
                        append("Sign in with ")
                    }
                    withStyle(SpanStyle(color = GoogleBlueDark, fontWeight = FontWeight.Bold)) {
                        append("Google")
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FeatureBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun GoogleLogoIcon() {
    Surface(
        modifier = Modifier.size(22.dp),
        shape = CircleShape,
        color = Color.White
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        ) {
            val strokeWidth = size.minDimension * 0.24f
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

            // Red top arc
            drawArc(
                color = GoogleRed,
                startAngle = 205f,
                sweepAngle = 112f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Yellow left arc
            drawArc(
                color = GoogleYellow,
                startAngle = 140f,
                sweepAngle = 68f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Green bottom arc
            drawArc(
                color = GoogleGreen,
                startAngle = 40f,
                sweepAngle = 103f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Blue right lower arc
            drawArc(
                color = GoogleBlue,
                startAngle = -5f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Blue horizontal bar
            val cy = size.height / 2f
            drawLine(
                color = GoogleBlue,
                start = Offset(size.width * 0.52f, cy),
                end = Offset(size.width * 0.98f, cy),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt
            )
        }
    }
}
