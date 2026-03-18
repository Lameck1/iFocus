package com.lameck.ifocus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TomatoDarkPrimary,
    onPrimary = TomatoDarkOnPrimary,
    primaryContainer = TomatoDarkPrimaryContainer,
    onPrimaryContainer = TomatoDarkOnPrimaryContainer,
    secondary = TomatoDarkSecondary,
    onSecondary = TomatoDarkOnSecondary,
    secondaryContainer = TomatoDarkSecondaryContainer,
    onSecondaryContainer = TomatoDarkOnSecondaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = TomatoLightPrimary,
    onPrimary = TomatoLightOnPrimary,
    primaryContainer = TomatoLightPrimaryContainer,
    onPrimaryContainer = TomatoLightOnPrimaryContainer,
    secondary = TomatoLightSecondary,
    onSecondary = TomatoLightOnSecondary,
    secondaryContainer = TomatoLightSecondaryContainer,
    onSecondaryContainer = TomatoLightOnSecondaryContainer
)

@Composable
fun IFocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}