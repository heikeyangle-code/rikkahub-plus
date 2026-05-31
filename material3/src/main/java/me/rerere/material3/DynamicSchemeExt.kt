package me.rerere.material3

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dynamiccolor.DynamicScheme
import palettes.TonalPalette

fun DynamicScheme.toColorScheme(): ColorScheme {
    fun TonalPalette.tone(light: Double, dark: Double): Color {
        return Color(if (isDark) getHct(dark).toInt() else getHct(light).toInt())
    }
    val s = this
    return if (isDark) {
        darkColorScheme(
            primary = s.primaryPalette.tone(40.0, 80.0),
            onPrimary = s.primaryPalette.tone(100.0, 20.0),
            primaryContainer = s.primaryPalette.tone(90.0, 30.0),
            onPrimaryContainer = s.primaryPalette.tone(10.0, 90.0),
            inversePrimary = s.primaryPalette.tone(80.0, 40.0),
            secondary = s.secondaryPalette.tone(40.0, 80.0),
            onSecondary = s.secondaryPalette.tone(100.0, 20.0),
            secondaryContainer = s.secondaryPalette.tone(90.0, 30.0),
            onSecondaryContainer = s.secondaryPalette.tone(10.0, 90.0),
            tertiary = s.tertiaryPalette.tone(40.0, 80.0),
            onTertiary = s.tertiaryPalette.tone(100.0, 20.0),
            tertiaryContainer = s.tertiaryPalette.tone(90.0, 30.0),
            onTertiaryContainer = s.tertiaryPalette.tone(10.0, 90.0),
            background = s.neutralPalette.tone(98.0, 6.0),
            onBackground = s.neutralPalette.tone(10.0, 90.0),
            surface = s.neutralPalette.tone(98.0, 6.0),
            onSurface = s.neutralPalette.tone(10.0, 90.0),
            surfaceVariant = s.neutralVariantPalette.tone(90.0, 30.0),
            onSurfaceVariant = s.neutralVariantPalette.tone(30.0, 80.0),
            surfaceTint = s.primaryPalette.tone(40.0, 80.0),
            inverseSurface = s.neutralPalette.tone(20.0, 90.0),
            inverseOnSurface = s.neutralPalette.tone(95.0, 20.0),
            error = s.errorPalette.tone(40.0, 80.0),
            onError = s.errorPalette.tone(100.0, 20.0),
            errorContainer = s.errorPalette.tone(90.0, 30.0),
            onErrorContainer = s.errorPalette.tone(10.0, 90.0),
            outline = s.neutralVariantPalette.tone(50.0, 60.0),
            outlineVariant = s.neutralVariantPalette.tone(80.0, 30.0),
        )
    } else {
        lightColorScheme(
            primary = s.primaryPalette.tone(40.0, 80.0),
            onPrimary = s.primaryPalette.tone(100.0, 20.0),
            primaryContainer = s.primaryPalette.tone(90.0, 30.0),
            onPrimaryContainer = s.primaryPalette.tone(10.0, 90.0),
            inversePrimary = s.primaryPalette.tone(80.0, 40.0),
            secondary = s.secondaryPalette.tone(40.0, 80.0),
            onSecondary = s.secondaryPalette.tone(100.0, 20.0),
            secondaryContainer = s.secondaryPalette.tone(90.0, 30.0),
            onSecondaryContainer = s.secondaryPalette.tone(10.0, 90.0),
            tertiary = s.tertiaryPalette.tone(40.0, 80.0),
            onTertiary = s.tertiaryPalette.tone(100.0, 20.0),
            tertiaryContainer = s.tertiaryPalette.tone(90.0, 30.0),
            onTertiaryContainer = s.tertiaryPalette.tone(10.0, 90.0),
            background = s.neutralPalette.tone(98.0, 6.0),
            onBackground = s.neutralPalette.tone(10.0, 90.0),
            surface = s.neutralPalette.tone(98.0, 6.0),
            onSurface = s.neutralPalette.tone(10.0, 90.0),
            surfaceVariant = s.neutralVariantPalette.tone(90.0, 30.0),
            onSurfaceVariant = s.neutralVariantPalette.tone(30.0, 80.0),
            surfaceTint = s.primaryPalette.tone(40.0, 80.0),
            inverseSurface = s.neutralPalette.tone(20.0, 90.0),
            inverseOnSurface = s.neutralPalette.tone(95.0, 20.0),
            error = s.errorPalette.tone(40.0, 80.0),
            onError = s.errorPalette.tone(100.0, 20.0),
            errorContainer = s.errorPalette.tone(90.0, 30.0),
            onErrorContainer = s.errorPalette.tone(10.0, 90.0),
            outline = s.neutralVariantPalette.tone(50.0, 60.0),
            outlineVariant = s.neutralVariantPalette.tone(80.0, 30.0),
        )
    }
}
