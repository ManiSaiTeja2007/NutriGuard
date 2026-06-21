package com.example.ui.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutriScreenScaffold(
    title: String,
    onOpenDrawer: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onOpenDrawer != null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(imageVector = NutriIcons.Menu, contentDescription = "Open Menu")
                        }
                    } else if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("results_back_button")
                        ) {
                            Icon(imageVector = NutriIcons.Back, contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = floatingActionButton,
        modifier = modifier,
        content = content
    )
}

@Preview
@Composable
fun PreviewNutriScaffold() {
    MaterialTheme {
        NutriScreenScaffold(
            title = "Preview Scaffold",
            onOpenDrawer = {}
        ) { padding ->
            Text(
                text = "Scaffold Content",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            )
        }
    }
}
