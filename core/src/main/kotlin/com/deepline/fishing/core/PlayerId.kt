package com.deepline.fishing.core

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Who a player is on the battle server, without an account: a public id (shown to opponents, so
 * they can send a gift) and a secret only this device knows. Together they make the recovery
 * code, which restores a cloud save on a new phone.
 */
data class PlayerId(val id: String, val secret: String) {
    /** "DL-ABCD-EFGH-…": the id, then the secret, in groups of four. */
    val recoveryCode: String get() = "DL-" + (id + secret).chunked(4).joinToString("-")

    companion object {
        /** Letters and digits that can't be mistaken for one another. */
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val ID_LENGTH = 8
        const val SECRET_LENGTH = 16

        fun isValid(id: String) = id.length == ID_LENGTH && id.all { it in ALPHABET }

        fun isValidSecret(secret: String) = secret.length == SECRET_LENGTH && secret.all { it in ALPHABET }

        fun random(rnd: SecureRandom = SecureRandom()): PlayerId {
            fun word(n: Int) = String(CharArray(n) { ALPHABET[rnd.nextInt(ALPHABET.length)] })
            return PlayerId(word(ID_LENGTH), word(SECRET_LENGTH))
        }

        /** Reads a recovery code typed by the player (dashes, spaces and case don't matter). */
        fun fromRecoveryCode(code: String): PlayerId? {
            var clean = code.uppercase().filter { it in ALPHABET }
            if (clean.length == ID_LENGTH + SECRET_LENGTH + 2 && clean.startsWith("DL")) clean = clean.drop(2)
            if (clean.length != ID_LENGTH + SECRET_LENGTH) return null
            return PlayerId(clean.take(ID_LENGTH), clean.drop(ID_LENGTH))
        }

        /** What the server keeps instead of the secret. */
        fun hash(secret: String): String =
            MessageDigest.getInstance("SHA-256").digest(("deepline:" + secret).toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
