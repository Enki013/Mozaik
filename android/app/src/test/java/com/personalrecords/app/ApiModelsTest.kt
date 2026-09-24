package com.personalrecords.app

import com.personalrecords.app.core.network.model.FilmsResponseDto
import com.personalrecords.app.core.network.model.FoodEntriesResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testLetterboxdFilmsDeserialization() {
        val jsonString = """
        {
          "items": [
            {
              "film_key": "lb:9d7969188a3ed8c4a2ae5a5680e7",
              "letterboxd_uri": "https://letterboxd.com/enesben_/film/american-psycho/",
              "name": "American Psycho",
              "year": 2000,
              "rating": 4.5,
              "watched_date": "2026-07-05",
              "diary_date": "Mon, 6 Jul 2026 08:56:38 +1200",
              "rewatch": "Yes",
              "tags": "",
              "review": "Watched on Sunday July 5, 2026.",
              "image_url": "https://a.ltrbxd.com/poster.jpg"
            }
          ]
        }
        """.trimIndent()

        val response = json.decodeFromString<FilmsResponseDto>(jsonString)
        assertEquals(1, response.items.size)
        val film = response.items.first()
        assertEquals("American Psycho", film.displayTitle)
        assertEquals(2000, film.safeYear)
        assertEquals(4.5, film.safeRating, 0.001)
        assertTrue(film.rewatch)
    }

    @Test
    fun testLiveLetterboxdFilmsDeserialization() {
        val stream = javaClass.classLoader?.getResourceAsStream("films.json")
        val jsonString = stream?.bufferedReader()?.use { it.readText() } ?: throw IllegalStateException("Resource not found")
        val response = json.decodeFromString<FilmsResponseDto>(jsonString)
        assertEquals(15, response.items.size)
        val first = response.items.first()
        assertEquals("American Psycho", first.displayTitle)
        assertEquals(2000, first.safeYear)
        assertEquals(0.0, first.safeRating, 0.001)
        assertTrue(first.rewatch)
    }

    @Test
    fun testFoodEntriesDeserialization() {
        val jsonString = """
        {
          "items": [
            {
              "food_entry_id": "24849275501",
              "date_int": 20714,
              "date": "2026-09-18",
              "meal": "Breakfast",
              "food_entry_name": "Peynes Süzme Peynir",
              "food_entry_description": "1 serving Peynes Süzme Peynir",
              "calories": 225,
              "carbohydrate": 4,
              "protein": 12,
              "fat": 17.5
            }
          ]
        }
        """.trimIndent()

        val response = json.decodeFromString<FoodEntriesResponseDto>(jsonString)
        assertEquals(1, response.items.size)
        val food = response.items.first()
        assertEquals("Peynes Süzme Peynir", food.displayName)
        assertEquals("1 serving Peynes Süzme Peynir", food.displayAmount)
        assertEquals("2026-09-18", food.displayDate)
        assertEquals(225.0, food.calories, 0.001)
    }
}
