package com.example.sequencemakerbuddy.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Greyscale color schemes — the entire UI is black & white so the
 * simulated ball colors are the only colour on screen.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Grey90,
    onPrimary = Color.Black,
    secondary = Grey70,
    onSecondary = Color.Black,
    tertiary = Grey50,
    onTertiary = Color.Black,
    background = Grey10,
    onBackground = Grey90,
    surface = Grey20,
    onSurface = Grey90,
    surfaceVariant = Grey30,
    onSurfaceVariant = Grey70,
    outline = Grey50
)

private val LightColorScheme = lightColorScheme(
    primary = Grey30,
    onPrimary = Color.White,
    secondary = Grey50,
    onSecondary = Color.White,
    tertiary = Grey70,
    onTertiary = Color.Black,
    background = Color.White,
    onBackground = Grey10,
    surface = Grey90,
    onSurface = Grey10,
    surfaceVariant = Grey70,
    onSurfaceVariant = Grey30,
    outline = Grey50
)

@Composable
fun SequenceMakerBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled — we want a strict greyscale look
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
