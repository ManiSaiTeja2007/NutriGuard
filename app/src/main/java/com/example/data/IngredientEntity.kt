package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredients")
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val riskLevel: String, // "HIGH", "MODERATE"
    val reason: String
)
