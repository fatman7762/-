package jp.co.lightpath.reception.ui.layout

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class StaffRole {
    Normal,
    Nakahara,
    Yanase,
}

sealed class MainCell {
    abstract val id: String
    abstract val deleteLocked: Boolean

    data class Person(
        override val id: String,
        val name: String,
        val romaji: String,
        val role: StaffRole = StaffRole.Normal,
        override val deleteLocked: Boolean = false,
    ) : MainCell()

    data class Clock(
        override val id: String = "clock",
        override val deleteLocked: Boolean = true,
    ) : MainCell()

    data class Empty(
        override val id: String,
        override val deleteLocked: Boolean = false,
    ) : MainCell()
}

data class PartyCell(
    val id: String,
    val value: Int,
    val label: String,
)

/** Main grid capacity including the clock cell (default 8, expandable in edit mode). */
const val MAX_MAIN_CELLS = 18

data class ReceptionLayout(
    val mainCells: List<MainCell>,
    val partyCells: List<PartyCell>,
) {
    val canExpandMain: Boolean get() = mainCells.size < MAX_MAIN_CELLS
}

fun defaultReceptionLayout(): ReceptionLayout {
    val people = listOf(
        MainCell.Person(id = "p-nosaka", name = "野坂", romaji = "Nosaka"),
        MainCell.Person(id = "p-ito", name = "伊藤", romaji = "Ito"),
        MainCell.Person(
            id = "p-yanase",
            name = "梁瀬",
            romaji = "VTuber",
            role = StaffRole.Yanase,
            deleteLocked = true,
        ),
        MainCell.Person(
            id = "p-nakahara",
            name = "中原",
            romaji = "dismissed",
            role = StaffRole.Nakahara,
            deleteLocked = true,
        ),
        MainCell.Person(id = "p-sakamoto", name = "坂本", romaji = "Sakamoto"),
        MainCell.Person(id = "p-gouda", name = "合田", romaji = "gouda"),
        MainCell.Person(id = "p-other", name = "その他", romaji = "Other"),
    )
    return ReceptionLayout(
        mainCells = people + MainCell.Clock(),
        partyCells = (1..6).map { n ->
            PartyCell(
                id = "party-$n",
                value = n,
                label = if (n >= 6) "6～" else n.toString(),
            )
        },
    )
}

fun newEmptyCell(): MainCell.Empty = MainCell.Empty(id = "empty-${UUID.randomUUID()}")

fun newPerson(name: String, romaji: String): MainCell.Person =
    MainCell.Person(
        id = "p-${UUID.randomUUID()}",
        name = name.trim(),
        romaji = romaji.trim().ifBlank { name.trim() },
    )

fun newParty(value: Int, label: String): PartyCell =
    PartyCell(
        id = "party-${UUID.randomUUID()}",
        value = value.coerceIn(1, 6),
        label = label.trim().ifBlank { if (value >= 6) "6～" else value.toString() },
    )

fun ReceptionLayout.toJson(): String {
    val main = JSONArray()
    mainCells.forEach { cell ->
        main.put(
            when (cell) {
                is MainCell.Person -> JSONObject()
                    .put("type", "person")
                    .put("id", cell.id)
                    .put("name", cell.name)
                    .put("romaji", cell.romaji)
                    .put("role", cell.role.name)
                    .put("deleteLocked", cell.deleteLocked)
                is MainCell.Clock -> JSONObject()
                    .put("type", "clock")
                    .put("id", cell.id)
                is MainCell.Empty -> JSONObject()
                    .put("type", "empty")
                    .put("id", cell.id)
            },
        )
    }
    val party = JSONArray()
    partyCells.forEach { cell ->
        party.put(
            JSONObject()
                .put("id", cell.id)
                .put("value", cell.value)
                .put("label", cell.label),
        )
    }
    return JSONObject()
        .put("mainCells", main)
        .put("partyCells", party)
        .toString()
}

fun receptionLayoutFromJson(raw: String?): ReceptionLayout? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val root = JSONObject(raw)
        val mainArr = root.getJSONArray("mainCells")
        val main = buildList {
            for (i in 0 until mainArr.length()) {
                val obj = mainArr.getJSONObject(i)
                add(
                    when (obj.getString("type")) {
                        "person" -> MainCell.Person(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            romaji = obj.optString("romaji", ""),
                            role = runCatching {
                                StaffRole.valueOf(obj.optString("role", StaffRole.Normal.name))
                            }.getOrDefault(StaffRole.Normal),
                            deleteLocked = obj.optBoolean("deleteLocked", false),
                        )
                        "clock" -> MainCell.Clock(id = obj.optString("id", "clock"))
                        else -> MainCell.Empty(id = obj.optString("id", newEmptyCell().id))
                    },
                )
            }
        }
        val partyArr = root.getJSONArray("partyCells")
        val party = buildList {
            for (i in 0 until partyArr.length()) {
                val obj = partyArr.getJSONObject(i)
                add(
                    PartyCell(
                        id = obj.getString("id"),
                        value = obj.getInt("value"),
                        label = obj.getString("label"),
                    ),
                )
            }
        }
        // Ensure locked cells exist
        ensureLockedCells(ReceptionLayout(main, party))
    }.getOrNull()
}

private fun ensureLockedCells(layout: ReceptionLayout): ReceptionLayout {
    var main = layout.mainCells.toMutableList()
    val hasClock = main.any { it is MainCell.Clock }
    val hasYanase = main.any { it is MainCell.Person && it.role == StaffRole.Yanase }
    val hasNakahara = main.any { it is MainCell.Person && it.role == StaffRole.Nakahara }
    if (!hasClock) main.add(MainCell.Clock())
    if (!hasYanase) {
        main.add(
            MainCell.Person(
                id = "p-yanase",
                name = "梁瀬",
                romaji = "VTuber",
                role = StaffRole.Yanase,
                deleteLocked = true,
            ),
        )
    }
    if (!hasNakahara) {
        main.add(
            MainCell.Person(
                id = "p-nakahara",
                name = "中原",
                romaji = "dismissed",
                role = StaffRole.Nakahara,
                deleteLocked = true,
            ),
        )
    }
    // Force deleteLocked on special roles; keep 梁瀬 romaji as VTuber
    main = main.map { cell ->
        when (cell) {
            is MainCell.Person ->
                when (cell.role) {
                    StaffRole.Yanase -> cell.copy(deleteLocked = true, romaji = "VTuber")
                    StaffRole.Nakahara -> cell.copy(deleteLocked = true)
                    else -> cell
                }
            is MainCell.Clock -> cell.copy(deleteLocked = true)
            else -> cell
        }
    }.toMutableList()
    return layout.copy(mainCells = main, partyCells = layout.partyCells)
}
