package com.yomi.reader.ui.auth

enum class PasswordStrength(val label: String, val level: Int) {
    EMPTY("Enter a password", 0),
    WEAK("Weak", 1),
    FAIR("Fair", 2),
    GOOD("Good", 3),
    STRONG("Strong", 4)
}

data class PasswordAnalysis(
    val hasMinLength: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigit: Boolean,
    val hasSpecialChar: Boolean,
    val hasNoSpaces: Boolean,
    val strength: PasswordStrength,
    val isValidForSignUp: Boolean
)

data class FieldValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

object AuthFormValidator {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val USERNAME_REGEX = Regex("^[A-Za-z0-9._-]{3,}$")

    fun validateFullName(name: String): FieldValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> FieldValidationResult(false, "Full name is required")
            trimmed.length < 2 -> FieldValidationResult(false, "Enter at least 2 characters")
            else -> FieldValidationResult(true)
        }
    }

    fun validateUsernameOrEmail(input: String): FieldValidationResult {
        val trimmed = input.trim()
        return when {
            trimmed.isEmpty() -> FieldValidationResult(false, "Username or email is required")
            trimmed.any { it.isWhitespace() } -> FieldValidationResult(false, "Cannot contain spaces")
            trimmed.contains("@") -> {
                if (EMAIL_REGEX.matches(trimmed)) {
                    FieldValidationResult(true)
                } else {
                    FieldValidationResult(false, "Enter a valid email address (e.g. name@example.com)")
                }
            }
            trimmed.length < 3 -> FieldValidationResult(false, "Username must be at least 3 characters")
            !USERNAME_REGEX.matches(trimmed) -> {
                FieldValidationResult(false, "Use only letters, numbers, dots, hyphens, or underscores")
            }
            else -> FieldValidationResult(true)
        }
    }

    fun validateSignInPassword(password: String): FieldValidationResult {
        val trimmed = password.trim()
        return when {
            trimmed.isEmpty() -> FieldValidationResult(false, "Password is required")
            trimmed.length < 4 -> FieldValidationResult(false, "Password must be at least 4 characters")
            else -> FieldValidationResult(true)
        }
    }

    fun analyzePassword(password: String): PasswordAnalysis {
        val hasMinLength = password.length >= 8
        val hasUppercase = password.any { it.isUpperCase() }
        val hasLowercase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        val hasNoSpaces = password.none { it.isWhitespace() }

        val isValidForSignUp = hasMinLength &&
            hasUppercase &&
            hasLowercase &&
            hasDigit &&
            hasNoSpaces

        val strength = when {
            password.isEmpty() -> PasswordStrength.EMPTY
            !hasNoSpaces -> PasswordStrength.WEAK
            else -> {
                val score = listOf(
                    hasMinLength,
                    hasUppercase,
                    hasLowercase,
                    hasDigit,
                    hasSpecialChar || password.length >= 12
                ).count { it }

                when {
                    score <= 2 -> PasswordStrength.WEAK
                    score == 3 -> PasswordStrength.FAIR
                    score == 4 -> PasswordStrength.GOOD
                    else -> PasswordStrength.STRONG
                }
            }
        }

        return PasswordAnalysis(
            hasMinLength = hasMinLength,
            hasUppercase = hasUppercase,
            hasLowercase = hasLowercase,
            hasDigit = hasDigit,
            hasSpecialChar = hasSpecialChar,
            hasNoSpaces = hasNoSpaces,
            strength = strength,
            isValidForSignUp = isValidForSignUp
        )
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): FieldValidationResult {
        return when {
            confirmPassword.isEmpty() -> FieldValidationResult(false, "Please confirm your password")
            confirmPassword != password -> FieldValidationResult(false, "Passwords do not match")
            else -> FieldValidationResult(true)
        }
    }
}

