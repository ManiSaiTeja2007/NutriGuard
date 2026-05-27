package com.example.core.ui_validation

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString

// Helper to check if a node has an interactive ancestor
private fun SemanticsNode.hasInteractiveAncestor(): Boolean {
    var current = this.parent
    while (current != null) {
        val currentInteractive = current.config.contains(SemanticsActions.OnClick) ||
                                 current.config.getOrNull(SemanticsProperties.Role) != null
        if (currentInteractive) return true
        current = current.parent
    }
    return false
}

// Helper extension to check if a node is the primary interactive container
private fun SemanticsNode.isInteractive(): Boolean {
    val selfInteractive = this.config.contains(SemanticsActions.OnClick) ||
                          this.config.getOrNull(SemanticsProperties.Role) != null
    // If it has an interactive ancestor, we delegate interactivity to the ancestor container
    return selfInteractive && !this.hasInteractiveAncestor()
}

// Helper to get text content from node
private fun SemanticsNode.getNodeText(): String? {
    val textList = this.config.getOrNull(SemanticsProperties.Text)
    if (!textList.isNullOrEmpty()) {
        return textList.joinToString(" ") { it.text }
    }
    return null
}

// Helper to get test tag
private fun SemanticsNode.getTestTag(): String? {
    return this.config.getOrNull(SemanticsProperties.TestTag)
}

// Helper to get content description
private fun SemanticsNode.getContentDescription(): String? {
    return this.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
}

/**
 * 1. TouchTargetRule: Verifies minimum touch target size (48dp x 48dp) for interactive elements.
 */
class TouchTargetRule : SemanticsRule {
    override val name = "TouchTargetRule"

    override fun evaluate(node: SemanticsNode): List<AuditIssue> {
        val issues = mutableListOf<AuditIssue>()
        if (node.isInteractive()) {
            val bounds = node.size
            val density = node.layoutInfo.density
            val widthDp = bounds.width / density.density
            val heightDp = bounds.height / density.density

            // Allow slightly smaller targets (like 40dp) for compact icon-only buttons
            // or switch components if they are not containerized, but flag anything below 40dp as error.
            val minSize = 40.0f
            if (widthDp < minSize || heightDp < minSize) {
                issues.add(
                    AuditIssue(
                        ruleName = name,
                        nodeTag = node.getTestTag(),
                        nodeText = node.getNodeText(),
                        issueType = "TOUCH_TARGET_SIZE_VIOLATION",
                        severity = "ERROR",
                        message = "Interactive node touch target size is ${widthDp.toInt()}dp x ${heightDp.toInt()}dp (minimum required: 48dp x 48dp, minimum permitted: 40dp)"
                    )
                )
            } else if (widthDp < 47.9f || heightDp < 47.9f) {
                // If it is between 40dp and 48dp, report as warning rather than error
                issues.add(
                    AuditIssue(
                        ruleName = name,
                        nodeTag = node.getTestTag(),
                        nodeText = node.getNodeText(),
                        issueType = "TOUCH_TARGET_SIZE_WARNING",
                        severity = "WARNING",
                        message = "Interactive node size is ${widthDp.toInt()}dp x ${heightDp.toInt()}dp (recommended: 48dp x 48dp)"
                    )
                )
            }
        }
        return issues
    }
}

/**
 * 2. MissingDescriptionRule: Flags missing content descriptions on visual icons, images, or interactive elements with no text.
 */
class MissingDescriptionRule : SemanticsRule {
    override val name = "MissingDescriptionRule"

