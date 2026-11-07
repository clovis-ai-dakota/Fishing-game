package com.deepline.fishing.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.deepline.fishing.R
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.asString
import java.text.NumberFormat

/*
 * Numbers and units as the player reads them, in the game's language: digit grouping and decimal
 * separators come from the locale, unit words from strings_common.xml. The default locale follows
 * the game's language (see AppLanguages.wrap), so the plain functions work outside composables too.
 */

/** "12,500" (English), "12 500" (French), "12.500" (German): a whole number with grouping. */
fun formatPoints(value: Long): String = NumberFormat.getIntegerInstance().format(value)

fun formatPoints(value: Int): String = formatPoints(value.toLong())

/** A decimal number with at most [maxDecimals] decimals ("4.5", "4,5"). */
fun formatDecimal(value: Double, maxDecimals: Int = 1): String =
    NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = maxDecimals
        minimumFractionDigits = 0
    }.format(value)

/** "1,250 pts": a points amount with its unit. */
fun pointsText(value: Long): UiText = UiText.res(R.string.common_points, UiText.number(value))

@Composable
@ReadOnlyComposable
fun pointsLabel(value: Long): String = pointsText(value).asString()

/** "950", "5k", "25k", "1.5M": for tight spots such as chips. */
fun compactPointsText(value: Long): UiText = when {
    value >= 1_000_000 -> UiText.res(R.string.common_compact_millions, UiText.decimal(value / 100_000 / 10.0))
    value >= 1_000 -> UiText.res(R.string.common_compact_thousands, UiText.decimal(value / 100 / 10.0))
    else -> UiText.number(value)
}

@Composable
@ReadOnlyComposable
fun compactPoints(value: Long): String = compactPointsText(value).asString()

/** "21:00": a time on the hour. */
fun hourText(hour: Int): UiText = UiText.res(R.string.common_time_on_the_hour, hour)

/** A rope stat's value with its unit ("120 m", "10 kg", "4.5 m/s"). Rope upgrades are in the Shop tab. */
fun statText(stat: RopeStat, value: Float): UiText = when (stat) {
    RopeStat.LENGTH -> UiText.res(R.string.common_meters, UiText.decimal(value, 0))
    RopeStat.STRENGTH -> UiText.res(R.string.common_kg, UiText.decimal(value, 0))
    RopeStat.AGILITY -> UiText.res(R.string.common_meters_per_second, UiText.decimal(value, 1))
}

@Composable
@ReadOnlyComposable
fun formatStat(stat: RopeStat, value: Float): String {
    LocalConfiguration.current
    return statText(stat, value).resolve(LocalContext.current.resources)
}

/** "120 m": meters, rounded to whole meters. */
fun metersText(value: Float): UiText = UiText.res(R.string.common_meters, UiText.decimal(value, 0))
