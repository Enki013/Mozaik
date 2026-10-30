package com.personalrecords.app.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.ActivityItemDto
import com.personalrecords.app.core.network.model.BookDto
import com.personalrecords.app.core.network.model.FilmDto
import com.personalrecords.app.core.network.model.FoodEntryDto
import com.personalrecords.app.core.network.model.HealthRecordDto
import com.personalrecords.app.core.network.model.MusicScrobbleDto
import com.personalrecords.app.core.network.model.NotificationDto
import com.personalrecords.app.ui.screens.music.MusicDetailBottomSheet
import com.personalrecords.app.core.theme.*

// Health record and activity item detail content.

@Composable
fun HealthRecordDetailContent(
    record: HealthRecordDto,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categoryLabel = when (record.category) {
        "activity" -> stringResource(R.string.health_cat_activity)
        "sleep" -> stringResource(R.string.health_cat_sleep)
        "vitals" -> stringResource(R.string.health_cat_vitals)
        "exercise" -> stringResource(R.string.health_cat_exercise)
        "body" -> stringResource(R.string.health_cat_body)
        else -> stringResource(R.string.title_health_connect)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = M3Shapes.CornerFull,
                color = recordCategoryColors(RecordCategory.Health).container
            ) {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = recordCategoryColors(RecordCategory.Health).onContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (record.record_type.isNotBlank()) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = record.record_type.replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            val dateTimeText = listOf(record.formattedDate, record.formattedTime)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (dateTimeText.isNotBlank()) {
                Text(
                    text = dateTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = record.title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (record.subtitle.isNotBlank()) {
            Text(
                text = record.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (record.start_time.isNotBlank() || record.data_origin.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (record.start_time.isNotBlank()) {
                        val startFormatted = record.start_time.replace('T', ' ').take(19)
                        val endFormatted = record.end_time.replace('T', ' ').take(19)
                        val windowStr = when {
                            startFormatted.isBlank() -> endFormatted
                            endFormatted.isBlank() || endFormatted == startFormatted -> startFormatted
                            startFormatted.take(10) == endFormatted.take(10) -> {
                                val date = startFormatted.take(10)
                                val startTime = startFormatted.drop(11)
                                val endTime = endFormatted.drop(11)
                                "$date · $startTime → $endTime"
                            }
                            else -> "$startFormatted → $endFormatted"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.health_detail_time_range),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = windowStr,
                                    style = MaterialTheme.typography.bodySmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (record.start_time.isNotBlank() && record.data_origin.isNotBlank()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }

                    if (record.data_origin.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.health_detail_source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = record.data_origin,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val textToCopy = "${record.title}\n${record.subtitle}\n${record.formattedDate} ${record.formattedTime}".trim()
                    copyToClipboard(context, "Health Connect", textToCopy)
                },
                shapes = expressiveButtonShapes(),
                modifier = Modifier
                    .weight(1f)
                    ,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.notifications_copy_text),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onClose,
                shapes = expressiveButtonShapes(),
                modifier = Modifier
                    .weight(1f)
                    ,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthRecordDetailBottomSheet(
    record: HealthRecordDto,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        HealthRecordDetailContent(
            record = record,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun HealthRecordDetailPaneCard(
    record: HealthRecordDto,
    onClose: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp)
        ) {
            HealthRecordDetailContent(record = record, onClose = onClose)
        }
    }
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.str(key: String): String {
    return this[key]?.toString()?.removeSurrounding("\"")?.takeIf { it != "null" } ?: ""
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.dbl(key: String): Double {
    return str(key).toDoubleOrNull() ?: 0.0
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.intVal(key: String): Int {
    return str(key).toIntOrNull() ?: 0
}

@Composable
fun ActivityItemDetailBottomSheet(
    item: ActivityItemDto,
    onDismiss: () -> Unit
) {
    val d = item.details
    when (item.source) {
        "fatsecret" -> {
            val food = FoodEntryDto(
                food_entry_id = d.str("food_entry_id").ifBlank { item.id },
                date = item.date,
                meal = d.str("meal").ifBlank { item.subtitle },
                food_entry_name = item.title,
                food_entry_description = d.str("description").ifBlank { item.subtitle },
                calories = d.dbl("calories"),
                carbohydrate = d.dbl("carbohydrate"),
                protein = d.dbl("protein"),
                fat = d.dbl("fat")
            )
            FoodEntryDetailBottomSheet(item = food, onDismiss = onDismiss)
        }
        "letterboxd" -> {
            val film = FilmDto(
                film_key = d.str("film_key").ifBlank { item.id },
                name = item.title,
                year = d.intVal("year").takeIf { it > 0 } ?: item.subtitle.toIntOrNull(),
                rating = d.dbl("rating").takeIf { it > 0 },
                rewatch = item.rewatch || d.str("rewatch").equals("true", ignoreCase = true) || d.str("rewatch") == "1",
                review = d.str("review"),
                image_url = item.image_url ?: d.str("image_url").ifBlank { null },
                watched_date = item.date
            )
            FilmDetailBottomSheet(film = film, onDismiss = onDismiss)
        }
        "goodreads" -> {
            val book = BookDto(
                book_id = d.str("book_id").ifBlank { item.id },
                title = item.title,
                author = d.str("author").ifBlank { item.subtitle },
                my_rating = d.dbl("my_rating").takeIf { it > 0 } ?: d.dbl("rating"),
                average_rating = d.dbl("average_rating"),
                number_of_pages = d.intVal("number_of_pages"),
                exclusive_shelf = d.str("exclusive_shelf"),
                image_url = item.image_url ?: d.str("image_url").ifBlank { null },
                date_read = item.date
            )
            BookDetailBottomSheet(book = book, onDismiss = onDismiss)
        }
        "health_connect" -> {
            val rec = HealthRecordDto(
                id = d.str("id").ifBlank { item.id },
                record_type = d.str("record_type").ifBlank { item.type },
                category = d.str("category").ifBlank { "activity" },
                date_iso = item.date,
                start_time = d.str("start_time").ifBlank { item.timestamp ?: item.date },
                end_time = d.str("end_time").ifBlank { item.timestamp ?: item.date },
                value_primary = d.dbl("value_primary").takeIf { it > 0 },
                value_secondary = d.dbl("value_secondary").takeIf { it > 0 },
                unit = d.str("unit"),
                title = item.title,
                subtitle = item.subtitle,
                data_origin = d.str("data_origin")
            )
            HealthRecordDetailBottomSheet(record = rec, onDismiss = onDismiss)
        }
        "music" -> {
            val scrobble = MusicScrobbleDto(
                id = d.str("id").ifBlank { item.id.removePrefix("music_") },
                track = d.str("track").ifBlank { item.title },
                artist = d.str("artist").ifBlank { item.subtitle.substringBefore(" • ") },
                album = d.str("album").ifBlank { if (item.subtitle.contains(" • ")) item.subtitle.substringAfter(" • ") else "" },
                albumArtist = d.str("album_artist"),
                duration = d.intVal("duration"),
                listenedAt = item.timestamp ?: item.date,
                uts = d.str("uts").toLongOrNull() ?: 0L,
                source = d.str("source").ifBlank { "lastfm" },
                originUrl = d.str("origin_url"),
                imageUrl = item.image_url ?: d.str("image_url")
            )
            MusicDetailBottomSheet(scrobble = scrobble, onDismiss = onDismiss)
        }
        else -> {
            val postTime = item.timestamp?.takeIf { it.length >= 16 }
                ?: if (!item.time.isNullOrBlank()) "${item.date}T${item.time}" else item.date
            val notif = NotificationDto(
                id = item.id,
                package_name = d.str("package_name"),
                app_name = d.str("app_name"),
                title = item.title,
                text = item.subtitle,
                sub_text = d.str("sub_text"),
                big_text = d.str("big_text"),
                category = d.str("category"),
                post_time = postTime
            )
            NotificationDetailBottomSheet(notif = notif, onDismiss = onDismiss)
        }
    }
}
