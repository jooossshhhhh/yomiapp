package com.yomi.reader.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPasswordHasherTest {

    @Test
    fun hashPassword_producesUniqueSaltedPbkdf2Hashes() {
        val hashA = LocalPasswordHasher.hashPassword("Secret123")
        val hashB = LocalPasswordHasher.hashPassword("Secret123")

        assertTrue(LocalPasswordHasher.isHashed(hashA))
        assertTrue(LocalPasswordHasher.isHashed(hashB))
        assertNotEquals(hashA, hashB)
        assertFalse(hashA.contains("Secret123"))
    }

    @Test
    fun verifyPassword_acceptsCorrectPasswordAndRejectsWrongPassword() {
        val stored = LocalPasswordHasher.hashPassword("MyPass!42")

        assertTrue(LocalPasswordHasher.verifyPassword("MyPass!42", stored))
        assertFalse(LocalPasswordHasher.verifyPassword("WrongPass", stored))
        assertFalse(LocalPasswordHasher.verifyPassword("MyPass!42", "pbkdf2\$120000\$invalid"))
    }

    @Test
    fun verifyPassword_supportsLegacyPlaintextVerificationForMigration() {
        assertFalse(LocalPasswordHasher.isHashed("legacyPlain"))
        assertTrue(LocalPasswordHasher.verifyPassword("legacyPlain", "legacyPlain"))
        assertFalse(LocalPasswordHasher.verifyPassword("other", "legacyPlain"))
    }
}

