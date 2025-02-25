package io.pawsomepals.app.service.weather

// WeatherInfo.kt
data class WeatherInfo(
    val temp: Double,
    val condition: String,
    val icon: String,
    val isPlaydateSafe: Boolean
) {
    fun getDisplayTemp(): String = "${temp.toInt()}°F"

    fun getWeatherEmoji(): String = when(condition.lowercase()) {
        "clear" -> "☀️"
        "clouds" -> "☁️"
        "rain" -> "🌧️"
        "snow" -> "❄️"
        else -> "🌤️"
    }

    fun getPlaydateWarning(): String? = when {
        temp > 95 -> "Too hot for outdoor activities"
        temp < 32 -> "Too cold for outdoor activities"
        !isPlaydateSafe -> "Weather may not be suitable"
        else -> null
    }
}