package com.personalrecords.app.core.network.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

object RewatchSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Rewatch", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        return try {
            val jsonInput = decoder as? JsonDecoder
            if (jsonInput != null) {
                val element = jsonInput.decodeJsonElement()
                if (element is JsonPrimitive) {
                    element.booleanOrNull ?: run {
                        val str = element.content.trim()
                        str.equals("Yes", ignoreCase = true) ||
                        str.equals("true", ignoreCase = true) ||
                        str == "1"
                    }
                } else false
            } else {
                decoder.decodeBoolean()
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }
}

@Serializable
data class HealthResponse(
    val ok: Boolean = true
)

@Immutable
@Serializable
data class DashboardDto(
    val counts: Map<String, Int> = emptyMap(),
    val today: Map<String, JsonElement> = emptyMap(),
    val connections: Map<String, JsonElement> = emptyMap(),
    val last_sync: Map<String, JsonElement> = emptyMap(),
    @SerialName("calorie_target")
    val calorieTarget: Int? = null
)

@Immutable
@Serializable
data class ActivityItemDto(
    val id: String = "",
    val source: String = "", // "fatsecret", "goodreads", "letterboxd"
    val type: String = "",   // "meal", "book", "film"
    val date: String = "",   // YYYY-MM-DD
    val time: String? = null,
    val timestamp: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val image_url: String? = null,
    val rewatch: Boolean = false,
    val details: Map<String, JsonElement> = emptyMap()
)

@Immutable
@Serializable
data class ActivityDayGroupDto(
    val date: String = "",
    val label: String = "",
    val item_count: Int = 0,
    val summary: Map<String, JsonElement> = emptyMap(),
    val items: List<ActivityItemDto> = emptyList()
)

@Immutable
@Serializable
data class ActivityResponseDto(
    val days: Int = 0,
    val total: Int = 0,
    val items: List<ActivityItemDto> = emptyList(),
    val groups: List<ActivityDayGroupDto> = emptyList()
)

@Immutable
@Serializable
data class FoodEntryDto(
    val food_entry_id: String = "",
    val date_int: Int = 0,
    val date: String = "",
    val date_str: String = "",
    val meal: String = "",
    val food_entry_name: String = "",
    val name: String = "",
    val food_entry_description: String = "",
    val amount: String = "",
    val calories: Double = 0.0,
    val carbohydrate: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0
) {
    val displayName: String
        get() = if (food_entry_name.isNotBlank()) food_entry_name else name

    val displayAmount: String
        get() = if (food_entry_description.isNotBlank()) food_entry_description else amount

    val displayDate: String
        get() = if (date.isNotBlank()) date else date_str
}

@Serializable
data class FoodEntriesResponseDto(
    val items: List<FoodEntryDto> = emptyList()
)

@Immutable
@Serializable
data class DailySummaryDto(
    val date_int: Int = 0,
    val date: String = "",
    val date_str: String = "",
    val entry_count: Int = 0,
    val entries_count: Int = 0,
    val calories: Double = 0.0,
    val total_calories: Double = 0.0
) {
    val displayDate: String
        get() = if (date.isNotBlank()) date else date_str

    val displayCount: Int
        get() = if (entry_count > 0) entry_count else entries_count

    val displayCalories: Double
        get() = if (calories > 0) calories else total_calories
}

@Serializable
data class DailySummaryResponseDto(
    val items: List<DailySummaryDto> = emptyList()
)

@Immutable
@Serializable
data class BookDto(
    val book_id: String = "",
    val title: String = "",
    val author: String = "",
    val my_rating: Double = 0.0,
    val average_rating: Double = 0.0,
    val number_of_pages: Int = 0,
    val exclusive_shelf: String = "",
    val image_url: String? = null,
    val date_read: String = "",
    val year_published: Int = 0
)

@Serializable
data class BooksResponseDto(
    val items: List<BookDto> = emptyList()
)

@Immutable
@Serializable
data class FilmDto(
    val film_key: String = "",
    val name: String? = null,
    val title: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    @Serializable(with = RewatchSerializer::class)
    val rewatch: Boolean = false,
    val review: String? = null,
    val image_url: String? = null,
    val watched_date: String? = null,
    val diary_date: String? = null,
    val imported_at: String? = null
) {
    val displayTitle: String
        get() = name?.ifBlank { null } ?: title ?: ""

    val safeYear: Int
        get() = year ?: 0

    val safeRating: Double
        get() = rating ?: 0.0

    val safeWatchedDate: String
        get() = watched_date ?: ""

    val safeReview: String
        get() = review ?: ""
}

@Serializable
data class FilmsResponseDto(
    val items: List<FilmDto> = emptyList()
)

@Serializable
data class SyncFatSecretRequest(
    val days: Int = 30
)

@Serializable
data class SyncResultDto(
    val success: Boolean = true,
    val inserted: Int = 0,
    val updated: Int = 0,
    val message: String = "",
    val error: String? = null
)

@Serializable
data class CalorieTargetResponseDto(
    val success: Boolean = true,
    val target: Int? = null,
    @SerialName("calorie_target")
    val calorieTarget: Int? = null,
    val message: String = "",
    val error: String? = null
)

@Serializable
data class FatSecretSettingsDto(
    val has_keys: Boolean = false,
    val is_connected: Boolean = false,
    val consumer_key: String = "",
    val raw_consumer_key: String = "",
    val success: Boolean = true,
    val error: String? = null
)

@Serializable
data class SaveFatSecretKeysRequest(
    val consumer_key: String = "",
    val consumer_secret: String = ""
)

@Serializable
data class SaveFatSecretKeysResponse(
    val success: Boolean = true,
    val message: String = "",
    val error: String? = null
)

@Serializable
data class SyncSettingsDto(
    val feed_url: String = "",
    val interval_minutes: Int = 360,
    val enabled: Boolean = false,
    val success: Boolean = true,
    val error: String? = null
)

@Serializable
data class SaveSyncSettingsRequest(
    val feed_url: String = "",
    val interval_minutes: Int = 360,
    val enabled: Boolean = true
)

@Serializable
data class SaveSyncSettingsResponse(
    val success: Boolean = true,
    val feed_url: String = "",
    val message: String = "",
    val error: String? = null
)

@Immutable
@Serializable
data class NotificationDto(
    val id: String = "",
    val package_name: String = "",
    val app_name: String = "",
    val title: String = "",
    val text: String = "",
    val sub_text: String = "",
    val big_text: String = "",
    val category: String = "",
    val post_time: String = "",
    val notification_key: String = "",
    val is_ongoing: Int = 0,
    val created_at: String = ""
) {
    val displayTitle: String
        get() = if (title.isNotBlank()) title else (if (app_name.isNotBlank()) app_name else package_name)

    val displayBody: String
        get() = if (text.isNotBlank()) text else (if (big_text.isNotBlank()) big_text else sub_text)

    val displayAppName: String
        get() = if (app_name.isNotBlank()) app_name else package_name.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    val formattedTime: String
        get() = if (post_time.length >= 16) post_time.substring(11, 16) else ""

    val formattedDate: String
        get() = if (post_time.length >= 10) post_time.substring(0, 10) else ""
}

@Serializable
data class NotificationsResponse(
    val count: Int = 0,
    val items: List<NotificationDto> = emptyList()
)


