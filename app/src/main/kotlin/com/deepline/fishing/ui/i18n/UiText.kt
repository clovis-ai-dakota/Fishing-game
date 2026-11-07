package com.deepline.fishing.ui.i18n

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.deepline.fishing.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Player-facing text built outside a composable (the view model, controllers, the tour) and
 * resolved where it is shown, in the language the app is showing then. A message that is on
 * screen when the language changes is shown in the new language.
 *
 * - [res] / [plural]: a string resource with positional arguments (`%1$s`, `%2$d`). Arguments may
 *   be numbers, plain strings or other [UiText]s (resolved first).
 * - [raw]: text that is already final and never translated: names players typed, codes.
 * - [number] / [decimal]: a number formatted for the language (grouping, decimal separator).
 * - [join] / [list]: several texts in one ("a, b and c" for [list], in the language's own words).
 *
 * In a composable: `text.asString()`. Elsewhere, with a Context: `text.resolve(context)` (for a
 * notification built in a worker, use `AppLanguages.localized(context)` first).
 */
sealed interface UiText {
    fun resolve(resources: Resources): String

    fun resolve(context: Context): String = resolve(context.resources)

    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        override fun resolve(resources: Resources): String =
            if (args.isEmpty()) resources.getString(id) else resources.getString(id, *resolveArgs(args, resources))
    }

    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any>) : UiText {
        override fun resolve(resources: Resources): String =
            resources.getQuantityString(id, count, *resolveArgs(args, resources))
    }

    /** Text that is already final: a player's name, a code, a number shown as is. */
    data class Raw(val text: String) : UiText {
        override fun resolve(resources: Resources): String = text
    }

    /** A whole number with the language's digit grouping ("12,500", "12 500", "12.500"). */
    data class Number(val value: Long) : UiText {
        override fun resolve(resources: Resources): String = NumberFormat.getIntegerInstance(resources.locale()).format(value)
    }

    /** A decimal number with the language's separator ("1.5", "1,5"), at most [maxDecimals] decimals. */
    data class Decimal(val value: Double, val maxDecimals: Int = 1) : UiText {
        override fun resolve(resources: Resources): String =
            NumberFormat.getNumberInstance(resources.locale()).apply {
                maximumFractionDigits = maxDecimals
                minimumFractionDigits = 0
            }.format(value)
    }

    /** Texts joined with [separator] (a line break, or a separator that needs no translation like " · "). */
    data class Join(val parts: List<UiText>, val separator: String) : UiText {
        override fun resolve(resources: Resources): String = parts.joinToString(separator) { it.resolve(resources) }
    }

    /** "a", "a and b", "a, b and c": a list in the language's own words (`common_list_*`). */
    data class ListOf(val parts: List<UiText>) : UiText {
        override fun resolve(resources: Resources): String {
            val items = parts.map { it.resolve(resources) }
            return when (items.size) {
                0 -> ""
                1 -> items[0]
                else -> {
                    val head = items.dropLast(1).reduce { acc, s -> resources.getString(R.string.common_list_middle, acc, s) }
                    resources.getString(R.string.common_list_end, head, items.last())
                }
            }
        }
    }

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        /** A quantity string; with no [args], [count] is also the only argument (`%d`). */
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
            Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

        fun raw(text: String): UiText = Raw(text)

        fun number(value: Long): UiText = Number(value)

        fun number(value: Int): UiText = Number(value.toLong())

        fun decimal(value: Double, maxDecimals: Int = 1): UiText = Decimal(value, maxDecimals)

        fun decimal(value: Float, maxDecimals: Int = 1): UiText = Decimal(value.toDouble(), maxDecimals)

        fun join(parts: List<UiText>, separator: String): UiText = Join(parts, separator)

        fun lines(parts: List<UiText>): UiText = Join(parts, "\n")

        fun list(parts: List<UiText>): UiText = ListOf(parts)
    }
}

/** The text in the language shown now (recomposes when the configuration changes). */
@Composable
@ReadOnlyComposable
fun UiText.asString(): String {
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}

private fun resolveArgs(args: List<Any>, resources: Resources): Array<Any> =
    Array(args.size) { i -> (args[i] as? UiText)?.resolve(resources) ?: args[i] }

/** The language these resources are in. */
internal fun Resources.locale(): Locale = configuration.locales[0] ?: Locale.getDefault()
