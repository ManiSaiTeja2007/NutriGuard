package com.example.utils

import android.content.Context

class TestLabelAssetRepository(
    private val context: Context,
    private val assetDir: String = TEST_LABEL_DIR
) {
    fun listImageNames(): List<String> {
        return context.assets.list(assetDir)
            ?.filter { name ->
                name.endsWith(".jpg", ignoreCase = true) ||
                    name.endsWith(".jpeg", ignoreCase = true) ||
                    name.endsWith(".png", ignoreCase = true)
            }
            ?.sortedWith(compareBy({ imageNumber(it) ?: Int.MAX_VALUE }, { it }))
            .orEmpty()
    }

    fun load(fileName: String): LoadedBitmapAsset {
        return BitmapAssetLoader.loadWithMetadata(context, "$assetDir/$fileName")
    }

    private fun imageNumber(fileName: String): Int? {
        return Regex("\\d+").find(fileName)?.value?.toIntOrNull()
    }

    private companion object {
        const val TEST_LABEL_DIR = "test_labels"
    }
}
