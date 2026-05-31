package com.example.ui

import android.content.Context
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.repository.MeetlyRepository
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID
import com.example.ui.theme.Typography
import kotlinx.coroutines.launch
import kotlin.random.Random

// --- Navigation Destinations ---
sealed class MeetlyScreen {
    object Splash : MeetlyScreen()
    object Auth : MeetlyScreen()
    object Onboarding : MeetlyScreen()
    object Home : MeetlyScreen()
    object IntentSearch : MeetlyScreen()
    data class ActivityDiscovery(val query: String) : MeetlyScreen()
    object Assistant : MeetlyScreen()
    data class ActivityGroupDetails(val groupId: String) : MeetlyScreen()
    object Profile : MeetlyScreen()
    object Settings : MeetlyScreen()
    object Map : MeetlyScreen()
}

// --- Screen State Holder / ViewModel ---
class MeetlyViewModel(private val repository: MeetlyRepository) : ViewModel() {

    // Theme & Language Settings State
    val settingsState = repository.settingsFlow

    // User Profile
    val userProfileState = repository.userProfileFlow

    // Activity Groups
    val allGroupsState = repository.allActivityGroupsFlow

    // AI Assistant Messages
    val assistantMessagesState = repository.assistantMessagesFlow

    // Current Navigation Screen
    private val _currentScreen = MutableStateFlow<MeetlyScreen>(MeetlyScreen.Splash)
    val currentScreen: StateFlow<MeetlyScreen> = _currentScreen.asStateFlow()

    // Intent Search State
    var lastSearchQuery by mutableStateOf("")

    // Selected Activity Group Details Cache
    private val _activeGroupChat = MutableStateFlow<List<LocalChatMessage>>(emptyList())
    val activeGroupChat = _activeGroupChat.asStateFlow()

    // Active Simulated Weather Vibe
    val weatherTemp = 28 // Bishkek summer degree standard
    var weatherRainy by mutableStateOf(false)

    init {
        // Randomize weather condition each session for fun real-life dynamics
        weatherRainy = Random.nextBoolean()
    }

    fun navigateTo(screen: MeetlyScreen) {
        _currentScreen.value = screen
    }

    fun handleOnboardingFinish() {
        viewModelScope.launch {
            repository.updateOnboardingFinished(true)
            navigateTo(MeetlyScreen.Auth)
        }
    }

    fun handleSignIn(name: String, bio: String, phone: String, prefs: String, profilePictureUrl: String = "") {
        viewModelScope.launch {
            repository.updateProfile(name, bio, phone, prefs, profilePictureUrl)
            repository.updateUserLoggedIn(true)
            navigateTo(MeetlyScreen.Home)
        }
    }

    fun handleLogout() {
        viewModelScope.launch {
            repository.updateUserLoggedIn(false)
            navigateTo(MeetlyScreen.Auth)
        }
    }

    fun changeLanguage(language: AppLanguage) {
        viewModelScope.launch {
            val s = settingsState.firstOrNull() ?: LocalSetting()
            repository.saveSetting(
                selectedLanguage = language.name,
                themeMode = s.themeMode,
                notifications = s.notificationsEnabled,
                incognito = s.incognitoMode
            )
        }
    }

    fun changeTheme(mode: ThemeMode) {
        viewModelScope.launch {
            val s = settingsState.firstOrNull() ?: LocalSetting()
            repository.saveSetting(
                selectedLanguage = s.selectedLanguage,
                themeMode = mode.name,
                notifications = s.notificationsEnabled,
                incognito = s.incognitoMode
            )
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val s = settingsState.firstOrNull() ?: LocalSetting()
            repository.saveSetting(
                selectedLanguage = s.selectedLanguage,
                themeMode = s.themeMode,
                notifications = enabled,
                incognito = s.incognitoMode
            )
        }
    }

    fun toggleIncognito(incognito: Boolean) {
        viewModelScope.launch {
            val s = settingsState.firstOrNull() ?: LocalSetting()
            repository.saveSetting(
                selectedLanguage = s.selectedLanguage,
                themeMode = s.themeMode,
                notifications = s.notificationsEnabled,
                incognito = incognito
            )
        }
    }

    // --- General AI Assistant ---
    var isAssistantGenerating by mutableStateOf(false)

    fun sendAssistantMessage(prompt: String, lang: AppLanguage) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            isAssistantGenerating = true
            repository.sendAssistantPrompt(prompt, lang.code)
            isAssistantGenerating = false
        }
    }

    fun clearAssistantMessage() {
        viewModelScope.launch {
            repository.clearAssistantChat()
        }
    }

    // --- Intent Search & Smart Matches ---
    var isMatchingInProgress by mutableStateOf(false)
    var intentMatchSummary by mutableStateOf("")
    var matchingResults = mutableStateListOf<LocalActivityGroup>()

    fun runIntentSearch(userIntent: String, lang: AppLanguage, onComplete: () -> Unit) {
        if (userIntent.isBlank()) return
        lastSearchQuery = userIntent
        viewModelScope.launch {
            isMatchingInProgress = true
            val response = repository.matchIntent(userIntent, lang.code)
            matchingResults.clear()
            when (response) {
                is MeetlyRepository.MatchResult.Found -> {
                    matchingResults.addAll(response.matchedGroups)
                    intentMatchSummary = response.recommendationMessage
                }
                is MeetlyRepository.MatchResult.CreatedNewGroup -> {
                    matchingResults.add(response.newGroup)
                    intentMatchSummary = response.recommendationMessage
                }
            }
            isMatchingInProgress = false
            onComplete()
        }
    }

    // --- Individual Activity Group Actions ---
    fun observeGroupMessages(groupId: String): Flow<List<LocalChatMessage>> {
        return repository.getGroupMessagesFlow(groupId)
    }

    fun sendGroupChatMessage(groupId: String, text: String, senderName: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendChatMessage(groupId, text, senderName, "me")

            // Simulate lovely interactive conversational reaction!
            delay(2000)
            val randomReaction = listOf(
                "That sounds like an amazing plan! I'm in.",
                "Weather looks perfect for this. Let's meet on time!",
                "Great! I'm bringing a friend along too. See you soon!",
                "Excellent choice. Count me in!",
                "Sounds awesome. I am walking from nearby boulevard.",
                "Let's split up of any snacks or table booking charges!"
            ).random()

            val randomSender = listOf("Alexander", "Eldar", "Meerim", "Sofia", "Danila").random()
            repository.sendChatMessage(groupId, randomReaction, randomSender, senderId = "peer_user")
        }
    }

    fun toggleJoinGroup(groupId: String) {
        viewModelScope.launch {
            val user = userProfileState.firstOrNull()
            repository.joinOrLeaveGroup(groupId, user)
        }
    }

    fun createActivityDirectly(title: String, type: String, desc: String, loc: String, time: String, priceCode: String) {
        viewModelScope.launch {
            val user = userProfileState.firstOrNull()
            val myName = user?.name ?: "Salamat"
            val newDirect = LocalActivityGroup(
                groupId = "custom_direct_${UUID.randomUUID().toString().take(5)}",
                title = title,
                activityType = type,
                description = desc,
                creatorName = myName,
                date = "Today",
                time = time,
                locationName = "$loc (GPS Matched)",
                latitude = 42.87,
                longitude = 74.60,
                priceStr = priceCode,
                durationStr = "1.5 Hours",
                participantCount = 1,
                maxParticipants = 6,
                isJoined = true,
                participantsCsv = myName
            )
            repository.insertCustomActivityGroup(newDirect)
        }
    }

    fun createActivityOnMap(title: String, type: String, desc: String, loc: String, time: String, priceCode: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            val user = userProfileState.firstOrNull()
            val myName = user?.name ?: "Salamat"
            val newDirect = LocalActivityGroup(
                groupId = "custom_map_${java.util.UUID.randomUUID().toString().take(5)}",
                title = title,
                activityType = type,
                description = desc,
                creatorName = myName,
                date = "Today",
                time = time,
                locationName = "$loc (Map Pin)",
                latitude = lat,
                longitude = lng,
                priceStr = priceCode,
                durationStr = "2 Hours",
                participantCount = 1,
                maxParticipants = 8,
                isJoined = true,
                participantsCsv = myName
            )
            repository.insertCustomActivityGroup(newDirect)
        }
    }


}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

