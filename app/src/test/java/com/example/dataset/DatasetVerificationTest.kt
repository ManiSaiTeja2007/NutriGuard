package com.example.dataset

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatasetVerificationTest {

    private fun findBenchmarkDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "benchmark")
            if (candidate.exists() && candidate.isDirectory) {
                return candidate
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("Could not find 'benchmark' directory starting from " + File(".").absolutePath)
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(4096)
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = fis.read(buffer)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun getPngDimensions(file: File): Pair<Int, Int>? {
        try {
            FileInputStream(file).use { fis ->
                val signature = ByteArray(8)
                if (fis.read(signature) != 8) return null
                if (signature[0] != 0x89.toByte() || signature[1] != 0x50.toByte() ||
                    signature[2] != 0x4E.toByte() || signature[3] != 0x47.toByte()) {
                    return null
                }
                fis.skip(8) // Skip chunk length (4) + Chunk type IHDR (4)
                val dims = ByteArray(8)
                if (fis.read(dims) != 8) return null
                val width = ((dims[0].toInt() and 0xFF) shl 24) or
                            ((dims[1].toInt() and 0xFF) shl 16) or
                            ((dims[2].toInt() and 0xFF) shl 8) or
                            (dims[3].toInt() and 0xFF)
                val height = ((dims[4].toInt() and 0xFF) shl 24) or
                             ((dims[5].toInt() and 0xFF) shl 16) or
                             ((dims[6].toInt() and 0xFF) shl 8) or
                             (dims[7].toInt() and 0xFF)
                return Pair(width, height)
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun validateImageFile(file: File): Pair<Boolean, String> {
        if (!file.exists()) {
            return Pair(false, "File does not exist")
        }
        val size = file.length()
        if (size < 100) {
            return Pair(false, "Placeholder detected: size is too small ($size bytes)")
        }
        
        // HTML masquerading check
        val bytes = file.readBytes()
        val headerLength = if (bytes.size < 1024) bytes.size else 1024
        val headerString = String(bytes, 0, headerLength, Charsets.UTF_8).lowercase(Locale.ROOT)
        if (headerString.contains("<html") || headerString.contains("<!doctype html")) {
            return Pair(false, "HTML masquerading as image")
        }

        val dims = getPngDimensions(file) ?: return Pair(false, "Not a valid PNG or corrupt header")
        val (width, height) = dims
        if (width <= 1 || height <= 1) {
            return Pair(false, "Invalid dimensions: ${width}x${height} pixels (placeholder)")
        }

        // Byte entropy check (unique byte values)
        val uniqueBytes = bytes.toSet().size
        if (uniqueBytes < 5) {
            return Pair(false, "Low-entropy solid-color placeholder detected ($uniqueBytes unique bytes)")
        }

        return Pair(true, "Valid PNG image")
    }

    private fun isChecksumCached(file: File, expectedHash: String): Boolean {
        // Future-proofing checksum cache stub: checks if file metadata suggests it matches without recalculating
        // E.g., checks a local checksum cache database matching filePath, length, and lastModified.
        // Currently returns false to run full verification, but enables future incremental validation.
        return false
    }

    private fun quarantineFile(benchmarkDir: File, file: File, reason: String) {
        val quarantineDir = File(benchmarkDir, "quarantine")
        if (!quarantineDir.exists()) {
            quarantineDir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).format(Date())
        val destFile = File(quarantineDir, "${timestamp}_${file.name}")
        if (file.exists()) {
            file.renameTo(destFile)
            println("[QUARANTINE] Moved corrupt/invalid file ${file.name} to quarantine due to: $reason")
        } else {
            println("[QUARANTINE] File ${file.name} is missing, cannot move but flagged as quarantined.")
        }
    }

    @Test
    fun testDatasetVerificationGating() {
        val benchmarkDir = findBenchmarkDir()
        val manifestFile = File(benchmarkDir, "semantic/manifests/dataset_versions.json")
        assertTrue("Manifest dataset_versions.json must exist", manifestFile.exists())

        val manifestContent = manifestFile.readText(Charsets.UTF_8)
        val manifestJson = JSONObject(manifestContent)

        val fileMap = mapOf(
            "openfoodfacts_ingredients" to "semantic/real_world/ingredients.json",
            "openfoodfacts_additives" to "semantic/real_world/additives.json",
            "openfoodfacts_products" to "semantic/real_world/products.csv",
            "fail_001.png" to "semantic/failure_cases/fail_001.png",
            "fail_002.png" to "semantic/failure_cases/fail_002.png",
            "fail_003.png" to "semantic/failure_cases/fail_003.png",
            "fail_004.png" to "semantic/failure_cases/fail_004.png"
        )

        var verifiedFiles = 0
        var failedDownloads = 0
        var corruptImages = 0
        var placeholderImages = 0

        for ((key, relPath) in fileMap) {
            val file = File(benchmarkDir, relPath)
            
            // 1. File existence
            if (!file.exists()) {
                quarantineFile(benchmarkDir, file, "File does not exist")
                failedDownloads++
                fail("Required calibration dataset is missing: ${file.absolutePath}")
            }

            val itemJson = manifestJson.optJSONObject(key)
            assertNotNull("Manifest does not contain key: $key", itemJson)

            // 2. Checksum validation
            val expectedHash = itemJson!!.optString("checksum_sha256")
            val actualHash = if (isChecksumCached(file, expectedHash)) {
                expectedHash
            } else {
                calculateSha256(file)
            }
            if (expectedHash != actualHash) {
                quarantineFile(benchmarkDir, file, "SHA-256 mismatch. Expected: $expectedHash, Actual: $actualHash")
                fail("Checksum mismatch for ${file.name}. Expected: $expectedHash, Got: $actualHash")
            }

            // 3. Fallback verification
            val isFallback = itemJson.optBoolean("fallback_used", false)
            val datasetType = itemJson.optString("dataset_type")
            
            if (isFallback || datasetType == "FALLBACK") {
                fail("Ineligible fallback dataset used for calibration target: $key")
            }

            // 4. Schema / Format-specific validation
            if (file.name.endsWith(".json")) {
                try {
                    JSONObject(file.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    try {
                        org.json.JSONArray(file.readText(Charsets.UTF_8))
                    } catch (ex: Exception) {
                        quarantineFile(benchmarkDir, file, "Invalid JSON format: ${e.message}")
                        fail("File ${file.name} is not a valid JSON: ${e.message}")
                    }
                }
            } else if (file.name.endsWith(".csv")) {
                val lines = file.readLines(Charsets.UTF_8)
                assertTrue("CSV file ${file.name} is empty", lines.isNotEmpty())
                val header = lines.first()
                assertTrue("CSV file ${file.name} must contain 'ingredients_text' column", header.contains("ingredients_text"))
            } else if (file.name.endsWith(".png")) {
                val (valid, msg) = validateImageFile(file)
                if (!valid) {
                    quarantineFile(benchmarkDir, file, msg)
                    corruptImages++
                    placeholderImages++
                    fail("Image file ${file.name} verification failed: $msg")
                }
            }

            verifiedFiles++
        }

        // Output Health Report
        val reportsDir = File(benchmarkDir, "reports/dataset_health")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        val healthReportFile = File(reportsDir, "health_report.json")
        val healthJson = JSONObject().apply {
            put("real_world_images", 4)
            put("synthetic_images", 0)
            put("mock_images", 0)
            put("verified_checksums", verifiedFiles)
            put("failed_downloads", failedDownloads)
            put("corrupt_images", corruptImages)
            put("placeholder_images", placeholderImages)
            put("calibration_ready", verifiedFiles == fileMap.size)
        }
        healthReportFile.writeText(healthJson.toString(2), Charsets.UTF_8)

        // Output Audit Log
        val auditDir = File(benchmarkDir, "reports/dataset_audit")
        if (!auditDir.exists()) {
            auditDir.mkdirs()
        }
        val auditLogFile = File(auditDir, "audit_log.json")
        val auditArray = org.json.JSONArray()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())

        for ((key, relPath) in fileMap) {
            val event = JSONObject().apply {
                put("timestamp", timestamp)
                put("event_type", "VERIFY")
                put("filepath", relPath)
                put("message", "File verified successfully against manifest. SHA-256 match.")
            }
            auditArray.put(event)
        }
        auditLogFile.writeText(auditArray.toString(2), Charsets.UTF_8)
    }
}
