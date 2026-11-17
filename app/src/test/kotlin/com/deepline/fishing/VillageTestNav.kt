package com.deepline.fishing

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/** A building's plate tag, by the names tests use (the old tabs' names work too: fish, battle). */
private fun plateTag(name: String) = "village_" + when (name) {
    "fish" -> "fishing"
    "battle" -> "duel"
    else -> name
}

/** Whether the village is on screen. */
fun ComposeTestRule.inVillage(): Boolean = onAllNodesWithTag("village").fetchSemanticsNodes().isNotEmpty()

/** Back up to the village with the screens' own back buttons. */
fun ComposeTestRule.backToVillage() {
    waitForIdle()
    for (i in 0 until 5) {
        if (inVillage()) break
        if (onAllNodesWithTag("back_village").fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag("back_village").performClick()
        } else {
            onAllNodesWithTag("back").onFirst().performClick()
        }
        waitForIdle()
    }
}

/**
 * Goes to a building's menu the way a player does: back up to the village, then a tap on the
 * building's name plate.
 */
fun ComposeTestRule.openBuilding(name: String) {
    backToVillage()
    onNodeWithTag(plateTag(name)).performClick()
    waitForIdle()
}
