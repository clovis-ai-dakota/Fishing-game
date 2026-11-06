package com.deepline.fishing.privacy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepline.fishing.AccountClient
import com.deepline.fishing.analytics.GameAnalytics
import com.deepline.fishing.analytics.Telemetry
import com.deepline.fishing.core.KeyValueStore
import com.deepline.fishing.core.PlayerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The player's privacy choices and actions, for the consent sheet and Settings > Privacy:
 * whether usage statistics and crash reports may be sent (asked once, before anything is
 * collected, and changeable at any time), resetting the analytics id, and deleting the online
 * account. Owned by the view model (`vm.privacy`).
 */
class PrivacyController(store: KeyValueStore, private val scope: CoroutineScope) {
    private val consent = ConsentStore(store)

    /** The player's answer, or null while they haven't answered. */
    var choices by mutableStateOf(consent.load())
        private set

    /** What applies now: nothing until the player has said yes. */
    val current: PrivacyChoices get() = choices ?: PrivacyChoices.NONE

    /** Whether this build can send anything (Firebase configured): otherwise nobody is asked. */
    val telemetryAvailable: Boolean get() = Telemetry.available

    /** The consent sheet shows until answered (only in builds that can collect something). */
    val askConsent: Boolean get() = choices == null && telemetryAvailable

    private val waiting = ArrayList<() -> Unit>()

    init {
        Telemetry.applyChoices(choices)
    }

    /** Runs [block] now, or once the consent sheet is answered (the tour waits for it). */
    fun afterConsent(block: () -> Unit) {
        if (askConsent) waiting += block else block()
    }

    /** The consent sheet's answer (or a change in Settings). */
    fun choose(newChoices: PrivacyChoices) {
        consent.save(newChoices)
        choices = newChoices
        Telemetry.applyChoices(newChoices)
        val next = waiting.toList()
        waiting.clear()
        next.forEach { it() }
    }

    fun setAnalytics(on: Boolean) = choose(current.copy(analytics = on))

    fun setCrashReports(on: Boolean) = choose(current.copy(crashReports = on))

    /** Clears the analytics data on this phone and starts a new random id. */
    fun resetAnalyticsData() {
        Telemetry.resetAnalyticsData()
        message = "The statistics kept on this phone were cleared, and you have a new random ID."
    }

    // --- The online account -----------------------------------------------------------------

    /** The "Delete my online account?" confirmation is showing. */
    var confirmingDeletion by mutableStateOf(false)
        private set

    /** The server is deleting the account. */
    var deleting by mutableStateOf(false)
        private set

    /** What just happened (shown in the Privacy section), until dismissed. */
    var message by mutableStateOf<String?>(null)
        private set

    fun askToDeleteAccount() {
        message = null
        confirmingDeletion = true
    }

    fun cancelDeletion() {
        confirmingDeletion = false
    }

    fun dismissMessage() {
        message = null
    }

    /**
     * Deletes everything the game server keeps for [who] (cloud backup, gifts waiting, the id
     * itself). Once the server confirms, [onDeleted] receives a fresh random identity to use
     * from now on, so the old id is never registered again. Progress on this phone is kept.
     */
    fun deleteAccount(accounts: AccountClient, who: PlayerId, onDeleted: (PlayerId) -> Unit) {
        confirmingDeletion = false
        if (deleting) return
        deleting = true
        message = null
        scope.launch {
            try {
                accounts.deleteAccount(who)
                onDeleted(PlayerId.random())
                message = "Your online account was deleted. Your progress on this phone is still here, " +
                    "and the game now uses a new player ID."
            } catch (e: AccountClient.AccountException) {
                GameAnalytics.problem("account_delete", e)
                message = "Your account wasn't deleted: ${e.message}"
            } finally {
                deleting = false
            }
        }
    }
}