// --- Dynamic Color Harmonizer Setup for Sleek Vibrant Palette ---
val SummerOrange = Color(0xFF006495) // Primary Vibrant Blue
val TealPulse = Color(0xFF146B3A)    // Emerald Green status/success
val SeaSky = Color(0xFF004A6F)       // Deep Accent Blue
val WarmSand = Color(0xFFFDE7AA)     // Soft Amber Accent

val SlateDarkSurface = Color(0xFF001D31) // Dark Slate Background
val SlateDarkOnSurface = Color(0xFFF8FAFC)
val CardDarkBg = Color(0xFF00223D)       // Dark Secondary Card Bg

val SlateLightSurface = Color(0xFFFBFCFF) // Warm Light Blue/White Background wrapper
val SlateLightOnSurface = Color(0xFF001D31) // Primary Text Dark Slate
val CardLightBg = Color(0xFFFFFFFF)       // Pure White Cards

// --- Primary Core Composable View ---
@Composable
fun MeetlyComposeApp(viewModel: MeetlyViewModel) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle(initialValue = LocalSetting())
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle(initialValue = LocalUserProfile())
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Detect selected language
    val appLang = when (settings?.selectedLanguage) {
        "EN" -> AppLanguage.EN
        "RU" -> AppLanguage.RU
        "KY" -> AppLanguage.KY
        else -> AppLanguage.EN // Default
    }

    // Detect Theme Switch
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (settings?.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemDark
    }

    val customColors = if (isDark) {
        darkColorScheme(
            primary = Color(0xFF76D1FF),
            secondary = Color(0xFF004A6F),
            tertiary = Color(0xFF001D31),
            background = SlateDarkSurface,
            surface = CardDarkBg,
            onBackground = SlateDarkOnSurface,
            onSurface = SlateDarkOnSurface
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006495),
            secondary = Color(0xFFD1E4FF),
            tertiary = Color(0xFF001D31),
            background = SlateLightSurface,
            surface = CardLightBg,
            onBackground = SlateLightOnSurface,
            onSurface = SlateLightOnSurface
        )
    }

    MaterialTheme(
        colorScheme = customColors,
        typography = com.example.ui.theme.Typography
    ) {
        CompositionLocalProvider(LocalAppLanguage provides appLang) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("app_root_surface"),
                color = MaterialTheme.colorScheme.background
            ) {
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        is MeetlyScreen.Splash -> SplashScreen(viewModel)
                        is MeetlyScreen.Auth -> AuthenticationScreen(viewModel)
                        is MeetlyScreen.Onboarding -> OnboardingScreen(viewModel)
                        is MeetlyScreen.Home -> HomeScreen(viewModel)
                        is MeetlyScreen.IntentSearch -> IntentSearchScreen(viewModel)
                        is MeetlyScreen.ActivityDiscovery -> ActivityDiscoveryScreen(viewModel, screen.query)
                        is MeetlyScreen.Assistant -> AssistantScreen(viewModel)
                        is MeetlyScreen.ActivityGroupDetails -> ActivityGroupScreen(viewModel, screen.groupId)
                        is MeetlyScreen.Profile -> UserProfileScreen(viewModel)
                        is MeetlyScreen.Settings -> SettingsScreen(viewModel)
                        is MeetlyScreen.Map -> MapScreen(viewModel)
                    }
                }
            }
        }
    }
}

