package com.deepline.fishing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.ui.i18n.UiText

/**
 * Text built outside composables ([UiText]) as the player reads it, in the test's language
 * (English under Robolectric). Needs a Robolectric test (an Android context).
 */
val UiText.text: String get() = resolve(ApplicationProvider.getApplicationContext<Context>())

/** A string resource in the test's language. */
fun str(id: Int, vararg args: Any): String = UiText.res(id, *args).text
