package com.personalrecords.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ISO date helpers and the date / date-range filter dialog.

enum class DateSelectionMode {
    SINGLE,
    RANGE
}

private val utcIsoFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
}

private val localIsoParser = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
}

fun formatMillisToIsoDate(millis: Long): String {
    return utcIsoFormatter.get()!!.format(Date(millis))
}

fun parseIsoDateToMillis(dateStr: String?): Long? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val clean = dateStr.trim().replace('/', '-')
        utcIsoFormatter.get()!!.parse(clean)?.time
    } catch (_: Exception) {
        null
    }
}

fun formatIsoToReadableDate(dateStr: String): String {
    return try {
        val clean = dateStr.trim().replace('/', '-')
        val date = localIsoParser.get()!!.parse(clean) ?: return dateStr
        val outputFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        outputFormatter.format(date)
    } catch (_: Exception) {
        dateStr
    }
}

fun formatIsoToShortDate(dateStr: String): String {
    return try {
        val clean = dateStr.trim().replace('/', '-')
        val date = localIsoParser.get()!!.parse(clean) ?: return dateStr
        val outputFormatter = SimpleDateFormat("d MMM", Locale.getDefault())
        outputFormatter.format(date)
    } catch (_: Exception) {
        dateStr
    }
}

fun formatDateRangeChipLabel(
    singleDate: String?,
    fromDate: String?,
    toDate: String?,
    defaultText: String
): String {
    return when {
        !singleDate.isNullOrBlank() -> formatIsoToReadableDate(singleDate)
        !fromDate.isNullOrBlank() && !toDate.isNullOrBlank() -> {
            "${formatIsoToShortDate(fromDate)} – ${formatIsoToShortDate(toDate)}"
        }
        !fromDate.isNullOrBlank() -> "${formatIsoToShortDate(fromDate)} →"
        !toDate.isNullOrBlank() -> "→ ${formatIsoToShortDate(toDate)}"
        else -> defaultText
    }
}

/**
 * Material 3 Expressive Date & Date Range Picker Dialog.
 * Allows switching seamlessly between single day selection and date range selection with spring transitions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveDateFilterDialog(
    initialStartDate: String? = null,
    initialEndDate: String? = null,
    onDismissRequest: () -> Unit,
    onDateSelected: (String) -> Unit,
    onDateRangeSelected: (String, String) -> Unit,
    onClear: () -> Unit
) {
    val initialStartMillis = remember(initialStartDate) { parseIsoDateToMillis(initialStartDate) }
    val initialEndMillis = remember(initialEndDate) { parseIsoDateToMillis(initialEndDate) }

    var selectionMode by remember {
        mutableStateOf(
            if (!initialEndDate.isNullOrBlank()) DateSelectionMode.RANGE
            else DateSelectionMode.SINGLE
        )
    }

    val singleDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialStartMillis
    )

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    val haptic = LocalHapticFeedback.current
    val pickerColors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            // Up to the M3 picker width, but never wider than the window.
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = DatePickerDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // M3 Expressive Connected Button Group mode toggle
                ExpressiveConnectedButtonGroup(
                    items = listOf(DateSelectionMode.SINGLE, DateSelectionMode.RANGE),
                    selectedItem = selectionMode,
                    onItemSelected = { selectionMode = it },
                    fillWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    labelProvider = { mode ->
                        if (mode == DateSelectionMode.SINGLE) {
                            stringResource(R.string.date_picker_single_day)
                        } else {
                            stringResource(R.string.date_picker_date_range)
                        }
                    }
                )

                // Opacity crossfade must use an Effects spring (damping 1.0, no overshoot) per motion.md
                Crossfade(
                    targetState = selectionMode,
                    animationSpec = M3ExpressiveMotion.defaultEffects(),
                    label = "date_picker_mode_fade"
                ) { mode ->
                    if (mode == DateSelectionMode.SINGLE) {
                        val selectedIso = singleDatePickerState.selectedDateMillis?.let { formatMillisToIsoDate(it) }
                        val headlineText = if (selectedIso != null) {
                            formatIsoToReadableDate(selectedIso)
                        } else {
                            stringResource(R.string.filter_pick_date)
                        }
                        DatePicker(
                            state = singleDatePickerState,
                            title = null,
                            headline = {
                                Text(
                                    text = headlineText,
                                    style = MaterialTheme.typography.headlineSmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(
                                        start = 24.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 12.dp
                                    )
                                )
                            },
                            showModeToggle = false,
                            colors = pickerColors
                        )
                    } else {
                        val startIso = dateRangePickerState.selectedStartDateMillis?.let { formatMillisToIsoDate(it) }
                        val endIso = dateRangePickerState.selectedEndDateMillis?.let { formatMillisToIsoDate(it) }
                        val rangeHeadline = formatDateRangeChipLabel(
                            singleDate = null,
                            fromDate = startIso,
                            toDate = endIso,
                            defaultText = stringResource(R.string.date_picker_date_range)
                        )
                        DateRangePicker(
                            state = dateRangePickerState,
                            title = null,
                            headline = {
                                Text(
                                    text = rangeHeadline,
                                    style = MaterialTheme.typography.titleLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(
                                        start = 24.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 12.dp
                                    )
                                )
                            },
                            showModeToggle = false,
                            colors = pickerColors,
                            modifier = Modifier.height(420.dp)
                        )
                    }
                }

                // Dialog Actions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasAnySelection = !initialStartDate.isNullOrBlank() ||
                        !initialEndDate.isNullOrBlank() ||
                        singleDatePickerState.selectedDateMillis != null ||
                        dateRangePickerState.selectedStartDateMillis != null

                    if (hasAnySelection) {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onClear()
                                onDismissRequest()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.date_picker_clear),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (selectionMode == DateSelectionMode.SINGLE) {
                                singleDatePickerState.selectedDateMillis?.let { millis ->
                                    onDateSelected(formatMillisToIsoDate(millis))
                                }
                            } else {
                                val startMillis = dateRangePickerState.selectedStartDateMillis
                                val endMillis = dateRangePickerState.selectedEndDateMillis
                                if (startMillis != null && endMillis != null) {
                                    onDateRangeSelected(
                                        formatMillisToIsoDate(startMillis),
                                        formatMillisToIsoDate(endMillis)
                                    )
                                } else if (startMillis != null) {
                                    onDateSelected(formatMillisToIsoDate(startMillis))
                                }
                            }
                            onDismissRequest()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.date_picker_apply),
                            style = MaterialTheme.typography.labelLargeEmphasized
                        )
                    }
                }
            }
        }
    }
}