// --- Screen 1: Splash Screen ---
@Composable
fun SplashScreen(viewModel: MeetlyViewModel) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle(initialValue = null)
    val lang = LocalAppLanguage.current

    var startAnimation by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (startAnimation) 1.2f else 0.8f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "LogoAnimation"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2600) // Beautiful cinematic delay
        val configs = settings
        if (configs != null) {
            if (!configs.isOnboarded) {
                viewModel.navigateTo(MeetlyScreen.Onboarding)
            } else if (!configs.isLoggedIn) {
                viewModel.navigateTo(MeetlyScreen.Auth)
            } else {
                viewModel.navigateTo(MeetlyScreen.Home)
            }
        } else {
            // Null state fallback on database initialization wait
            viewModel.navigateTo(MeetlyScreen.Onboarding)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        TealPulse
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // High polish custom vector summer sun-waves logo drawn in Canvas!
            Canvas(
                modifier = Modifier
                    .size(140.dp)
                    .testTag("splash_logo_canvas")
            ) {
                // Outer glowing Summer Sun circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = size.width / 2f
                )
                drawCircle(
                    color = Color.White,
                    radius = (size.width / 2.5f) * scale.value
                )
                // Fun sports stitching path
                val stitchPath = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.5f)
                    quadraticTo(
                        size.width * 0.5f, size.height * 0.2f,
                        size.width * 0.8f, size.height * 0.5f
                    )
                }
                drawPath(
                    path = stitchPath,
                    color = SummerOrange,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                val stitchPath2 = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.5f)
                    quadraticTo(
                        size.width * 0.5f, size.height * 0.8f,
                        size.width * 0.8f, size.height * 0.5f
                    )
                }
                drawPath(
                    path = stitchPath2,
                    color = SummerOrange,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = LanguageManager.getString(lang, TextKey.APP_NAME),
                fontSize = 48.sp,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Text(
                text = "\"${LanguageManager.getString(lang, TextKey.TAGLINE)}\"",
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = LanguageManager.getString(lang, TextKey.SPLASH_SUBTITLE),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- Screen 2: Authentication Screen & Custom onboarding Profile ---
@Composable
fun AuthenticationScreen(viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current
    var nameInput by remember { mutableStateOf("Salamat") }
    var userBioInput by remember { mutableStateOf("Love Beach Volleyball, cycling runs, and grabbing milk tea!") }
    var phoneInput by remember { mutableStateOf("+996 555 125 487") }

    // Intersecting summer preferences chooser
    val interestsList = listOf("Volleyball", "Football", "Walking", "Cycling", "Board Games", "Hiking", "Running", "Picnic")
    val selectedPrefs = remember { mutableStateListOf("Volleyball", "Walking") }

    var isCreatingScreen by remember { mutableStateOf(false) }
    var showGoogleDialog by remember { mutableStateOf(false) }

    if (showGoogleDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleDialog = false },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = "Google Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (lang == AppLanguage.RU) "Вход через Google" else if (lang == AppLanguage.KY) "Google менен кирүү" else "Sign in with Google",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (lang == AppLanguage.RU) "Выберите аккаунт для продолжения" else if (lang == AppLanguage.KY) "Улантуу үчүн аккаунтту тандаңыз" else "Choose an account to continue to Meetly",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Option 1: Aimore
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nameInput = "Aimore"
                                userBioInput = if (lang == AppLanguage.RU) "Привет! Люблю волейбол, прогулки и новые знакомства в Бишкеке." else "Love summer vibes, volleyball, and meeting new friends in Bishkek!"
                                phoneInput = "+996 555 125 487"
                                isCreatingScreen = true
                                showGoogleDialog = false
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("A", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Aimore", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("aimoreai103@gmail.com", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    // Option 2: Alexander
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nameInput = "Alexander"
                                userBioInput = if (lang == AppLanguage.RU) "Организую вечерние настолки в Дубовом парке и прогулки в горах." else "Love casual walking groups and organizing night board games near Oak Park."
                                phoneInput = "+996 700 888 999"
                                isCreatingScreen = true
                                showGoogleDialog = false
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("A", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Alexander", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("alexander.k@gmail.com", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    // Option 3: Use other
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nameInput = "Guest User"
                                userBioInput = "Ready for summer adventures!"
                                phoneInput = "+996 550 111 222"
                                isCreatingScreen = true
                                showGoogleDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Other Account", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == AppLanguage.RU) "Использовать другой аккаунт" else if (lang == AppLanguage.KY) "Башка аккаунтту колдонуу" else "Use another account",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGoogleDialog = false }) {
                    Text(if (lang == AppLanguage.RU) "Отмена" else if (lang == AppLanguage.KY) "Жок кылуу" else "Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                    center = Offset(500f, 1500f)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Summer Header
            Text(
                text = LanguageManager.getString(lang, TextKey.APP_NAME),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )

            Text(
                text = LanguageManager.getString(lang, TextKey.TAGLINE),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (!isCreatingScreen) {
                // Visual Vibe Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = LanguageManager.getString(lang, TextKey.AUTH_TITLE),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = LanguageManager.getString(lang, TextKey.AUTH_SUBTITLE),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        // Google Authentic button
                        Button(
                            onClick = {
                                showGoogleDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("google_signin_button")
                        ) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Google", tint = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = LanguageManager.getString(lang, TextKey.AUTH_GOOGLE_BTN),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Text(
                    text = LanguageManager.getString(lang, TextKey.AUTH_AGREE),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                // Interactive Profile Maker Onboarding Form
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Aesthetic Profile Setup",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Display Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input")
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Filled.Person, "Name") }
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Filled.Phone, "Phone") }
                        )

                        OutlinedTextField(
                            value = userBioInput,
                            onValueChange = { userBioInput = it },
                            label = { Text("Bio / Intended Activities notes") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Filled.Edit, "Bio") },
                            maxLines = 3
                        )

                        Text(
                            text = LanguageManager.getString(lang, TextKey.PROFILE_PREFS),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Categories checkboxes
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            mainAxisSpacing = 6.dp,
                            crossAxisSpacing = 6.dp
                        ) {
                            interestsList.forEach { tag ->
                                val selected = selectedPrefs.contains(tag)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (selected) selectedPrefs.remove(tag) else selectedPrefs.add(tag)
                                    },
                                    label = { Text(tag) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                val csv = selectedPrefs.joinToString(",")
                                viewModel.handleSignIn(nameInput, userBioInput, phoneInput, csv)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("complete_profile_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Create & Launch Meetly", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- Screen 3: Onboarding Walkthrough with beautiful Canvas Vector arts ---
@Composable
fun OnboardingScreen(viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current
    var step by remember { mutableStateOf(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Meetly Onboard",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(
                    onClick = { viewModel.handleOnboardingFinish() },
                    modifier = Modifier.testTag("onboard_skip_button")
                ) {
                    Text(LanguageManager.getString(lang, TextKey.SKIP), color = MaterialTheme.colorScheme.primary)
                }
            }

            // Beautiful vector-drawn Canvas art dynamic display based on Onboarding step!
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val onBackground = MaterialTheme.colorScheme.onBackground
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .padding(16.dp)
                ) {
                    when (step) {
                        1 -> {
                            // "Intention First" canvas: draw a beautiful glowing magnifying intent loop
                            drawCircle(
                                color = SummerOrange.copy(alpha = 0.15f),
                                radius = size.width / 2.2f,
                                center = center
                            )
                            drawCircle(
                                color = SummerOrange,
                                radius = size.width / 8f,
                                center = center.copy(x = center.x - 20f, y = center.y - 20f)
                            )
                            drawCircle(
                                color = TealPulse,
                                radius = size.width / 12f,
                                center = center.copy(x = center.x + 30f, y = center.y + 30f)
                            )
                            drawLine(
                                color = onBackground,
                                start = center.copy(x = center.x - 20f, y = center.y - 20f),
                                end = center.copy(x = center.x + 80f, y = center.y + 80f),
                                strokeWidth = 12f,
                                cap = StrokeCap.Round
                            )
                        }
                        2 -> {
                            // "Smart AI Groups" canvas: draw beautiful connecting nodes
                            val nodeA = Offset(center.x - 50f, center.y - 40f)
                            val nodeB = Offset(center.x + 50f, center.y - 30f)
                            val nodeC = Offset(center.x, center.y + 50f)

                            drawCircle(TealPulse.copy(alpha = 0.2f), radius = 60f, center = center)

                            drawLine(SummerOrange, start = nodeA, end = nodeB, strokeWidth = 6f)
                            drawLine(SummerOrange, start = nodeB, end = nodeC, strokeWidth = 6f)
                            drawLine(SummerOrange, start = nodeC, end = nodeA, strokeWidth = 6f)

                            drawCircle(SummerOrange, radius = 22f, center = nodeA)
                            drawCircle(TealPulse, radius = 22f, center = nodeB)
                            drawCircle(SeaSky, radius = 22f, center = nodeC)
                        }
                        3 -> {
                            // "Real life action" canvas: beach picnic / Volleyball sunset
                            drawArc(
                                color = SummerOrange.copy(alpha = 0.3f),
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true,
                                size = Size(size.width, size.height),
                                topLeft = Offset(0f, 40f)
                            )
                            drawCircle(
                                color = SummerOrange,
                                radius = 40f,
                                center = Offset(center.x, center.y - 20f)
                            )
                            // Wave line below
                            drawLine(
                                color = TealPulse,
                                start = Offset(0f, center.y + 40f),
                                end = Offset(size.width, center.y + 40f),
                                strokeWidth = 10f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Onboarding Texts
                val title = when (step) {
                    1 -> LanguageManager.getString(lang, TextKey.ONBOARDING_TITLE_1)
                    2 -> LanguageManager.getString(lang, TextKey.ONBOARDING_TITLE_2)
                    else -> LanguageManager.getString(lang, TextKey.ONBOARDING_TITLE_3)
                }

                val desc = when (step) {
                    1 -> LanguageManager.getString(lang, TextKey.ONBOARDING_DESC_1)
                    2 -> LanguageManager.getString(lang, TextKey.ONBOARDING_DESC_2)
                    else -> LanguageManager.getString(lang, TextKey.ONBOARDING_DESC_3)
                }

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Text(
                    text = desc,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
                )
            }

            // Bottom Flow Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Steps Indicator Pill
                Row(
                    modifier = Modifier.padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..3).forEach { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = if (step == i) 24.dp else 8.dp, height = 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (step == i) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        if (step < 3) {
                            step++
                        } else {
                            viewModel.handleOnboardingFinish()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboard_next_button")
                ) {
                    Text(
                        text = if (step < 3) LanguageManager.getString(lang, TextKey.NEXT) else LanguageManager.getString(lang, TextKey.GET_STARTED),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Screen 4: Home Screen (The primary discover terminal) ---
@Composable
fun HomeScreen(viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current
    val groups by viewModel.allGroupsState.collectAsStateWithLifecycle(initialValue = emptyList())
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle(initialValue = null)

    var searchInput by remember { mutableStateOf("") }

    // Floating Creator Dialog state
    var showDirectLauncher by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            BottomNavigationHub(currentSelected = "home", viewModel = viewModel)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDirectLauncher = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_create_activity")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create Intent")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Header profile area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Meetly",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "MEET PEOPLE. DO THINGS.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSystemInDarkTheme()) Color(0xFF76D1FF) else Color(0xFF006495),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hi, ${userProfile?.name ?: "Salamat"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                // Small Status Verified badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(TealPulse.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Verified Status",
                        tint = TealPulse,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Verified Core",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPulse
                    )
                }
            }

            // Interactive Dynamic Weather alert as an elegant Gradient Suggestion Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(Color(0xFF006495), Color(0xFF004A6F))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Face,
                                    contentDescription = "Gemini Assistant",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gemini Assistant",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (viewModel.weatherRainy) "RAIN ALERT" else "GOOD WEATHER",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (viewModel.weatherRainy) {
                            LanguageManager.getString(lang, TextKey.WEATHER_RAIN_ALERT)
                        } else {
                            LanguageManager.getString(lang, TextKey.WEATHER_GOOD_ALERT)
                        },
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.navigateTo(MeetlyScreen.Assistant)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF006495)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(
                            "Match Me Now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // "What do you want to do?" Natural intent Search Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    placeholder = {
                        Text(
                            text = LanguageManager.getString(lang, TextKey.SEARCH_PLACEHOLDER),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_search_field"),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (searchInput.isNotEmpty()) {
                            IconButton(onClick = { searchInput = "" }) {
                                Icon(Icons.Filled.Clear, "Clear")
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Primary matchmaking action trigger
                IconButton(
                    onClick = {
                        if (searchInput.isNotBlank()) {
                            viewModel.runIntentSearch(searchInput, lang) {
                                viewModel.navigateTo(MeetlyScreen.ActivityDiscovery(searchInput))
                            }
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .testTag("intent_search_action_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
            }

            // Quick Category Tag Selector buttons
            val categories = listOf("Volleyball", "Football", "Cycling", "Walking", "Games")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                searchInput = cat
                                viewModel.runIntentSearch(cat, lang) {
                                    viewModel.navigateTo(MeetlyScreen.ActivityDiscovery(cat))
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (cat) {
                                else -> Icons.Filled.Star
                            }
                            Icon(icon, contentDescription = cat, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Live Nearby Active Activity stream lists
            Text(
                text = LanguageManager.getString(lang, TextKey.SECTION_NEARBY),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("nearby_activities_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (groups.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                "No active groups nearby. Create one or ask Gemini assistant!",
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(groups) { item ->
                        ActivityGroupRowCard(group = item) {
                            viewModel.navigateTo(MeetlyScreen.ActivityGroupDetails(item.groupId))
                        }
                    }
                }
            }
        }

        // Floating Dialog definition for adding active intentions
        if (showDirectLauncher) {
            var directTitle by remember { mutableStateOf("Beach Volley Bishkek") }
            var directType by remember { mutableStateOf("Volleyball") }
            var directDesc by remember { mutableStateOf("Casual volleyball mesh. Let's practice serves!") }
            var directLoc by remember { mutableStateOf("Bishkek Sports Arena") }
            var directTime by remember { mutableStateOf("Tonight 19:00") }
            var directPrice by remember { mutableStateOf("Free") }

            AlertDialog(
                onDismissRequest = { showDirectLauncher = false },
                title = { Text("Launch Real-world Activity") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = directTitle,
                            onValueChange = { directTitle = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Activity Type", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val types = listOf("Volleyball", "Football", "Cycling", "Walking", "Board Games", "Hiking", "Running", "Picnic")
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            types.forEach { t ->
                                FilterChip(
                                    selected = directType == t,
                                    onClick = { directType = t },
                                    label = { Text(t) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = directDesc,
                            onValueChange = { directDesc = it },
                            label = { Text("Describe intended players needed") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = directLoc,
                            onValueChange = { directLoc = it },
                            label = { Text("Meeting Point") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = directTime,
                            onValueChange = { directTime = it },
                            label = { Text("Time Guidelines") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = directPrice,
                            onValueChange = { directPrice = it },
                            label = { Text("Cost / Budget context") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.createActivityDirectly(directTitle, directType, directDesc, directLoc, directTime, directPrice)
                            showDirectLauncher = false
                        },
                        modifier = Modifier.testTag("confirm_create_activity_btn")
                    ) {
                        Text("Launch")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDirectLauncher = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// --- Card item UI for Home Activity Row Stream ---
@Composable
fun ActivityGroupRowCard(group: LocalActivityGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("activity_card_${group.groupId}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Time/Accent Badge
            val isUrgent = group.participantCount >= group.maxParticipants - 2
            val badgeBg = if (isUrgent) Color(0xFFE2F1E7) else Color(0xFFD1E4FF)
            val badgeText = if (isUrgent) Color(0xFF146B3A) else Color(0xFF006495)
            
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val timeParts = group.time.split(" ")
                    val primaryTime = timeParts.getOrNull(0) ?: group.time
                    val period = timeParts.getOrNull(1) ?: ""
                    
                    Text(
                        text = primaryTime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = badgeText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (period.isNotEmpty()) {
                        Text(
                            text = period,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeText
                        )
                    }
                }
            }

            // Central Info Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = "Place",
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${group.locationName} • ${group.durationStr}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = group.activityType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Spots alert
                    if (isUrgent) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFDE7AA))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NEED ${group.maxParticipants - group.participantCount} MORE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF7A5900)
                            )
                        }
                    } else {
                        // Max details
                        Text(
                            text = "${group.participantCount}/${group.maxParticipants} joined",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Right Action Join Button
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = "Join",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// --- Screen 5 & 6: Intent Search & Activity Discovery Screen ---
@Composable
fun IntentSearchScreen(viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current
    var intentInput by remember { mutableStateOf("") }
    var budgetValue by remember { mutableStateOf("Cheap") }

    Scaffold(
        bottomBar = { BottomNavigationHub("home", viewModel) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = LanguageManager.getString(lang, TextKey.INTENT_PROMPT_LABEL),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    OutlinedTextField(
                        value = intentInput,
                        onValueChange = { intentInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("intent_desc_textfield"),
                        placeholder = { Text("What do you want to do? Time constraint? Budget? E.g., 'Volleyball in 10 minutes near downtown, budget is free.'") },
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Preferred Budget Bounds", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val buds = listOf("Free", "Cheap", "Moderate", "Premium")
                        buds.forEach { b ->
                            FilterChip(
                                selected = budgetValue == b,
                                onClick = { budgetValue = b },
                                label = { Text(b) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (viewModel.isMatchingInProgress) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(
                            "AI matching raw intent against active user circles...",
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Button(
                            onClick = {
                                if (intentInput.isNotBlank()) {
                                    val finalQuery = "$intentInput, budget mode: $budgetValue"
                                    viewModel.runIntentSearch(finalQuery, lang) {
                                        viewModel.navigateTo(MeetlyScreen.ActivityDiscovery(finalQuery))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("run_intent_match_btn")
                        ) {
                            Text("Compute AI Coordinates Match", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityDiscoveryScreen(viewModel: MeetlyViewModel, query: String) {
    val lang = LocalAppLanguage.current
    val results = viewModel.matchingResults

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(MeetlyScreen.Home) },
                    modifier = Modifier.testTag("back_to_home_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GPS discovery grid",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        bottomBar = { BottomNavigationHub("home", viewModel) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Intent overview header card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Intent Query: \"$query\"",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Gemini matches: \"${viewModel.intentMatchSummary}\"",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            // Simple illustrative Canvas map representing nearby GPS coordinates!
            Text(
                "GPS Mapping Node Radar",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ) {
                // Interactive Radar canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midWidth = size.width / 2f
                    val midHeight = size.height / 2f

                    // Draw sonar concentric radar rings
                    drawCircle(Color.LightGray.copy(alpha = 0.4f), radius = 50f, center = center)
                    drawCircle(Color.LightGray.copy(alpha = 0.2f), radius = 100f, center = center)
                    drawCircle(SummerOrange.copy(alpha = 0.1f), radius = 160f, center = center)

                    // User Center Node
                    drawCircle(SummerOrange, radius = 10f, center = center)

                    // Placed Result Nodes
                    if (results.isNotEmpty()) {
                        drawCircle(TealPulse, radius = 8f, center = Offset(midWidth - 120f, midHeight + 30f))
                        drawLine(
                            color = TealPulse,
                            start = center,
                            end = Offset(midWidth - 120f, midHeight + 30f),
                            strokeWidth = 2f
                        )

                        drawCircle(SeaSky, radius = 8f, center = Offset(midWidth + 140f, midHeight - 40f))
                        drawLine(
                            color = SeaSky,
                            start = center,
                            end = Offset(midWidth + 140f, midHeight - 40f),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            Text(
                text = LanguageManager.getString(lang, TextKey.DISCOVER_MATCHES_TITLE),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(results) { res ->
                    ActivityGroupRowCard(group = res) {
                        viewModel.navigateTo(MeetlyScreen.ActivityGroupDetails(res.groupId))
                    }
                }
            }
        }
    }
}

// --- Screen 7: Dedicated Dedicated AI Assistant chat page ---
@Composable
fun AssistantScreen(viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current
    val chatHistory by viewModel.assistantMessagesState.collectAsStateWithLifecycle(initialValue = emptyList())
    var promptInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Scroll chat history to bottom automatically on new items!
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationHub("assistant", viewModel) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Screen Title Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Face,
                        contentDescription = "AI Ass",
                        tint = SummerOrange,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = LanguageManager.getString(lang, TextKey.ASSISTANT_TITLE),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                IconButton(
                    onClick = { viewModel.clearAssistantMessage() },
                    modifier = Modifier.testTag("clear_assistant_chat_btn")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            // Message streams
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            Icon(Icons.Filled.Face, "Assistant", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Meetly summer assistant. Ask Gemini: 'Where is beach volleyball popular in Bishkek?', 'Organize volleyball tonight' or 'I am bored what can I do next?'",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(chatHistory) { msg ->
                        val isMe = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 0.dp,
                                    bottomEnd = if (isMe) 0.dp else 16.dp
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = msg.content,
                                        fontSize = 14.sp,
                                        color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                if (viewModel.isAssistantGenerating) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gemini is matching plans...", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Pre-suggest buttons below input for fast summer entertainment vibes typing
            val quickHelpers = listOf("I am bored", "Fun & cheap things", "Sunset volleyball coordinates")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickHelpers.forEach { q ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable {
                                promptInput = q
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(q, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text(LanguageManager.getString(lang, TextKey.ASSISTANT_PLACEHOLDER), fontSize = 12.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_chat_input"),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            viewModel.sendAssistantMessage(promptInput, lang)
                            promptInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .testTag("send_assistant_msg_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Direct",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// --- Screen 8: Activity Temporary Group Screen (Contains chat, details, map) ---
@Composable
fun ActivityGroupScreen(viewModel: MeetlyViewModel, groupId: String) {
    val lang = LocalAppLanguage.current
    val allGroups by viewModel.allGroupsState.collectAsStateWithLifecycle(initialValue = emptyList())
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle(initialValue = null)

    val currentGroup = allGroups.find { it.groupId == groupId }
    val isMyStateJoined = currentGroup?.isJoined ?: false

    val chatHistory by viewModel.observeGroupMessages(groupId).collectAsStateWithLifecycle(initialValue = emptyList())
    var textMessageInput by remember { mutableStateOf("") }

    val context = LocalContext.current

    if (currentGroup == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Activity matching session expired or inactive.")
        }
        return
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.navigateTo(MeetlyScreen.Home) },
                        modifier = Modifier.testTag("back_from_detail_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentGroup.activityType,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Safety Moderation Button Rule
                IconButton(
                    onClick = {
                        Toast.makeText(
                            context,
                            LanguageManager.getString(lang, TextKey.SAFETY_REPORT_SUBMITTED),
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    modifier = Modifier.testTag("safety_report_moderator_badge")
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Report Moderation",
                        tint = SummerOrange
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // General Details layout card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentGroup.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = currentGroup.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("When", fontSize = 11.sp, color = SummerOrange, fontWeight = FontWeight.Bold)
                            Text(currentGroup.time, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Where", fontSize = 11.sp, color = SummerOrange, fontWeight = FontWeight.Bold)
                            Text(currentGroup.locationName, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Join / Leave trigger button
                    Button(
                        onClick = {
                            viewModel.toggleJoinGroup(groupId)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMyStateJoined) Color.Gray else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("group_join_leave_toggle_btn")
                    ) {
                        Text(
                            text = if (isMyStateJoined) LanguageManager.getString(lang, TextKey.GROUP_LEAVE) else LanguageManager.getString(lang, TextKey.GROUP_JOIN),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Participants Avatar Line-up
            Text(
                text = "${LanguageManager.getString(lang, TextKey.GROUP_MEMBERS)} (${currentGroup.participantCount})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val membersList = currentGroup.participantsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                membersList.forEach { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.AccountBox,
                            contentDescription = name,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Verified Member Code",
                            tint = TealPulse,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Ephemeral chat room
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        LanguageManager.getString(lang, TextKey.GROUP_CHAT),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SummerOrange,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Chat messages list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(chatHistory) { msg ->
                            if (msg.isSystem) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = msg.messageText,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                val isMe = msg.senderId == "me"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                ) {
                                    Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                                        Text(msg.senderName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Box(
                                            modifier = Modifier
                                                .clip(
                                                    RoundedCornerShape(
                                                        topStart = 10.dp,
                                                        topEnd = 10.dp,
                                                        bottomStart = if (isMe) 10.dp else 0.dp,
                                                        bottomEnd = if (isMe) 0.dp else 10.dp
                                                    )
                                                )
                                                .background(if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
                                                .background(if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                msg.messageText,
                                                fontSize = 13.sp,
                                                color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Chat bottom input
                    if (isMyStateJoined) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = textMessageInput,
                                onValueChange = { textMessageInput = it },
                                placeholder = { Text("Write chat arrangements...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("group_chat_text_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    val myName = userProfile?.name ?: "Salamat"
                                    viewModel.sendGroupChatMessage(groupId, textMessageInput, myName)
                                    textMessageInput = ""
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    .testTag("send_group_chat_msg_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = Color.White)
                            }
                        }
                    } else {
                        // Display message explaining that join is required to talk
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Please Join standard Group above to participate in arrangements.", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- Screen 9: User Profile Screen ---
@Composable
fun UserProfileScreen(viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle(initialValue = null)

    var directBioEdit by remember { mutableStateOf("") }
    var directNameEdit by remember { mutableStateOf("") }
    var directPhoneEdit by remember { mutableStateOf("") }
    var directPhotoUrlEdit by remember { mutableStateOf("") }

    var isEditState by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val destFile = java.io.File(context.filesDir, "custom_profile_picture.jpg")
                val outputStream = java.io.FileOutputStream(destFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                directPhotoUrlEdit = destFile.absolutePath
                Toast.makeText(
                    context,
                    if (lang == AppLanguage.RU) "Фото успешно загружено!" else "Photo loaded successfully!",
                    Toast.LENGTH_SHORT
                ).show()
                if (!isEditState) {
                    viewModel.handleSignIn(
                        directNameEdit.ifBlank { userProfile?.name ?: "Salamat" },
                        directBioEdit,
                        directPhoneEdit,
                        userProfile?.preferredCategories ?: "Volleyball,Walking",
                        destFile.absolutePath
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                directPhotoUrlEdit = selectedUri.toString()
                if (!isEditState) {
                    viewModel.handleSignIn(
                        directNameEdit.ifBlank { userProfile?.name ?: "Salamat" },
                        directBioEdit,
                        directPhoneEdit,
                        userProfile?.preferredCategories ?: "Volleyball,Walking",
                        selectedUri.toString()
                    )
                }
            }
        }
    }

    val presetAvatars = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=256&q=80",
        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=256&q=80",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=256&q=80",
        "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=256&q=80",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=256&q=80",
        "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=256&q=80"
    )

    LaunchedEffect(userProfile) {
        userProfile?.let {
            directBioEdit = it.bio
            directNameEdit = it.name
            directPhoneEdit = it.phone
            directPhotoUrlEdit = it.profilePictureUrl
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationHub("profile", viewModel) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Beautiful Large Adaptive Avatar circle
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { photoLauncher.launch("image/*") },
            ) {
                // Image layer
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val currentPhoto = directPhotoUrlEdit.ifBlank { userProfile?.profilePictureUrl ?: "" }
                    if (currentPhoto.isNotBlank()) {
                        AsyncImage(
                            model = currentPhoto,
                            contentDescription = "User Profile Picture",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = (userProfile?.name ?: "Salamat").take(1).uppercase(),
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Camera Badge overlay at bottom end
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Create,
                        contentDescription = "Edit photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = userProfile?.name ?: "Salamat",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Safe status label with verified shield icon!
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TealPulse.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Verified Avatar",
                    tint = TealPulse,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Shield Safety Verified",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealPulse
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card structure for contact/prefer details
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        LanguageManager.getString(lang, TextKey.PROFILE_TITLE),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = SummerOrange,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (!isEditState) {
                        Text("Active Bio", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(userProfile?.bio ?: "", fontSize = 14.sp)

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Phone", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(userProfile?.phone ?: "", fontSize = 14.sp)

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Summer Vibe Preferences", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(
                            userProfile?.preferredCategories ?: "Volleyball, Walking",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { isEditState = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("edit_profile_bio_switch_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(LanguageManager.getString(lang, TextKey.PROFILE_EDIT))
                        }
                    } else {
                        OutlinedTextField(
                            value = directNameEdit,
                            onValueChange = { directNameEdit = it },
                            label = { Text("Display Name") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        )

                        OutlinedTextField(
                            value = directPhoneEdit,
                            onValueChange = { directPhoneEdit = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        )

                        OutlinedTextField(
                            value = directBioEdit,
                            onValueChange = { directBioEdit = it },
                            label = { Text("Bio description") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Text(
                            text = if (lang == AppLanguage.RU) "Выберите готовый аватар:" else "Choose a Preset Avatar:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetAvatars.forEach { url ->
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = if (directPhotoUrlEdit == url) 3.dp else 1.dp,
                                            color = if (directPhotoUrlEdit == url) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            directPhotoUrlEdit = url
                                        }
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Preset Avatar option",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                        }

                        // Add Photo Button from Device gallery
                        Button(
                            onClick = { photoLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .height(44.dp)
                                .testTag("upload_custom_profile_photo_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                modifier = Modifier.size(18.dp),
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lang == AppLanguage.RU) "Загрузить фото с устройства" else "Upload Photo from Device",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = directPhotoUrlEdit,
                            onValueChange = { directPhotoUrlEdit = it },
                            label = { Text(if (lang == AppLanguage.RU) "Или вставьте ссылку на фото" else "Or Custom Photo URL") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.handleSignIn(directNameEdit, directBioEdit, directPhoneEdit, userProfile?.preferredCategories ?: "Volleyball,Walking", directPhotoUrlEdit)
                                isEditState = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_edited_profile_btn")
                        ) {
                            Text("Save Hub Details")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout action button
            Button(
                onClick = { viewModel.handleLogout() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Log Out Session", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- Screen 10: Settings Screen (Central preferences: Language, Theme) ---
@Composable
fun SettingsScreen(viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current
    val settings by viewModel.settingsState.collectAsStateWithLifecycle(initialValue = LocalSetting())

    val context = LocalContext.current

    Scaffold(
        bottomBar = { BottomNavigationHub("settings", viewModel) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = LanguageManager.getString(lang, TextKey.SETTINGS_TITLE),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Dynamic Language Selector Switch
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = LanguageManager.getString(lang, TextKey.SETTINGS_LANG),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SummerOrange
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.values().forEach { language ->
                            val active = lang == language
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable {
                                        viewModel.changeLanguage(language)
                                        Toast.makeText(context, "Switched to ${language.displayName}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = language.displayName,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Dynamic Visual Theme selector
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = LanguageManager.getString(lang, TextKey.SETTINGS_THEME),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SummerOrange
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
                            val active = settings?.themeMode == mode.name
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable {
                                        viewModel.changeTheme(mode)
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.name,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Push toggle row
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = LanguageManager.getString(lang, TextKey.SETTINGS_NOTIFICATIONS),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Receive GPS matching updates in Bishkek",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Switch(
                            checked = settings?.notificationsEnabled ?: true,
                            onCheckedChange = { viewModel.toggleNotifications(it) },
                            modifier = Modifier.testTag("push_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = LanguageManager.getString(lang, TextKey.SETTINGS_PRIVACY),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Bypass public matching coordinates",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Switch(
                            checked = settings?.incognitoMode ?: false,
                            onCheckedChange = { viewModel.toggleIncognito(it) },
                            modifier = Modifier.testTag("incognito_switch")
                        )
                    }
                }
            }
        }
    }
}

// --- Navigation Hub Components defined at Bottom bar ---
@Composable
fun BottomNavigationHub(currentSelected: String, viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current

    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentSelected == "home",
            onClick = { viewModel.navigateTo(MeetlyScreen.Home) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Active Discover") },
            label = { Text(LanguageManager.getString(lang, TextKey.NAV_HOME), fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_btn_home")
        )

        NavigationBarItem(
            selected = currentSelected == "map",
            onClick = { viewModel.navigateTo(MeetlyScreen.Map) },
            icon = { Icon(Icons.Filled.Place, contentDescription = "Visual Map Pin") },
            label = { Text(LanguageManager.getString(lang, TextKey.NAV_MAP), fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_btn_map")
        )

        NavigationBarItem(
            selected = currentSelected == "assistant",
            onClick = { viewModel.navigateTo(MeetlyScreen.Assistant) },
            icon = { Icon(Icons.Filled.Face, contentDescription = "AI Ass") },
            label = { Text(LanguageManager.getString(lang, TextKey.NAV_ASSISTANT), fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_btn_assistant")
        )

        NavigationBarItem(
            selected = currentSelected == "profile",
            onClick = { viewModel.navigateTo(MeetlyScreen.Profile) },
            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Hub profile") },
            label = { Text(LanguageManager.getString(lang, TextKey.NAV_PROFILE), fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_btn_profile")
        )

        NavigationBarItem(
            selected = currentSelected == "settings",
            onClick = { viewModel.navigateTo(MeetlyScreen.Settings) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Options Settings") },
            label = { Text(LanguageManager.getString(lang, TextKey.NAV_SETTINGS), fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_btn_settings")
        )
    }
}

// --- Supporting FlowRow for tags wrap layout ---
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val layoutWidth = constraints.maxWidth
        val lines = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentLine = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentLineWidth = 0

        placeables.forEach { placeable ->
            val spaceNow = if (currentLineWidth > 0) mainAxisSpacing.roundToPx() else 0
            if (currentLineWidth + spaceNow + placeable.width > layoutWidth) {
                lines.add(currentLine)
                currentLine = mutableListOf(placeable)
                currentLineWidth = placeable.width
            } else {
                currentLine.add(placeable)
                currentLineWidth += spaceNow + placeable.width
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        val height = lines.sumOf { line -> line.maxOf { it.height } } +
                (lines.size - 1).coerceAtLeast(0) * crossAxisSpacing.roundToPx()

        layout(layoutWidth, height) {
            var currentY = 0
            lines.forEach { line ->
                var currentX = 0
                val lineHeight = line.maxOf { it.height }
                line.forEach { placeable ->
                    placeable.placeRelative(currentX, currentY)
                    currentX += placeable.width + mainAxisSpacing.roundToPx()
                }
                currentY += lineHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}

// --- Interactive GPS Activity Map Screen ---
@Composable
fun MapScreen(viewModel: MeetlyViewModel) {
    val lang = LocalAppLanguage.current
    val groups by viewModel.allGroupsState.collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()

    // Map Center Calibration (Bishkek baseline)
    var centerLat by remember { mutableStateOf(42.875) }
    var centerLng by remember { mutableStateOf(74.600) }
    var latSpan by remember { mutableStateOf(0.02) }
    var lngSpan by remember { mutableStateOf(0.03) }

    // Gesture interaction state
    var selectedGroupOnMap by remember { mutableStateOf<LocalActivityGroup?>(null) }
    var tappedOffset by remember { mutableStateOf<Offset?>(null) }
    var calculatedLat by remember { mutableStateOf(centerLat) }
    var calculatedLng by remember { mutableStateOf(centerLng) }

    // Dialog state for adding a custom activity at tapped point
    var showCreateDialogOnMap by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newLocationName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Volleyball") }
    var newPrice by remember { mutableStateOf("Free") }
    var newTime by remember { mutableStateOf("19:00") }

    Scaffold(
        bottomBar = {
            BottomNavigationHub(currentSelected = "map", viewModel = viewModel)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Header layout
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lang == AppLanguage.RU) "GPS Радар Активностей" else if (lang == AppLanguage.KY) "GPS Иш-аракет Радары" else "GPS Live Activity Map",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (lang == AppLanguage.RU) "Нажмите на карту, чтобы отметить место или используйте Быстрый Выбор!" else "Tap on the map to place a custom pin or use the Quick Place Selector!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Quick Place Selector in Bishkek
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (lang == AppLanguage.RU) "📍 Места в Бишкеке:" else "📍 Bishkek Places:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val bishkekSpots = listOf(
                                Triple(if (lang == AppLanguage.RU) "Бул. Эркиндик" else "Erkindik Blvd", 42.875, 74.605),
                                Triple(if (lang == AppLanguage.RU) "Площадь Ала-Тоо" else "Ala-Too Square", 42.876, 74.603),
                                Triple(if (lang == AppLanguage.RU) "Парк Южные Ворота" else "South Gates Park", 42.825, 74.608),
                                Triple(if (lang == AppLanguage.RU) "Дубовый Парк" else "Oak Park", 42.878, 74.607),
                                Triple(if (lang == AppLanguage.RU) "Вефа спорт" else "Vefa Sport", 42.859, 74.617),
                                Triple(if (lang == AppLanguage.RU) "Филармония" else "Philharmonia", 42.877, 74.588),
                                Triple(if (lang == AppLanguage.RU) "Карагачевая роща" else "Karagachevaya Grove", 42.908, 74.611),
                                Triple(if (lang == AppLanguage.RU) "Парк Победы" else "Victory Park", 42.839, 74.591),
                                Triple(if (lang == AppLanguage.RU) "Ала-Арча" else "Ala-Archa Valley", 42.642, 74.484)
                            )

                            bishkekSpots.forEach { (name, lat, lng) ->
                                Button(
                                    onClick = {
                                        centerLat = lat
                                        centerLng = lng
                                        calculatedLat = lat
                                        calculatedLng = lng
                                        newLocationName = name
                                        newTitle = if (lang == AppLanguage.RU) "Сходка в $name" else "Meetup at $name"
                                        showCreateDialogOnMap = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Interactive Map Canvas Container
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                ) {
                    val density = LocalDensity.current.density
                    val width = constraints.maxWidth.toFloat()
                    val height = constraints.maxHeight.toFloat()
                    val midWidth = width / 2f
                    val midHeight = height / 2f

                    val isLight = true
                    val parkColor = Color(0xFFE2F0D9)
                    val waterColor = Color(0xFFD3E7F0)
                    val streetColor = Color(0xFFFBFBFB)
                    val gridColor = Color(0xFFE2E2E2)

                    // Unified Gestures overlay Box
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(centerLat, centerLng, latSpan, lngSpan) {
                                detectTapGestures { offset ->
                                    val latDiff = ((midHeight - offset.y) / height) * latSpan.toFloat()
                                    val lngDiff = ((offset.x - midWidth) / width) * lngSpan.toFloat()

                                    calculatedLat = centerLat + latDiff.toDouble()
                                    calculatedLng = centerLng + lngDiff.toDouble()
                                    tappedOffset = offset

                                    // Check if user tapped near an existing group marker (threshold 28.dp)
                                    val tapY = offset.y
                                    val tapX = offset.x
                                    var matched: LocalActivityGroup? = null

                                    groups.forEach { group ->
                                        val gX = midWidth + (group.longitude - centerLng).toFloat() * (width / lngSpan.toFloat())
                                        val gY = midHeight - (group.latitude - centerLat).toFloat() * (height / latSpan.toFloat())
                                        val distance = kotlin.math.sqrt((gX - tapX) * (gX - tapX) + (gY - tapY) * (gY - tapY))
                                        if (distance < 60f) {
                                            matched = group
                                        }
                                    }

                                    if (matched != null) {
                                        selectedGroupOnMap = matched
                                        tappedOffset = null // clear temporary pin
                                    } else {
                                        selectedGroupOnMap = null
                                    }
                                }
                            }
                    ) {
                        // Draw Blueprint Topography
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Local coordinate mapping helpers
                            fun getX(gLng: Double): Float =
                                midWidth + (gLng - centerLng).toFloat() * (width / lngSpan.toFloat())

                            fun getY(gLat: Double): Float =
                                midHeight - (gLat - centerLat).toFloat() * (height / latSpan.toFloat())

                            // 1. Grid Background
                            drawRect(color = Color(0xFFF1F1F3))

                            // Draw moving coordinate lattice grid lines
                            val latStart = java.lang.Math.floor((centerLat - latSpan) * 200) / 200.0
                            val latEnd = java.lang.Math.ceil((centerLat + latSpan) * 200) / 200.0
                            val lngStart = java.lang.Math.floor((centerLng - lngSpan) * 200) / 200.0
                            val lngEnd = java.lang.Math.ceil((centerLng + lngSpan) * 200) / 200.0

                            var curLat = latStart
                            while (curLat <= latEnd) {
                                val y = getY(curLat)
                                drawLine(gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                                curLat += 0.005
                            }

                            var curLng = lngStart
                            while (curLng <= lngEnd) {
                                val x = getX(curLng)
                                drawLine(gridColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
                                curLng += 0.005
                            }

                            // 2. Green Parks & Recreational Zones
                            // Oak Park
                            val oakLeft = getX(74.605)
                            val oakRight = getX(74.611)
                            val oakTop = getY(42.882)
                            val oakBottom = getY(42.876)
                            drawRect(
                                color = parkColor,
                                topLeft = Offset(oakLeft, oakTop),
                                size = androidx.compose.ui.geometry.Size(oakRight - oakLeft, oakBottom - oakTop)
                            )

                            // Panfilov Park
                            val panLeft = getX(74.590)
                            val panRight = getX(74.596)
                            val panTop = getY(42.880)
                            val panBottom = getY(42.875)
                            drawRect(
                                color = parkColor,
                                topLeft = Offset(panLeft, panTop),
                                size = androidx.compose.ui.geometry.Size(panRight - panLeft, panBottom - panTop)
                            )

                            // Erkindik Alley (runs along 74.605, width 0.001)
                            val erkLeft = getX(74.6045)
                            val erkRight = getX(74.6055)
                            val erkTop = getY(42.890)
                            val erkBottom = getY(42.860)
                            drawRect(
                                color = parkColor,
                                topLeft = Offset(erkLeft, erkTop),
                                size = androidx.compose.ui.geometry.Size(erkRight - erkLeft, erkBottom - erkTop)
                            )

                            // South Gates Park
                            val sgLeft = getX(74.602)
                            val sgRight = getX(74.612)
                            val sgTop = getY(42.828)
                            val sgBottom = getY(42.820)
                            drawRect(
                                color = parkColor,
                                topLeft = Offset(sgLeft, sgTop),
                                size = androidx.compose.ui.geometry.Size(sgRight - sgLeft, sgBottom - sgTop)
                            )

                            // 3. Draw Water Canal/River (Chuy river bypass at 42.888)
                            val riverY = getY(42.888)
                            drawLine(
                                color = waterColor,
                                start = Offset(0f, riverY),
                                end = Offset(size.width, riverY),
                                strokeWidth = 18f
                            )

                            // 4. Main Streets Blueprint Outline
                            // Chuy Avenue
                            val chuyY = getY(42.875)
                            drawLine(color = streetColor, start = Offset(0f, chuyY), end = Offset(size.width, chuyY), strokeWidth = 24f)
                            drawLine(color = gridColor, start = Offset(0f, chuyY), end = Offset(size.width, chuyY), strokeWidth = 1f)

                            // Kievskaya St
                            val kievY = getY(42.877)
                            drawLine(color = streetColor, start = Offset(0f, kievY), end = Offset(size.width, kievY), strokeWidth = 16f)

                            // Toktogul St
                            val toktY = getY(42.872)
                            drawLine(color = streetColor, start = Offset(0f, toktY), end = Offset(size.width, toktY), strokeWidth = 16f)

                            // Moskovskaya St
                            val moskY = getY(42.869)
                            drawLine(color = streetColor, start = Offset(0f, moskY), end = Offset(size.width, moskY), strokeWidth = 18f)

                            // Ahunbaeva St
                            val ahunY = getY(42.843)
                            drawLine(color = streetColor, start = Offset(0f, ahunY), end = Offset(size.width, ahunY), strokeWidth = 18f)

                            // Abdrakhmanov (Sovietskaya)
                            val sovietskayaX = getX(74.612)
                            drawLine(color = streetColor, start = Offset(sovietskayaX, 0f), end = Offset(sovietskayaX, size.height), strokeWidth = 20f)

                            // Manas Ave
                            val manasX = getX(74.588)
                            drawLine(color = streetColor, start = Offset(manasX, 0f), end = Offset(manasX, size.height), strokeWidth = 20f)
                        }

                        // 5. Overlaid Compass Coordinates HUD labels (Using elegant Compose overlapping views instead of nativeCanvas Paint)
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Left latitudes labels
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(80.dp)
                            ) {
                                Text(String.format("%.3f° N", centerLat + latSpan * 0.4), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(String.format("%.3f° N", centerLat), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(String.format("%.3f° N", centerLat - latSpan * 0.4), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            }

                            // Bottom longitudes labels
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp, start = 80.dp, end = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(String.format("%.3f° E", centerLng - lngSpan * 0.4), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(String.format("%.3f° E", centerLng), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(String.format("%.3f° E", centerLng + lngSpan * 0.4), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 6. Draw Live Group Markers
                        groups.forEach { group ->
                            val gX = midWidth + (group.longitude.toFloat() - centerLng.toFloat()) * (width / lngSpan.toFloat())
                            val gY = midHeight - (group.latitude.toFloat() - centerLat.toFloat()) * (height / latSpan.toFloat())

                            // Prevent drawing out-of-bounds pins
                            if (gX >= 0 && gX <= width && gY >= 0 && gY <= height) {
                                val catColor = when (group.activityType) {
                                    "Volleyball" -> SummerOrange
                                    "Football" -> TealPulse
                                    "Walking" -> SeaSky
                                    "Cycling" -> Color(0xFFFFB200)
                                    "Hiking" -> Color(0xFF8B5A2B)
                                    "Board Games" -> Color(0xFF9B59B6)
                                    else -> MaterialTheme.colorScheme.primary
                                }

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = (gX / density - 16).dp,
                                            y = (gY / density - 32).dp
                                        )
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Place,
                                        contentDescription = group.title,
                                        tint = catColor,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Tiny label overlay
                                Card(
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                                    modifier = Modifier
                                        .offset(
                                            x = (gX / density - 40).dp,
                                            y = (gY / density - 50).dp
                                        )
                                        .widthIn(max = 80.dp),
                                    border = BorderStroke(1.dp, catColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = group.title,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // 7. Draw Temporary Placed Pin (blinking neon red point)
                        tappedOffset?.let { offset ->
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = (offset.x / density - 16).dp,
                                        y = (offset.y / density - 32).dp
                                    )
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = "Temporary Pin",
                                    tint = Color.Red,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // 8. Elegant floating zoom & pan controller HUD
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                .padding(6.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Zoom control row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Zoom In Button
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                latSpan = (latSpan * 0.70).coerceAtLeast(0.005)
                                                lngSpan = (lngSpan * 0.70).coerceAtLeast(0.0075)
                                            }
                                    ) {
                                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }

                                    // Zoom Out Button
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                latSpan = (latSpan * 1.40).coerceAtMost(0.15)
                                                lngSpan = (lngSpan * 1.40).coerceAtMost(0.22)
                                            }
                                    ) {
                                        Text("—", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // D-Pad controller with clickables
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("▲", modifier = Modifier.clickable { centerLat += latSpan * 0.25 }, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("◀", modifier = Modifier.clickable { centerLng -= lngSpan * 0.25 }, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    // Reset map back to Bishkek baseline center
                                    Icon(
                                        imageVector = Icons.Filled.Place,
                                        contentDescription = "Center",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                centerLat = 42.875
                                                centerLng = 74.600
                                                latSpan = 0.02
                                                lngSpan = 0.03
                                                tappedOffset = null
                                            }
                                    )
                                    Text("▶", modifier = Modifier.clickable { centerLng += lngSpan * 0.25 }, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("▼", modifier = Modifier.clickable { centerLat -= latSpan * 0.25 }, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Spotlight Interaction / Temporary pin info HUD card
                AnimatedVisibility(
                    visible = selectedGroupOnMap != null || tappedOffset != null,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        if (selectedGroupOnMap != null) {
                            val group = selectedGroupOnMap!!
                            val catColor = when (group.activityType) {
                                "Volleyball" -> SummerOrange
                                "Football" -> TealPulse
                                "Walking" -> SeaSky
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(2.dp, catColor),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(catColor, RoundedCornerShape(5.dp))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = group.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "📍 ${group.locationName} • 👥 ${group.participantCount}/${group.maxParticipants}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = "🕒 ${group.time} | 💸 ${group.priceStr}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = catColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            viewModel.navigateTo(MeetlyScreen.ActivityGroupDetails(group.groupId))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = catColor),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (lang == AppLanguage.RU) "Открыть" else "Enter",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else if (tappedOffset != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = if (lang == AppLanguage.RU) "📍 Координаты выбраны" else "📍 PIN PLACED",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.Red
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Lat: ${String.format("%.4f", calculatedLat)} | Lng: ${String.format("%.4f", calculatedLng)}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            newLocationName = "Бишкек"
                                            newTitle = if (lang == AppLanguage.RU) "Сходка на " + String.format("%.3f", calculatedLat) else "Meetup at " + String.format("%.3f", calculatedLat)
                                            showCreateDialogOnMap = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (lang == AppLanguage.RU) "Создать активность здесь" else "Host Match at this Point",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Create Activity Dialog near marked GPS node ---
    if (showCreateDialogOnMap) {
        AlertDialog(
            onDismissRequest = { showCreateDialogOnMap = false },
            title = {
                Text(
                    text = if (lang == AppLanguage.RU) "Новое намерение на карте" else "Host on coordinates point",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(if (lang == AppLanguage.RU) "Название (например: Волейбол под вечер)" else "Title of Intent") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newLocationName,
                        onValueChange = { newLocationName = it },
                        label = { Text(if (lang == AppLanguage.RU) "Местоположение" else "Named Venue") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("Category / Категория", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val cats = listOf("Volleyball", "Football", "Walking", "Cycling", "Board Games")
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                cats.take(3).forEach { cat ->
                                    FilterChip(
                                        selected = newCategory == cat,
                                        onClick = { newCategory = cat },
                                        label = { Text(cat, fontSize = 11.sp) },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newTime,
                        onValueChange = { newTime = it },
                        label = { Text(if (lang == AppLanguage.RU) "Время (например: 19:00)" else "Timing Constraint") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            viewModel.createActivityOnMap(
                                title = newTitle,
                                type = newCategory,
                                desc = "Host coordinates: " + String.format("%.4fN, %.4fE", calculatedLat, calculatedLng),
                                loc = newLocationName,
                                time = newTime,
                                priceCode = newPrice,
                                lat = calculatedLat,
                                lng = calculatedLng
                            )
                            showCreateDialogOnMap = false
                            tappedOffset = null // clear temporary pin
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (lang == AppLanguage.RU) "Разместить" else "Publish Node")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialogOnMap = false }) {
                    Text(if (lang == AppLanguage.RU) "Отмена" else "Dismiss")
                }
            }
        )
    }
}