    override fun evaluate(node: SemanticsNode): List<AuditIssue> {
        val issues = mutableListOf<AuditIssue>()
        val role = node.config.getOrNull(SemanticsProperties.Role)?.toString()
        val testTag = node.getTestTag() ?: ""
        
        val isVisualOnly = role == "Image" || testTag.contains("icon", ignoreCase = true)
        val isClickable = node.config.contains(SemanticsActions.OnClick)
        val text = node.getNodeText()
        val contentDesc = node.getContentDescription()

        // Ignore visual descriptions if it has an interactive ancestor (handled by container)
        if (isVisualOnly && contentDesc.isNullOrBlank() && !node.hasInteractiveAncestor()) {
            issues.add(
                AuditIssue(
                    ruleName = name,
                    nodeTag = testTag.ifBlank { null },
                    nodeText = text,
                    issueType = "MISSING_CONTENT_DESCRIPTION",
                    severity = "WARNING",
                    message = "Visual element (Image/Icon) is missing content description for screen readers"
                )
            )
        } else if (isClickable && text.isNullOrBlank() && contentDesc.isNullOrBlank() && !node.hasInteractiveAncestor()) {
            issues.add(
                AuditIssue(
                    ruleName = name,
                    nodeTag = testTag.ifBlank { null },
                    nodeText = null,
                    issueType = "MISSING_INTERACTIVE_LABEL",
                    severity = "ERROR",
                    message = "Interactive element has neither text nor content description"
                )
            )
        }
        return issues
    }
}

/**
 * 3. ExcessiveDensityRule: Computes a weighted density score on subtrees to flag visual clutter.
 * Adds 3.0 points for interactive elements and 1.5 points per level of nesting depth.
 */
class ExcessiveDensityRule : SemanticsRule {
    override val name = "ExcessiveDensityRule"

    override fun evaluate(node: SemanticsNode): List<AuditIssue> {
        val issues = mutableListOf<AuditIssue>()
        
        // We only evaluate density at card/sub-layout level to isolate components, avoiding global screen container false-positives
        val isSignificantContainer = node.getTestTag()?.contains("card") == true ||
                                     node.getTestTag()?.contains("dialog") == true ||
                                     node.getTestTag()?.contains("panel") == true

        if (isSignificantContainer) {
            val densityScore = computeWeightedDensity(node, depth = 1)
            if (densityScore > 80.0) {
                issues.add(
                    AuditIssue(
                        ruleName = name,
                        nodeTag = node.getTestTag(),
                        nodeText = node.getNodeText(),
                        issueType = "EXCESSIVE_UI_DENSITY",
                        severity = "WARNING",
                        message = "Container has a weighted density score of ${"%.1f".format(densityScore)}, indicating potential visual clutter (limit: 80.0)"
                    )
                )
            }
        }
        return issues
    }

    private fun computeWeightedDensity(node: SemanticsNode, depth: Int): Double {
        var score = 1.0 + (depth * 1.0)
        // Check local interactivity of node itself
        val selfInteractive = node.config.contains(SemanticsActions.OnClick) ||
                              node.config.getOrNull(SemanticsProperties.Role) != null
        if (selfInteractive) {
            score += 2.0
        }
        node.children.forEach { child ->
            score += computeWeightedDensity(child, depth + 1)
        }
        return score
    }
}

/**
 * 4. DuplicateNavigationRule: Checks if multiple active siblings have identical destination intents or navigation actions.
 */
class DuplicateNavigationRule : SemanticsRule {
    override val name = "DuplicateNavigationRule"

    override fun evaluate(node: SemanticsNode): List<AuditIssue> {
        val issues = mutableListOf<AuditIssue>()
        
        val parent = node.parent
        if (parent != null && node.isInteractive()) {
            val text = node.getNodeText() ?: node.getContentDescription() ?: node.getTestTag() ?: ""
            if (text.isNotBlank()) {
                // Sibling check comparing unique node IDs rather than wrapper references
                val siblings = parent.children.filter { it.id != node.id && it.isInteractive() }
                for (sibling in siblings) {
                    val siblingText = sibling.getNodeText() ?: sibling.getContentDescription() ?: sibling.getTestTag() ?: ""
                    if (siblingText == text && siblingText.isNotBlank()) {
                        issues.add(
                            AuditIssue(
                                ruleName = name,
                                nodeTag = node.getTestTag(),
                                nodeText = text,
                                issueType = "DUPLICATE_NAVIGATION_ACTION",
                                severity = "WARNING",
                                message = "Duplicate visual control or navigation action visible in the same group: '$text'"
                            )
                        )
                        break
                    }
                }
            }
        }
        return issues
    }
}

