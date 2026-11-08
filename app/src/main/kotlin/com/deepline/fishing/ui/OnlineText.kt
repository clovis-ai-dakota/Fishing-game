package com.deepline.fishing.ui

import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.UiText

/**
 * The battle server's English texts in the player's language. The protocol carries text, not
 * codes (`ServerMessage.Error.message`, `MatchOver.reason`, kept as they are so older apps still
 * read them), so the texts the server sends (server `Lobby.kt`) are matched here; anything else,
 * like a newer server's message or a reason this app wrote itself, is shown as it came.
 * `OnlineTextTest` reads the server's source and fails on a text that isn't mapped.
 */
object OnlineText {
    private val errors: Map<String, Int> = mapOf(
        "That cast is not expected right now." to R.string.online_error_cast_unexpected,
        "That cast is not possible with the tournament rope." to R.string.online_error_cast_impossible,
        "Unreadable message." to R.string.online_error_unreadable,
        "Please update the app to play online." to R.string.online_error_update_app,
        "You are already waiting for or playing a match." to R.string.online_error_busy,
    )

    private val noRoom = Regex("No room with the code (.+)\\.") // i18n-ignore: the server's English, matched to show ours

    // Why a battle ended early: the server names the player who gave it away (as typed).
    private val forfeited = Regex("(.+) forfeited\\.", RegexOption.DOT_MATCHES_ALL) // i18n-ignore: the server's English, matched
    private val left = Regex("(.+) left the battle\\.", RegexOption.DOT_MATCHES_ALL) // i18n-ignore: the server's English, matched

    /** A `ServerMessage.Error`'s text. */
    fun error(message: String): UiText {
        errors[message]?.let { return UiText.res(it) }
        noRoom.matchEntire(message)?.let { return UiText.res(R.string.online_error_no_room, it.groupValues[1]) }
        return UiText.raw(message)
    }

    /** Why an online battle ended by forfeit (`OnlineMatch.overReason`). */
    fun overReason(reason: String): UiText {
        forfeited.matchEntire(reason)?.let { return UiText.res(R.string.online_over_forfeited, it.groupValues[1]) }
        left.matchEntire(reason)?.let { return UiText.res(R.string.online_over_left, it.groupValues[1]) }
        return UiText.raw(reason)
    }
}
