package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ocr.OcrResult

@Composable
fun CameraOcrOutputPanel(
    result: OcrResult?,
    dark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val background = if (dark) Color.Transparent else Color.White
    val border = if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFFC8D4CF)
    val textColor = if (dark) Color.White else Color(0xFF163832)
    val bodyColor = if (dark) Color(0xFFEAF3F0) else Color(0xFF263D38)
    val text = result?.text?.ifBlank { "No text recognized" } ?: "OCR pending"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "OCR OUTPUT",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = bodyColor
        )
    }
}
