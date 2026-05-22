package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutriGuardApp(
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier
) {
    val rawText by viewModel.rawText.collectAsStateWithLifecycle()
    val flaggedWarnings by viewModel.flaggedWarnings.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()

    // Pulse animation for the "Scanner Active" dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Brand Logo Icon matching Tailwind style
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield Icon",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "NutriGuard",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00201E),
                                fontSize = 16.sp,
                                lineHeight = 18.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .alpha(pulseAlpha)
                                        .background(Color(0xFF006A60), RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = "SCANNER ACTIVE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    color = Color(0xFF56605E),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (rawText.isNotEmpty() || flaggedWarnings.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearScannedData() },
                            modifier = Modifier.testTag("clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Results",
                                tint = Color(0xFF3F4948)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.border(width = 1.dp, color = BrandOutline)
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BrandBackground)
        ) {
            // Top Half: Camera View Finder & Tech Brackets HUD Overlay
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.dp,
                        color = BrandOutline,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                // Live Camera View
                CameraPreviewScreen(
                    onTextScanned = { text ->
                        viewModel.processScannedText(text)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // High-fidelity Tech Brackets and Crosshair Alignment Frame
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Scanning Target Frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .fillMaxHeight(0.55f)
                            .align(Alignment.Center)
                            .border(
                                width = 1.dp,
                                color = Color(0x6600FFCC),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        // Precise Tech Corner Bracket accents (Tailwind style)
                        val accentColor = Color(0xFF00FFCC)
                        val thickness = 3.dp
                        val length = 16.dp

                        // Top-Left corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(length)
                                .border(width = thickness, color = accentColor, shape = RoundedCornerShape(topStart = 4.dp))
                        )
                        // Top-Right corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(length)
                                .border(width = thickness, color = accentColor, shape = RoundedCornerShape(topEnd = 4.dp))
                        )
                        // Bottom-Left corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .size(length)
                                .border(width = thickness, color = accentColor, shape = RoundedCornerShape(bottomStart = 4.dp))
                        )
                        // Bottom-Right corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(length)
                                .border(width = thickness, color = accentColor, shape = RoundedCornerShape(bottomEnd = 4.dp))
                        )

                        // Real laser scanning animation lines
                        val verticalOffset by infiniteTransition.animateFloat(
                            initialValue = 0.15f,
                            targetValue = 0.85f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "laserPulse"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.015f)
                                .align(Alignment.TopCenter)
                                .fillMaxHeight(verticalOffset)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            accentColor.copy(alpha = 0.1f),
                                            accentColor.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )

                        // Info instruction pill inside scanner field
                        Text(
                            text = "POSITION LABEL HERE",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.65f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Scanning active pulse badge
                    if (isAnalyzing) {
                        Text(
                            text = "ANALYZING INGREDIENTS...",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF006A60),
                                            Color(0xFF3B9B90)
                                        )
                                    ),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Bottom Half: Resilient Results Panel (White Card Bottom Sheet style)
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .border(
                        width = 1.dp,
                        color = BrandOutline,
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            ) {
                // Bottom Sheet drag handle
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(6.dp)
                        .background(Color(0xFFE1E3E1), RoundedCornerShape(3.dp))
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom sheet header with alert counts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected Risks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00201E)
                    )

                    val alertCount = flaggedWarnings.size
                    if (alertCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$alertCount ${if (alertCount == 1) "ALERT" else "ALERTS"}",
                                color = Color(0xFFBA1A1A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE0F2F1), RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "STABLE",
                                color = Color(0xFF00796B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (flaggedWarnings.isEmpty()) {
                    // Elevated, warm Empty Scanning State with detailed guidelines
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(0.95f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = "Aim Guide",
                                tint = Color(0xFF006A60).copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Lock onto ingredients text",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00201E),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "NutriGuard is watching. Any matched high-risk items trigger instant explanations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF56605E),
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            // Highlight current local directory matching list
                            Text(
                                text = "MONITORED COMPONENT LIST:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E9290),
                                letterSpacing = 0.75.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start)
                            ) {
                                val itemNames = listOf("Maltodextrin", "Aspartame", "Nitrite", "Carrageenan")
                                itemNames.forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color(0xFFF0F2F1),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(1.dp, Color(0xFFE1E3E1), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = item,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF4A6360)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Flagged database occurrences
                    Column(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(flaggedWarnings) { warning ->
                                WarningCard(warning)
                            }
                        }

                        // Export / Log Database button matching the Professional Polish HTML Theme
                        Button(
                            onClick = {
                                // Real design polish feedback log to DB action
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF006A60)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Save Log to Database",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WarningCard(warning: FlaggedWarning) {
    val isHighRisk = warning.ingredient.riskLevel == "HIGH"
    val cardBg = if (isHighRisk) HighRiskBg else ModerateRiskBg
    val cardBorderColor = if (isHighRisk) Color(0xFFFFDAD6) else Color(0xFFFBE0C1)
    val badgeBg = if (isHighRisk) Color(0xFFBA1A1A) else Color(0xFF964B00)
    val textColor = if (isHighRisk) HighRiskText else ModerateRiskText
    val badgeTextColor = Color.White

    Card(
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = cardBorderColor, shape = RoundedCornerShape(16.dp))
            .testTag("warning_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = warning.ingredient.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    if (warning.distance > 0) {
                        Text(
                            text = "Scanned: \"${warning.matchedTerm}\" (Fuzzy dist: ${warning.distance})",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "Scanned exact match: \"${warning.matchedTerm}\"",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Brand polished Risk Tag
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isHighRisk) Icons.Default.Block else Icons.Default.Warning,
                            contentDescription = "Risk Badge",
                            tint = badgeTextColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isHighRisk) "HIGH RISK" else "MODERATE",
                            color = badgeTextColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Risk Explanation",
                    tint = textColor.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(15.dp)
                        .padding(top = 1.dp)
                )
                Text(
                    text = warning.ingredient.reason,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = textColor.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
