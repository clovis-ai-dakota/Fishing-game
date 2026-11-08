package com.deepline.fishing.ui.boat

import com.deepline.fishing.R
import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BoatPartKind
import com.deepline.fishing.core.ClothPattern
import com.deepline.fishing.core.Emblem
import com.deepline.fishing.core.FlagStyle
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.PartRequirement
import com.deepline.fishing.core.SailStyle
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.labelText
import com.deepline.fishing.ui.i18n.swatchText
import com.deepline.fishing.ui.pointsText

/** How a locked boat option unlocks, e.g. "1,200 pts" or "Catch 25 species" (strings_boat.xml). */
fun requirementText(r: PartRequirement): UiText = when (r) {
    PartRequirement.Free -> UiText.res(R.string.boat_requirement_free)
    is PartRequirement.Points -> pointsText(r.cost.toLong())
    is PartRequirement.SpeciesCaught -> UiText.plural(R.plurals.boat_requirement_species, r.count)
    is PartRequirement.FishInZone -> UiText.res(R.string.boat_requirement_zone, r.zone.labelText())
    is PartRequirement.MilestonesClaimed -> UiText.plural(R.plurals.boat_requirement_milestones, r.count)
    PartRequirement.PremiumOnly -> UiText.res(R.string.boat_requirement_shop)
    PartRequirement.ClubOnly -> UiText.res(R.string.core_shop_item_captains_club)
}

/** The option's name as shown in the editor. */
fun partName(part: BoatPart): UiText = when (part.kind) {
    BoatPartKind.COLOR -> swatchText(part.id)
    BoatPartKind.PATTERN -> HullPattern.entries.first { it.name == part.id }.labelText()
    BoatPartKind.SAIL -> SailStyle.entries.first { it.name == part.id }.labelText()
    BoatPartKind.FLAG -> FlagStyle.entries.first { it.name == part.id }.labelText()
    BoatPartKind.EMBLEM -> Emblem.entries.first { it.name == part.id }.labelText()
    BoatPartKind.CLOTH -> ClothPattern.entries.first { it.name == part.id }.labelText()
}
