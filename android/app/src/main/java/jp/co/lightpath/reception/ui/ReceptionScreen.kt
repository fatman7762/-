package jp.co.lightpath.reception.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.lightpath.reception.data.ReceptionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val STAFF_NAMES = listOf("野坂", "伊藤", "梁瀬", "中原", "坂本", "合田", "その他")
private val PARTY_SIZES = (1..6).toList()

private enum class FlashKind { None, Success, Error }

@Composable
fun ReceptionScreen(api: ReceptionApi) {
    var selectedStaff by remember { mutableStateOf<String?>(null) }
    var selectedPartySize by remember { mutableStateOf<Int?>(null) }
    var flash by remember { mutableStateOf(FlashKind.None) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun clearSelection() {
        selectedStaff = null
        selectedPartySize = null
    }

    fun trySubmit(staff: String?, size: Int?) {
        if (staff == null || size == null || submitting) return
        submitting = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.createReception(staff, size)
                }
            }
            flash = when {
                result.isFailure -> FlashKind.Error
                result.getOrNull()?.notified == false -> FlashKind.Error
                else -> FlashKind.Success
            }
            delay(700)
            flash = FlashKind.None
            clearSelection()
            submitting = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
    ) {
        val landscape = maxWidth > maxHeight

        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StaffGrid(
                    selected = selectedStaff,
                    flash = flash,
                    enabled = !submitting,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onSelect = { name ->
                        selectedStaff = name
                        trySubmit(name, selectedPartySize)
                    },
                )
                PartySizeColumn(
                    selected = selectedPartySize,
                    flash = flash,
                    enabled = !submitting,
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(),
                    onSelect = { size ->
                        selectedPartySize = size
                        trySubmit(selectedStaff, size)
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StaffGrid(
                    selected = selectedStaff,
                    flash = flash,
                    enabled = !submitting,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onSelect = { name ->
                        selectedStaff = name
                        trySubmit(name, selectedPartySize)
                    },
                )
                PartySizeRow(
                    selected = selectedPartySize,
                    flash = flash,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.22f),
                    onSelect = { size ->
                        selectedPartySize = size
                        trySubmit(selectedStaff, size)
                    },
                )
            }
        }
    }
}

@Composable
private fun StaffGrid(
    selected: String?,
    flash: FlashKind,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val rows = STAFF_NAMES.chunked(2)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { name ->
                    ChoiceButton(
                        label = name,
                        selected = selected == name,
                        flash = flash,
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onSelect(name) },
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PartySizeRow(
    selected: Int?,
    flash: FlashKind,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PARTY_SIZES.forEach { size ->
            ChoiceButton(
                label = size.toString(),
                selected = selected == size,
                flash = flash,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = { onSelect(size) },
            )
        }
    }
}

@Composable
private fun PartySizeColumn(
    selected: Int?,
    flash: FlashKind,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PARTY_SIZES.forEach { size ->
            ChoiceButton(
                label = size.toString(),
                selected = selected == size,
                flash = flash,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onClick = { onSelect(size) },
            )
        }
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    flash: FlashKind,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val textStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        textAlign = TextAlign.Center,
    )

    if (selected) {
        val colors = when (flash) {
            FlashKind.Success -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                disabledContainerColor = MaterialTheme.colorScheme.tertiary,
                disabledContentColor = MaterialTheme.colorScheme.onTertiary,
            )
            FlashKind.Error -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.error,
                disabledContentColor = MaterialTheme.colorScheme.onError,
            )
            FlashKind.None -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = colors,
        ) {
            Text(text = label, style = textStyle)
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(text = label, style = textStyle)
        }
    }
}
