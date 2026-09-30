package jp.co.lightpath.reception.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.lightpath.reception.data.ReceptionApi
import jp.co.lightpath.reception.data.ReceptionResponse
import jp.co.lightpath.reception.ui.layout.LayoutStore
import jp.co.lightpath.reception.ui.layout.MAX_MAIN_CELLS
import jp.co.lightpath.reception.ui.layout.MainCell
import jp.co.lightpath.reception.ui.layout.PartyCell
import jp.co.lightpath.reception.ui.layout.ReceptionLayout
import jp.co.lightpath.reception.ui.layout.StaffRole
import jp.co.lightpath.reception.ui.layout.newEmptyCell
import jp.co.lightpath.reception.ui.layout.newParty
import jp.co.lightpath.reception.ui.layout.newPerson
import jp.co.lightpath.reception.ui.theme.LightpathReceptionTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private enum class FlashKind { None, Success, Error }

private enum class SpecialSelect { None, Fired, Welcome }

private val WelcomeYellow = Color(0xFFF9A825)
private val OnWelcomeYellow = Color(0xFF212121)

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E)", java.util.Locale.JAPANESE)

private data class TypeScale(
    val staffName: TextUnit,
    val staffRomaji: TextUnit,
    val party: TextUnit,
    val gap: Dp,
)

private fun typeScale(
    maxWidth: Dp,
    maxHeight: Dp,
    landscape: Boolean,
    mainCount: Int = 8,
): TypeScale {
    val shortest = min(maxWidth.value, maxHeight.value)
    val rows = ((mainCount + 1) / 2).coerceIn(4, 9)
    val staffName = when {
        landscape -> (maxHeight.value / (rows * 2.4f + 2f)).coerceIn(22f, 64f)
        else -> (shortest / (rows * 2.6f + 2f)).coerceIn(22f, 68f)
    }
    val party = when {
        landscape -> (maxHeight.value / 14f).coerceIn(22f, 48f)
        else -> (maxHeight.value / 22f).coerceIn(20f, 42f)
    }
    return TypeScale(
        staffName = staffName.sp,
        staffRomaji = (staffName * 0.38f).coerceIn(10f, 24f).sp,
        party = party.sp,
        gap = if (shortest >= 700f && rows <= 5) 14.dp else if (rows >= 7) 6.dp else 10.dp,
    )
}

private const val APPEND_MAIN_SENTINEL = "__append__"

private sealed class EditDialog {
    data class Person(val index: Int, val name: String, val romaji: String) : EditDialog()
    data class Party(val index: Int, val value: Int, val label: String) : EditDialog()
    data object AddPerson : EditDialog()
    data object AddParty : EditDialog()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceptionScreen(api: ReceptionApi) {
    val context = LocalContext.current
    val store = remember { LayoutStore(context) }
    val intercom = remember { IntercomSound(context) }
    DisposableEffect(Unit) {
        onDispose { intercom.release() }
    }
    var layout by remember { mutableStateOf(store.load()) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var movingMainId by rememberSaveable { mutableStateOf<String?>(null) }
    var movingPartyId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<EditDialog?>(null) }

