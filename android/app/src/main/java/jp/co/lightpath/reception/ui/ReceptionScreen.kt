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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.lightpath.reception.data.ReceptionApi
import jp.co.lightpath.reception.data.ReceptionResponse
import jp.co.lightpath.reception.ui.theme.LightpathReceptionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StaffOption(val name: String, val romaji: String)

private val STAFF = listOf(
    StaffOption("野坂", "Nosaka"),
    StaffOption("伊藤", "Ito"),
    StaffOption("梁瀬", "Yanase"),
    StaffOption("中原", "Nakahara"),
    StaffOption("坂本", "Sakamoto"),
    StaffOption("合田", "Aida"),
    StaffOption("その他", "Other"),
)
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
            // Slack 通知は後から接続。受付 API が成功すれば緑フラッシュにする。
            flash = if (result.isSuccess) FlashKind.Success else FlashKind.Error
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
                        .weight(0.18f),
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
    val rows = STAFF.chunked(2)
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
                rowItems.forEach { staff ->
                    ChoiceButton(
                        label = staff.name,
                        subtitle = staff.romaji,
                        selected = selected == staff.name,
                        flash = flash,
                        enabled = enabled,
                        large = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onSelect(staff.name) },
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
    subtitle: String? = null,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    val labelStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        fontSize = if (large) 42.sp else 30.sp,
        textAlign = TextAlign.Center,
        lineHeight = if (large) 46.sp else 34.sp,
    )
    val subtitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = if (large) 16.sp else 14.sp,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp,
    )

    @Composable
    fun LabelContent() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = label, style = labelStyle)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = subtitleStyle,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

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
            LabelContent()
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            LabelContent()
        }
    }
}

private class PreviewReceptionApi : ReceptionApi("http://127.0.0.1:8081") {
    override fun createReception(staffName: String, partySize: Int): ReceptionResponse {
        return ReceptionResponse(
            id = "preview",
            staffName = staffName,
            partySize = partySize,
            acceptedAt = "2026-09-30T01:00:00.000Z",
            notified = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280, name = "縦向き")
@Composable
private fun ReceptionPortraitPreview() {
    LightpathReceptionTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ReceptionScreen(api = PreviewReceptionApi())
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800, name = "横向き")
@Composable
private fun ReceptionLandscapePreview() {
    LightpathReceptionTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ReceptionScreen(api = PreviewReceptionApi())
        }
    }
}
