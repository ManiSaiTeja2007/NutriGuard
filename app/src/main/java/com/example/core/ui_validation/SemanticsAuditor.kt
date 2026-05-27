package com.example.core.ui_validation

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull

object SemanticsAuditor {
    private val rules = mutableListOf<SemanticsRule>()

    init {
        // Register all default rules
        registerRule(TouchTargetRule())
        registerRule(MissingDescriptionRule())
        registerRule(ExcessiveDensityRule())
        registerRule(DuplicateNavigationRule())
        registerRule(RepeatedBadgeRule())
        registerRule(DuplicateActionRule())
    }

    fun registerRule(rule: SemanticsRule) {
        rules.add(rule)
    }

    fun clearRules() {
        rules.clear()
    }

    fun getRules(): List<SemanticsRule> {
        return rules.toList()
    }

    /**
     * Runs all registered semantics rules over the node hierarchy and aggregates issues.
     */
    fun audit(rootNode: SemanticsNode): AuditReport {
        val issues = mutableListOf<AuditIssue>()
        auditNodeRecursive(rootNode, issues)
        auditOverlaps(rootNode, issues)

        // Calculate Deterministic Visual Trust Score
        // Starts at 100, drops by 10 per ERROR and 4 per WARNING, capped at 0.
        var score = 100
        issues.forEach { issue ->
            score -= if (issue.severity == "ERROR") 10 else 4
        }
        val finalScore = score.coerceAtLeast(0)

        return AuditReport(
            visualTrustScore = finalScore,
            issues = issues
        )
    }

    private fun auditNodeRecursive(node: SemanticsNode, issues: MutableList<AuditIssue>) {
        rules.forEach { rule ->
            try {
                issues.addAll(rule.evaluate(node))
            } catch (e: Exception) {
                // Prevent failures in individual rule evaluations from breaking the auditor
                issues.add(
                    AuditIssue(
                        ruleName = rule.name,
                        nodeTag = node.config.getOrNull(SemanticsProperties.TestTag),
                        nodeText = null,
                        issueType = "RULE_EXECUTION_FAILURE",
                        severity = "WARNING",
                        message = "Failed to run rule: ${e.message}"
                    )
                )
            }
        }
        node.children.forEach { child ->
            auditNodeRecursive(child, issues)
        }
    }

    private fun auditOverlaps(root: SemanticsNode, issues: MutableList<AuditIssue>) {
        val allInteractiveNodes = mutableListOf<SemanticsNode>()
        collectInteractiveNodes(root, allInteractiveNodes)

        // Compare each pair for bounding box overlaps
        for (i in 0 until allInteractiveNodes.size) {
            for (j in i + 1 until allInteractiveNodes.size) {
                val nodeA = allInteractiveNodes[i]
                val nodeB = allInteractiveNodes[j]

                val rectA = nodeA.boundsInRoot
                val rectB = nodeB.boundsInRoot

                // Check if they intersect
                val intersects = rectA.left < rectB.right && rectA.right > rectB.left &&
                                 rectA.top < rectB.bottom && rectA.bottom > rectB.top

                if (intersects) {
                    val tagA = nodeA.config.getOrNull(SemanticsProperties.TestTag) ?: "NodeA"
                    val tagB = nodeB.config.getOrNull(SemanticsProperties.TestTag) ?: "NodeB"
                    // Overlaps are permitted for children/parents, but siblings overlapping could be a collision
                    if (nodeA.parent?.id != nodeB.id && nodeB.parent?.id != nodeA.id) {
                        issues.add(
                            AuditIssue(
                                ruleName = "OverlapAuditor",
                                nodeTag = "$tagA / $tagB",
                                nodeText = null,
                                issueType = "UI_ELEMENT_OVERLAP",
                                severity = "WARNING",
                                message = "Visual elements overlap in root bounds: $tagA and $tagB"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun collectInteractiveNodes(node: SemanticsNode, list: MutableList<SemanticsNode>) {
        if (node.config.contains(SemanticsActions.OnClick)) {
            list.add(node)
        }
        node.children.forEach { collectInteractiveNodes(it, list) }
    }
}

data class AuditReport(
    val visualTrustScore: Int,
    val issues: List<AuditIssue>
)
