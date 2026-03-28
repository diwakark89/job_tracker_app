package com.thewalkersoft.linkedin_job_tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ============================================================================
// LinkedIn Job Tracker Color Schemes (Material 3)
// ============================================================================

private val FintechDarkColorScheme = darkColorScheme(
    primary = LinkedInPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF073B75),
    onPrimaryContainer = Color(0xFFD6E4FF),

    secondary = LinkedInSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0B5E38),
    onSecondaryContainer = Color(0xFFCFE9DD),

    tertiary = LinkedInTertiary,
    onTertiary = DarkBackground,
    tertiaryContainer = Color(0xFF6F4A10),
    onTertiaryContainer = Color(0xFFF1E2C4),

    background = DarkBackground,
    onBackground = OnDarkSurface,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFDCE3E8),

    error = CriticalRed,
    onError = Color.White,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF2B8B5),

    outline = Color(0xFF3A4249),
    outlineVariant = Color(0xFF2F353A)
)

// ============================================================================
// Light Color Scheme
// ============================================================================

private val FintechLightColorScheme = lightColorScheme(
    primary = LinkedInPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF062C55),

    secondary = LinkedInSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9DD),
    onSecondaryContainer = Color(0xFF043F25),

    tertiary = LinkedInTertiary,
    onTertiary = OnLightSurface,
    tertiaryContainer = Color(0xFFF1E2C4),
    onTertiaryContainer = Color(0xFF4A2D05),

    background = LightBackground,
    onBackground = OnLightSurface,
    surface = LightSurface,
    onSurface = OnLightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF364049),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    outline = Color(0xFFC7D0D6),
    outlineVariant = Color(0xFFDCE3E8)
)

// ============================================================================
// Theme Configuration
// ============================================================================

@Composable
fun LinkedIn_Job_TrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color support: uses device wallpaper colors on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Priority 1: Dynamic Color from System (Android 12+)
        // This automatically adapts colors based on user's wallpaper
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        // Priority 2: Custom Fintech Dark Theme
        darkTheme -> FintechDarkColorScheme

        // Priority 3: Fintech Light Theme
        else -> FintechLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}