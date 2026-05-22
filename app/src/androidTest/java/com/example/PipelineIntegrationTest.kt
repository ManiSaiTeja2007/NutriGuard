package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.data.AppDatabase
import com.example.data.IngredientEntity
import com.example.domain.FuzzyMatcher
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PipelineIntegrationTest {

    @get:org.junit.Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private lateinit var db: AppDatabase
    private lateinit var testIngredients: List<IngredientEntity>

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Use an in-memory database for testing
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

        // Seed with standard dangerous ingredients
        testIngredients = listOf(
            IngredientEntity(name = "Maltodextrin", riskLevel = "HIGH", reason = "Spikes blood sugar"),
            IngredientEntity(name = "Aspartame", riskLevel = "MODERATE", reason = "Artificial sweetener"),
            IngredientEntity(name = "Sodium Nitrite", riskLevel = "HIGH", reason = "Meat preservative"),
            IngredientEntity(name = "High Fructose Corn Syrup", riskLevel = "HIGH", reason = "Processed sweetener"),
            IngredientEntity(name = "Carrageenan", riskLevel = "MODERATE", reason = "Causes inflammation")
        )

        val dao = db.ingredientDao
        // Since it's an in-memory DB and we need it seeded
        runBlocking {
            dao.insertIngredients(testIngredients)
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun runFullPipelineOnTestLabels() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetManager = context.assets
        val testLabelsDir = "test_labels"

        val images = assetManager.list(testLabelsDir) ?: emptyArray()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val reportBuilder = StringBuilder()
        reportBuilder.append("<html><body><h1>NutriGuard Pipeline Report</h1>")
        reportBuilder.append("<table border='1'><tr><th>Image File Name</th><th>Raw OCR Text</th><th>Detected Warnings</th></tr>")

        for (imageFileName in images) {
            if (!imageFileName.lowercase().endsWith(".bmp") &&
                !imageFileName.lowercase().endsWith(".jpg") &&
                !imageFileName.lowercase().endsWith(".jpeg") &&
                !imageFileName.lowercase().endsWith(".png")) continue

            val inputStream: InputStream = assetManager.open("$testLabelsDir/$imageFileName")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // Synchronous OCR processing
            val visionText = try {
                Tasks.await(recognizer.process(inputImage), 30, TimeUnit.SECONDS)
            } catch (e: Exception) {
                null
            }

            val rawText = visionText?.text ?: ""
            val detectedWarnings = performMatching(rawText)

            val warningsStr = detectedWarnings.joinToString("<br>") {
                "${it.matchedTerm} -> ${it.ingredient.name} (Dist: ${it.distance}, Risk: ${it.ingredient.riskLevel})"
            }

            reportBuilder.append("<tr>")
            reportBuilder.append("<td>$imageFileName</td>")
            reportBuilder.append("<td>${rawText.replace("\n", "<br>")}</td>")
            reportBuilder.append("<td>$warningsStr</td>")
            reportBuilder.append("</tr>")
        }

        reportBuilder.append("</table></body></html>")

        writeReportToDownloads(reportBuilder.toString())
    }

    private fun performMatching(rawText: String): List<TestFlaggedWarning> {
        if (rawText.isBlank()) return emptyList()

        val parsedSegments = rawText.split(Regex($$"[,;\\(\\)\\.\\n:\\x5B\\x5D]"))
            .map { it.trim() }
            .filter { it.length >= 3 }

        val parsedWords = rawText.split(Regex("[^A-Za-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }

        val candidates = (parsedSegments + parsedWords).distinct()
        val warnings = mutableListOf<TestFlaggedWarning>()

        for (ingredient in testIngredients) {
            var bestDistance = Int.MAX_VALUE
            var bestTerm = ""

            for (candidate in candidates) {
                val distance = FuzzyMatcher.calculateDistance(candidate, ingredient.name)
                val maxDistance = when {
                    ingredient.name.length <= 4 -> 0
                    ingredient.name.length <= 6 -> 1
                    else -> 2
                }

                if (distance <= maxDistance && distance < bestDistance) {
                    bestDistance = distance
                    bestTerm = candidate
                }
            }

            if (bestDistance != Int.MAX_VALUE) {
                warnings.add(TestFlaggedWarning(bestTerm, ingredient, bestDistance))
            }
        }
        return warnings
    }

    private fun writeReportToDownloads(htmlContent: String) {
        val fileName = "NutriGuard_Report.html"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir == null) return

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val reportFile = File(downloadsDir, fileName)
        FileOutputStream(reportFile).use { fos ->
            fos.write(htmlContent.toByteArray())
        }
        println("Report written to: ${reportFile.absolutePath}")
    }

    data class TestFlaggedWarning(
        val matchedTerm: String,
        val ingredient: IngredientEntity,
        val distance: Int
    )
}
