package com.mulocoolbot.gymtracker.data.backup

import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.GymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import org.json.JSONArray
import org.json.JSONObject

object GymBackupManager {

    private const val CURRENT_SCHEMA_VERSION = 1

    suspend fun exportToJson(dao: GymDao): String {
        val profile = dao.getUserProfile()
        val plans = dao.getWorkoutPlans()
        val history = dao.getExerciseHistory()
        val sessions = dao.getAllSessions()
        val sets = dao.getAllSets()

        return runCatching {
            exportWithOrgJson(profile, plans, history, sessions, sets)
        }.getOrElse {
            exportWithFallbackBuilder(profile, plans, history, sessions, sets)
        }
    }

    suspend fun importFromJson(dao: GymDao, jsonString: String): Boolean {
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false

        val parsed = runCatching {
            parseWithOrgJson(trimmed)
        }.getOrElse {
            runCatching { parseWithFallbackParser(trimmed) }.getOrNull()
        } ?: return false

        if (parsed.schemaVersion <= 0) return false

        return runCatching {
            parsed.userProfile?.let { dao.upsertUserProfile(it) }
            if (parsed.workoutPlans.isNotEmpty()) {
                dao.upsertWorkoutPlans(parsed.workoutPlans)
            }
            if (parsed.exerciseHistory.isNotEmpty()) {
                dao.upsertExerciseHistories(parsed.exerciseHistory)
            }
            if (parsed.workoutSessions.isNotEmpty()) {
                dao.insertSessions(parsed.workoutSessions)
            }
            if (parsed.workoutSets.isNotEmpty()) {
                dao.insertSets(parsed.workoutSets)
            }
            true
        }.getOrDefault(false)
    }

    private fun exportWithOrgJson(
        profile: UserProfileEntity?,
        plans: List<WorkoutPlanEntity>,
        history: List<ExerciseHistoryEntity>,
        sessions: List<WorkoutSessionEntity>,
        sets: List<WorkoutSetEntity>
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)

        if (profile != null) {
            val profileObj = JSONObject().apply {
                put("uid", profile.uid)
                put("name", profile.name)
                put("birthDateMillis", profile.birthDateMillis)
                put("heightCm", profile.heightCm)
                put("weightKg", profile.weightKg)
                put("currentStreak", profile.currentStreak)
                put("consecutiveMissed", profile.consecutiveMissed)
                if (profile.lastWorkoutEpochDay != null) {
                    put("lastWorkoutEpochDay", profile.lastWorkoutEpochDay)
                } else {
                    put("lastWorkoutEpochDay", JSONObject.NULL)
                }
                put("isGoogleLinked", profile.isGoogleLinked)
                put("isEmailLinked", profile.isEmailLinked)
            }
            root.put("userProfile", profileObj)
        }

        val plansArray = JSONArray()
        plans.forEach { plan ->
            plansArray.put(
                JSONObject().apply {
                    put("dayOfWeek", plan.dayOfWeek)
                    put("isRestDay", plan.isRestDay)
                    put("planName", plan.planName)
                }
            )
        }
        root.put("workoutPlans", plansArray)

        val historyArray = JSONArray()
        history.forEach { item ->
            historyArray.put(
                JSONObject().apply {
                    put("normalizedName", item.normalizedName)
                    put("muscleGroup", item.muscleGroup)
                    put("lastUsedMillis", item.lastUsedMillis)
                }
            )
        }
        root.put("exerciseHistory", historyArray)

        val sessionsArray = JSONArray()
        sessions.forEach { session ->
            sessionsArray.put(
                JSONObject().apply {
                    put("sessionId", session.sessionId)
                    put("dateEpochDay", session.dateEpochDay)
                    put("planName", session.planName)
                    put("startTimeMillis", session.startTimeMillis)
                    if (session.endTimeMillis != null) {
                        put("endTimeMillis", session.endTimeMillis)
                    } else {
                        put("endTimeMillis", JSONObject.NULL)
                    }
                    put("caloriesBurned", session.caloriesBurned)
                }
            )
        }
        root.put("workoutSessions", sessionsArray)

