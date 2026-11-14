package com.yomi.reader.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFormValidatorTest {

    @Test
    fun validateFullName_requiresAtLeastTwoNonWhitespaceCharacters() {
        assertFalse(AuthFormValidator.validateFullName("").isValid)
        assertFalse(AuthFormValidator.validateFullName(" A ").isValid)
        assertTrue(AuthFormValidator.validateFullName("Al").isValid)
        assertTrue(AuthFormValidator.validateFullName("Juan Dela Cruz").isValid)
    }

    @Test
    fun validateUsernameOrEmail_validatesUsernamesAndEmails() {
        assertFalse(AuthFormValidator.validateUsernameOrEmail("").isValid)
        assertFalse(AuthFormValidator.validateUsernameOrEmail("ab").isValid)
        assertFalse(AuthFormValidator.validateUsernameOrEmail("user name").isValid)
        assertFalse(AuthFormValidator.validateUsernameOrEmail("user@invalid").isValid)

        assertTrue(AuthFormValidator.validateUsernameOrEmail("yomi_user1").isValid)
        assertTrue(AuthFormValidator.validateUsernameOrEmail("reader@example.com").isValid)
    }

    @Test
    fun analyzePassword_evaluatesStrengthAndRequiredRules() {
        val empty = AuthFormValidator.analyzePassword("")
        assertEquals(PasswordStrength.EMPTY, empty.strength)
        assertFalse(empty.isValidForSignUp)

        val weak = AuthFormValidator.analyzePassword("abc")
        assertEquals(PasswordStrength.WEAK, weak.strength)
        assertFalse(weak.isValidForSignUp)

        val good = AuthFormValidator.analyzePassword("Reader12")
        assertEquals(PasswordStrength.GOOD, good.strength)
        assertTrue(good.isValidForSignUp)
        assertTrue(good.hasMinLength)
        assertTrue(good.hasUppercase)
        assertTrue(good.hasLowercase)
        assertTrue(good.hasDigit)
        assertFalse(good.hasSpecialChar)

        val strong = AuthFormValidator.analyzePassword("Reader12!")
        assertEquals(PasswordStrength.STRONG, strong.strength)
        assertTrue(strong.isValidForSignUp)
        assertTrue(strong.hasSpecialChar)

        val withSpaces = AuthFormValidator.analyzePassword("Reader 12!")
        assertEquals(PasswordStrength.WEAK, withSpaces.strength)
        assertFalse(withSpaces.hasNoSpaces)
        assertFalse(withSpaces.isValidForSignUp)
    }

    @Test
    fun validateConfirmPassword_matchesExactPassword() {
        assertFalse(AuthFormValidator.validateConfirmPassword("Reader12!", "").isValid)
        assertFalse(AuthFormValidator.validateConfirmPassword("Reader12!", "Reader12").isValid)
        assertTrue(AuthFormValidator.validateConfirmPassword("Reader12!", "Reader12!").isValid)
    }

    @Test
    fun validateSignInPassword_allowsExistingMinimumFourCharacters() {
        assertFalse(AuthFormValidator.validateSignInPassword("").isValid)
        assertFalse(AuthFormValidator.validateSignInPassword("123").isValid)
        assertTrue(AuthFormValidator.validateSignInPassword("1234").isValid)
    }
}

