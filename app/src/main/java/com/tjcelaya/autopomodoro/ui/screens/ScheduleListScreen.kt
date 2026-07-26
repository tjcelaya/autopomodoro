package com.tjcelaya.autopomodoro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import com.tjcelaya.autopomodoro.ui.ScheduleViewModel
import com.tjcelaya.autopomodoro.ui.components.CyclePreviewStrip
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    viewModel: ScheduleViewModel,
    onAddClick: () -> Unit,
    onScheduleClick: (Int) -> Unit,
) {
    val schedules by viewModel.schedules.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("autopomodoro") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add schedule")
            }
        },
    ) { padding ->
        if (schedules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No schedules yet", style = MaterialTheme.typography.bodyLarge)
                Text("Tap + to create one", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onClick = { onScheduleClick(schedule.id) },
                        onToggle = { viewModel.toggleEnabled(schedule) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: PomodoroSchedule,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${schedule.daysOn} on / ${schedule.daysOff} off  •  " +
                                "${schedule.windowStart.format(timeFmt)}–${schedule.windowEnd.format(timeFmt)}  •  " +
                                "every ${schedule.intervalMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = { onToggle() },
                )
            }
            CyclePreviewStrip(
                schedule = schedule,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