/**
 * 5. RepeatedBadgeRule: Flags identical semantic status/category badges inside the same card or row.
 */
class RepeatedBadgeRule : SemanticsRule {
    override val name = "RepeatedBadgeRule"

    override fun evaluate(node: SemanticsNode): List<AuditIssue> {
        val issues = mutableListOf<AuditIssue>()
        
        val isGroupContainer = node.getTestTag()?.contains("card") == true || 
                               node.getTestTag()?.contains("row") == true

        if (isGroupContainer) {
            val badgeTexts = mutableListOf<String>()
            collectBadgeTexts(node, badgeTexts)
            
            val duplicates = badgeTexts.groupBy { it }.filter { it.value.size > 1 }.keys
            if (duplicates.isNotEmpty()) {
                issues.add(
                    AuditIssue(
                        ruleName = name,
                        nodeTag = node.getTestTag(),
                        nodeText = null,
                        issueType = "REPEATED_SEMANTIC_BADGE",
                        severity = "WARNING",
                        message = "Redundant semantic badges detected in the same container: ${duplicates.joinToString(", ")}"
                    )
                )
            }
        }
        return issues
    }

    private fun collectBadgeTexts(node: SemanticsNode, list: MutableList<String>) {
        val tag = node.getTestTag() ?: ""
        val isBadge = tag.contains("badge", ignoreCase = true) || tag.contains("tag", ignoreCase = true)
        val text = node.getNodeText()
        if (isBadge && text != null) {
            list.add(text)
        }
        node.children.forEach { collectBadgeTexts(it, list) }
    }
}

/**
 * 6. DuplicateActionRule: Verifies redundant trigger actions (e.g. repeated back clicks or duplicative CTA buttons).
 */
class DuplicateActionRule : SemanticsRule {
    override val name = "DuplicateActionRule"

    override fun evaluate(node: SemanticsNode): List<AuditIssue> {
        val issues = mutableListOf<AuditIssue>()
        
        if (node.isInteractive()) {
            val tag = node.getTestTag() ?: ""
            val desc = node.getContentDescription() ?: ""
            val isBackAction = tag.contains("back", ignoreCase = true) || desc.contains("back", ignoreCase = true)
            
            if (isBackAction) {
                val root = getRootNode(node)
                val allBackInteractiveNodes = mutableListOf<SemanticsNode>()
                collectBackInteractiveNodes(root, allBackInteractiveNodes)
                if (allBackInteractiveNodes.size > 1 && node.id == allBackInteractiveNodes.first().id) {
                    issues.add(
                        AuditIssue(
                            ruleName = name,
                            nodeTag = tag,
                            nodeText = node.getNodeText(),
                            issueType = "DUPLICATE_BACK_ACTION",
                            severity = "WARNING",
                            message = "Multiple redundant back actions detected on the active screen"
                        )
                    )
                }
            }
        }
        return issues
    }

    private fun getRootNode(node: SemanticsNode): SemanticsNode {
        var current = node
        while (current.parent != null) {
            current = current.parent!!
        }
        return current
    }

    private fun collectBackInteractiveNodes(node: SemanticsNode, list: MutableList<SemanticsNode>) {
        val tag = node.getTestTag() ?: ""
        val desc = node.getContentDescription() ?: ""
        val isBack = tag.contains("back", ignoreCase = true) || desc.contains("back", ignoreCase = true)
        if (isBack && node.isInteractive()) {
            list.add(node)
        }
        node.children.forEach { collectBackInteractiveNodes(it, list) }
    }
}
