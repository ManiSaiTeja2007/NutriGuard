package com.example.core.ocr.reconstruction

import android.graphics.Rect
import com.example.core.ocr.OCRLine
import com.example.core.ocr.OCRWord

object OCRLineReconstructor {

    /**
     * Groups fragmented OCR words into coherent lines.
     *
     * @param words The raw, unordered list of OCRWords detected.
     * @param lineThresholdY The multiplier of line height for vertical overlap tolerance.
     * @param wordGapXMultiplier The multiplier of average word height to decide if they are in the same word/cluster.
     */
    fun reconstruct(
        words: List<OCRWord>,
        lineThresholdY: Float = 0.5f,
        wordGapXMultiplier: Float = 1.5f
    ): List<OCRLine> {
        if (words.isEmpty()) return emptyList()

        // 1. Group words into horizontal lines based on vertical overlap/proximity
        val lines = mutableListOf<MutableList<OCRWord>>()
        
        // Sort words by vertical center to process top-to-bottom
        val sortedByY = words.sortedBy { it.bounds.centerY() }
        
        for (word in sortedByY) {
            val centerY = word.bounds.centerY()
            
            // Find a line where the word's center falls within vertical tolerance
            var matchedLine: MutableList<OCRWord>? = null
            for (line in lines) {
                val avgCenterY = line.map { it.bounds.centerY() }.average().toFloat()
                val avgHeight = line.map { it.bounds.height() }.average().toFloat()
                val tolerance = avgHeight * lineThresholdY
                if (Math.abs(centerY - avgCenterY) <= tolerance) {
                    matchedLine = line
                    break
                }
            }
            
            if (matchedLine != null) {
                matchedLine.add(word)
            } else {
                lines.add(mutableListOf(word))
            }
        }
        
        // 2. Sort words within each line from left to right, and merge close fragments
        val reconstructedLines = mutableListOf<OCRLine>()
        for (lineWords in lines) {
            val sortedLineWords = lineWords.sortedBy { it.bounds.left }
            val mergedWords = mutableListOf<OCRWord>()
            
            for (word in sortedLineWords) {
                if (mergedWords.isEmpty()) {
                    mergedWords.add(word)
                } else {
                    val lastWord = mergedWords.last()
                    val gap = word.bounds.left - lastWord.bounds.right
                    val avgHeight = (word.bounds.height() + lastWord.bounds.height()) / 2f
                    
                    // Spatial clustering: if the gap is extremely small, merge their texts directly
                    // This fixes OCR fragmentation (e.g. "gredier", "ents:" -> "ingredients:")
                    if (gap <= avgHeight * 0.15f) {
                        val mergedText = lastWord.text + word.text
                        val mergedBounds = Rect(
                            lastWord.bounds.left,
                            minOf(lastWord.bounds.top, word.bounds.top),
                            maxOf(lastWord.bounds.right, word.bounds.right),
                            maxOf(lastWord.bounds.bottom, word.bounds.bottom)
                        )
                        val avgConfidence = (lastWord.confidence + word.confidence) / 2f
                        mergedWords[mergedWords.lastIndex] = OCRWord(mergedText, avgConfidence, mergedBounds)
                    } else {
                        mergedWords.add(word)
                    }
                }
            }
            
            if (mergedWords.isNotEmpty()) {
                val lineLeft = mergedWords.minOf { it.bounds.left }
                val lineTop = mergedWords.minOf { it.bounds.top }
                val lineRight = mergedWords.maxOf { it.bounds.right }
                val lineBottom = mergedWords.maxOf { it.bounds.bottom }
                val lineBounds = Rect(lineLeft, lineTop, lineRight, lineBottom)
                val lineConfidence = mergedWords.map { it.confidence }.average().toFloat()
                
                reconstructedLines.add(OCRLine(mergedWords, lineBounds, lineConfidence))
            }
        }
        
        // 3. Sort the lines from top to bottom
        return reconstructedLines.sortedBy { it.bounds.top }
    }
}
