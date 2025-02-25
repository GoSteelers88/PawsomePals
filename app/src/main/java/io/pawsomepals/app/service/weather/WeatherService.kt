package io.pawsomepals.app.service.weather

import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WeatherService @Inject constructor(
    private val api: OpenWeatherApi,
    private val remoteConfig: RemoteConfigManager
) {
    suspend fun getWeather(lat: Double, lon: Double): WeatherInfo {
        return withContext(Dispatchers.IO) {
            val response = api.getCurrentWeather(
                lat = lat,
                lon = lon,
                apiKey = remoteConfig.getWeatherKey()
            )
            WeatherInfo(
                temp = response.main.temp,
                condition = response.weather.first().description,
                icon = response.weather.first().icon,
                isPlaydateSafe = isPlaydateWeather(response)
            )
        }
    }


    private fun isPlaydateWeather(response: WeatherResponse): Boolean {
        return when {
            response.main.temp > 95 -> false  // Too hot
            response.main.temp < 32 -> false  // Too cold
            response.weather.any { it.main == "Rain" || it.main == "Snow" } -> false
            else -> true
        }
    }
}