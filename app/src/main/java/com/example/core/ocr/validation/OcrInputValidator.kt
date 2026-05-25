package com.example.core.ocr.validation

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.core.intelligence.correction.FailureType

object OcrInputValidator {
    const val MIN_SIZE = 32
    const val MAX_ALLOCATION_PIXELS = 16_000_000 // 4000x4000 limit to prevent OOM

    data class ValidationResult(
        val isValid: Boolean,
        val failureType: FailureType? = null,
        val message: String? = null
    )

    fun validate(bitmap: Bitmap?): ValidationResult {
        if (bitmap == null) {
            return ValidationResult(false, FailureType.INVALID_BITMAP_FAILURE, "Bitmap is null")
        }
        if (bitmap.isRecycled) {
            return ValidationResult(false, FailureType.INVALID_BITMAP_FAILURE, "Bitmap is already recycled")
        }
        if (bitmap.width < MIN_SIZE || bitmap.height < MIN_SIZE) {
            return ValidationResult(
                false,
                FailureType.INVALID_IMAGE_SIZE_FAILURE,
                "InputImage width and height should be at least $MIN_SIZE. Found: ${bitmap.width}x${bitmap.height}"
            )
        }
        if (bitmap.width * bitmap.height > MAX_ALLOCATION_PIXELS) {
            return ValidationResult(
                false,
                FailureType.INVALID_BITMAP_FAILURE,
                "Bitmap allocation exceeds limit: ${bitmap.width}x${bitmap.height}"
            )
        }
        // Verification of valid allocation / pixel readability
        try {
            // Read a single pixel to check if the memory backing the bitmap is accessible
            bitmap.getPixel(0, 0)
        } catch (e: Exception) {
            return ValidationResult(
                false,
                FailureType.INVALID_BITMAP_FAILURE,
                "Bitmap allocation is invalid or inaccessible: ${e.message}"
            )
        }
        return ValidationResult(true)
    }

    fun validateCrop(bitmap: Bitmap, cropRect: Rect): ValidationResult {
        val basic = validate(bitmap)
        if (!basic.isValid) return basic

        if (cropRect.isEmpty) {
            return ValidationResult(
                false,
                FailureType.INVALID_BITMAP_FAILURE,
                "Crop rect is empty: $cropRect"
            )
        }

        if (cropRect.left < 0 || cropRect.top < 0 || cropRect.right > bitmap.width || cropRect.bottom > bitmap.height) {
            return ValidationResult(
                false,
                FailureType.INVALID_BITMAP_FAILURE,
                "Crop rect $cropRect is outside bitmap bounds (${bitmap.width}x${bitmap.height})"
            )
        }

        if (cropRect.width() < MIN_SIZE || cropRect.height() < MIN_SIZE) {
            return ValidationResult(
                false,
                FailureType.INVALID_IMAGE_SIZE_FAILURE,
                "Crop dimensions must be at least $MIN_SIZE. Found: ${cropRect.width()}x${cropRect.height()}"
            )
        }

        return ValidationResult(true)
    }
}
