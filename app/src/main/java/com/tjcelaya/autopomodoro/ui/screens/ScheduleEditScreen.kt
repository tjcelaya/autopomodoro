package com.tjcelaya.autopomodoro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import com.tjcelaya.autopomodoro.ui.ScheduleViewModel
import com.tjcelaya.autopomodoro.ui.components.CyclePreviewStrip
import com.tjcelaya.autopomodoro.ui.components.DatePickerDialog
import com.tjcelaya.autopomodoro.ui.components.TimePickerDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Parses the cooldown minutes text field into a positive [Int], or `null` if the input is
 * blank, whitespace, non-numeric, zero, negative, or otherwise unparsable (including values
 * too large to fit in an Int). Mirrors the "null or non-positive means no cooldown" contract
 * documented on [PomodoroSchedule.cooldownMinutes].
 */
internal fun parseCooldownMinutesInput(text: String): Int? =
    text.trim().toIntOrNull()?.takeIf { it > 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    viewModel: ScheduleViewModel,
    scheduleId: Int?,
    onBack: () -> Unit,
) {
    val isNew = scheduleId == null || scheduleId == 0

    var name by remember { mutableStateOf("") }
    var cycleStartDate by remember { mutableStateOf(LocalDate.now()) }
    var daysOn by remember { mutableIntStateOf(4) }
    var daysOff by remember { mutableIntStateOf(3) }
    var windowStart by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var windowEnd by remember { mutableStateOf(LocalTime.of(17, 0)) }
    var intervalMinutes by remember { mutableIntStateOf(60) }
    var isEnabled by remember { mutableStateOf(true) }
    var cooldownEnabled by remember { mutableStateOf(false) }
    var cooldownMinutesText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(isNew) }

    // Load existing schedule when editing
    if (!isNew) {
        LaunchedEffect(scheduleId) {
            val existing = viewModel.getById(scheduleId!!) ?: return@LaunchedEffect
            name = existing.name
            cycleStartDate = existing.cycleStartDate
            daysOn = existing.daysOn
            daysOff = existing.daysOff
            windowStart = existing.windowStart
            windowEnd = existing.windowEnd
            intervalMinutes = existing.intervalMinutes
            isEnabled = existing.isEnabled
            cooldownEnabled = (existing.cooldownMinutes ?: 0) > 0
            cooldownMinutesText = existing.cooldownMinutes?.takeIf { it > 0 }?.toString() ?: ""
            loaded = true
        }
    }

    // Resolved cooldown value: null whenever the toggle is off, or the field doesn't parse
    // to a positive number. Threaded into every PomodoroSchedule(...) construction below.
    val cooldownMinutes = if (cooldownEnabled) parseCooldownMinutesInput(cooldownMinutesText) else null

    // Picker dialog state
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            title = "Cycle Start Date",
            initial = cycleStartDate,
            onConfirm = { cycleStartDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showStartTimePicker) {
        TimePickerDialog(
            title = "Window Start",
            initial = windowStart,
            onConfirm = { windowStart = it; showStartTimePicker = false },
            onDismiss = { showStartTimePicker = false },
        )
    }
    if (showEndTimePicker) {
        TimePickerDialog(
            title = "Window End",
            initial = windowEnd,
            onConfirm = { windowEnd = it; showEndTimePicker = false },
            onDismiss = { showEndTimePicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New Schedule" else "Edit Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = {
                            viewModel.delete(
                                PomodoroSchedule(
                                    id = scheduleId!!,
                                    name = name,
                                    cycleStartDate = cycleStartDate,
                                    daysOn = daysOn,
                                    daysOff = daysOff,
                                    windowStart = windowStart,
                                    windowEnd = windowEnd,
                                    intervalMinutes = intervalMinutes,
                                    cooldownMinutes = cooldownMinutes,
                                )
                            )
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Enabled toggle
            if (!isNew) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                    )
                }
            }

            // Cycle start date
            Text("Cycle Start Date", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(cycleStartDate.format(dateFmt))
            }

            // Days on / off
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = if (daysOn == 0) "" else daysOn.toString(),
                    onValueChange = { daysOn = it.toIntOrNull() ?: 0 },
                    label = { Text("Days On") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = if (daysOff == 0) "" else daysOff.toString(),
                    onValueChange = { daysOff = it.toIntOrNull() ?: 0 },
                    label = { Text("Days Off") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            // Time window
            Text("Active Window", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier.weight(1f),
                ) { Text(windowStart.format(timeFmt)) }
                OutlinedButton(
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier.weight(1f),
                ) { Text(windowEnd.format(timeFmt)) }
            }

            // Interval
            OutlinedTextField(
                value = if (intervalMinutes == 0) "" else intervalMinutes.toString(),
                onValueChange = { intervalMinutes = it.toIntOrNull() ?: 0 },
                label = { Text("Repeat Interval (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            // Cooldown (optional) — toggle reveals the minutes field, matching how
            // "Enabled" reads above. Off (or an unparsable/non-positive field) means no
            // cooldown phase at all, per PomodoroSchedule.cooldownMinutes's contract.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Cooldown After Active Period", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = cooldownEnabled,
                    onCheckedChange = { cooldownEnabled = it },
                )
            }
            if (cooldownEnabled) {
                OutlinedTextField(
                    value = cooldownMinutesText,
                    onValueChange = { cooldownMinutesText = it },
                    label = { Text("Cooldown Minutes") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            // Live cycle preview — reacts to changes in cycleStartDate, daysOn, daysOff
            if (daysOn > 0 && (daysOn + daysOff) > 0) {
                Text("Cycle Preview", style = MaterialTheme.typography.labelMedium)
                CyclePreviewStrip(
                    schedule = PomodoroSchedule(
                        id = 0,
                        name = "",
                        cycleStartDate = cycleStartDate,
                        daysOn = daysOn,
                        daysOff = daysOff,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        intervalMinutes = intervalMinutes.coerceAtLeast(1),
                        cooldownMinutes = cooldownMinutes,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val schedule = PomodoroSchedule(
                        id = scheduleId ?: 0,
                        name = name.ifBlank { "Untitled" },
                        cycleStartDate = cycleStartDate,
                        daysOn = daysOn.coerceAtLeast(1),
                        daysOff = daysOff.coerceAtLeast(0),
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        intervalMinutes = intervalMinutes.coerceAtLeast(1),
                        isEnabled = isEnabled,
                        cooldownMinutes = cooldownMinutes,
                    )
                    viewModel.save(schedule)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && daysOn > 0 && intervalMinutes > 0,
            ) {
                Text("Save")
            }

            if (!isNew) {
                OutlinedButton(
                    onClick = {
                        viewModel.delete(
                            PomodoroSchedule(
                                id = scheduleId!!,
                                name = name,
                                cycleStartDate = cycleStartDate,
                                daysOn = daysOn,
                                daysOff = daysOff,
                                windowStart = windowStart,
                                windowEnd = windowEnd,
                                intervalMinutes = intervalMinutes,
                                cooldownMinutes = cooldownMinutes,
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            }
        }
    }
}
