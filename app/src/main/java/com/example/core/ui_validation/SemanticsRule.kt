package com.example.core.ui_validation

import androidx.compose.ui.semantics.SemanticsNode

data class AuditIssue(
    val ruleName: String,
    val nodeTag: String?,
    val nodeText: String?,
    val issueType: String,
    val severity: String, // "ERROR" or "WARNING"
    val message: String
)

interface SemanticsRule {
    val name: String
    fun evaluate(node: SemanticsNode): List<AuditIssue>
}
