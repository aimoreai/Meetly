package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "app_settings")
data class LocalSetting(
    @PrimaryKey val id: Int = 1,
    val selectedLanguage: String = "EN",
    val themeMode: String = "SYSTEM", // "LIGHT", "DARK", "SYSTEM"
    val notificationsEnabled: Boolean = true,
    val incognitoMode: Boolean = false,
    val isOnboarded: Boolean = false,
    val isLoggedIn: Boolean = false
)

@Entity(tableName = "user_profiles")
data class LocalUserProfile(
    @PrimaryKey val uid: String = "default_user",
    val name: String = "Salamat",
    val profilePictureUrl: String = "",
    val phone: String = "+996 555 125 487",
    val bio: String = "Love beach volleyball, evening walks near Erkindik Boulevard, and deep-space science fiction.",
    val isVerified: Boolean = true,
    val preferredCategories: String = "SportsActivities,BoardGames" // CSV string
)

@Entity(tableName = "activity_groups")
data class LocalActivityGroup(
    @PrimaryKey val groupId: String,
    val title: String,
    val activityType: String, // "Football", "Volleyball", "Walking", "Cycling", "Board Games", "Hiking", "Running", "Picnic"
    val description: String,
    val creatorName: String,
    val date: String,
    val time: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val priceStr: String,
    val durationStr: String,
    val participantCount: Int,
    val maxParticipants: Int,
    val isJoined: Boolean = false,
    val participantsCsv: String = "Alexander, Eldar, Meerim", // comma separated
    val creationTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "group_messages")
data class LocalChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: String,
    val senderName: String,
    val senderId: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false
)

@Entity(tableName = "assistant_messages")
data class LocalAssistantMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Daos ---

@Dao
interface MeetlyDao {

    // Settings
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<LocalSetting?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): LocalSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: LocalSetting)

    // User Profile
    @Query("SELECT * FROM user_profiles WHERE uid = :uid LIMIT 1")
    fun getUserProfileFlow(uid: String = "default_user"): Flow<LocalUserProfile?>

    @Query("SELECT * FROM user_profiles WHERE uid = :uid LIMIT 1")
    suspend fun getUserProfileDirect(uid: String = "default_user"): LocalUserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: LocalUserProfile)

    // Activity Groups
    @Query("SELECT * FROM activity_groups ORDER BY creationTime DESC")
    fun getAllActivityGroupsFlow(): Flow<List<LocalActivityGroup>>

    @Query("SELECT * FROM activity_groups WHERE groupId = :groupId LIMIT 1")
    fun getActivityGroupFlow(groupId: String): Flow<LocalActivityGroup?>

    @Query("SELECT * FROM activity_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getActivityGroupDirect(groupId: String): LocalActivityGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityGroups(groups: List<LocalActivityGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityGroup(group: LocalActivityGroup)

    @Query("UPDATE activity_groups SET isJoined = :isJoined, participantCount = :count, participantsCsv = :participantsCsv WHERE groupId = :groupId")
    suspend fun updateGroupJoinStatus(groupId: String, isJoined: Boolean, count: Int, participantsCsv: String)

    @Query("DELETE FROM activity_groups WHERE groupId = :groupId")
    suspend fun deleteActivityGroup(groupId: String)

    // Temporary Group Messages
    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getGroupMessagesFlow(groupId: String): Flow<List<LocalChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessage(message: LocalChatMessage)

    // Dedicated Intelligent Assistant General Chat
    @Query("SELECT * FROM assistant_messages ORDER BY timestamp ASC")
    fun getAssistantMessagesFlow(): Flow<List<LocalAssistantMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssistantMessage(message: LocalAssistantMessage)

    @Query("DELETE FROM assistant_messages")
    suspend fun clearAssistantChat()
}

// --- AppDatabase ---

@Database(
    entities = [
        LocalSetting::class,
        LocalUserProfile::class,
        LocalActivityGroup::class,
        LocalChatMessage::class,
        LocalAssistantMessage::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MeetlyDatabase : RoomDatabase() {
    abstract fun dao(): MeetlyDao
}
