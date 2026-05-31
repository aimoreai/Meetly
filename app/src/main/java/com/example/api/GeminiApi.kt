package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Moshi Compatible Models ---

data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

data class Content(
    @Json(name = "parts") val parts: List<Part>
)

data class Part(
    @Json(name = "text") val text: String
)

data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null
)

data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

data class Candidate(
    @Json(name = "content") val content: Content?
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Retrofit Client ---

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

// --- Utility Wrapper for API calls ---

object GeminiController {

    /**
     * Sends a query to Gemini and returns the response string.
     * Uses BuildConfig.GEMINI_API_KEY from user's AI Studio secrets.
     */
    suspend fun generateResponse(
        prompt: String,
        systemPrompt: String = "You are Meetly, an intelligent summer activity expert for the real-world social discovery platform 'Meetly'."
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val isSimulated = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

        if (isSimulated) {
            return@withContext getLocalVibeReply(prompt)
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.7f),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: getLocalVibeReply(prompt)
        } catch (e: Exception) {
            getLocalVibeReply(prompt)
        }
    }

    private fun getLocalVibeReply(prompt: String): String {
        val q = prompt.lowercase()
        val isRussian = q.any { it in 'а'..'я' || it in 'А'..'Я' }

        if (isRussian) {
            return when {
                q.contains("волей") || q.contains("volley") || q.contains("сетк") || q.contains("пляж") -> {
                    "Привет! Волейбол — шикарный выбор для активного вечера! 🏐 Популярные песчаные площадки в Бишкеке включают сквер на Южных воротах, корты в комплексе 'Касиет', а также открытые сетки в Карагачевой роще. В приложении сейчас как раз собираются три активные группы на вечер. Хочешь присоединиться к ним или подсказать время?"
                }
                q.contains("фут") || q.contains("foot") || q.contains("мяч") -> {
                    "Салют! Футбол — это страсть и командный дух! ⚽ Поля возле манежа КГАФКиС (Физкультурный), площадки 'Спорт-Сити' и 'Вефа-спорт' открыты для аренды. Вечером многие компании ищут по 1-2 игрока для товарищеских матчей 5х5 или 6х6. Могу помочь тебе быстро найти такую группу!"
                }
                q.contains("вело") || q.contains("cycl") || q.contains("байк") || q.contains("двухколес") -> {
                    "Отличный выбор! Велосипед — лучший способ развеяться. 🚴 Популярные маршруты: вечерние круги по бульвару Эркиндик, Южная магистраль, или затяжной подъем к селу Кок-Жар. По выходным ребята устраивают велопробеги в Ала-Арчу. Многие собираются у памятника Уркуе Салиевой. Показать активных райдеров поблизости?"
                }
                q.contains("поход") || q.contains("гор") || q.contains("хайк") || q.contains("hike") -> {
                    "Горы зовут! ⛰️ В окрестностях Бишкека есть потрясающие места: ущелье Ала-Арча (поход к водопаду или хижине Рацека), Аламединское ущелье (расслабление у Теплых ключей) или Кегети с величественным водопадом. Группы любителей хайкинга обычно выезжают рано утром в субботу/воскресенье из центра. Желаешь запланировать совместный поход?"
                }
                q.contains("игр") || q.contains("game") || q.contains("настол") || q.contains("карт") || q.contains("мафи") -> {
                    "Привет! Настольные игры — уютный и интеллектуальный отдых! 🎲 В Бишкеке можно отлично поиграть в антикафе 'Look', клубе настолок 'Чемодан' или коворкингах. Популярные игры: Catan, Кодовые имена, Имаджинариум и Мафия. Сегодня в 19:30 собирается отличная кампания. Показать подробности сходки?"
                }
                q.contains("прогул") || q.contains("walk") || q.contains("гул") || q.contains("пеш") -> {
                    "Вечерняя прогулка — идеальный способ насладиться летом! 🚶 Самые зеленые точки города: бульвар Эркиндик (Дзержинка), тенистые аллеи Дубового парка и Парк Победы. Обычно собираются группы по 3-5 человек поболтать, попить кваса или кофе и встретить закат. Показать компании, гуляющие прямо сейчас?"
                }
                else -> {
                    "Привет! Я твой верный ИИ-помощник Meetly. ☀️ Я помогу тебе найти отличную компанию для пляжного волейбола, вечерних велопрогулок, настольных игр или походов в горы Ала-Арчи. Просто напиши, чем ты хочешь заняться, и я мгновенно подберу живые группы единомышленников!"
                }
            }
        } else {
            return when {
                q.contains("volley") || q.contains("ball") || q.contains("net") -> {
                    "Hey there! Volleyball is a classic summer choice! 🏐 Beautiful beach courts are available near South Gates, Kasiet club, and Karagachevaya grove. There are some groups searching for core players right now. Would you like to check out their timings and join in?"
                }
                q.contains("foot") || q.contains("soccer") -> {
                    "Hey! Football is outstanding! ⚽ Pitches near Vefa center and Sport-City have matches on schedule tonight. Most groups are looking for 1 or 2 extra players for 5v5 action. Let me know if you want to join an active group!"
                }
                q.contains("cycl") || q.contains("bike") || q.contains("ride") -> {
                    "Awesome selection! Riding a bike is extremely refreshing. 🚴 Scenic routes include Erkindik alley, Southern Highway, or hiking up towards Kok-Jar. Bikers often gather at Urkuya Salieva monument before departure. Should I connect you with any of them?"
                }
                q.contains("hike") || q.contains("mountain") -> {
                    "The mountains are calling! ⛰️ Local favorites are Ala-Archa National Park (hike up to Ak-Sai waterfall or Ratsek cabin), Alamudun Gorge hot springs, or Kegety waterfall. Hikers usually carpool early on Saturday mornings. Would you like to view active trips?"
                }
                q.contains("game") || q.contains("board") -> {
                    "Hi! Board gaming is spectacular! 🎲 Anti-cafes like 'Look' or 'Chemodan' run daily gaming sessions with chess, Catan, and Mafia evenings. A friendly circle is hosting a game tonight at 7:00 PM. Let me know if you want to check it out!"
                }
                else -> {
                    "Hi! I am your Meetly AI social assistant. ☀️ I help you find awesome company for volleyball, football matches, nature walks, and Ala-Archa hikes here in Bishkek. Tell me what you want to do, and I will match you with active groups instantly!"
                }
            }
        }
    }
}
