package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.IngredientEntity
import com.example.domain.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FlaggedWarning(
    val matchedTerm: String,
    val ingredient: IngredientEntity,
    val distance: Int
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.ingredientDao

    private val _rawText = MutableStateFlow("")
    val rawText: StateFlow<String> = _rawText.asStateFlow()

    private val _flaggedWarnings = MutableStateFlow<List<FlaggedWarning>>(emptyList())
    val flaggedWarnings: StateFlow<List<FlaggedWarning>> = _flaggedWarnings.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private var dbIngredientsCached: List<IngredientEntity> = emptyList()

    init {
        // Pre-populate the database with the 5 dangerous ingredients
        viewModelScope.launch(Dispatchers.IO) {
            val count = dao.getAllIngredients().size
            if (count == 0) {
                val dangerousIngredients = listOf(
                    IngredientEntity(
                        name = "Maltodextrin",
                        riskLevel = "HIGH",
                        reason = "Spikes blood sugar extremely rapidly and can negatively alter gut bacteria."
                    ),
                    IngredientEntity(
                        name = "Aspartame",
                        riskLevel = "MODERATE",
                        reason = "Artificial sweetener linked to headaches, mood shifts, and gut microbiome disruption."
                    ),
                    IngredientEntity(
                        name = "Sodium Nitrite",
                        riskLevel = "HIGH",
                        reason = "Meat preservative that forms carcinogenic compounds (nitrosamines) under high heat."
                    ),
                    IngredientEntity(
                        name = "High Fructose Corn Syrup",
                        riskLevel = "HIGH",
                        reason = "Processed sweetener linked to visceral fat accumulation, obesity, and fatty liver disease."
                    ),
                    IngredientEntity(
                        name = "Carrageenan",
                        riskLevel = "MODERATE",
                        reason = "Seaweed-derived thickener known to cause gastrointestinal inflammation and IBS symptoms."
                    )
                )
                dao.insertIngredients(dangerousIngredients)
            }
            dbIngredientsCached = dao.getAllIngredients()
        }
    }

    fun processScannedText(rawText: String) {
        if (rawText.isBlank()) return
        _rawText.value = rawText

        viewModelScope.launch(Dispatchers.Default) {
            _isAnalyzing.value = true

            if (dbIngredientsCached.isEmpty()) {
                dbIngredientsCached = withContext(Dispatchers.IO) { dao.getAllIngredients() }
            }

            // Parse text by lines, symbols, brackets, or commas to extract individual components
            val parsedSegments = rawText.split(Regex("[,;\\(\\)\\.\\n:\\x5B\\x5D]"))
                .map { it.trim() }
                .filter { it.length >= 3 }

            // Also split by spaces to capture individual words
            val parsedWords = rawText.split(Regex("[^A-Za-z0-9]+"))
                .map { it.trim() }
                .filter { it.length >= 3 }

            val candidates = (parsedSegments + parsedWords).distinct()

            val warningsMap = mutableMapOf<Int, FlaggedWarning>()

            for (ingredient in dbIngredientsCached) {
                var bestWarningForThisIngredient: FlaggedWarning? = null

                for (candidate in candidates) {
                    val distance = FuzzyMatcher.calculateDistance(candidate, ingredient.name)
                    // Determine threshold based on length to prevent false matches on tiny string snippets
                    val maxDistance = when {
                        ingredient.name.length <= 4 -> 0
                        ingredient.name.length <= 6 -> 1
                        else -> 2
                    }

                    if (distance <= maxDistance) {
                        val newWarning = FlaggedWarning(
                            matchedTerm = candidate,
                            ingredient = ingredient,
                            distance = distance
                        )
                        if (bestWarningForThisIngredient == null || distance < bestWarningForThisIngredient.distance) {
                            bestWarningForThisIngredient = newWarning
                        }
                    }
                }

                if (bestWarningForThisIngredient != null) {
                    warningsMap[ingredient.id] = bestWarningForThisIngredient
                }
            }

            _flaggedWarnings.value = warningsMap.values.toList().sortedByDescending { it.ingredient.riskLevel == "HIGH" }
            _isAnalyzing.value = false
        }
    }

    fun clearScannedData() {
        _rawText.value = ""
        _flaggedWarnings.value = emptyList()
    }
}
