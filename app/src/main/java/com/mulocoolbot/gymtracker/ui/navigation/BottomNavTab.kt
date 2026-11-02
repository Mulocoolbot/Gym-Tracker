package com.mulocoolbot.gymtracker.ui.navigation

enum class BottomNavTab(
    val route: String,
    val label: String
) {
    HOME(route = "home", label = "Home"),
    PLAN(route = "plan", label = "Plan"),
    WORKOUT(route = "workout", label = "Workout"),
    STATS(route = "stats", label = "Stats"),
    SETTINGS(route = "settings", label = "Settings")
}

