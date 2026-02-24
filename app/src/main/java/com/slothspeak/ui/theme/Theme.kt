package com.slothspeak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SlothSpeakColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = PureBlack,
    secondary = AccentGreen,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = PureWhite,
    surface = SurfaceDark,
    onSurface = PureWhite,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = PureWhite,
    error = ErrorRed,
    onError = PureBlack,
    outline = AccentGreen
)

@Composable
fun SlothSpeakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SlothSpeakColorScheme,
        typography = SlothSpeakTypography,
        content = content
    )
}
