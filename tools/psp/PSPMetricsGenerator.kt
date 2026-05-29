import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import org.json.JSONArray

object PSPMetricsGenerator {
    fun generate(rootDir: File) {
        println("=== [PSPMetricsGenerator] Starting Metrics Ingestion ===")
        val healthFile = File(rootDir, "benchmark/reports/project_health.json")
        val metricsFile = File(rootDir, "benchmark/reports/psp_metrics.json")
        
        var pspVersion = "1.0"
        var lastRuntimeAudit = "UNKNOWN"
        var pspStatus = "UNKNOWN"
        var testsPassed = 0
        var testsFailed = 0
        
        if (healthFile.exists()) {
            try {
                val json = JSONObject(healthFile.readText(Charsets.UTF_8))
                pspVersion = json.optString("psp_version", "1.0")
                lastRuntimeAudit = json.optString("last_runtime_audit", "UNKNOWN")
                pspStatus = json.optString("psp_status", "UNKNOWN")
                testsPassed = json.optInt("tests_passed", 0)
                testsFailed = json.optInt("tests_failed", 0)
            } catch (e: Exception) {
                println("[WARN] Failed to parse project health file for metrics generation: ${e.message}")
            }
        }
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        
        val lastSync = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val metricsJson = JSONObject().apply {
            put("schema_version", "1.0")
            put("generation_mode", "AUTOMATED")
            put("generated_at", timestamp)
            put("psp_version", pspVersion)
            put("last_sync", lastSync)
            put("last_runtime_audit", lastRuntimeAudit)
            put("psp_status", pspStatus)
            put("tests_passed", testsPassed)
            put("tests_failed", testsFailed)
            put("data_sources", JSONArray().apply {
                put("gradle_test_report")
                put("project_health")
            })
        }
        
        metricsFile.writeText(metricsJson.toString(2), Charsets.UTF_8)
        println("Saved metrics to: ${metricsFile.absolutePath}")
    }
}
