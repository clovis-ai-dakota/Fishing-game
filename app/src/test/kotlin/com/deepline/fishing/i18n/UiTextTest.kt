package com.deepline.fishing.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.R
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.ui.i18n.nameText
import com.deepline.fishing.ui.compactPointsText
import com.deepline.fishing.ui.hourText
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.pointsText
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.social.ordinal
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** [UiText] resolves resources, nested arguments, plurals and numbers in the resources' language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiTextTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun resourcesIn(tag: String): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) }
        return context.createConfigurationContext(config).resources
    }

    @Test
    fun resourcesAndNestedArguments() {
        val en = context.resources
        assertEquals("Thank you! Lantern pouch: 3 Lanterns.", UiText.res(R.string.shop_message_thanks, ShopCatalog.byId("lantern_pouch")!!.nameText(), ShopText.tackleCount(Tackle.LANTERN, 3)).resolve(en))
        assertEquals("1 Lantern", UiText.plural(R.plurals.common_lanterns, 1).resolve(en))
        assertEquals("2 days", UiText.plural(R.plurals.common_days, 2).resolve(en))
        assertEquals("Ahab", UiText.raw("Ahab").resolve(en))
        assertEquals("a, b and c", UiText.list(listOf(UiText.raw("a"), UiText.raw("b"), UiText.raw("c"))).resolve(en))
        assertEquals("a and b", UiText.list(listOf(UiText.raw("a"), UiText.raw("b"))).resolve(en))
        assertEquals("x\ny", UiText.lines(listOf(UiText.raw("x"), UiText.raw("y"))).resolve(en))
        assertEquals("21:00", hourText(21).resolve(en))
    }

    @Test
    fun numbersFollowTheLanguage() {
        assertEquals("12,500 pts", pointsText(12_500).resolve(resourcesIn("en")))
        // French groups with a (narrow) no-break space, German with a dot.
        assertEquals("12500", pointsText(12_500).resolve(resourcesIn("fr")).filter { it.isDigit() })
        assertEquals(false, pointsText(12_500).resolve(resourcesIn("fr")).contains(','))
        assertEquals("12.500 pts", pointsText(12_500).resolve(resourcesIn("de")))
        assertEquals("1,5", UiText.decimal(1.5).resolve(resourcesIn("de")))
        assertEquals("1.5", UiText.decimal(1.5).resolve(resourcesIn("en")))
        assertEquals("25k", compactPointsText(25_000).resolve(resourcesIn("en")))
        assertEquals("1.5M", compactPointsText(1_500_000).resolve(resourcesIn("en")))
        assertEquals("950", compactPointsText(950).resolve(resourcesIn("en")))
    }

    @Test
    fun ranksAreTheLanguagesOrdinals() {
        // Tournament ranks ("You finished 3rd of 40").
        assertEquals(listOf("1st", "2nd", "3rd", "11th", "23rd"), listOf(1, 2, 3, 11, 23).map { ordinal(it, Locale.ENGLISH) })
        assertEquals(listOf("1er", "2e"), listOf(1, 2).map { ordinal(it, Locale.FRENCH) })
        assertEquals("3.", ordinal(3, Locale.GERMAN))
    }

    @Test
    fun equalTextsAreEqual() {
        // Messages are compared (state, tests): same resource and arguments, same message.
        assertEquals(UiText.res(R.string.common_points, UiText.number(5)), UiText.res(R.string.common_points, UiText.number(5)))
    }
}
