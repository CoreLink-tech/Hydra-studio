package com.corelink.frame.ai.download

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 checks for downloaded model files. Compares lowercase hex only. */
object Integrity {
    private const val BUFFER_BYTES = 1 shl 16

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun matches(actual: String, expected: String): Boolean =
        expected.isNotBlank() && actual.equals(expected.trim(), ignoreCase = true)
}
