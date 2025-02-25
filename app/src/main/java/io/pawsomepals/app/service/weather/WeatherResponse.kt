package io.pawsomepals.app.service.weather

// WeatherResponse.kt
data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>,
    val name: String
) {
    data class Main(
        val temp: Double,
        val feels_like: Double,
        val humidity: Int
    )

    data class Weather(
        val id: Int,
        val main: String,
        val description: String,
        val icon: String
    )
}