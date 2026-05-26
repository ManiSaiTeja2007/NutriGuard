package com.example.core.additives

import com.example.core.ontology.IngredientCategory

data class ENumberEntry(
    val code: String,
    val canonicalName: String,
    val category: IngredientCategory,
    val aliases: List<String>,
    val description: String,
    val commonOcrErrors: List<String> = emptyList()
) {
    companion object {
        fun find(code: String): ENumberEntry? = ENumberRepository.find(code)
        fun getAll(): List<ENumberEntry> = ENumberRepository.getAll()
    }
}
