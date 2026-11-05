package com.deepline.fishing.ui

import com.deepline.fishing.core.RopeStat

/** A rope stat's value with its unit ("120 m", "10 kg", "4.5 m/s"). Rope upgrades are in the Shop tab. */
fun formatStat(stat: RopeStat, value: Float): String = when (stat) {
    RopeStat.LENGTH -> "%.0f m".format(value)
    RopeStat.STRENGTH -> "%.0f kg".format(value)
    RopeStat.AGILITY -> "%.1f m/s".format(value)
}
