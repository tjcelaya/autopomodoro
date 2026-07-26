package com.tjcelaya.autopomodoro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import com.tjcelaya.autopomodoro.scheduler.CycleCalculator
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

private val WEEKDAY_ABBR = mapOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "Th",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "Su",
)

private val dayFmt = DateTimeFormatter.ofPattern("d")
private val monthDayFmt = DateTimeFormatter.ofPattern("M/d")

private const val NUM_DAYS = 7

/**
 * A row of 7 colored boxes representing the next 7 days (today inclusive).
 * Active days use the theme primary color; off days use a muted grey.
 *
 * Below the boxes is a legend row. Tapping anywhere on the strip cycles between:
 *  0 → weekday abbreviations (M, T, W, Th, …)
 *  1 → calendar day numbers (showing month prefix only when crossing a month boundary)
 */
@Composable
fun CyclePreviewStrip(
    schedule: PomodoroSchedule,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    val days = remember(today) { (0L until NUM_DAYS).map { today.plusDays(it) } }
    val activeFlags = remember(schedule, today) {
        days.map { CycleCalculator.isActiveDay(schedule, it) }
    }

    // 0 = weekday, 1 = calendar date
    var labelMode by remember { mutableIntStateOf(0) }

    val accentColor = MaterialTheme.colorScheme.primary
    val offColor = MaterialTheme.colorScheme.outlineVariant

    // Detect month boundary for calendar-date mode
    val crossesMonth = remember(days) {
        days.any { it.month != days.first().month }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { labelMode = (labelMode + 1) % 2 },
    ) {
        // Boxes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .then(
                            if (i < days.size - 1) Modifier.width(0.dp) // spacer handled by arrangement
                            else Modifier
                        )
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (activeFlags[i]) accentColor else offColor),
                )
                if (i < days.size - 1) {
                    Box(Modifier.width(3.dp))
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEachIndexed { i, date ->
                val label = when (labelMode) {
                    0 -> WEEKDAY_ABBR[date.dayOfWeek] ?: ""
                    else -> if (crossesMonth) date.format(monthDayFmt) else date.format(dayFmt)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (activeFlags[i]) Color.Unspecified else MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                if (i < days.size - 1) {
                    Box(Modifier.width(3.dp))
                }
            }
        }
    }
}
