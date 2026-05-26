package com.example.ui.features.production

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.platform.health.AppHealthMonitor
import com.example.ui.design.*
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen

@Composable
fun HomeScreen(
    navController: NavController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppHealthMonitor.trackScreenTransition("Home")

    NutriScreenScaffold(
        title = "NutriGuard",
        onOpenDrawer = onOpenDrawer,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(NutriSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NutriSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NutriCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(NutriSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Clean Eating Made Simple",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(NutriSpacing.sm))
                    Text(
                        text = "Scan product labels to instantly identify ingredients, additives, and allergen warnings fully offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Text(
                text = "How It Works",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Start)
            )

            WorkflowStep(number = "1", title = "Scan Product", desc = "Point the camera at any ingredient list.")
            WorkflowStep(number = "2", title = "View Ingredients", desc = "Read the reconstructed, clean list of ingredients.")
            WorkflowStep(number = "3", title = "Understand Additives", desc = "See full definitions for food coloring and chemical preservatives.")

            Spacer(modifier = Modifier.height(NutriSpacing.md))

            NutriPrimaryButton(
                text = "Scan Product Label",
                onClick = { navController.navigateTo(Screen.Scan) },
                icon = NutriIcons.Search,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WorkflowStep(
    number: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, NutriShapes.card)
            .padding(NutriSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(NutriSpacing.md))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
