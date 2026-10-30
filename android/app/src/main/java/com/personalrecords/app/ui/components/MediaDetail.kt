package com.personalrecords.app.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import com.personalrecords.app.R
import androidx.compose.foundation.horizontalScroll
import com.personalrecords.app.core.network.model.BookDto
import com.personalrecords.app.core.network.model.FilmDto
import com.personalrecords.app.core.network.model.MdbListRatingsDto
import com.personalrecords.app.core.theme.*
import java.util.Locale

// Film and book detail content, plus third-party rating badges.

@Composable
fun MediaRatingBadges(
    ratings: MdbListRatingsDto?,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (ratings == null && !isLoading) return

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpressiveLoadingIndicator(size = 14.dp, strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.ratings_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (ratings != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // IMDb
                ratings.score_imdb?.takeIf { it > 0 }?.let { score ->
                    RatingPill(
                        label = "IMDb",
                        score = String.format(Locale.US, "%.1f", score),
                        brandColor = Color(0xFFF5C518)
                    )
                }

                // Rotten Tomatoes
                ratings.score_tomatoes?.takeIf { it > 0 }?.let { score ->
                    RatingPill(
                        label = "🍅",
                        score = "$score%",
                        brandColor = Color(0xFFFA320A)
                    )
                }

                // RT Audience
                ratings.score_audience?.takeIf { it > 0 }?.let { score ->
                    RatingPill(
                        label = "🍿",
                        score = "$score%",
                        brandColor = Color(0xFFFF9800)
                    )
                }

                // Metacritic
                ratings.score_metacritic?.takeIf { it > 0 }?.let { score ->
                    RatingPill(
                        label = "Metascore",
                        score = "$score",
                        brandColor = Color(0xFF00CE7A)
                    )
                }

                // Trakt
                ratings.score_trakt?.takeIf { it > 0 }?.let { score ->
                    RatingPill(
                        label = "Trakt",
                        score = "$score%",
                        brandColor = Color(0xFFED1C24)
                    )
                }

                // Letterboxd
                ratings.score_letterboxd?.takeIf { it > 0 }?.let { score ->
                    RatingPill(
                        label = "Letterboxd",
                        score = String.format(Locale.US, "%.1f", score),
                        brandColor = Color(0xFF00E054)
                    )
                }
            }
        }
    }
}

/**
 * Third-party score: label and score in readable theme colors on a tonal pill; the service's
 * brand color only marks the dot, so light brand colors (IMDb yellow) keep their contrast.
 */
@Composable
fun RatingPill(
    label: String,
    score: String,
    brandColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = M3Shapes.CornerFull,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(brandColor, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = score,
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FilmDetailContent(
    film: FilmDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val watchedDate = film.safeWatchedDate.ifBlank { film.diary_date ?: "" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Row: Poster + Title & Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            PosterThumbnail(
                imageUrl = film.image_url,
                fallbackIcon = Icons.Rounded.Movie,
                width = 72,
                height = 104
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isMdbList = film.source.equals("mdblist", ignoreCase = true)
                    val sourceLabel = if (isMdbList) "MDBList" else stringResource(R.string.movies_badge_letterboxd)
                    val sourceColor = if (isMdbList) MaterialTheme.colorScheme.tertiary else recordCategoryColors(RecordCategory.Movies).accent
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = sourceColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmallEmphasized,
                            color = sourceColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                        )
                    }
                    if (film.isShow) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = stringResource(R.string.movies_type_show),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                    if (film.safeYear > 0) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "${film.safeYear}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                    if (film.rewatch) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = recordCategoryColors(RecordCategory.Movies).container
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Repeat,
                                    contentDescription = null,
                                    tint = recordCategoryColors(RecordCategory.Movies).onContainer,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = stringResource(R.string.movies_rewatch_badge),
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = recordCategoryColors(RecordCategory.Movies).onContainer
                                )
                            }
                        }
                    }
                }

                Text(
                    text = film.displayTitle,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (film.safeRating > 0) {
                        RatingBadge(rating = film.safeRating)
                    }
                    if (watchedDate.isNotBlank()) {
                        Text(
                            text = watchedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Full Review Box if available
        if (film.safeReview.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "“${film.safeReview}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Actions: Copy & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val copyStr = buildString {
                        append(film.displayTitle)
                        if (film.safeYear > 0) append(" (${film.safeYear})")
                        if (film.safeRating > 0) append(" · ★ ${film.safeRating}")
                        if (film.safeReview.isNotBlank()) append("\n\n${film.safeReview}")
                    }
                    copyToClipboard(context, film.displayTitle, copyStr)
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

            if (onClose != null) {
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
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmDetailBottomSheet(
    film: FilmDto,
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
        FilmDetailContent(
            film = film,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun FilmDetailPaneCard(
    film: FilmDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
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
        ) {
            FilmDetailContent(
                film = film,
                onClose = onClose
            )
        }
    }
}

@Composable
fun BookDetailContent(
    book: BookDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shelfLabel = when (book.exclusive_shelf.lowercase()) {
        "read" -> stringResource(R.string.books_shelf_read)
        "to-read" -> stringResource(R.string.books_shelf_to_read)
        "currently-reading" -> stringResource(R.string.books_shelf_currently_reading)
        else -> book.exclusive_shelf.ifBlank { stringResource(R.string.books_badge_book) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Row: Cover + Title, Author, Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            PosterThumbnail(
                imageUrl = book.image_url,
                fallbackIcon = Icons.AutoMirrored.Rounded.MenuBook,
                width = 72,
                height = 104
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = recordCategoryColors(RecordCategory.Books).container
                    ) {
                        Text(
                            text = shelfLabel,
                            style = MaterialTheme.typography.labelSmallEmphasized,
                            color = recordCategoryColors(RecordCategory.Books).onContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                        )
                    }
                    if (book.year_published > 0) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "${book.year_published}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (book.my_rating > 0) {
                        RatingBadge(rating = book.my_rating)
                    }
                    if (book.average_rating > 0) {
                        Text(
                            text = "${stringResource(R.string.badge_avg)} ★ ${String.format(Locale.US, "%.2f", book.average_rating)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Metadata surface if pages or date_read exist
        if (book.number_of_pages > 0 || book.date_read.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (book.number_of_pages > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = recordCategoryColors(RecordCategory.Books).accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.books_pages, book.number_of_pages),
                                style = MaterialTheme.typography.bodySmallEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (book.date_read.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = book.date_read,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Actions: Copy & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val copyStr = buildString {
                        append(book.title)
                        if (book.author.isNotBlank()) append(" — ${book.author}")
                        if (book.my_rating > 0) append(" (★ ${book.my_rating})")
                    }
                    copyToClipboard(context, book.title, copyStr)
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

            if (onClose != null) {
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
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailBottomSheet(
    book: BookDto,
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
        BookDetailContent(
            book = book,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun BookDetailPaneCard(
    book: BookDto,
    onClose: (() -> Unit)? = null,
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
        ) {
            BookDetailContent(
                book = book,
                onClose = onClose
            )
        }
    }
}
