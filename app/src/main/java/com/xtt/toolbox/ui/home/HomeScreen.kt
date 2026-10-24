package com.xtt.toolbox.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.R
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenTxtReader: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val markdownUnavailable = stringResource(R.string.markdown_reader_unavailable)
    val settingsUnavailable = stringResource(R.string.settings_unavailable)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            ToolEntryCard(
                title = stringResource(R.string.txt_reader_title),
                description = stringResource(R.string.txt_reader_description),
                status = stringResource(R.string.status_available),
                isAvailable = true,
                onClick = onOpenTxtReader,
            )
            ToolEntryCard(
                title = stringResource(R.string.markdown_reader_title),
                description = stringResource(R.string.markdown_reader_description),
                status = stringResource(R.string.status_not_implemented),
                isAvailable = false,
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar(markdownUnavailable)
                    }
                },
            )
            ToolEntryCard(
                title = stringResource(R.string.settings_title),
                description = stringResource(R.string.settings_description),
                status = stringResource(R.string.status_not_implemented),
                isAvailable = false,
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar(settingsUnavailable)
                    }
                },
            )
        }
    }
}

@Composable
private fun ToolEntryCard(
    title: String,
    description: String,
    status: String,
    isAvailable: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = if (isAvailable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
