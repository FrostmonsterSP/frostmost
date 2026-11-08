package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Light palette of the FrostMost colour scheme (docs/superpowers/specs/2026-11-08-frostmost-colour-scheme.md).
 * Every text role clears 4.5:1 against `surface`; the ratios are asserted by ColourSchemeTest.
 */
private val LightColor = lightColorScheme(
    primary = Color(0xFF0B57D0), // 6.39:1 on surface
    onPrimary = Color(0xFFFFFFFF), // 6.39:1 on primary
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF0A2E6E), // 9.99:1 on its container
    secondary = Color(0xFFB3261E), // 6.54:1 on surface; replaces #F97910 at 2.70:1
    onSecondary = Color(0xFFFFFFFF), // 6.54:1 on secondary
    secondaryContainer = Color(0xFFFCE8E6),
    onSecondaryContainer = Color(0xFF5C0F0A),
    tertiary = Color(0xFF0A6E4A), // 6.28:1 on surface
    onTertiary = Color(0xFFFFFFFF), // 6.28:1 on tertiary
    tertiaryContainer = Color(0xFFE6F4EA),
    onTertiaryContainer = Color(0xFF0A3B26),
    error = Color(0xFF9B1C1C), // 8.15:1 on surface
    errorContainer = Color(0xFFFCE8E6),
    onError = Color(0xFFFFFFFF), // 8.15:1 on error
    onErrorContainer = Color(0xFF5C0F0A),
    background = Color(0xFFFCF8FA),
    onBackground = Color(0xFF1C1B1F), // 16.27:1 on background
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F), // 17.13:1 on surface
    surfaceVariant = Color(0xFFF0ECEF),
    onSurfaceVariant = Color(0xFF44474E), // 9.30:1 on surface
    outline = Color(0xFF6C6F78), // 5.02:1 on surface; UI borders carry meaning
    outlineVariant = Color(0xFFC4C6CF), // decorative, 1.70:1
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFAAC7FF),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF0B57D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F5F8),
    surfaceContainer = Color(0xFFF4F0F3),
    surfaceContainerHigh = Color(0xFFEEEBED),
    surfaceContainerHighest = Color(0xFFE8E5E8),
)

/**
 * Dark palette: lightness is inverted relative to the light theme, and depth is a lighter container
 * rather than a shadow, as the colour scheme requires.
 */
private val DarkColor = darkColorScheme(
    primary = Color(0xFFAAC7FF), // 10.05:1 on surface
    onPrimary = Color(0xFF0A2E6E), // 7.57:1 on primary
    primaryContainer = Color(0xFF2A3E6E),
    onPrimaryContainer = Color(0xFFD9E2FF), // 8.09:1 on its container
    secondary = Color(0xFFFFB4AB), // 10.09:1 on surface
    onSecondary = Color(0xFF690005), // 7.72:1 on secondary
    secondaryContainer = Color(0xFF6B1410),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFF7EE0A6), // 10.69:1 on surface
    onTertiary = Color(0xFF0A2E1C), // 9.22:1 on tertiary
    tertiaryContainer = Color(0xFF14371F),
    onTertiaryContainer = Color(0xFFB4F0CC),
    error = Color(0xFFFFB3AC), // 10.03:1 on surface
    errorContainer = Color(0xFF6B1410),
    onError = Color(0xFF690005), // 7.67:1 on error
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141317), // dark grey, not pure black
    onBackground = Color(0xFFE5E1E6), // 14.31:1 on background
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE5E1E6), // 13.25:1 on surface
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = Color(0xFFC7C5CE), // 10.04:1 on surface
    outline = Color(0xFF8E909A), // 5.39:1 on surface
    outlineVariant = Color(0xFF44464F), // decorative, 1.82:1
    inverseSurface = Color(0xFFE5E1E6),
    inverseOnSurface = Color(0xFF1C1B1F),
    inversePrimary = Color(0xFF0B57D0),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFAAC7FF),
    surfaceContainerLowest = Color(0xFF0F0E12),
    surfaceContainerLow = Color(0xFF19181C),
    surfaceContainer = Color(0xFF1E1D21),
    surfaceContainerHigh = Color(0xFF282729),
    surfaceContainerHighest = Color(0xFF333234),
)

// ---------- Status roles ----------
// A status colour is never painted straight onto a subscription accent: the pixel measurements in
// docs/superpowers/specs/2026-11-08-frostmost-colour-scheme.md put every such combination at about
// 1.0-2.9:1. Status therefore always travels inside a chip that carries its own background and its
// own foreground, and the colour is never the only channel (a glyph or the text says the same).
//
// Dark theme needs two values per status on purpose:
//   * *Chip / *OnChip  -- a light tint with dark text, because a dark chip would sink into the dark
//                         accents (measured 1.90:1 for the discarded dark-chip variant);
//   * *OnNeutral       -- the saturated tone for coloured text directly on a neutral surface.

