package com.campus.spaces

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.campus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun ChoiceMenu(
    label: String,
    selected: String,
    choices: Map<String, String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CampusSecondaryButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(label + "：" + (choices[selected] ?: selected), maxLines = 2)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, value) ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersDialog(initial: SpaceFilters, onDismiss: () -> Unit, onApply: (SpaceFilters) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var distance by remember { mutableStateOf(initial.maxDistance.toInt().toString()) }
    var choosingOrigin by remember { mutableStateOf(false) }

    var duration by remember {
        mutableStateOf(initial.durationMinutes.takeIf { it > 0 }?.toString().orEmpty())
    }
    val hasPlannedTime = draft.startAt.isNotEmpty() || (duration.toIntOrNull() ?: 0) > 0
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var datePicker by remember { mutableStateOf<DatePickerDialog?>(null) }
    var timePicker by remember { mutableStateOf<TimePickerDialog?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            datePicker?.dismiss()
            timePicker?.dismiss()
        }
    }
    fun chooseTime() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        parseInstant(draft.startAt)?.let { calendar.timeInMillis = it }
        datePicker?.dismiss()
        timePicker?.dismiss()
        datePicker =
            DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        calendar.set(Calendar.YEAR, year)
                        calendar.set(Calendar.MONTH, month)
                        calendar.set(Calendar.DAY_OF_MONTH, day)
                        timePicker =
                            TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                                        calendar.set(Calendar.MINUTE, minute)
                                        calendar.set(Calendar.SECOND, 0)
                                        calendar.set(Calendar.MILLISECOND, 0)
                                        val format =
                                            SimpleDateFormat(
                                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                                Locale.US,
                                            )
                                        format.timeZone = TimeZone.getTimeZone("UTC")
                                        draft = draft.copy(startAt = format.format(calendar.time))
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    true,
                                )
                                .also { it.show() }
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH),
                )
                .also { it.show() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = CampusComponents.DialogMaxHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(CampusSpacing.ExtraLarge),
                verticalArrangement = Arrangement.spacedBy(CampusSpacing.Medium),
            ) {
                Text("筛选", style = MaterialTheme.typography.headlineMedium)
                CampusSecondaryButton(onClick = { choosingOrigin = true }, modifier = Modifier.fillMaxWidth()) {
                    CampusIcon(CampusSymbol.Pin)
                    Spacer(Modifier.width(CampusSpacing.Small))
                    Text(if (draft.hasLocation) draft.originLabel.ifBlank { "已选起点" } else "地图选择起点", maxLines = 2)
                }
                ChoiceMenu("空间类型", draft.type, spaceTypes) { draft = draft.copy(type = it) }
                ChoiceMenu("排序", draft.sort, sortNames) { draft = draft.copy(sort = it) }
                CampusTextInput(
                    distance,
                    { distance = it.take(10) },
                    label = { Text("距离范围 · 米") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                )
                ChoiceMenu(
                    "最低安静等级",
                    draft.minQuiet?.toString().orEmpty(),
                    linkedMapOf("" to "不限") +
                        (1..5).associate { it.toString() to (it.toString() + " 级及以上") },
                ) {
                    draft = draft.copy(minQuiet = it.toIntOrNull())
                }
                ChoiceMenu(
                    "最高拥挤率",
                    draft.maxOccupancy?.toString().orEmpty(),
                    linkedMapOf("" to "不限", "0.4" to "40%", "0.7" to "70%", "1.0" to "100%"),
                ) {
                    draft = draft.copy(maxOccupancy = it.toDoubleOrNull())
                }
                Text("所需设施", style = MaterialTheme.typography.labelLarge)
                facilityNames.forEach { (key, title) ->
                    Row {
                        Checkbox(
                            key in draft.facilities,
                            {
                                draft =
                                    draft.copy(
                                        facilities =
                                            if (it) draft.facilities + key
                                            else draft.facilities - key,
                                    )
                            },
                        )
                        Text(title, Modifier.padding(top = CampusSpacing.Medium))
                    }
                }
                if (!hasPlannedTime) {
                    Row {
                        Checkbox(draft.openOnly, { draft = draft.copy(openOnly = it) })
                        Text("仅显示当前开放", Modifier.padding(top = CampusSpacing.Medium))
                    }
                }
                Text("预计学习", style = MaterialTheme.typography.labelLarge)
                CampusSecondaryButton(onClick = ::chooseTime) {
                    Text(
                        if (draft.startAt.isEmpty()) "选择预计开始时间"
                        else displayTime(draft.startAt) + "（北京时间）",
                    )
                }
                if (draft.startAt.isNotEmpty())
                    TextButton(onClick = { draft = draft.copy(startAt = "") }) { Text("使用当前时间") }
                CampusTextInput(
                    duration,
                    { duration = it.filter(Char::isDigit).take(4) },
                    label = { Text("学习时长 · 分钟") },
                    placeholder = { Text("不限时长") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                )
                if (hasPlannedTime) SupportingText("按预计学习时段筛选，空位以实时数据为准")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                CampusPrimaryButton(
                    onClick = {
                        val d = distance.toDoubleOrNull()
                        val lat = draft.latitude
                        val lon = draft.longitude
                        val minutes = duration.ifBlank { "0" }.toIntOrNull()
                        if (d == null || !d.isFinite() || d !in 0.0..1000000.0)
                            error = "距离须为 0—1000000 米"
                        else if (
                            lat == null ||
                                lon == null ||
                                !lat.isFinite() ||
                                !lon.isFinite() ||
                                lat !in -90.0..90.0 ||
                                lon !in -180.0..180.0
                        )
                            error = "请先在地图上选择起点"
                        else if (minutes == null || minutes !in 0..1440) error = "时长须为 0—1440 分钟"
                        else
                            onApply(
                                draft.copy(
                                    maxDistance = d,
                                    latitude = lat,
                                    longitude = lon,
                                    durationMinutes = minutes,
                                ),
                            )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("应用筛选")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
        }
    }
    if (choosingOrigin) OriginPicker(
        initial = if (draft.hasLocation) OriginPlace(draft.latitude!!, draft.longitude!!, draft.originLabel.ifBlank { "已选起点" }) else null,
        onDismiss = { choosingOrigin = false },
        onConfirm = { place ->
            draft = draft.copy(latitude = place.latitude, longitude = place.longitude, originLabel = place.title)
            error = null
            choosingOrigin = false
        },
    )
}
