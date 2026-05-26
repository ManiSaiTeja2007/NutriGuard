package com.example.core.ontology

data class IngredientEntry(
    val canonicalName: String,
    val aliases: List<String>,
    val category: IngredientCategory,
    val additiveCode: String?,
    val tags: List<String>
)
