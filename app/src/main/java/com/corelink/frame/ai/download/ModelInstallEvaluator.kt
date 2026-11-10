package com.corelink.frame.ai.download

import com.corelink.frame.ai.models.InstallState
import com.corelink.frame.ai.models.ModelFile
import com.corelink.frame.ai.models.ModelInstallState
import com.corelink.frame.ai.models.ModelMetadata
import java.io.File
import java.security.MessageDigest

/**
 * Pure install-state evaluation (no Android Context).
 *
 * Rules:
 * - metadata.files empty → NOT_PRESENT (pack not installable yet).
 * - dir empty / no matching files → NOT_PRESENT.
 * - some expected files missing → PARTIAL (with missingFiles list).
 * - file present but wrong size or SHA-256 → CORRUPT.
 * - all present and checks pass → INSTALLED.
 * - checksumUnverified stays true only when at least one file had no sha256
 *   in metadata (legacy-style entry). Prefer full checksums.
 */
object ModelInstallEvaluator {

    fun evaluate(
        metadata: ModelMetadata,
        dir: File,
        filesOnDisk: List<File>,
        totalBytes: Long,
        nowMs: Long = System.currentTimeMillis(),
        checksumVerifier: (File, String) -> Boolean = ::defaultVerifyChecksum
    ): ModelInstallState {
        val expected: List<ModelFile> = metadata.files

        // Catalogue entry with no file list cannot be installed yet.
        if (expected.isEmpty()) {
            return if (filesOnDisk.isEmpty()) {
                ModelInstallState(metadata.id, InstallState.NOT_PRESENT)
            } else {
                // Orphan files under the model dir with no catalogue definition.
                ModelInstallState(
                    modelId = metadata.id,
                    state = InstallState.CORRUPT,
                    localPath = dir.absolutePath,
                    bytesOnDisk = totalBytes,
                    validationError = "Model directory has files but catalogue entry has empty files[] (not installable yet)"
                )
            }
        }

        if (filesOnDisk.isEmpty()) {
            return ModelInstallState(
                modelId = metadata.id,
                state = InstallState.NOT_PRESENT,
                missingFiles = expected.map { it.name }
            )
        }

        val byName = filesOnDisk.associateBy { it.name }
        val missing = mutableListOf<String>()
        val corrupt = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var anyChecksumMissing = false
        var presentCount = 0

        for (spec in expected) {
            val onDisk = byName[spec.name]
            if (onDisk == null || !onDisk.isFile) {
                missing += spec.name
                continue
            }
            presentCount++

            if (spec.sizeBytes > 0L && onDisk.length() != spec.sizeBytes) {
                corrupt += spec.name
                errors += "${spec.name}: size ${onDisk.length()} != expected ${spec.sizeBytes}"
                continue
            }

            if (spec.hasChecksum) {
                if (!checksumVerifier(onDisk, spec.sha256!!)) {
                    corrupt += spec.name
                    errors += "${spec.name}: SHA-256 mismatch"
                }
            } else {
                anyChecksumMissing = true
            }
        }

        return when {
            presentCount == 0 -> ModelInstallState(
                modelId = metadata.id,
                state = InstallState.NOT_PRESENT,
                localPath = dir.absolutePath,
                bytesOnDisk = totalBytes,
                missingFiles = missing
            )
            missing.isNotEmpty() && corrupt.isEmpty() -> ModelInstallState(
                modelId = metadata.id,
                state = InstallState.PARTIAL,
                localPath = dir.absolutePath,
                bytesOnDisk = totalBytes,
                validationError = "Missing files: ${missing.joinToString()}",
                missingFiles = missing
            )
            corrupt.isNotEmpty() -> ModelInstallState(
                modelId = metadata.id,
                state = InstallState.CORRUPT,
                localPath = dir.absolutePath,
                bytesOnDisk = totalBytes,
                validationError = errors.joinToString("; "),
                missingFiles = missing,
                corruptFiles = corrupt
            )
            else -> ModelInstallState(
                modelId = metadata.id,
                state = InstallState.INSTALLED,
                localPath = dir.absolutePath,
                bytesOnDisk = totalBytes,
                lastValidatedAt = nowMs,
                checksumUnverified = anyChecksumMissing,
                validationError = if (anyChecksumMissing) {
                    "Installed; one or more files had no SHA-256 in metadata"
                } else null
            )
        }
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
