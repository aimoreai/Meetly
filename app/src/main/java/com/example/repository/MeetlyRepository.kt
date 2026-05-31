package com.example.repository

import android.content.Context
import androidx.room.Room
import com.example.api.GeminiController
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MeetlyRepository(private val context: Context) {

    // Room Database Init
    private val db: MeetlyDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            MeetlyDatabase::class.java,
            "meetly_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    private val dao = db.dao()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Flow Accessors
    val settingsFlow: Flow<LocalSetting?> = dao.getSettingsFlow()
    val userProfileFlow: Flow<LocalUserProfile?> = dao.getUserProfileFlow()
    val allActivityGroupsFlow: Flow<List<LocalActivityGroup>> = dao.getAllActivityGroupsFlow()
    val assistantMessagesFlow: Flow<List<LocalAssistantMessage>> = dao.getAssistantMessagesFlow()

    init {
        // Run seed on dispatcher thread to ensure nice default data works right away!
        repositoryScope.launch {
            seedInitialData()
        }
    }

    // --- Seeding Initial Data for Summery/Youth Social Discoverability Vibe ---
    private suspend fun seedInitialData() {
        // App settings seed
        if (dao.getSettingsDirect() == null) {
            dao.saveSetting(LocalSetting(id = 1))
        }

        // Active user profile seed
        if (dao.getUserProfileDirect() == null) {
            dao.saveUserProfile(
                LocalUserProfile(
                    uid = "default_user",
                    name = "Salamat",
                    profilePictureUrl = "",
                    phone = "+996 555 125 487",
                    bio = "Beach volleyball player. Casual runner. Love grabbing coffee and play board games in Bishkek.",
                    isVerified = true,
                    preferredCategories = "SportsActivities,BoardGames"
                )
            )
        }

        // Prepopulate lovely real-life activities if they are empty
        val groupsList = dao.getAllActivityGroupsFlow().firstOrNull() ?: emptyList()
        if (groupsList.isEmpty()) {
            val sampleGroups = listOf(
                LocalActivityGroup(
                    groupId = "vball_sunset_ok",
                    title = "Sunset Beach Volleyball 3v3",
                    activityType = "Volleyball",
                    description = "We are looking for two more players for a friendly 3v3 beach volleyball match at local park courts. Casual and friendly!",
                    creatorName = "Eldar",
                    date = "Today",
                    time = "18:30 (In 1 hour)",
                    locationName = "Tychyny Park courts (2.4 km away)",
                    latitude = 42.87,
                    longitude = 74.60,
                    priceStr = "Free",
                    durationStr = "2 Hours",
                    participantCount = 4,
                    maxParticipants = 6,
                    isJoined = false,
                    participantsCsv = "Eldar, Meerim, Arthur, Jamilya"
                ),
                LocalActivityGroup(
                    groupId = "board_games_alpha",
                    title = "Table Board Games & Drinks",
                    activityType = "Board Games",
                    description = "Playing Catan, Dixit, and Avalon. Beginners are welcome! We have snacks and tea ready.",
                    creatorName = "Alexander",
                    date = "Today",
                    time = "19:00",
                    locationName = "Alpha Geek Coffee (0.8 km away)",
                    latitude = 42.88,
                    longitude = 74.61,
                    priceStr = "$5 (Table charge)",
                    durationStr = "3 Hours",
                    participantCount = 3,
                    maxParticipants = 6,
                    isJoined = false,
                    participantsCsv = "Alexander, Sofia, Roman"
                ),
                LocalActivityGroup(
                    groupId = "cycling_gorge",
                    title = "Uphill Cycling Run Ala-Archa",
                    activityType = "Cycling",
                    description = "Cycling up from Southern gates in Bishkek up towards the national park inlet. High endurance required!",
                    creatorName = "Meerim",
                    date = "Tomorrow",
                    time = "08:00 AM",
                    locationName = "Southern Gates starting point (5.1 km away)",
                    latitude = 42.82,
                    longitude = 74.58,
                    priceStr = "Free",
                    durationStr = "4 Hours",
                    participantCount = 2,
                    maxParticipants = 8,
                    isJoined = false,
                    participantsCsv = "Meerim, Danila"
                ),
                LocalActivityGroup(
                    groupId = "football_5x5_arena",
                    title = "Football Catchup 5x5 Dordoi",
                    activityType = "Football",
                    description = "Need a goalkeeper and one outfield player for weekly outdoor 5v5 friendly match.",
                    creatorName = "Damir",
                    date = "Today",
                    time = "20:00",
                    locationName = "Dordoi Sport Complex (4.2 km away)",
                    latitude = 42.90,
                    longitude = 74.62,
                    priceStr = "$3 (Split pitch fee)",
                    durationStr = "1.5 Hours",
                    participantCount = 8,
                    maxParticipants = 10,
                    isJoined = false,
                    participantsCsv = "Damir, Pavel, Ivan, Tilek, Ruslan, Chingiz, Kairat, Oleg"
                ),
                LocalActivityGroup(
                    groupId = "evening_stroll_erkindik",
                    title = "Evening Stroll & Ice Cream",
                    activityType = "Walking",
                    description = "Just a relaxing breeze stroll along Erkindik Boulevard. Grab a Pistachio ice cream scoop and walk.",
                    creatorName = "Aisha",
                    date = "Today",
                    time = "21:00",
                    locationName = "Erkindik Boulevard Oak trees (1.2 km away)",
                    latitude = 42.875,
                    longitude = 74.605,
                    priceStr = "Free",
                    durationStr = "1 Hour",
                    participantCount = 1,
                    maxParticipants = 5,
                    isJoined = false,
                    participantsCsv = "Aisha"
                ),
                LocalActivityGroup(
                    groupId = "hike_panoramas",
                    title = "Sunset Panorama Hike",
                    activityType = "Hiking",
                    description = "Slightly uphill walk to the panoramic hills. Watching the Bishkek summer lights turn on. Bring windbreakers!",
                    creatorName = "Arsen",
                    date = "This Weekend",
                    time = "17:00 Saturday",
                    locationName = "Orto-Sai Panoramic Trail (6.7 km away)",
                    latitude = 42.79,
                    longitude = 74.61,
                    priceStr = "Free",
                    durationStr = "3 Hours",
                    participantCount = 4,
                    maxParticipants = 12,
                    isJoined = false,
                    participantsCsv = "Arsen, Victoria, Timur, Bermet"
                )
            )
            dao.insertActivityGroups(sampleGroups)

            // Insert matching seed messages for these pre-populated chats so they feel active right away
            sampleGroups.forEach { group ->
                dao.insertGroupMessage(
                    LocalChatMessage(
                        groupId = group.groupId,
                        senderName = "System",
                        senderId = "system",
                        messageText = "Activity Group created for '${group.title}' coordinates. Temporary chat active.",
                        isSystem = true
                    )
                )
                dao.insertGroupMessage(
                    LocalChatMessage(
                        groupId = group.groupId,
                        senderName = group.creatorName,
                        senderId = "creator",
                        messageText = "Hey everyone! Thanks for looking. Drop a message here once you join so we can split logistics!",
                        timestamp = System.currentTimeMillis() - 3600000
                    )
                )
            }
        }
    }

    // --- Preference Handlers ---

    suspend fun saveSetting(selectedLanguage: String, themeMode: String, notifications: Boolean, incognito: Boolean) {
        val current = dao.getSettingsDirect() ?: LocalSetting()
        dao.saveSetting(
            current.copy(
                selectedLanguage = selectedLanguage,
                themeMode = themeMode,
                notificationsEnabled = notifications,
                incognitoMode = incognito
            )
        )
    }

    suspend fun updateOnboardingFinished(finished: Boolean) {
        val current = dao.getSettingsDirect() ?: LocalSetting()
        dao.saveSetting(current.copy(isOnboarded = finished))
    }

    suspend fun updateUserLoggedIn(loggedIn: Boolean) {
        val current = dao.getSettingsDirect() ?: LocalSetting()
        dao.saveSetting(current.copy(isLoggedIn = loggedIn))
    }

    suspend fun updateProfile(name: String, bio: String, phone: String, preferredCategories: String, profilePictureUrl: String = "") {
        dao.saveUserProfile(
            LocalUserProfile(
                uid = "default_user",
                name = name,
                bio = bio,
                phone = phone,
                profilePictureUrl = profilePictureUrl,
                isVerified = true,
                preferredCategories = preferredCategories
            )
        )
    }

    // --- Intention Hub Actions ---

    suspend fun insertCustomActivityGroup(group: LocalActivityGroup) {
        dao.insertActivityGroup(group)
        // Add welcome message
        dao.insertGroupMessage(
            LocalChatMessage(
                groupId = group.groupId,
                senderName = "System",
                senderId = "system",
                messageText = "Temporary Group '${group.title}' started. Real-time GPS coordinates matching nearby users.",
                isSystem = true
            )
        )
    }

    fun getGroupMessagesFlow(groupId: String): Flow<List<LocalChatMessage>> {
        return dao.getGroupMessagesFlow(groupId)
    }

    suspend fun sendChatMessage(groupId: String, text: String, senderName: String, senderId: String = "me", isSystem: Boolean = false) {
        dao.insertGroupMessage(
            LocalChatMessage(
                groupId = groupId,
                senderName = senderName,
                senderId = senderId,
                messageText = text,
                isSystem = isSystem
            )
        )
    }

    suspend fun joinOrLeaveGroup(groupId: String, userProfile: LocalUserProfile?): Boolean {
        val group = dao.getActivityGroupDirect(groupId) ?: return false
        val myName = userProfile?.name ?: "Salamat"

        return if (group.isJoined) {
            // Leave
            val list = group.participantsCsv.split(",").map { it.trim() }.filter { it != myName && it.isNotEmpty() }
            val updatedCsv = list.joinToString(", ")
            val updatedCount = (group.participantCount - 1).coerceAtLeast(1)
            dao.updateGroupJoinStatus(groupId, false, updatedCount, updatedCsv)
            sendChatMessage(groupId, "$myName left the activity.", "System", "system", isSystem = true)
            false
        } else {
            // Join
            val list = group.participantsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (!list.contains(myName)) list.add(myName)
            val updatedCsv = list.joinToString(", ")
            val updatedCount = group.participantCount + 1
            dao.updateGroupJoinStatus(groupId, true, updatedCount, updatedCsv)
            sendChatMessage(groupId, "$myName joined the activity!", "System", "system", isSystem = true)
            true
        }
    }

    // --- AI Assistant Dedicated Actions ---

    suspend fun sendAssistantPrompt(userInput: String, languageCode: String): String {
        // Safe user record
        dao.insertAssistantMessage(
            LocalAssistantMessage(role = "user", content = userInput)
        )

        // Compile query injecting active context to Gemini to ensure replies are formatted beautifully and localized!
        val systemPrompt = """
            You are 'Meetly AI', the smart social coordinator for the app 'Meetly' (Tagline: 'Meet people. Do things.' - Google Search for real-life activities).
            Your goal in this conversation is to recommend specific real-world summer activities, plan meetup details, suggest nearby venues in Bishkek/local area, understand budgets, and matches.
            
            IMPORTANT:
            1. Respond in the requested language: '${languageCode}'.
            2. Keep responses brief, encouraging, minimalistic, and filled with specific local sports, hiking, walking group suggestions containing virtual prices and times.
            3. Use Material Design system emoji triggers occasionally to sound summer-vibe and exciting.
        """.trimIndent()

        // Generate response
        val modelReply = GeminiController.generateResponse(userInput, systemPrompt)

        // Safe model record
        dao.insertAssistantMessage(
            LocalAssistantMessage(role = "model", content = modelReply)
        )

        return modelReply
    }

    suspend fun clearAssistantChat() {
        dao.clearAssistantChat()
    }

    // --- Intelligent Matching Engine (Search / Match Intents) ---

    sealed class MatchResult {
        data class Found(val matchedGroups: List<LocalActivityGroup>, val recommendationMessage: String) : MatchResult()
        data class CreatedNewGroup(val newGroup: LocalActivityGroup, val recommendationMessage: String) : MatchResult()
    }

    /**
     * Parses the user's natural intent (e.g. 'I want to play volley tonight' or 'walking in one hour')
     * and either:
     * - Filters existing groups matching that intent.
     * - Dynamically creates a new matched temporary group if no match is found, matching the user's intent.
     */
    suspend fun matchIntent(userIntent: String, languageCode: String): MatchResult {
        val lowerIntent = userIntent.lowercase()

        // Get available groups
        val currentGroups = dao.getAllActivityGroupsFlow().firstOrNull() ?: emptyList()

        // Detect Activity Type
        val activityType = when {
            lowerIntent.contains("volley") || lowerIntent.contains("бол") && lowerIntent.contains("вол") || lowerIntent.contains("волей") -> "Volleyball"
            lowerIntent.contains("foot") || lowerIntent.contains("фут") || lowerIntent.contains("мяч") -> "Football"
            lowerIntent.contains("cycl") || lowerIntent.contains("вело") || lowerIntent.contains("байк") -> "Cycling"
            lowerIntent.contains("walk") || lowerIntent.contains("гул") || lowerIntent.contains("пеш") || lowerIntent.contains("прогул") -> "Walking"
            lowerIntent.contains("game") || lowerIntent.contains("настол") || lowerIntent.contains("игр") || lowerIntent.contains("карт") -> "Board Games"
            lowerIntent.contains("hike") || lowerIntent.contains("гор") || lowerIntent.contains("поход") || lowerIntent.contains("пик") -> "Hiking"
            lowerIntent.contains("run") || lowerIntent.contains("бег") -> "Running"
            lowerIntent.contains("picnic") || lowerIntent.contains("пикн") -> "Picnic"
            else -> "Social"
        }

        // Filter groups matching activityType
        val matchingGroups = currentGroups.filter {
            it.activityType.lowercase() == activityType.lowercase() ||
            it.title.lowercase().contains(activityType.lowercase())
        }

        // Ask Gemini to generate a personalized recommend message!
        val geminiQuery = """
            The user expressed real-life intent: "$userIntent".
            We parsed this as category: "$activityType".
            We found ${matchingGroups.size} existing group(s) nearby.
            Write a brief, 2-line personalized coordinator advice encouraging them.
            Explain what summer activity matches, travel times, or general weather match.
            Coordinate in language code: "$languageCode". Keep it friendly, short!
        """.trimIndent()

        val recommendationText = try {
            GeminiController.generateResponse(geminiQuery)
        } catch (e: Exception) {
            "Match engine active. Weather is warm and clear. Excellent choice for $activityType!"
        }

        if (matchingGroups.isNotEmpty()) {
            return MatchResult.Found(matchingGroups, recommendationText)
        } else {
            // Dynamically create a matching temporary activity group so the user "instant matches" and is NOT left with empty state!
            val parsedBudget = when {
                lowerIntent.contains("free") || lowerIntent.contains("беспл") -> "Free"
                lowerIntent.contains("cheap") || lowerIntent.contains("дешев") -> "$2"
                else -> "$4"
            }

            val parsedTime = when {
                lowerIntent.contains("hour") || lowerIntent.contains("час") -> "In 1 Hour"
                lowerIntent.contains("night") || lowerIntent.contains("веч") -> "Tonight at 20:00"
                else -> "Today at 19:30"
            }

            // Create beautiful summer matching title
            val locName = when (activityType) {
                "Volleyball" -> "Vostok court rings"
                "Football" -> "Dordoi Mini-pitch"
                "Cycling" -> "Ala-Archa Foothills"
                "Hiking" -> "Chunkurchak Valley Scenic point"
                "Board Games" -> "Coffee Loft Tables"
                "Walking" -> "Erkindik Central boulevard"
                else -> "Seaside / City hub"
            }

            val newGroup = LocalActivityGroup(
                groupId = "dynamic_match_${UUID.randomUUID().toString().take(6)}",
                title = "AI Matches: $activityType $parsedTime",
                activityType = activityType,
                description = "Automated matching cluster spawned by AI for intent: \"$userIntent\". Grouping active. Join now to coordinate!",
                creatorName = "Gemini Coordinator",
                date = "Today",
                time = parsedTime,
                locationName = "$locName (Nearby)",
                latitude = 42.87,
                longitude = 74.60,
                priceStr = parsedBudget,
                durationStr = "2 Hours",
                participantCount = 2,
                maxParticipants = 8,
                isJoined = true, // Auto-joined as creator
                participantsCsv = "Salamat, Eldar, AI Coordinator"
            )

            insertCustomActivityGroup(newGroup)

            return MatchResult.CreatedNewGroup(newGroup, recommendationText)
        }
    }
}