    var selectedStaff by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPartySize by rememberSaveable { mutableStateOf<Int?>(null) }
    var flash by rememberSaveable { mutableStateOf(FlashKind.None) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val selectedStaffSpecial = remember(selectedStaff, layout) {
        val person = layout.mainCells
            .filterIsInstance<MainCell.Person>()
            .firstOrNull { it.name == selectedStaff }
        when (person?.role) {
            StaffRole.Nakahara -> SpecialSelect.Fired
            StaffRole.Yanase -> SpecialSelect.Welcome
            else -> SpecialSelect.None
        }
    }

    fun persist(next: ReceptionLayout) {
        layout = next
        store.save(next)
    }

    fun clearSelection() {
        selectedStaff = null
        selectedPartySize = null
    }

    fun trySubmit(staff: String?, size: Int?) {
        if (editMode || staff == null || size == null || submitting) return
        submitting = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.createReception(staff, size)
                }
            }
            if (result.isSuccess) {
                flash = FlashKind.Success
                intercom.play(
                    when (selectedStaffSpecial) {
                        SpecialSelect.Fired -> ReceptionChime.Nakahara
                        SpecialSelect.Welcome -> ReceptionChime.Yanase
                        SpecialSelect.None -> ReceptionChime.Default
                    },
                )
            } else {
                flash = FlashKind.Error
            }
            delay(700)
            flash = FlashKind.None
            clearSelection()
            submitting = false
        }
    }

    fun toggleEditMode() {
        editMode = !editMode
        movingMainId = null
        movingPartyId = null
        dialog = null
        clearSelection()
        flash = FlashKind.None
    }

    fun moveMain(fromId: String, toId: String) {
        if (fromId == toId) {
            movingMainId = null
            return
        }
        val cells = layout.mainCells.toMutableList()
        val from = cells.indexOfFirst { it.id == fromId }
        val to = cells.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return
        val tmp = cells[from]
        cells[from] = cells[to]
        cells[to] = tmp
        persist(layout.copy(mainCells = cells))
        movingMainId = null
    }

    fun moveParty(fromId: String, toId: String) {
        if (fromId == toId) {
            movingPartyId = null
            return
        }
        val cells = layout.partyCells.toMutableList()
        val from = cells.indexOfFirst { it.id == fromId }
        val to = cells.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return
        val tmp = cells[from]
        cells[from] = cells[to]
        cells[to] = tmp
        persist(layout.copy(partyCells = cells))
        movingPartyId = null
    }

    fun deleteMain(id: String) {
        val cells = layout.mainCells.toMutableList()
        val idx = cells.indexOfFirst { it.id == id }
        if (idx < 0) return
        val cell = cells[idx]
        if (cell.deleteLocked) return
        cells[idx] = newEmptyCell()
        persist(layout.copy(mainCells = cells))
        if (movingMainId == id) movingMainId = null
    }

    fun deleteParty(id: String) {
        val next = layout.partyCells.filterNot { it.id == id }
        if (next.isEmpty()) return
        persist(layout.copy(partyCells = next))
        if (movingPartyId == id) movingPartyId = null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
    ) {
        val landscape = maxWidth > maxHeight
        val scale = typeScale(maxWidth, maxHeight, landscape, layout.mainCells.size)
        val columns = 2

        fun openExpandPerson() {
            if (!layout.canExpandMain) return
            movingMainId = APPEND_MAIN_SENTINEL
            dialog = EditDialog.AddPerson
        }
        Column(Modifier = Modifier.fillMaxSize()) {
            if (editMode) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "編集モード（タップで移動 / −で削除 / ＋で追加 / 最大${MAX_MAIN_CELLS}）",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        TextButton(onClick = { toggleEditMode() }) {
                            Text("完了")
                        }
                    }
                }
            }

            if (landscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(scale.gap),
                ) {
                    MainGrid(
                        cells = layout.mainCells,
                        columns = columns,
                        selectedStaff = selectedStaff,
                        flash = flash,
                        editMode = editMode,
                        movingId = movingMainId,
                        enabled = !submitting,
                        scale = scale,
                        modifier = Modifier
                            .weight(1.65f)
                            .fillMaxHeight(),
                        onClockLongPress = { toggleEditMode() },
                        onPersonClick = { person ->
                            if (editMode) {
                                val id = person.id
                                when {
                                    movingMainId == null -> movingMainId = id
                                    else -> moveMain(movingMainId!!, id)
                                }
                            } else {
                                selectedStaff = person.name
                                trySubmit(person.name, selectedPartySize)
                            }
                        },
                        onPersonLongClick = { index, person ->
                            if (editMode) {
                                dialog = EditDialog.Person(index, person.name, person.romaji)
                            }
                        },
                        onEmptyClick = { cell ->
                            if (!editMode) return@MainGrid
                            if (movingMainId != null) {
                                moveMain(movingMainId!!, cell.id)
                            } else {
                                dialog = EditDialog.AddPerson
                                // remember target empty by temporarily selecting it
                                movingMainId = cell.id
                            }
                        },
                        onDelete = { deleteMain(it) },
                        canExpand = layout.canExpandMain,
                        onExpand = { openExpandPerson() },
                        onClockClick = {
                            if (editMode) {
                                if (movingMainId == null) {
                                    movingMainId = "clock"
                                } else {
                                    moveMain(movingMainId!!, "clock")
                                }
                            }
                        },
                    )
                    PartyStrip(
                        cells = layout.partyCells,
                        vertical = true,
                        selected = selectedPartySize,
                        flash = flash,
                        staffSpecial = selectedStaffSpecial,
                        editMode = editMode,
                        movingId = movingPartyId,
                        enabled = !submitting,
                        scale = scale,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(),
                        onClick = { cell ->
                            if (editMode) {
                                when {
                                    movingPartyId == null -> movingPartyId = cell.id
                                    else -> moveParty(movingPartyId!!, cell.id)
                                }
                            } else {
                                selectedPartySize = cell.value
                                trySubmit(selectedStaff, cell.value)
                            }
                        },
                        onLongClick = { index, cell ->
                            if (editMode) {
                                dialog = EditDialog.Party(index, cell.value, cell.label)
                            }
                        },
                        onDelete = { deleteParty(it) },
                        onAdd = { dialog = EditDialog.AddParty },
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(scale.gap),
                ) {
                    MainGrid(
                        cells = layout.mainCells,
                        columns = columns,
                        selectedStaff = selectedStaff,
                        flash = flash,
                        editMode = editMode,
                        movingId = movingMainId,
                        enabled = !submitting,
                        scale = scale,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onClockLongPress = { toggleEditMode() },
                        onPersonClick = { person ->
                            if (editMode) {
                                val id = person.id
                                when {
                                    movingMainId == null -> movingMainId = id
                                    else -> moveMain(movingMainId!!, id)
                                }
                            } else {
                                selectedStaff = person.name
                                trySubmit(person.name, selectedPartySize)
                            }
                        },
                        onPersonLongClick = { index, person ->
                            if (editMode) {
                                dialog = EditDialog.Person(index, person.name, person.romaji)
                            }
                        },
                        onEmptyClick = { cell ->
                            if (!editMode) return@MainGrid
                            if (movingMainId != null) {
                                moveMain(movingMainId!!, cell.id)
                            } else {
                                dialog = EditDialog.AddPerson
                                movingMainId = cell.id
                            }
                        },
                        onDelete = { deleteMain(it) },
                        canExpand = layout.canExpandMain,
                        onExpand = { openExpandPerson() },
                        onClockClick = {
                            if (editMode) {
                                if (movingMainId == null) {
                                    movingMainId = "clock"
                                } else {
                                    moveMain(movingMainId!!, "clock")
                                }
                            }
                        },
                    )
                    PartyStrip(
                        cells = layout.partyCells,
                        vertical = false,
                        selected = selectedPartySize,
                        flash = flash,
                        staffSpecial = selectedStaffSpecial,
                        editMode = editMode,
                        movingId = movingPartyId,
                        enabled = !submitting,
                        scale = scale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.17f),
                        onClick = { cell ->
                            if (editMode) {
                                when {
                                    movingPartyId == null -> movingPartyId = cell.id
                                    else -> moveParty(movingPartyId!!, cell.id)
                                }
                            } else {
                                selectedPartySize = cell.value
                                trySubmit(selectedStaff, cell.value)
                            }
                        },
                        onLongClick = { index, cell ->
                            if (editMode) {
                                dialog = EditDialog.Party(index, cell.value, cell.label)
                            }
                        },
                        onDelete = { deleteParty(it) },
                        onAdd = { dialog = EditDialog.AddParty },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        is EditDialog.Person -> PersonEditDialog(
            title = "担当者を編集",
            initialName = d.name,
            initialRomaji = d.romaji,
            onDismiss = { dialog = null; movingMainId = null },
            onConfirm = { name, romaji ->
                val cells = layout.mainCells.toMutableList()
                val current = cells.getOrNull(d.index) as? MainCell.Person ?: return@PersonEditDialog
                cells[d.index] = current.copy(name = name, romaji = romaji)
                persist(layout.copy(mainCells = cells))
                dialog = null
                movingMainId = null
            },
        )
        is EditDialog.Party -> PartyEditDialog(
            title = "人数を編集",
            initialValue = d.value,
            initialLabel = d.label,
            onDismiss = { dialog = null },
            onConfirm = { value, label ->
                val cells = layout.partyCells.toMutableList()
                if (d.index in cells.indices) {
                    cells[d.index] = cells[d.index].copy(value = value, label = label)
                    persist(layout.copy(partyCells = cells))
                }
                dialog = null
            },
        )
        EditDialog.AddPerson -> PersonEditDialog(
            title = "担当者を追加",
            initialName = "",
            initialRomaji = "",
            onDismiss = { dialog = null; movingMainId = null },
            onConfirm = { name, romaji ->
                val person = newPerson(name, romaji)
                val cells = layout.mainCells.toMutableList()
                val append = movingMainId == APPEND_MAIN_SENTINEL
                if (append) {
                    if (cells.size >= MAX_MAIN_CELLS) return@PersonEditDialog
                    cells.add(person)
                } else {
                    val emptyIdx = movingMainId?.let { id ->
                        cells.indexOfFirst { it.id == id && it is MainCell.Empty }
                    }?.takeIf { it >= 0 }
                        ?: cells.indexOfFirst { it is MainCell.Empty }
                    if (emptyIdx >= 0) {
                        cells[emptyIdx] = person
                    } else if (cells.size < MAX_MAIN_CELLS) {
                        cells.add(person)
                    } else {
                        return@PersonEditDialog
                    }
                }
                persist(layout.copy(mainCells = cells))
                dialog = null
                movingMainId = null
            },
        )
        EditDialog.AddParty -> PartyEditDialog(
            title = "人数を追加",
            initialValue = 1,
            initialLabel = "1",
            onDismiss = { dialog = null },
            onConfirm = { value, label ->
                val next = layout.partyCells + newParty(value, label)
                persist(layout.copy(partyCells = next))
                dialog = null
            },
        )
        null -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainGrid(
    cells: List<MainCell>,
    columns: Int,
    selectedStaff: String?,
    flash: FlashKind,
    editMode: Boolean,
    movingId: String?,
    enabled: Boolean,
    scale: TypeScale,
    modifier: Modifier = Modifier,
    canExpand: Boolean = false,
    onExpand: () -> Unit = {},
    onClockLongPress: () -> Unit,
    onClockClick: () -> Unit,
    onPersonClick: (MainCell.Person) -> Unit,
    onPersonLongClick: (Int, MainCell.Person) -> Unit,
    onEmptyClick: (MainCell.Empty) -> Unit,
    onDelete: (String) -> Unit,
) {
    val rows = cells.chunked(columns)
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
                rowItems.forEach { cell ->
                    val index = cells.indexOfFirst { it.id == cell.id }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        when (cell) {
                            is MainCell.Person -> {
                                val isSelected = !editMode && selectedStaff == cell.name
                                // 中原・梁瀬は受付完了フラッシュ中も専用表示を維持（緑にしない）
                                val special = when {
                                    !isSelected -> SpecialSelect.None
                                    cell.role == StaffRole.Nakahara -> SpecialSelect.Fired
                                    cell.role == StaffRole.Yanase -> SpecialSelect.Welcome
                                    else -> SpecialSelect.None
                                }
                                val (label, subtitle) = when (special) {
                                    SpecialSelect.Fired -> "クビ" to cell.name
                                    SpecialSelect.Welcome -> "おかえりなさい" to cell.name
                                    SpecialSelect.None -> cell.name to cell.romaji
                                }
                                ChoiceButton(
                                    label = label,
                                    subtitle = subtitle,
                                    selected = isSelected || movingId == cell.id,
                                    // 専用ロールは緑フラッシュを適用しない
                                    flash = if (special == SpecialSelect.None) flash else FlashKind.None,
                                    enabled = enabled || editMode,
                                    specialSelect = special,
                                    moving = movingId == cell.id,
                                    labelSize = scale.staffName,
                                    subtitleSize = scale.staffRomaji,
                                    modifier = Modifier.fillMaxSize(),
                                    onClick = { onPersonClick(cell) },
                                    onLongClick = { onPersonLongClick(index, cell) },
                                )
                                if (editMode && !cell.deleteLocked) {
                                    MinusBadge(
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        onClick = { onDelete(cell.id) },
                                    )
                                }
                            }
                            is MainCell.Clock -> {
                                ClockPopout(
                                    scale = scale,
                                    editMode = editMode,
                                    moving = movingId == cell.id,
                                    modifier = Modifier.fillMaxSize(),
                                    onLongClick = onClockLongPress,
                                    onClick = onClockClick,
                                )
                                if (editMode && canExpand) {
                                    PlusBadge(
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        onClick = onExpand,
                                    )
                                }
                            }
                            is MainCell.Empty -> {
                                PlusCell(
                                    editMode = editMode,
                                    moving = movingId == cell.id,
                                    modifier = Modifier.fillMaxSize(),
                                    onClick = { onEmptyClick(cell) },
                                )
                            }
                        }
                    }
                }
                repeat(columns - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PartyStrip(
    cells: List<PartyCell>,
    vertical: Boolean,
    selected: Int?,
    flash: FlashKind,
    staffSpecial: SpecialSelect,
    editMode: Boolean,
    movingId: String?,
    enabled: Boolean,
    scale: TypeScale,
    modifier: Modifier = Modifier,
    onClick: (PartyCell) -> Unit,
    onLongClick: (Int, PartyCell) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val arrangement = Arrangement.spacedBy(scale.gap)
    val partyFlash = if (staffSpecial == SpecialSelect.None) flash else FlashKind.None
    if (vertical) {
        Column(modifier = modifier, verticalArrangement = arrangement) {
            cells.forEachIndexed { index, cell ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ChoiceButton(
                        label = cell.label,
                        selected = (!editMode && selected == cell.value) || movingId == cell.id,
                        flash = partyFlash,
                        enabled = enabled || editMode,
                        specialSelect = if (!editMode && selected == cell.value) staffSpecial else SpecialSelect.None,
                        moving = movingId == cell.id,
                        labelSize = scale.party,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { onClick(cell) },
                        onLongClick = { onLongClick(index, cell) },
                    )
                    if (editMode) {
                        MinusBadge(
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = { onDelete(cell.id) },
                        )
                    }
                }
            }
            if (editMode) {
                PlusCell(
                    editMode = true,
                    moving = false,
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxWidth(),
                    onClick = onAdd,
                )
            }
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = arrangement) {
            cells.forEachIndexed { index, cell ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    ChoiceButton(
                        label = cell.label,
                        selected = (!editMode && selected == cell.value) || movingId == cell.id,
                        flash = partyFlash,
                        enabled = enabled || editMode,
                        specialSelect = if (!editMode && selected == cell.value) staffSpecial else SpecialSelect.None,
                        moving = movingId == cell.id,
                        labelSize = scale.party,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { onClick(cell) },
                        onLongClick = { onLongClick(index, cell) },
                    )
                    if (editMode) {
                        MinusBadge(
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = { onDelete(cell.id) },
                        )
                    }
                }
            }
            if (editMode) {
                PlusCell(
                    editMode = true,
                    moving = false,
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxHeight(),
                    onClick = onAdd,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockPopout(
    scale: TypeScale,
    editMode: Boolean,
    moving: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .then(
                    if (moving) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (editMode) "長押しで完了" else now.format(DATE_FORMATTER),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = scale.staffRomaji,
                        textAlign = TextAlign.Center,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = now.format(TIME_FORMATTER),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = scale.staffName * 0.62f,
                        textAlign = TextAlign.Center,
                        lineHeight = scale.staffName * 0.7f,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PlusCell(
    editMode: Boolean,
    moving: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (!editMode) {
        Box(modifier = modifier)
        return
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.then(
            if (moving) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            else Modifier,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = "＋",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun MinusBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(6.dp)
            .size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("−", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun PlusBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(6.dp)
            .size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("＋", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    specialSelect: SpecialSelect = SpecialSelect.None,
    moving: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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

    val container = when {
        specialSelect == SpecialSelect.Fired && selected -> MaterialTheme.colorScheme.error
        specialSelect == SpecialSelect.Welcome && selected -> WelcomeYellow
        flash == FlashKind.Success && selected -> MaterialTheme.colorScheme.tertiary
        flash == FlashKind.Error && selected -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        specialSelect == SpecialSelect.Fired && selected -> MaterialTheme.colorScheme.onError
        specialSelect == SpecialSelect.Welcome && selected -> OnWelcomeYellow
        flash == FlashKind.Success && selected -> MaterialTheme.colorScheme.onTertiary
        flash == FlashKind.Error && selected -> MaterialTheme.colorScheme.onError
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (moving) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = contentColor,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = labelStyle,
                maxLines = 2,
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
}

@Composable
private fun PersonEditDialog(
    title: String,
    initialName: String,
    initialRomaji: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var romaji by remember { mutableStateOf(initialRomaji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名前") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = romaji,
                    onValueChange = { romaji = it },
                    label = { Text("ローマ字") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim(), romaji.trim())
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun PartyEditDialog(
    title: String,
    initialValue: Int,
    initialLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
) {
    var valueText by remember { mutableStateOf(initialValue.toString()) }
    var label by remember { mutableStateOf(initialLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("表示（例: 6～）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter { ch -> ch.isDigit() }.take(1) },
                    label = { Text("送信する人数 1〜6") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = valueText.toIntOrNull()?.coerceIn(1, 6) ?: return@TextButton
                    onConfirm(v, label.trim().ifBlank { if (v >= 6) "6～" else v.toString() })
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
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
