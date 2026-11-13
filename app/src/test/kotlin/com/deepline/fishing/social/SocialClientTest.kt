package com.deepline.fishing.social

import com.deepline.fishing.AccountClient
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.social.Profile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * An answer the game can't read (a captive portal's page, a newer server's battle mode or reward
 * tier) is a failed call like any other: the controllers only catch the clients' own exceptions,
 * so anything else thrown from a call would crash the app.
 */
class SocialClientTest {
    @Volatile
    private var answer = ""

    /** A tiny HTTP server answering 200 with [answer] to every request. */
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val serving = thread(isDaemon = true) {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            socket.use { s ->
                val input = s.getInputStream().bufferedReader()
                var length = 0
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                }
                repeat(length) { input.read() }
                val body = answer.toByteArray()
                val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                s.getOutputStream().apply {
                    write(head.toByteArray())
                    write(body)
                    flush()
                }
            }
        }
    }
    private val base = "http://127.0.0.1:${server.localPort}"
    private val who = PlayerId.random()

    @After
    fun stop() {
        server.close()
        serving.join(1_000)
    }

    private inline fun <reified E : Exception> failsWith(block: () -> Unit): E {
        try {
            block()
        } catch (e: Exception) {
            if (e is E) return e
            fail("threw ${e::class.java.name} instead of ${E::class.java.simpleName}")
        }
        fail("didn't fail")
        throw AssertionError()
    }

    @Test
    fun anUnreadableSocialAnswerIsAFailedCall() = runBlocking {
        val client = SocialClient { base }
        answer = "<html>Sign in to the Wi-Fi</html>"
        assertNull(failsWith<SocialClient.SocialException> { runBlocking { client.friends(who, Profile("Ann")) } }.problem)
        // A friend's newer app challenged with a battle mode this version doesn't know.
        answer = """{"challenges":[{"from":"ABCDEFGH","fromName":"Bo","code":"ABCDE","mode":"NEW_MODE","expiresAtMs":1}]}"""
        failsWith<SocialClient.SocialException> { runBlocking { client.friends(who, Profile("Ann")) } }
        client.close()
    }

    @Test
    fun anUnreadableAccountAnswerIsAFailedCall() = runBlocking {
        val client = AccountClient { base }
        answer = "<html>Sign in to the Wi-Fi</html>"
        failsWith<AccountClient.AccountException> { runBlocking { client.inbox(who) } }
        failsWith<AccountClient.AccountException> { runBlocking { client.load(who) } }
        client.close()
    }
}
