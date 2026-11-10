package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelInstallState
import com.corelink.frame.ai.models.ModelMetadata
import java.io.File
import java.security.MessageDigest

/**
 * Pure install-state evaluation (no Android Context).
 */
object ModelInstallEvaluator {

    fun evaluate(
        metadata: ModelMetadata,
        dir: File,
        files: List<File>,
        totalBytes: Long,
        nowMs: Long = System.currentTimeMillis(),
        checksumVerifier: (File, String) -> Boolean = ::defaultVerifyChecksum
    ): ModelInstallState {
        if (files.isEmpty()) {
            return ModelInstallState(metadata.id, InstallState.NOT_PRESENT)
        }

        val expected = metadata.expectedFiles
        if (expected.isNotEmpty()) {
            val missing = expected.filter { name -> files.none { it.name == name } }
            if (missing.isNotEmpty()) {
                return ModelInstallState(
                    modelId = metadata.id,
                    state = InstallState.CORRUPT,
                    localPath = dir.absolutePath,
                    bytesOnDisk = totalBytes,
                    validationError = "Missing expected files: ${missing.joinToString()}"
                )
            }
            val primary = files.firstOrNull { it.name == expected.first() }
            if (primary != null && !metadata.checksumSha256.isNullOrBlank()) {
                if (!checksumVerifier(primary, metadata.checksumSha256)) {
                    return ModelInstallState(
                        modelId = metadata.id,
                        state = InstallState.CORRUPT,
                        localPath = dir.absolutePath,
                        bytesOnDisk = totalBytes,
                        validationError = "SHA-256 mismatch for ${primary.name}"
                    )
                }
                return ModelInstallState(
                    modelId = metadata.id,
                    state = InstallState.INSTALLED,
                    localPath = dir.absolutePath,
                    bytesOnDisk = totalBytes,
                    lastValidatedAt = nowMs,
                    checksumUnverified = false
                )
            }
            return ModelInstallState(
                modelId = metadata.id,
                state = InstallState.INSTALLED,
                localPath = dir.absolutePath,
                bytesOnDisk = totalBytes,
                lastValidatedAt = nowMs,
                checksumUnverified = true,
                validationError = "Installed without checksum verification"
            )
        }

        if (totalBytes < 1_000_000L) {
            return ModelInstallState(
                modelId = metadata.id,
                state = InstallState.CORRUPT,
                localPath = dir.absolutePath,
                bytesOnDisk = totalBytes,
                validationError = "Model directory contains insufficient data and no expectedFiles list"
            )
        }
        return ModelInstallState(
            modelId = metadata.id,
            state = InstallState.INSTALLED,
            localPath = dir.absolutePath,
            bytesOnDisk = totalBytes,
            lastValidatedAt = nowMs,
            checksumUnverified = true,
            validationError = "Installed via size heuristic only (no expectedFiles)"
        )
    }

    fun defaultVerifyChecksum(file: File, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }
}