/** Palette of one status role. */
data class StatusColors(
    /** Chip background. */
    val chip: Color,
    /** Text and glyph on [chip]. */
    val onChip: Color,
    /** The role as coloured text on a neutral surface. */
    val onNeutral: Color,
)

val LightStatusSuccess = StatusColors(Color(0xFFE6F4EA), Color(0xFF17663C), Color(0xFF17663C))
val LightStatusWarning = StatusColors(Color(0xFFFDF0D9), Color(0xFF7A4A00), Color(0xFF7A4A00))
val LightStatusDanger = StatusColors(Color(0xFFFCE8E6), Color(0xFF9B1C1C), Color(0xFF9B1C1C))
val LightStatusInfo = StatusColors(Color(0xFFE5F0FC), Color(0xFF15497F), Color(0xFF15497F))

val DarkStatusSuccess = StatusColors(Color(0xFFB4F0CC), Color(0xFF12110F), Color(0xFF7EE0A6))
val DarkStatusWarning = StatusColors(Color(0xFFF7DFA8), Color(0xFF12110F), Color(0xFFF5C97A))
val DarkStatusDanger = StatusColors(Color(0xFFF9C9C4), Color(0xFF12110F), Color(0xFFFFB3AC))
val DarkStatusInfo = StatusColors(Color(0xFFBEDAF7), Color(0xFF12110F), Color(0xFF9CC4F5))

/** Status roles of the current theme, provided by [AppTheme]. */
val LocalStatusColors = staticCompositionLocalOf {
    StatusColorsPalette(
        success = LightStatusSuccess,
        warning = LightStatusWarning,
        danger = LightStatusDanger,
        info = LightStatusInfo,
    )
}

/** The four status roles of one theme. */
data class StatusColorsPalette(
    val success: StatusColors,
    val warning: StatusColors,
    val danger: StatusColors,
    val info: StatusColors,
)

val LightStatusPalette = StatusColorsPalette(
    success = LightStatusSuccess,
    warning = LightStatusWarning,
    danger = LightStatusDanger,
    info = LightStatusInfo,
)

val DarkStatusPalette = StatusColorsPalette(
    success = DarkStatusSuccess,
    warning = DarkStatusWarning,
    danger = DarkStatusDanger,
    info = DarkStatusInfo,
)

// ---------- Service tokens ----------

/** Divider line; decorative, so it stays below the text contrast thresholds by design. */
val dividerColorLight = Color(0xFFE0E0E0)
val dividerColorDark = Color(0xFF424242)

/**
 * Snackbar background. White text on each of these clears 6.3:1 in every theme, which the old
 * 85% translucent green did not (3.23:1 on a light page). The dark neutral is also 2.47:1 against
 * the dark surface, so the bar separates from the page instead of sinking into it.
 */
val snackbarNeutralLight = Color(0xFF353A3E)
val snackbarNeutralDark = Color(0xFF555A62)
val snackbarSuccessLight = Color(0xFF17663C)
val snackbarSuccessDark = Color(0xFF1E6B3C)
val snackbarErrorLight = Color(0xFF9B1C1C)
val snackbarErrorDark = Color(0xFFB3261E)
val snackbarInfoLight = Color(0xFF15497F)
val snackbarInfoDark = Color(0xFF2A4A78)
val snackbarText = Color(0xFFFFFFFF)

/** QR scanner frame; a fixed value so it reads the same whatever the theme does. */
val scannerFrameLight = Color(0xFF17663C)
val scannerFrameDark = Color(0xFF7EE0A6)
val scannerScrim = Color(0x80000000)

/** Type label of a server config in the routing editor. */
val colorConfigTypeLight = Color(0xFF0A6E4A)
val colorConfigTypeDark = Color(0xFF7EE0A6)

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val frost = if (darkTheme) DarkColor else LightColor
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val system = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // The system palette replaces the neutral roles and the primary action only. The accent
            // of a subscription and the status roles stay fixed, so "green means connected" cannot
            // turn into a different colour because the wallpaper changed.
            system.copy(
                primary = frost.primary,
                onPrimary = frost.onPrimary,
                primaryContainer = frost.primaryContainer,
                onPrimaryContainer = frost.onPrimaryContainer,
                error = frost.error,
                onError = frost.onError,
                errorContainer = frost.errorContainer,
                onErrorContainer = frost.onErrorContainer,
            )
        }

        else -> frost
    }
    // Status colours never come from dynamic colour: a status that changed hue with the wallpaper
    // would stop meaning anything.
    val statusColors = if (darkTheme) DarkStatusPalette else LightStatusPalette
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController,
        LocalStatusColors provides statusColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FrostTypography,
            shapes = FrostShapesForComposition,
        ) {
            // The root paints the page itself. Without this a screen that draws text in
            // `onSurface` but sets no Surface of its own showed black text over whatever the
            // window held, i.e. "black on black" in the light theme.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppSnackbarBridge(controller = snackbarController)
                    content()
                    AppSnackbarHost(hostState = snackbarController.hostState)
                }
            }
        }
    }
}
