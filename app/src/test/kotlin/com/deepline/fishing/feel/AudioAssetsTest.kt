package com.deepline.fishing.feel

import com.deepline.fishing.audio.AudioCredits
import com.deepline.fishing.audio.MusicTrack
import com.deepline.fishing.audio.Sfx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The audio files: present, credited (CC0 only), small. */
class AudioAssetsTest {
    private val dir = File("src/main/assets/audio")
    private val files = dir.walkTopDown().filter { it.isFile && it.extension == "ogg" }.toList()

    @Test
    fun everySoundAndTrackHasItsFile() {
        for (s in Sfx.entries) for (a in s.assets) assertTrue("missing $a", File("src/main/assets", a).isFile)
        for (t in MusicTrack.entries) assertTrue("missing ${t.asset}", File("src/main/assets", t.asset).isFile)
    }

    @Test
    fun everyFileIsUsedAndCreditedAsCc0() {
        val used = Sfx.entries.flatMap { it.files } + MusicTrack.entries.map { it.file }
        val credited = AudioCredits.all.flatMap { it.files }
        assertEquals("each file credited exactly once", credited.size, credited.toSet().size)
        val notes = File(dir, "CREDITS.md").readText()
        for (f in files) {
            val name = f.nameWithoutExtension
            assertTrue("$name is not used", name in used)
            assertTrue("$name has no credit in AudioCredits", name in credited)
            assertTrue("$name is not in CREDITS.md", "`$name" in notes)
        }
        for (c in AudioCredits.all) {
            assertTrue("${c.title}: only CC0 audio", c.licence.startsWith(AudioCredits.CC0))
            assertTrue("${c.title}: the source page", c.url.startsWith("https://opengameart.org/content/") && c.url in notes)
        }
    }

    @Test
    fun theAudioStaysSmall() {
        val total = files.sumOf { it.length() }
        assertTrue("audio is ${total / 1024} KB", total < 3L * 1024 * 1024)
        for (f in files.filter { it.parentFile.name == "sfx" }) assertTrue("${f.name} is ${f.length() / 1024} KB", f.length() < 64 * 1024)
    }
}
