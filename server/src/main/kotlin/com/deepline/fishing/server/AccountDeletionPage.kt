package com.deepline.fishing.server

import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.online.AccountApi
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseQueryString
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * The web page where a player can delete their online account without the app (Google Play asks
 * for one, linked from the Data safety form): `GET /delete-account` shows a form that takes the
 * recovery code (Shop > Account in the game), `POST` deletes the account like the in-app button.
 *
 * Set `DEEPLINE_PRIVACY_CONTACT` (an email address) and `DEEPLINE_PRIVACY_POLICY_URL` so players
 * without their code know whom to write to (see docs/privacy-policy.md).
 */
class AccountDeletionPage(
    private val contact: String? = System.getenv("DEEPLINE_PRIVACY_CONTACT"),
    private val policyUrl: String? = System.getenv("DEEPLINE_PRIVACY_POLICY_URL"),
) {
    fun form(error: String? = null): String = page(
        """
        <p>This page deletes your <b>Deep Line</b> online account and everything the game server
        keeps for it: your player ID, your cloud backup (Captain's Club), gifts waiting for you, and
        your social data (friends and friend requests, challenges, weekly leaderboard and
        tournament entries, and your club membership).
        It can't be undone, and your recovery code stops working.</p>
        <p>What stays: the progress saved on your phone (uninstall the game to remove it), and your
        purchases, which belong to your Google Play account. A Captain's Club subscription isn't
        cancelled: manage it in Google Play.</p>
        <p>You can also do this in the game: Settings, Privacy, <i>Delete my online account</i>.</p>
        ${error?.let { "<p class=\"error\">${escape(it)}</p>" } ?: ""}
        <form method="post" action="${AccountApi.DELETE_ACCOUNT_PAGE}">
          <label for="code">Your recovery code (in the game: Shop, Account, <i>Show my recovery code</i>)</label>
          <input id="code" name="code" autocomplete="off" spellcheck="false" placeholder="DL-..." required>
          <button type="submit">Delete my account</button>
        </form>
        ${noCodeHelp()}
        """,
    )

    fun done(): String = page(
        """
        <p><b>Your Deep Line online account was deleted.</b> The server no longer keeps anything for
        it. If the game is still installed, it will use a new player ID from now on.</p>
        """,
    )

    private fun noCodeHelp(): String {
        val write = contact?.takeIf { it.isNotBlank() }?.let {
            "write to <a href=\"mailto:${escape(it)}\">${escape(it)}</a> with your player name and roughly when you last played"
        } ?: "write to the address in the privacy policy with your player name and roughly when you last played"
        val policy = policyUrl?.takeIf { it.isNotBlank() }?.let { " <a href=\"${escape(it)}\">Privacy policy</a>." } ?: ""
        return "<p class=\"small\">No recovery code? $write: we'll answer within one month.$policy</p>"
    }

    private fun page(body: String) = """
        <!doctype html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Delete your Deep Line account</title>
        <style>
          body { font-family: system-ui, sans-serif; max-width: 36rem; margin: 2rem auto; padding: 0 1rem; color: #0b2233; background: #f4fafc; line-height: 1.5; }
          h1 { font-size: 1.4rem; }
          label { display: block; margin: 1rem 0 .25rem; }
          input { width: 100%; box-sizing: border-box; padding: .6rem; font: 1rem monospace; }
          button { margin-top: .75rem; padding: .6rem 1rem; font-size: 1rem; background: #a3262a; color: #fff; border: 0; border-radius: .4rem; }
          .error { color: #a3262a; font-weight: bold; }
          .small { font-size: .9rem; }
        </style>
        </head>
        <body>
        <h1>Delete your Deep Line online account</h1>
        $body
        </body>
        </html>
    """.trimIndent()

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

private const val MAX_FORM_BYTES = 4 * 1024

/** `GET` and `POST` [AccountApi.DELETE_ACCOUNT_PAGE]. */
fun Route.accountDeletionPage(accounts: AccountStore, page: AccountDeletionPage = AccountDeletionPage()) {
    get(AccountApi.DELETE_ACCOUNT_PAGE) {
        call.respondText(page.form(), ContentType.Text.Html)
    }
    post(AccountApi.DELETE_ACCOUNT_PAGE) {
        // A recovery code is short: a bigger form isn't read whole.
        val form = call.receiveTextAtMost(MAX_FORM_BYTES).orEmpty()
        val code = runCatching { parseQueryString(form)["code"] }.getOrNull().orEmpty()
        val who = PlayerId.fromRecoveryCode(code)
        if (who == null) {
            call.respondText(
                page.form(error = "That isn't a recovery code: it starts with DL- and has 24 letters and digits."),
                ContentType.Text.Html,
                HttpStatusCode.BadRequest,
            )
            return@post
        }
        try {
            accounts.delete(who.id, who.secret)
            call.respondText(page.done(), ContentType.Text.Html)
        } catch (e: AccountStore.Failure) {
            call.respondText(
                page.form(error = "That recovery code doesn't match an account. Check it and try again."),
                ContentType.Text.Html,
                HttpStatusCode.Forbidden,
            )
        }
    }
}
