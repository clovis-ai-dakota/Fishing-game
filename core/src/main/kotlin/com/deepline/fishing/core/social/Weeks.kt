package com.deepline.fishing.core.social

import com.deepline.fishing.core.Seasons

/**
 * The social week: weekly leaderboards, the weekly tournament and the clubs' weekly goal all
 * reset at the same moment, **Monday 00:00 UTC**. Weeks are numbered from Monday 5 January 2026
 * (week 0, the start of the first season), so a week index means the same thing on the phone
 * and on the server.
 */
object Weeks {
    const val EPOCH_MS = Seasons.EPOCH_MS
    const val WEEK_MS = 7 * 86_400_000L

    /** The week running at [now] (epoch ms). */
    fun indexAt(now: Long): Long = Math.floorDiv(now - EPOCH_MS, WEEK_MS)

    fun startOf(week: Long): Long = EPOCH_MS + week * WEEK_MS

    /** When [week] ends: the next Monday 00:00 UTC. */
    fun endOf(week: Long): Long = startOf(week + 1)
}
