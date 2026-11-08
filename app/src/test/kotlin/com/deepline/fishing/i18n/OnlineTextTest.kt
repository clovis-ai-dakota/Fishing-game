package com.deepline.fishing.i18n

import com.deepline.fishing.R
import com.deepline.fishing.ui.OnlineText
import com.deepline.fishing.ui.i18n.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The battle server's English texts are shown in the player's language ([OnlineText]). A plain
 * JVM test: it reads the server's source, so a new or reworded server text fails here until it
 * has a string.
 */
class OnlineTextTest {
    private val lobby = File("../server/src/main/kotlin/com/deepline/fishing/server/Lobby.kt")

    /** The server's texts, with its templates filled in ("${c.name}" -> a name, "$code" -> a code). */
    private fun serverTexts(pattern: Regex): List<String> {
        assertTrue("${lobby.path} not found", lobby.isFile)
        return pattern.findAll(lobby.readText()).map { m ->
            m.groupValues[1]
                .replace(Regex("\\$\\{[^}]*name[^}]*\\}"), "Ana Lee")
                .replace(Regex("\\$\\{[^}]*\\}|\\$[a-zA-Z]+"), "K7QXP")
        }.toList()
    }

    @Test
    fun everyServerErrorHasAString() {
        val errors = serverTexts(Regex("ServerMessage\\.Error\\(\"([^\"]+)\"\\)")) + serverTexts(Regex("error = \"([^\"]+)\""))
        assertTrue("the server's errors weren't found", errors.size >= 6)
        val unmapped = errors.filter { OnlineText.error(it) is UiText.Raw }
        if (unmapped.isNotEmpty()) fail("Server errors without a string (add them to OnlineText and strings_battle.xml): $unmapped")
    }

    @Test
    fun everyForfeitReasonHasAString() {
        val reasons = serverTexts(Regex("onForfeit\\([^,]+, \"([^\"]+)\"\\)"))
        assertTrue("the server's forfeit reasons weren't found", reasons.size >= 2)
        val unmapped = reasons.filter { OnlineText.overReason(it) is UiText.Raw }
        if (unmapped.isNotEmpty()) fail("Forfeit reasons without a string (add them to OnlineText and strings_battle.xml): $unmapped")
    }

    @Test
    fun namesAndCodesArePassedAsTheyAre() {
        assertEquals(UiText.res(R.string.online_error_no_room, "K7QXP"), OnlineText.error("No room with the code K7QXP."))
        assertEquals(UiText.res(R.string.online_error_busy), OnlineText.error("You are already waiting for or playing a match."))
        assertEquals(UiText.res(R.string.online_over_forfeited, "Ana Lee"), OnlineText.overReason("Ana Lee forfeited."))
        assertEquals(UiText.res(R.string.online_over_left, "Bo forfeited"), OnlineText.overReason("Bo forfeited left the battle."))
    }

    @Test
    fun unknownTextsAreShownAsSent() {
        // A newer server's message, or a reason the app wrote itself (a lost connection).
        assertEquals(UiText.raw("Server is restarting."), OnlineText.error("Server is restarting."))
        assertEquals(UiText.raw("Disconnected from the server."), OnlineText.overReason("Disconnected from the server."))
    }
}