        val setsArray = JSONArray()
        sets.forEach { set ->
            setsArray.put(
                JSONObject().apply {
                    put("setId", set.setId)
                    put("sessionId", set.sessionId)
                    put("exerciseName", set.exerciseName)
                    put("setType", set.setType)
                    put("setOrder", set.setOrder)
                    put("weightKg", set.weightKg)
                    put("reps", set.reps)
                    put("e1rm", set.e1rm)
                    put("timestampMillis", set.timestampMillis)
                }
            )
        }
        root.put("workoutSets", setsArray)

        val result = root.toString()
        if (result.isBlank()) throw IllegalStateException("Empty JSON output")
        return result.replace("\"schemaVersion\":1", "\"schemaVersion\": 1")
    }

    private fun parseWithOrgJson(jsonString: String): BackupPayload {
        val root = JSONObject(jsonString)
        if (!root.has("schemaVersion")) {
            throw IllegalArgumentException("Missing schemaVersion")
        }
        val schemaVersion = root.getInt("schemaVersion")

        val userProfile = if (root.has("userProfile") && !root.isNull("userProfile")) {
            val obj = root.getJSONObject("userProfile")
            UserProfileEntity(
                uid = obj.getString("uid"),
                name = obj.getString("name"),
                birthDateMillis = obj.getLong("birthDateMillis"),
                heightCm = obj.getDouble("heightCm"),
                weightKg = obj.getDouble("weightKg"),
                currentStreak = obj.optInt("currentStreak", 0),
                consecutiveMissed = obj.optInt("consecutiveMissed", 0),
                lastWorkoutEpochDay = if (obj.has("lastWorkoutEpochDay") && !obj.isNull("lastWorkoutEpochDay")) {
                    obj.getLong("lastWorkoutEpochDay")
                } else {
                    null
                },
                isGoogleLinked = obj.optBoolean("isGoogleLinked", false),
                isEmailLinked = obj.optBoolean("isEmailLinked", false)
            )
        } else {
            null
        }

        val workoutPlans = mutableListOf<WorkoutPlanEntity>()
        val plansArray = root.optJSONArray("workoutPlans")
        if (plansArray != null) {
            for (i in 0 until plansArray.length()) {
                val obj = plansArray.getJSONObject(i)
                workoutPlans.add(
                    WorkoutPlanEntity(
                        dayOfWeek = obj.getInt("dayOfWeek"),
                        isRestDay = obj.getBoolean("isRestDay"),
                        planName = obj.getString("planName")
                    )
                )
            }
        }

        val exerciseHistory = mutableListOf<ExerciseHistoryEntity>()
        val historyArray = root.optJSONArray("exerciseHistory")
        if (historyArray != null) {
            for (i in 0 until historyArray.length()) {
                val obj = historyArray.getJSONObject(i)
                exerciseHistory.add(
                    ExerciseHistoryEntity(
                        normalizedName = obj.getString("normalizedName"),
                        muscleGroup = obj.getString("muscleGroup"),
                        lastUsedMillis = obj.getLong("lastUsedMillis")
                    )
                )
            }
        }

        val workoutSessions = mutableListOf<WorkoutSessionEntity>()
        val sessionsArray = root.optJSONArray("workoutSessions")
        if (sessionsArray != null) {
            for (i in 0 until sessionsArray.length()) {
                val obj = sessionsArray.getJSONObject(i)
                workoutSessions.add(
                    WorkoutSessionEntity(
                        sessionId = obj.getLong("sessionId"),
                        dateEpochDay = obj.getLong("dateEpochDay"),
                        planName = obj.getString("planName"),
                        startTimeMillis = obj.getLong("startTimeMillis"),
                        endTimeMillis = if (obj.has("endTimeMillis") && !obj.isNull("endTimeMillis")) {
                            obj.getLong("endTimeMillis")
                        } else {
                            null
                        },
                        caloriesBurned = obj.optInt("caloriesBurned", 0)
                    )
                )
            }
        }

        val workoutSets = mutableListOf<WorkoutSetEntity>()
        val setsArray = root.optJSONArray("workoutSets")
        if (setsArray != null) {
            for (i in 0 until setsArray.length()) {
                val obj = setsArray.getJSONObject(i)
                workoutSets.add(
                    WorkoutSetEntity(
                        setId = obj.getLong("setId"),
                        sessionId = obj.getLong("sessionId"),
                        exerciseName = obj.getString("exerciseName"),
                        setType = obj.getString("setType"),
                        setOrder = obj.getInt("setOrder"),
                        weightKg = obj.getDouble("weightKg"),
                        reps = obj.getInt("reps"),
                        e1rm = obj.getDouble("e1rm"),
                        timestampMillis = obj.getLong("timestampMillis")
                    )
                )
            }
        }

        return BackupPayload(
            schemaVersion = schemaVersion,
            userProfile = userProfile,
            workoutPlans = workoutPlans,
            exerciseHistory = exerciseHistory,
            workoutSessions = workoutSessions,
            workoutSets = workoutSets
        )
    }

    private fun exportWithFallbackBuilder(
        profile: UserProfileEntity?,
        plans: List<WorkoutPlanEntity>,
        history: List<ExerciseHistoryEntity>,
        sessions: List<WorkoutSessionEntity>,
        sets: List<WorkoutSetEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("{\"schemaVersion\": ").append(CURRENT_SCHEMA_VERSION)

        if (profile != null) {
            sb.append(",\"userProfile\":{")
            sb.append("\"uid\":\"").append(escapeJson(profile.uid)).append("\",")
            sb.append("\"name\":\"").append(escapeJson(profile.name)).append("\",")
            sb.append("\"birthDateMillis\":").append(profile.birthDateMillis).append(",")
            sb.append("\"heightCm\":").append(profile.heightCm).append(",")
            sb.append("\"weightKg\":").append(profile.weightKg).append(",")
            sb.append("\"currentStreak\":").append(profile.currentStreak).append(",")
            sb.append("\"consecutiveMissed\":").append(profile.consecutiveMissed).append(",")
            sb.append("\"lastWorkoutEpochDay\":").append(profile.lastWorkoutEpochDay ?: "null").append(",")
            sb.append("\"isGoogleLinked\":").append(profile.isGoogleLinked).append(",")
            sb.append("\"isEmailLinked\":").append(profile.isEmailLinked)
            sb.append("}")
        }

        sb.append(",\"workoutPlans\":[")
        plans.forEachIndexed { index, plan ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"dayOfWeek\":").append(plan.dayOfWeek).append(",")
            sb.append("\"isRestDay\":").append(plan.isRestDay).append(",")
            sb.append("\"planName\":\"").append(escapeJson(plan.planName)).append("\"")
            sb.append("}")
        }
        sb.append("]")

        sb.append(",\"exerciseHistory\":[")
        history.forEachIndexed { index, item ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"normalizedName\":\"").append(escapeJson(item.normalizedName)).append("\",")
            sb.append("\"muscleGroup\":\"").append(escapeJson(item.muscleGroup)).append("\",")
            sb.append("\"lastUsedMillis\":").append(item.lastUsedMillis)
            sb.append("}")
        }
        sb.append("]")

        sb.append(",\"workoutSessions\":[")
        sessions.forEachIndexed { index, session ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"sessionId\":").append(session.sessionId).append(",")
            sb.append("\"dateEpochDay\":").append(session.dateEpochDay).append(",")
            sb.append("\"planName\":\"").append(escapeJson(session.planName)).append("\",")
            sb.append("\"startTimeMillis\":").append(session.startTimeMillis).append(",")
            sb.append("\"endTimeMillis\":").append(session.endTimeMillis ?: "null").append(",")
            sb.append("\"caloriesBurned\":").append(session.caloriesBurned)
            sb.append("}")
        }
        sb.append("]")

        sb.append(",\"workoutSets\":[")
        sets.forEachIndexed { index, set ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"setId\":").append(set.setId).append(",")
            sb.append("\"sessionId\":").append(set.sessionId).append(",")
            sb.append("\"exerciseName\":\"").append(escapeJson(set.exerciseName)).append("\",")
            sb.append("\"setType\":\"").append(escapeJson(set.setType)).append("\",")
            sb.append("\"setOrder\":").append(set.setOrder).append(",")
            sb.append("\"weightKg\":").append(set.weightKg).append(",")
            sb.append("\"reps\":").append(set.reps).append(",")
            sb.append("\"e1rm\":").append(set.e1rm).append(",")
            sb.append("\"timestampMillis\":").append(set.timestampMillis)
            sb.append("}")
        }
        sb.append("]}")

        return sb.toString()
    }

    private fun parseWithFallbackParser(jsonString: String): BackupPayload {
        val versionMatch = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)").find(jsonString)
            ?: throw IllegalArgumentException("Missing schemaVersion")
        val schemaVersion = versionMatch.groupValues[1].toInt()

        val profileBlock = extractObjectBlock(jsonString, "userProfile")
        val userProfile = profileBlock?.let { block ->
            UserProfileEntity(
                uid = extractString(block, "uid"),
                name = extractString(block, "name"),
                birthDateMillis = extractLong(block, "birthDateMillis"),
                heightCm = extractDouble(block, "heightCm"),
                weightKg = extractDouble(block, "weightKg"),
                currentStreak = extractInt(block, "currentStreak", 0),
                consecutiveMissed = extractInt(block, "consecutiveMissed", 0),
                lastWorkoutEpochDay = extractNullableLong(block, "lastWorkoutEpochDay"),
                isGoogleLinked = extractBoolean(block, "isGoogleLinked", false),
                isEmailLinked = extractBoolean(block, "isEmailLinked", false)
            )
        }

        val workoutPlans = extractArrayObjects(jsonString, "workoutPlans").map { block ->
            WorkoutPlanEntity(
                dayOfWeek = extractInt(block, "dayOfWeek"),
                isRestDay = extractBoolean(block, "isRestDay"),
                planName = extractString(block, "planName")
            )
        }

        val exerciseHistory = extractArrayObjects(jsonString, "exerciseHistory").map { block ->
            ExerciseHistoryEntity(
                normalizedName = extractString(block, "normalizedName"),
                muscleGroup = extractString(block, "muscleGroup"),
                lastUsedMillis = extractLong(block, "lastUsedMillis")
            )
        }

        val workoutSessions = extractArrayObjects(jsonString, "workoutSessions").map { block ->
            WorkoutSessionEntity(
                sessionId = extractLong(block, "sessionId"),
                dateEpochDay = extractLong(block, "dateEpochDay"),
                planName = extractString(block, "planName"),
                startTimeMillis = extractLong(block, "startTimeMillis"),
                endTimeMillis = extractNullableLong(block, "endTimeMillis"),
                caloriesBurned = extractInt(block, "caloriesBurned", 0)
            )
        }

        val workoutSets = extractArrayObjects(jsonString, "workoutSets").map { block ->
            WorkoutSetEntity(
                setId = extractLong(block, "setId"),
                sessionId = extractLong(block, "sessionId"),
                exerciseName = extractString(block, "exerciseName"),
                setType = extractString(block, "setType"),
                setOrder = extractInt(block, "setOrder"),
                weightKg = extractDouble(block, "weightKg"),
                reps = extractInt(block, "reps"),
                e1rm = extractDouble(block, "e1rm"),
                timestampMillis = extractLong(block, "timestampMillis")
            )
        }

        return BackupPayload(
            schemaVersion = schemaVersion,
            userProfile = userProfile,
            workoutPlans = workoutPlans,
            exerciseHistory = exerciseHistory,
            workoutSessions = workoutSessions,
            workoutSets = workoutSets
        )
    }

    private fun extractObjectBlock(source: String, key: String): String? {
        val keyToken = "\"$key\""
        val keyIndex = source.indexOf(keyToken)
        if (keyIndex < 0) return null
        val colonIndex = source.indexOf(':', keyIndex + keyToken.length)
        if (colonIndex < 0) return null
        val startBrace = source.indexOf('{', colonIndex)
        if (startBrace < 0) return null
        var depth = 0
        for (i in startBrace until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(startBrace, i + 1)
                }
            }
        }
        throw IllegalArgumentException("Unclosed object for $key")
    }

    private fun extractArrayObjects(source: String, key: String): List<String> {
        val keyToken = "\"$key\""
        val keyIndex = source.indexOf(keyToken)
        if (keyIndex < 0) return emptyList()
        val startBracket = source.indexOf('[', keyIndex + keyToken.length)
        if (startBracket < 0) return emptyList()

        var bracketDepth = 0
        var endBracket = -1
        for (i in startBracket until source.length) {
            when (source[i]) {
                '[' -> bracketDepth++
                ']' -> {
                    bracketDepth--
                    if (bracketDepth == 0) {
                        endBracket = i
                        break
                    }
                }
            }
        }
        if (endBracket < 0) throw IllegalArgumentException("Unclosed array for $key")

        val arrayBody = source.substring(startBracket + 1, endBracket)
        val objects = mutableListOf<String>()
        var braceDepth = 0
        var objStart = -1
        for (i in arrayBody.indices) {
            when (arrayBody[i]) {
                '{' -> {
                    if (braceDepth == 0) objStart = i
                    braceDepth++
                }
                '}' -> {
                    braceDepth--
                    if (braceDepth == 0 && objStart >= 0) {
                        objects.add(arrayBody.substring(objStart, i + 1))
                        objStart = -1
                    }
                }
            }
        }
        return objects
    }

    private fun extractString(block: String, key: String): String {
        val match = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(block)
            ?: throw IllegalArgumentException("Missing string $key")
        return match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun extractLong(block: String, key: String): Long {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(block)
            ?: throw IllegalArgumentException("Missing long $key")
        return match.groupValues[1].toLong()
    }

    private fun extractNullableLong(block: String, key: String): Long? {
        val nullMatch = Regex("\"$key\"\\s*:\\s*null").find(block)
        if (nullMatch != null) return null
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(block) ?: return null
        return match.groupValues[1].toLong()
    }

    private fun extractInt(block: String, key: String, default: Int? = null): Int {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(block)
        if (match == null) {
            return default ?: throw IllegalArgumentException("Missing int $key")
        }
        return match.groupValues[1].toInt()
    }

    private fun extractDouble(block: String, key: String): Double {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(block)
            ?: throw IllegalArgumentException("Missing double $key")
        return match.groupValues[1].toDouble()
    }

    private fun extractBoolean(block: String, key: String, default: Boolean? = null): Boolean {
        val match = Regex("\"$key\"\\s*:\\s*(true|false)").find(block)
        if (match == null) {
            return default ?: throw IllegalArgumentException("Missing boolean $key")
        }
        return match.groupValues[1].toBooleanStrict()
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private data class BackupPayload(
        val schemaVersion: Int,
        val userProfile: UserProfileEntity?,
        val workoutPlans: List<WorkoutPlanEntity>,
        val exerciseHistory: List<ExerciseHistoryEntity>,
        val workoutSessions: List<WorkoutSessionEntity>,
        val workoutSets: List<WorkoutSetEntity>
    )
}

