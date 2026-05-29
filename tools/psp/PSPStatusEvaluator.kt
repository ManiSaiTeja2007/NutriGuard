import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object PSPStatusEvaluator {
    fun evaluate(
        testsFailed: Int,
        testsPassed: Int,
        lastAuditDateStr: String,
        datasetHealthGood: Boolean,
        runtimeConsistencyPass: Boolean,
        currentDate: Date = Date()
    ): Pair<String, String> {
        val rulesDoc = StringBuilder()
        rulesDoc.append("PSP Status Evaluation Rules:\n")
        rulesDoc.append("- RED: testsFailed > 0 OR auditAge > 90 days OR datasetHealthGood == false OR runtimeConsistencyPass == false\n")
        rulesDoc.append("- YELLOW: auditAge is 30-90 days OR testsPassed == 0 (partial verification)\n")
        rulesDoc.append("- GREEN: testsFailed == 0 AND testsPassed > 0 AND auditAge < 30 days AND datasetHealthGood == true AND runtimeConsistencyPass == true\n")

        // Parse audit date to check age
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val auditAgeDays = try {
            val lastAuditDate = format.parse(lastAuditDateStr)
            val diffMs = currentDate.time - lastAuditDate.time
            val days = TimeUnit.MILLISECONDS.toDays(diffMs)
            days
        } catch (e: Exception) {
            999L // default to stale if parsing fails
        }

        val status = when {
            testsFailed > 0 -> "RED"
            !datasetHealthGood -> "RED"
            !runtimeConsistencyPass -> "RED"
            auditAgeDays > 90 -> "RED"
            auditAgeDays in 30..90 -> "YELLOW"
            testsPassed == 0 -> "YELLOW"
            else -> "GREEN"
        }

        val reason = when (status) {
            "RED" -> {
                val reasons = mutableListOf<String>()
                if (testsFailed > 0) reasons.add("$testsFailed failing unit tests")
                if (!datasetHealthGood) reasons.add("dataset calibration verification failed")
                if (!runtimeConsistencyPass) reasons.add("runtime consistency check failed")
                if (auditAgeDays > 90) reasons.add("runtime audit is extremely stale ($auditAgeDays days ago)")
                reasons.joinToString(", ")
            }
            "YELLOW" -> {
                val reasons = mutableListOf<String>()
                if (auditAgeDays in 30..90) reasons.add("runtime audit is stale ($auditAgeDays days ago)")
                if (testsPassed == 0) reasons.add("zero tests executed")
                reasons.joinToString(", ")
            }
            else -> "All verification checks pass successfully. Last audited $auditAgeDays days ago."
        }

        return Pair(status, reason)
    }
}
