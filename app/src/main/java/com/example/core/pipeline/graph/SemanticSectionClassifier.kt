package com.example.core.pipeline.graph

import com.example.core.ocr.OCRLine

class SemanticSectionClassifier : ExecutionStage<Unit, List<ClassifiedSection>> {
    override val stageName: String = "section_classification"

    override suspend fun execute(
        input: Unit,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<List<ClassifiedSection>> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        val lines = context.targetedOcrLines.sortedBy { it.bounds.top }
        val classifiedSections = mutableListOf<ClassifiedSection>()

        if (lines.isEmpty()) {
            val latency = android.os.SystemClock.elapsedRealtime() - started
            return ExecutionStageResult(context.executionId, stageName, emptyList(), latency, emptyMap(), failures)
        }

        var currentType = SectionType.UNKNOWN
        var currentHeader: OCRLine? = null
        val currentBody = mutableListOf<OCRLine>()
        var sectionConfidence = 1.0f
        var currentSource = "default"

        fun commitSection() {
            if (currentBody.isNotEmpty() || currentHeader != null) {
                classifiedSections.add(
                    ClassifiedSection(
                        type = currentType,
                        headerLine = currentHeader,
                        bodyLines = ArrayList(currentBody),
                        confidence = sectionConfidence,
                        classificationSource = currentSource
                    )
                )
                currentBody.clear()
                currentHeader = null
                currentType = SectionType.UNKNOWN
                sectionConfidence = 1.0f
                currentSource = "default"
            }
        }

        for (line in lines) {
            val text = line.words.joinToString(" ") { it.text }.lowercase()
            
            // Check if this line is an anchor header
            val headerType = detectHeaderType(text)
            
            // Also check for inline allergen warnings like "may contain peanut" which should instantly trigger ALLERGENS section
            val isAllergenMarker = text.contains("may contain") || text.contains("allergy advice") || text.contains("contains: ") || text.contains("allergen warning")

            if (headerType != null || isAllergenMarker) {
                if (isAllergenMarker && headerType == null) {
                    if (currentType == SectionType.ALLERGENS) {
                        currentBody.add(line)
                    } else {
                        commitSection()
                        currentType = SectionType.ALLERGENS
                        currentHeader = null
                        currentBody.add(line)
                        currentSource = "inline_marker"
                        commitSection()
                    }
                } else {
                    val nextType = headerType!!
                    if (nextType != currentType) {
                        commitSection()
                        currentType = nextType
                        currentHeader = line
                        currentSource = "keyword_header"
                    } else {
                        currentBody.add(line)
                    }
                }
            } else {
                // Check if layout gap to the last line in current body is too large
                if (currentBody.isNotEmpty()) {
                    val prevLine = currentBody.last()
                    val gap = line.bounds.top - prevLine.bounds.bottom
                    val avgHeight = (line.bounds.height() + prevLine.bounds.height()) / 2f
                    if (gap > avgHeight * 2.5f) {
                        commitSection()
                    }
                }
                currentBody.add(line)
            }
        }
        commitSection()

        context.classifiedSections.addAll(classifiedSections)

        val latency = android.os.SystemClock.elapsedRealtime() - started

        return ExecutionStageResult(
            executionId = context.executionId,
            stageName = stageName,
            output = classifiedSections,
            latencyMs = latency,
            replayArtifacts = mapOf(
                "sectionsCount" to classifiedSections.size,
                "sections" to classifiedSections.map { "${it.type} (lines: ${it.bodyLines.size}, source: ${it.classificationSource})" }
            ),
            failures = failures
        )
    }

    private fun detectHeaderType(text: String): SectionType? {
        return when {
            text.startsWith("ingredients:") || text.startsWith("ingredient list") || text.startsWith("contains:") || text.startsWith("composition:") || text.startsWith("zutaten:") -> SectionType.INGREDIENTS
            text.startsWith("allergy advice:") || text.contains("allergen warning:") || text.startsWith("allergens:") || text.startsWith("allergy info") -> SectionType.ALLERGENS
            text.startsWith("nutrition facts") || text.startsWith("nutritional info") || text.startsWith("typical values") || text.startsWith("nutrition info") -> SectionType.NUTRITION
            text.startsWith("storage:") || text.startsWith("keep ") || text.startsWith("store in") || text.startsWith("storage instructions:") -> SectionType.STORAGE
            text.startsWith("warning:") || text.startsWith("caution:") || text.startsWith("warnings:") || text.startsWith("safety instructions:") -> SectionType.WARNINGS
            text.startsWith("manufactured by") || text.startsWith("distributed by") || text.startsWith("mfd by") -> SectionType.MANUFACTURER
            text.startsWith("new ") || text.startsWith("delicious") || text.startsWith("marketing") -> SectionType.MARKETING
            else -> null
        }
    }
}
