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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.lightpath.reception.data.ReceptionApi
import jp.co.lightpath.reception.data.ReceptionResponse
import jp.co.lightpath.reception.ui.theme.LightpathReceptionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private data class StaffOption(val name: String, val romaji: String)

private val STAFF = listOf(
    StaffOption("野坂", "Nosaka"),
    StaffOption("伊藤", "Ito"),
    StaffOption("梁瀬", "Yanase"),
    StaffOption("中原", "fired"),
    StaffOption("坂本", "Sakamoto"),
    StaffOption("合田", "Aida"),
    StaffOption("その他", "Other"),
)
private val PARTY_SIZES = (1..6).toList()

private enum class FlashKind { None, Success, Error }

private data class TypeScale(
    val staffName: TextUnit,
    val staffRomaji: TextUnit,
    val party: TextUnit,
    val gap: Dp,
)

/**
 * Tablet-friendly type scale from available width/height so portrait and
 * landscape both fill the screen without scrolling.
 */
private fun typeScale(maxWidth: Dp, maxHeight: Dp, landscape: Boolean): TypeScale {
    val shortest = min(maxWidth.value, maxHeight.value)
    val staffName = when {
        landscape -> (maxHeight.value / 11f).coerceIn(34f, 64f)
        else -> (shortest / 12f).coerceIn(36f, 68f)
    }
    val party = when {
        landscape -> (maxHeight.value / 14f).coerceIn(28f, 48f)
        else -> (maxHeight.value / 22f).coerceIn(26f, 42f)
    }
    return TypeScale(
        staffName = staffName.sp,
        staffRomaji = (staffName * 0.38f).coerceIn(13f, 24f).sp,
        party = party.sp,
        gap = if (shortest >= 700f) 14.dp else 10.dp,
    )
}

@Composable
fun ReceptionScreen(api: ReceptionApi) {
    var selectedStaff by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPartySize by rememberSaveable { mutableStateOf<Int?>(null) }
    var flash by rememberSaveable { mutableStateOf(FlashKind.None) }
    var submitting by rememberSaveable { mutableStateOf(false) }
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
        val scale = typeScale(maxWidth, maxHeight, landscape)

        if (landscape) {
            // タブレット横: 左に担当者、右に人数
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(scale.gap),
            ) {
                StaffGrid(
                    selected = selectedStaff,
                    flash = flash,
                    enabled = !submitting,
                    scale = scale,
                    modifier = Modifier
                        .weight(1.65f)
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
                    scale = scale,
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                    onSelect = { size ->
                        selectedPartySize = size
                        trySubmit(selectedStaff, size)
                    },
                )
            }
        } else {
            // タブレット縦: 上に担当者、下に人数
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(scale.gap),
            ) {
                StaffGrid(
                    selected = selectedStaff,
                    flash = flash,
                    enabled = !submitting,
                    scale = scale,
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
                    scale = scale,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.17f),
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
    scale: TypeScale,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val rows = STAFF.chunked(2)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(scale.gap),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(scale.gap),
            ) {
                rowItems.forEach { staff ->
                    ChoiceButton(
                        label = staff.name,
                        subtitle = staff.romaji,
                        selected = selected == staff.name,
                        flash = flash,
                        enabled = enabled,
                        labelSize = scale.staffName,
                        subtitleSize = scale.staffRomaji,
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
    scale: TypeScale,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(scale.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PARTY_SIZES.forEach { size ->
            ChoiceButton(
                label = size.toString(),
                selected = selected == size,
                flash = flash,
                enabled = enabled,
                labelSize = scale.party,
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
    scale: TypeScale,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(scale.gap),
    ) {
        PARTY_SIZES.forEach { size ->
            ChoiceButton(
                label = size.toString(),
                selected = selected == size,
                flash = flash,
                enabled = enabled,
                labelSize = scale.party,
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
    labelSize: TextUnit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleSize: TextUnit = 14.sp,
    onClick: () -> Unit,
) {
    val labelStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        fontSize = labelSize,
        textAlign = TextAlign.Center,
        lineHeight = labelSize * 1.05f,
    )
    val subtitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = subtitleSize,
        textAlign = TextAlign.Center,
        lineHeight = subtitleSize * 1.1f,
    )

    val content: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = subtitleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            content = { content() },
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            content = { content() },
        )
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

@Preview(showBackground = true, widthDp = 800, heightDp = 1280, name = "タブレット縦")
@Composable
private fun ReceptionPortraitPreview() {
    LightpathReceptionTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ReceptionScreen(api = PreviewReceptionApi())
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800, name = "タブレット横")
@Composable
private fun ReceptionLandscapePreview() {
    LightpathReceptionTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ReceptionScreen(api = PreviewReceptionApi())
        }
    }
}
