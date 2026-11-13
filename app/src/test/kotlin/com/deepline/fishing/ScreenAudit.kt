package com.deepline.fishing

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.Hyphens
import java.io.File

/**
 * One text problem on a screen, found by [TextAudit]: text cut off, truncated, overlapping other
 * text, a word broken in the middle, or a number torn from its unit at a line break.
 */
data class TextProblem(val shot: String, val kind: String, val text: String) {
    override fun toString() = "$shot: $kind: \"${text.replace('\n', ' ')}\""
}

/**
 * Checks every text the screen shows (all windows, dialogs included) through Compose's own text
 * layouts, so translated layouts can be checked without looking at every picture:
 *
 * - `cut off`: the text doesn't fit its box, or a parent clips it (outside scrolling lists);
 * - `ellipsized`: a line ends in "…" (truncated on purpose: fine for names, never for sentences);
 * - `word broken`: a line break inside a word (a word longer than its box), unless the text is
 *   hyphenated (`Hyphens.Auto`);
 * - `unit torn`: a number at the end of a line and its unit ("m", "kg", "pts"...) on the next;
 * - `overlap`: two texts drawn over each other.
 */
object TextAudit {
    /** Units that must stay on their number's line ("300 m"), as the languages write them. */
    private val units = setOf("m", "m/s", "kg", "pts", "Punkte", "puan", "%", "×", "min", "h", "s", "d", "j", "g", "sa", "dk", "Std.", "Tg.")

    fun check(rule: ComposeTestRule, shot: String, overlaps: Boolean = true): List<TextProblem> {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        val problems = ArrayList<TextProblem>()
        val shown = ArrayList<Pair<SemanticsNode, Rect>>()
        for (node in nodes) {
            val clipped = node.boundsInRoot
            if (clipped.width <= 0.5f || clipped.height <= 0.5f) continue // scrolled away or hidden
            val layout = layoutOf(node) ?: continue
            val text = layout.layoutInput.text.text
            if (text.isBlank()) continue
            fun report(kind: String) = problems.add(TextProblem(shot, kind, text))
            val ellipsized = (0 until layout.lineCount).any(layout::isLineEllipsized)
            // TextLayoutResult.hasVisualOverflow can't be trusted here (the semantics action lays
            // the text out again at its box's maximum width): compare the glyphs with the box.
            val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
            val paragraph = layout.multiParagraph
            val cut = widest > layout.size.width + 1f || paragraph.height > layout.size.height + 1f || paragraph.didExceedMaxLines
            if (ellipsized) report("ellipsized")
            else if (cut || clippedByParent(node, clipped)) report("cut off")
            val hyphenated = layout.layoutInput.style.hyphens == Hyphens.Auto
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                if (end <= 0 || end >= text.length) continue
                val before = text[end - 1]
                val after = text[end]
                // A hyphenated text (Hyphens.Auto) breaks long words at syllables, with a hyphen.
                if (before.isLetterOrDigit() && after.isLetterOrDigit() && !hyphenated) report("word broken")
                if (before == ' ' && end >= 2 && text[end - 2].isDigit()) {
                    val next = text.substring(end).takeWhile { !it.isWhitespace() }.trimEnd(',', '.', ':', ';', ')', '!', '?')
                    if (next in units) report("unit torn")
                }
            }
            shown += node to textBounds(node, layout, clipped)
        }
        if (overlaps) {
            for (i in shown.indices) for (j in i + 1 until shown.size) {
                val (a, ra) = shown[i]
                val (b, rb) = shown[j]
                if (rootOf(a) !== rootOf(b) || isAncestor(a, b) || isAncestor(b, a)) continue
                val o = ra.intersect(rb)
                // A few pixels of touching line boxes are fine; drawn glyphs over glyphs are not.
                if (o.width > 6f && o.height > 6f) {
                    problems += TextProblem(shot, "overlap", "${layoutOf(a)?.layoutInput?.text} / ${layoutOf(b)?.layoutInput?.text}")
                }
            }
        }
        return problems.distinct()
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val results = ArrayList<TextLayoutResult>()
        return runCatching {
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.firstOrNull()
        }.getOrNull()
    }

    /** Where the glyphs are (not the whole box: a full-width text only covers its lines). */
    private fun textBounds(node: SemanticsNode, layout: TextLayoutResult, clipped: Rect): Rect {
        val origin = node.positionInRoot
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        for (line in 0 until layout.lineCount) {
            left = minOf(left, layout.getLineLeft(line))
            right = maxOf(right, layout.getLineRight(line))
        }
        val top = layout.getLineTop(0)
        val bottom = layout.getLineBottom(layout.lineCount - 1)
        return Rect(origin.x + left, origin.y + top, origin.x + right, origin.y + bottom).intersect(clipped)
    }

    /** True when an ancestor (or the window) cuts the text, outside a list that scrolls that way. */
    private fun clippedByParent(node: SemanticsNode, clipped: Rect): Boolean {
        val pos = node.positionInRoot
        val full = Rect(pos.x, pos.y, pos.x + node.size.width, pos.y + node.size.height)
        val tol = 1.5f
        val cutX = full.left < clipped.left - tol || full.right > clipped.right + tol
        val cutY = full.top < clipped.top - tol || full.bottom > clipped.bottom + tol
        if (!cutX && !cutY) return false
        var p = node.parent
        var scrollsX = false
        var scrollsY = false
        while (p != null) {
            if (SemanticsProperties.HorizontalScrollAxisRange in p.config) scrollsX = true
            if (SemanticsProperties.VerticalScrollAxisRange in p.config) scrollsY = true
            p = p.parent
        }
        return (cutX && !scrollsX) || (cutY && !scrollsY)
    }

    private fun rootOf(node: SemanticsNode): SemanticsNode {
        var n = node
        while (true) n = n.parent ?: return n
    }

    private fun isAncestor(a: SemanticsNode, b: SemanticsNode): Boolean {
        var p = b.parent
        while (p != null) {
            if (p.id == a.id && rootOf(p) === rootOf(a)) return true
            p = p.parent
        }
        return false
    }
}

/**
 * Draws [activity]'s window and the dialog showing over it (dimmed and centred, as on a phone)
 * into a bitmap. Works while the test clock is paused, unlike captureToImage. With
 * [transparent], only the Compose content is drawn, on a transparent background (the store
 * screenshots put it over a 3D frame).
 */
fun captureWindows(activity: Activity, transparent: Boolean = false): Bitmap {
    val decor = activity.window.decorView
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (transparent) {
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        content.getChildAt(0).draw(canvas)
    } else {
        decor.draw(canvas)
    }
    val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    if (dialog != null && dialog.isShowing) {
        val d = dialog.window!!.decorView
        canvas.drawColor(android.graphics.Color.argb(150, 0, 0, 0))
        canvas.save()
        canvas.translate((decor.width - d.width) / 2f, (decor.height - d.height) / 2f)
        d.draw(canvas)
        canvas.restore()
    }
    return bitmap
}

fun Bitmap.savePng(file: File) {
    file.parentFile?.mkdirs()
    file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
}
