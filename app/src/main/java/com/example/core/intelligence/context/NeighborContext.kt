package com.example.core.intelligence.context

/**
 * Pure semantic-domain model for representing neighboring context information
 * along with its relative distance (offset) from the target token.
 */
data class NeighborContext(
    val token: String,
    val category: String?,
    val distance: Int // index offset from target token (e.g. 1, 2, 3)
)
