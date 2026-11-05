package com.deepline.fishing.ui.boat

import com.deepline.fishing.core.BoatPalette
import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BoatPartKind
import com.deepline.fishing.core.ClothPattern
import com.deepline.fishing.core.Emblem
import com.deepline.fishing.core.FlagStyle
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.PartRequirement
import com.deepline.fishing.core.SailStyle
import com.deepline.fishing.ui.formatPoints

/** How a locked boat option unlocks, e.g. "1,200 pts" or "Catch 25 species". */
fun requirementText(r: PartRequirement): String = when (r) {
    PartRequirement.Free -> "Free"
    is PartRequirement.Points -> "${formatPoints(r.cost)} pts"
    is PartRequirement.SpeciesCaught -> "Catch ${r.count} species"
    is PartRequirement.FishInZone -> "Catch a fish in the ${r.zone.displayName}"
    is PartRequirement.MilestonesClaimed -> "Claim ${r.count} milestones"
    PartRequirement.PremiumOnly -> "In the shop"
    PartRequirement.ClubOnly -> "Captain's Club"
}

/** The option's name as shown in the editor. */
fun partName(part: BoatPart): String = when (part.kind) {
    BoatPartKind.COLOR -> BoatPalette.swatches.firstOrNull { it.name == part.id }?.name ?: part.id
    BoatPartKind.PATTERN -> HullPattern.entries.first { it.name == part.id }.displayName
    BoatPartKind.SAIL -> SailStyle.entries.first { it.name == part.id }.displayName
    BoatPartKind.FLAG -> FlagStyle.entries.first { it.name == part.id }.displayName
    BoatPartKind.EMBLEM -> Emblem.entries.first { it.name == part.id }.displayName
    BoatPartKind.CLOTH -> ClothPattern.entries.first { it.name == part.id }.displayName
}
