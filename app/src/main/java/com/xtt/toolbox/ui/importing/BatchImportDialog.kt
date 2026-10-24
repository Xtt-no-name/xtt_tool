package com.xtt.toolbox.ui.importing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.R
import com.xtt.toolbox.data.importing.BatchImportSummary

data class BatchImportUiState(
    val isVisible: Boolean = false,
    val isRunning: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val currentName: String? = null,
    val summary: BatchImportSummary? = null,
)

@Composable
fun BatchImportDialog(
    state: BatchImportUiState,
    onDismiss: () -> Unit,
) {
    if (!state.isVisible) return
    AlertDialog(
        onDismissRequest = { if (!state.isRunning) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (state.isRunning) R.string.batch_import_title
                    else R.string.batch_import_result_title,
                ),
            )
        },
        text = {
            Column {
                if (state.isRunning) {
                    LinearProgressIndicator(
                        progress = {
                            if (state.total == 0) 0f else {
                                state.completed.toFloat() / state.total.toFloat()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.batch_import_progress,
                            state.completed,
                            state.total,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.currentName?.let { name ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.summary?.let { summary ->
                        Text(
                            text = stringResource(
                                R.string.batch_import_summary,
                                summary.total,
                                summary.success,
                                summary.failed,
                                summary.skippedDuplicates,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!state.isRunning) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.batch_import_done))
                }
            }
        },
    )
}
