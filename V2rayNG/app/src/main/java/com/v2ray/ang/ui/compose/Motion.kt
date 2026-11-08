package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/**
 * The motion vocabulary of the fork: four durations and four specs, nothing else.
 *
 * Every spec goes through androidx.compose.animation.core, so the system "remove animations"
 * setting (Settings.Global.animator_duration_scale, surfaced to Compose as MotionDurationScale)
 * collapses each of them to its final value in a single frame. No spec here needs its own guard.
 */

/** 90 ms: a colour or alpha change that must not read as a transition. */
internal const val HOME_MOTION_INSTANT_MS: Int = 90

/** 180 ms: the standard length of a structural change, e.g. a section opening. */
internal const val HOME_MOTION_STANDARD_MS: Int = 180

/** 240 ms: a change that moves something the user is looking at. */
internal const val HOME_MOTION_EMPHASIS_MS: Int = 240

/** 320 ms: the longest length allowed; reserved for the connection card switching. */
internal const val HOME_MOTION_MAX_MS: Int = 320

/** Size and position changes of a section as it opens or closes; fed to `animateContentSize`. */
internal val HomeMotionEnterSpec: FiniteAnimationSpec<IntSize> =
    tween(durationMillis = HOME_MOTION_STANDARD_MS, easing = FastOutSlowInEasing)

/** Colour and alpha changes, including the lit/unlit screen background. */
internal val HomeMotionFadeSpec: TweenSpec<Color> =
    tween(durationMillis = HOME_MOTION_INSTANT_MS, easing = FastOutSlowInEasing)

/** Slow settle of something the user is dragging or that has just landed. */
internal val HomeMotionEmphasisSpec: TweenSpec<Float> =
    tween(durationMillis = HOME_MOTION_EMPHASIS_MS, easing = FastOutSlowInEasing)

/**
 * The emphasis length as a colour change: a status that turns while the user is watching it, as
 * opposed to [HomeMotionFadeSpec], which is short enough not to read as a transition.
 */
internal val HomeMotionEmphasisColorSpec: TweenSpec<Color> =
    tween(durationMillis = HOME_MOTION_EMPHASIS_MS, easing = FastOutSlowInEasing)

/**
 * The card opening and closing as the connection comes up and goes down: a structural change the
 * user is looking at, so it takes the emphasis length, and it is longer than [HomeMotionFadeSpec]
 * so the colour of the card and the air inside it move together instead of the colour arriving
 * first. Fed to `animateFloatAsState`/`animateColorAsState`.
 */
internal val HomeMotionRevealSpec: TweenSpec<Float> =
    tween(durationMillis = HOME_MOTION_EMPHASIS_MS, easing = FastOutSlowInEasing)

/**
 * The press scale of a tile, as a spec rather than a raw spring: no bounce, so a finger that rests
 * on a tile does not make it wobble. Fed to `animateFloatAsState`, so it is typed on `Float`.
 */
internal fun homeMotionSpringSpec(): SpringSpec<Float> =
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
