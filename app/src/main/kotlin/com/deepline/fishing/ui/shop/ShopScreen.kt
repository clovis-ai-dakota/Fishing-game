package com.deepline.fishing.ui.shop

import com.deepline.fishing.ui.shop.fishingTimeLabel
import com.deepline.fishing.ui.shop.timeLeftLabel
import com.deepline.fishing.ui.i18n.badgeText
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.description
import com.deepline.fishing.ui.i18n.label
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.ShopPage
import com.deepline.fishing.core.BoatParts
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Grant
import com.deepline.fishing.core.ItemKind
import com.deepline.fishing.core.PremiumSkins
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.core.ShopSection
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.TabScreen
import com.deepline.fishing.ui.compactPoints
import com.deepline.fishing.ui.dex.drawSlotArt
import com.deepline.fishing.ui.drawBoatTile
import com.deepline.fishing.ui.formatEuros
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.formatStat
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import java.text.DateFormat
import kotlin.math.roundToInt
import java.util.Date

/**
 * The Shop tab. Rope upgrades are bought with points only; everything else has two prices side
 * by side: euros (through the store) and points (at the shop's exchange rate, see
 * [ShopCatalog.pointsPrice]). Points purchases ask for a confirmation; store purchases go
 * through the store's own sheet (the test store's dialog in debug builds).
 */
@Composable
fun ShopScreen(vm: GameViewModel) {
    val p = vm.progress
    val pages = vm.shopPages
    val page = vm.shopPage.takeIf { it in pages } ?: ShopPage.ROPE
    var confirm by remember { mutableStateOf<ShopItem?>(null) }
    val time by rememberInfiniteTransition(label = "shop").animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "time",
    )
    val actions = ShopActions(vm, LocalContext.current.findActivity(), onPoints = { confirm = it })

    TabScreen(
        title = stringResource(R.string.shell_tab_shop),
        subtitle = {
            Text(
                stringResource(R.string.shop_balance, formatPoints(p.pointsBalance)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = { if (vm.isClubMember) ClubChip() },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (pg in pages) {
                FilterChip(
                    selected = pg == page,
                    onClick = { vm.selectShopPage(pg) },
                    label = { Text(stringResource(pg.labelRes)) },
                    modifier = Modifier.testTag("shop_page_${pg.name.lowercase()}"),
                )
            }
        }
        // One scroll position per page: a new page starts at its top.
        val gridState = androidx.compose.runtime.saveable.rememberSaveable(page, saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver) {
            androidx.compose.foundation.lazy.grid.LazyGridState()
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().testTag("shop_list"),
        ) {
            banners(vm, page, actions)
            when (page) {
                ShopPage.ROPE -> ropePage(vm)
                ShopPage.TACKLE -> tacklePage(vm, actions)
                ShopPage.STYLE -> stylePage(vm, actions, time)
                ShopPage.PASSES -> passesPage(vm, actions)
                ShopPage.AQUARIUM -> aquariumPage(vm, actions)
            }
            if (page != ShopPage.ROPE) footer(vm)
        }
    }

    confirm?.let { item -> PointsConfirmDialog(vm, item, onDone = { confirm = null }) }
}

/** Buying with points asks first: what you get, the price, and what's left. */
@Composable
internal fun PointsConfirmDialog(vm: GameViewModel, item: ShopItem, onDone: () -> Unit) {
    val p = vm.progress
    val price = vm.shopPointsPrice(item)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.shop_confirm_title, item.label())) },
        text = {
            Text(
                stringResource(
                    R.string.shop_confirm_body,
                    ShopText.contents(item, p).asString(),
                    formatPoints(price),
                    formatPoints(p.pointsBalance - price),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.buyWithPoints(item); onDone() }, modifier = Modifier.testTag("confirm_points")) {
                Text(stringResource(R.string.shop_confirm_buy, formatPoints(price)))
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** What a price button does (kept together so cards stay small). */
internal class ShopActions(val vm: GameViewModel, val activity: Activity?, val onPoints: (ShopItem) -> Unit) {
    fun buyWithMoney(item: ShopItem) = vm.buyWithMoney(item, activity)
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.header(key: String, @StringRes title: Int, @StringRes note: Int? = null, modifier: Modifier = Modifier) = full(key) {
    Column(modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (note != null) {
            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A bundle's saving as a badge ("-25%"). */
@Composable
private fun discountBadge(percent: Int): String = stringResource(R.string.shop_badge_discount, percent)

// ---- Banners: the Club's monthly pouch and the starter pack ------------------------------------

private fun LazyGridScope.banners(vm: GameViewModel, page: ShopPage, actions: ShopActions) {
    if (vm.clubPouchReady) {
        full("club_pouch") {
            val pouch = ShopCatalog.clubPouch
            Banner(
                title = stringResource(R.string.shop_club_pouch_title),
                text = stringResource(R.string.shop_club_pouch_body, ShopText.contents(pouch, vm.progress).asString()),
            ) {
                Button(onClick = vm::claimClubPouch, modifier = Modifier.testTag("claim_club_pouch")) { Text(stringResource(R.string.shop_claim)) }
            }
        }
    }
    val starter = ShopCatalog.byId("starter_pack")
    if (starter != null && vm.starterOfferOpen && page != ShopPage.PASSES) {
        full("starter_banner") {
            val discount = ShopCatalog.discountPercent(starter, vm.progress)
            Banner(
                title = starter.label() + (discount?.let { " · " + discountBadge(it) } ?: ""),
                text = stringResource(R.string.shop_starter_banner_body, ShopText.timeLeftLabel(vm.progress.starterOfferEndsMs - ShopClock.now())),
            ) {
                TextButton(onClick = { vm.selectShopPage(ShopPage.PASSES) }) { Text(stringResource(R.string.shop_starter_banner_see)) }
            }
        }
    }
}

@Composable
private fun Banner(title: String, text: String, action: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, SparkleGold),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.Trophy, contentDescription = null, tint = SparkleGold, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = com.deepline.fishing.ui.rewardTextColor())
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            action()
        }
    }
}

@Composable
private fun ClubChip() {
    Text(
        stringResource(R.string.core_shop_item_captains_club),
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFF64FFDA), Color(0xFFB388FF))))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = Color(0xFF0D1B2A),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

// ---- Prices ------------------------------------------------------------------------------------

/**
 * The two ways to pay, side by side: euros (filled, the store's price) and points (outlined,
 * greyed out when the balance is too low). [wide] cards spell points out in full.
 */
@Composable
internal fun PriceButtons(item: ShopItem, actions: ShopActions, wide: Boolean = false, modifier: Modifier = Modifier) {
    val vm = actions.vm
    val points = vm.shopPointsPrice(item)
    // Both buttons as tall as the taller one: a points price too long for one line ("1,1 Mio.
    // Punkte") shows its unit on a second line (PointsPriceLabel) instead of being cut off.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Button(
            onClick = { actions.buyWithMoney(item) },
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("buy_eur_${item.id}"),
        ) { Text(vm.billing.priceOf(item) ?: formatEuros(item.euroCents), maxLines = 1, fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = { actions.onPoints(item) },
            enabled = vm.progress.pointsBalance >= points,
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("buy_pts_${item.id}"),
        ) { PointsPriceLabel(if (wide) formatPoints(points) else compactPoints(points)) }
    }
}

/**
 * A points price ([number] already formatted) inside a narrow button: "260k pts" on one line when
 * it fits, otherwise the number above its unit (`common_points_stacked`), never cut off. A number
 * and its unit are joined by a no-break space, so they can't wrap on their own.
 */
@Composable
internal fun PointsPriceLabel(number: String) {
    val oneLine = stringResource(R.string.common_points, number)
    // Tried on one line first; if it doesn't fit, the stacked form from the next frame on.
    var stacked by remember(oneLine) { mutableStateOf(false) }
    if (stacked) {
        Text(stringResource(R.string.common_points_stacked, number), maxLines = 2, textAlign = TextAlign.Center)
    } else {
        Text(oneLine, maxLines = 1, softWrap = false, onTextLayout = { if (it.didOverflowWidth) stacked = true })
    }
}

/** Shown instead of the prices once a permanent item is owned. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun OwnedRow(label: String = stringResource(R.string.shop_owned), action: (@Composable () -> Unit)? = null) {
    // In a narrow card the button moves under a long label ("Kapitänsclub") rather than
    // squeezing it into broken words.
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
        )
        action?.invoke()
    }
}

@Composable
internal fun Badge(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SparkleGold)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        color = Color(0xFF3E2723),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

// ---- Rope (points only) ------------------------------------------------------------------------

private fun LazyGridScope.ropePage(vm: GameViewModel) {
    header("rope_header", R.string.shop_rope_title, R.string.shop_rope_note)
    for (stat in RopeStat.entries) {
        full("rope_${stat.name}") {
            val tagged = if (stat == RopeStat.entries.first()) Modifier.tourTarget(TourTarget.SHOP_ROPE) else Modifier
            RopeCard(vm, stat, tagged)
        }
    }
}

@Composable
private fun RopeCard(vm: GameViewModel, stat: RopeStat, modifier: Modifier) {
    val p = vm.progress
    val level = p.level(stat)
    val cost = Progression.upgradeCost(p, stat)
    Card(Modifier.fillMaxWidth().then(modifier)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stat.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.shop_rope_level, level, stat.maxLevel), style = MaterialTheme.typography.labelLarge)
            }
            Text(stat.description(), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { level.toFloat() / stat.maxLevel }, modifier = Modifier.fillMaxWidth())
            val current = formatStat(stat, stat.valueAt(level))
            Progression.downgradeRefund(p, stat)?.let { refund ->
                TextButton(onClick = { vm.downgrade(stat) }) {
                    Text(stringResource(R.string.shop_rope_downgrade, formatStat(stat, stat.valueAt(level - 1)), formatPoints(refund)))
                }
            }
            if (cost == null) {
                Text(stringResource(R.string.shop_rope_maxed, current), fontWeight = FontWeight.SemiBold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$current → ${formatStat(stat, stat.valueAt(level + 1))}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.buyUpgrade(stat) }, enabled = p.pointsBalance >= cost) {
                        Text(stringResource(R.string.common_points, formatPoints(cost)))
                    }
                }
            }
        }
    }
}

// ---- Tackle --------------------------------------------------------------------------------------

private fun LazyGridScope.tacklePage(vm: GameViewModel, actions: ShopActions) {
    header("tackle_header", R.string.shop_tackle_title, R.string.shop_tackle_note)
    for (t in Tackle.entries) {
        full("tackle_${t.name}") {
            val tagged = if (t == Tackle.entries.first()) Modifier.tourTarget(TourTarget.SHOP_TACKLE) else Modifier
            TackleCard(vm, t, actions, tagged)
        }
    }
}

@Composable
private fun TackleCard(vm: GameViewModel, t: Tackle, actions: ShopActions, modifier: Modifier) {
    val p = vm.progress
    val single = Progression.tacklePrice(p, t)
    val packs = ShopCatalog.inSection(ShopSection.TACKLE).filter { item ->
        item.grants.any { it is Grant.TacklePack && it.tackle == t }
    }
    Card(Modifier.fillMaxWidth().then(modifier)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.shop_tackle_owned, Progression.tackleCount(p, t)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                stringResource(if (t.usableInBattles) R.string.shop_tackle_desc_battles else R.string.shop_tackle_desc_solo, t.description()),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.shop_tackle_single), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { vm.buyTackle(t) },
                    enabled = p.pointsBalance >= single,
                    modifier = Modifier.testTag("buy_one_${t.name.lowercase()}"),
                ) { Text(stringResource(R.string.common_points, formatPoints(single))) }
            }
            for (pack in packs) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(pack.label(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            pack.badgeText()?.let { Badge(it.asString()) }
                        }
                        Text(ShopText.contents(pack, p).asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val tagged = if (pack == ShopCatalog.inSection(ShopSection.TACKLE).first()) Modifier.tourTarget(TourTarget.SHOP_PRICES) else Modifier
                    Box(Modifier.weight(1.3f)) { PriceButtons(pack, actions, modifier = tagged) }
                }
            }
        }
    }
}

// ---- Style ---------------------------------------------------------------------------------------

private fun LazyGridScope.stylePage(vm: GameViewModel, actions: ShopActions, time: Float) {
    val style = ShopCatalog.inSection(ShopSection.STYLE)
    val boats = style.filter { it.grants.size == 1 && it.grants.first() is Grant.Preset }
    val sets = style.filter { it.grants.size > 1 && it.grants.any { g -> g is Grant.Preset } }
    val parts = style.filter { it.grants.singleOrNull() is Grant.BoatParts }
    val gear = style.filter { it.grants.singleOrNull() is Grant.Cosmetics }
    val badges = style.filter { (it.grants.singleOrNull() as? Grant.Unlock)?.key?.startsWith("badge:") == true }
    val cards = style.filter { (it.grants.singleOrNull() as? Grant.Unlock)?.key?.startsWith("card:") == true }
    val known = (boats + sets + parts + gear + badges + cards + style.filter { it.id.startsWith("theme_") }).toSet()
    header("boats_header", R.string.shop_style_boats_title, R.string.shop_style_boats_note, Modifier.tourTarget(TourTarget.SHOP_STYLE))
    items(boats, key = { it.id }) { BoatTile(vm, it, actions, time) }
    if (sets.isNotEmpty()) {
        header("sets_header", R.string.shop_style_sets_title, R.string.shop_style_sets_note)
        for (set in sets) full(set.id) { SetCard(vm, set, actions, time) }
    }
    header("parts_header", R.string.shop_style_parts_title, R.string.shop_style_parts_note)
    items(parts, key = { it.id }) { PartTile(vm, it, actions, time) }
    header("gear_header", R.string.shop_style_gear_title, R.string.shop_style_gear_note)
    items(gear, key = { it.id }) { GearTile(vm, it, actions, time) }
    val themeItems = style.filter { it.id.startsWith("theme_") }
    header("themes_header", R.string.shop_style_themes_title, R.string.shop_style_themes_note, Modifier.tourTarget(TourTarget.SHOP_THEMES))
    items(themeItems.filter { it.grants.size == 1 }, key = { it.id }) { ThemeTile(vm, it, actions) }
    for (bundle in themeItems.filter { it.grants.size > 1 }) full(bundle.id) { ItemCard(vm, bundle, actions) }
    header("badges_header", R.string.shop_style_badges_title, R.string.shop_style_badges_note)
    items(badges, key = { it.id }) { BadgeTile(vm, it, actions) }
    header("cards_header", R.string.shop_style_cards_title, R.string.shop_style_cards_note)
    items(cards, key = { it.id }) { CardStyleTile(vm, it, actions) }
    val others = style - known
    if (others.isNotEmpty()) {
        header("style_more", R.string.shop_style_more_title)
        for (item in others) full(item.id) { ItemCard(vm, item, actions) }
    }
}

@Composable
internal fun Tile(name: String, badge: String?, preview: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TileWater),
            ) {
                preview()
                if (badge != null) Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { Badge(badge) }
            }
            // Two lines, hyphenated: German item names are long compounds ("Sternennacht-Lackierung").
            Text(name, style = MaterialTheme.typography.titleSmall.copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            bottom()
        }
    }
}

private val TileWater = Brush.verticalGradient(listOf(Color(0xFF0288D1), Color(0xFF01396B)))

@Composable
private fun BoatTile(vm: GameViewModel, item: ShopItem, actions: ShopActions, time: Float) {
    val presetId = (item.grants.first() as Grant.Preset).presetId
    val skin = PremiumSkins.byId(presetId) ?: return
    Tile(item.label(), item.badgeText()?.asString(), preview = { Canvas(Modifier.fillMaxSize()) { drawBoatTile(null, skin.design, time) } }) {
        Text(skin.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        when {
            vm.isWearingPreset(presetId) -> OwnedRow(stringResource(R.string.shop_worn))
            ShopCatalog.ownsPreset(vm.progress, presetId) -> OwnedRow { TextButton(onClick = { vm.wearPreset(presetId) }) { Text(stringResource(R.string.shop_wear)) } }
            else -> PriceButtons(item, actions)
        }
    }
}

@Composable
private fun SetCard(vm: GameViewModel, item: ShopItem, actions: ShopActions, time: Float) {
    val presetId = item.grants.filterIsInstance<Grant.Preset>().first().presetId
    val design = PremiumSkins.byId(presetId)?.design
    val gear = item.grants.filterIsInstance<Grant.Cosmetics>().flatMap { it.items }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                item.badgeText()?.let { Badge(it.asString()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1.6f).aspectRatio(1.3f).clip(RoundedCornerShape(10.dp)).background(TileWater)) {
                    Canvas(Modifier.fillMaxSize()) { drawBoatTile(null, design, time) }
                }
                for (c in gear) {
                    Box(Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(10.dp)).background(TileWater)) {
                        Canvas(Modifier.fillMaxSize()) { drawSlotArt(c.slot, c, time) }
                    }
                }
            }
            Text(
                stringResource(R.string.shop_set_contents, ShopText.contents(item, vm.progress).asString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ShopCatalog.owns(vm.progress, item.id)) OwnedRow() else PriceButtons(item, actions, wide = true)
        }
    }
}

@Composable
private fun PartTile(vm: GameViewModel, item: ShopItem, actions: ShopActions, time: Float) {
    val part = (item.grants.first() as Grant.BoatParts).parts.first()
    val p = vm.progress
    val preview = BoatParts.applyTo(p.boatDesign, part, p)
    Tile(item.label(), item.badgeText()?.asString(), preview = { Canvas(Modifier.fillMaxSize()) { drawBoatTile(null, preview, time) } }) {
        when {
            vm.isOnBoat(part) -> OwnedRow(stringResource(R.string.shop_on_your_boat))
            ShopCatalog.owns(p, item.id) || BoatParts.isAvailable(p, part) ->
                OwnedRow {
                    TextButton(onClick = { vm.applyBoatPart(part) }, modifier = Modifier.testTag("apply_${item.id}")) { Text(stringResource(R.string.shop_apply)) }
                }
            else -> PriceButtons(item, actions)
        }
    }
}

@Composable
private fun GearTile(vm: GameViewModel, item: ShopItem, actions: ShopActions, time: Float) {
    val c: Cosmetic = (item.grants.first() as Grant.Cosmetics).items.first()
    val p = vm.progress
    Tile(item.label(), item.badgeText()?.asString(), preview = { Canvas(Modifier.fillMaxSize()) { drawSlotArt(c.slot, c, time) } }) {
        val worn = p.equippedSkins[c.slot] == c
        when {
            worn -> OwnedRow(stringResource(R.string.shop_worn)) {
                TextButton(onClick = { vm.equipSkin(c.slot, null) }) { Text(stringResource(R.string.shop_take_off)) }
            }
            ShopCatalog.ownsCosmetic(p, c) -> OwnedRow { TextButton(onClick = { vm.equipSkin(c.slot, c) }) { Text(stringResource(R.string.shop_wear)) } }
            else -> PriceButtons(item, actions)
        }
    }
}

@Composable
private fun ThemeTile(vm: GameViewModel, item: ShopItem, actions: ShopActions) {
    val theme = com.deepline.fishing.core.MenuTheme.entries.first { it.unlockKey == (item.grants.first() as Grant.Unlock).key }
    val night = vm.nightMode.isNight(androidx.compose.foundation.isSystemInDarkTheme())
    Tile(theme.label(), "★".repeat(theme.tier), preview = { ThemePreview(theme, night, Modifier.fillMaxSize()) }) {
        when {
            vm.menuTheme == theme -> OwnedRow(stringResource(R.string.shop_in_use))
            ShopCatalog.owns(vm.progress, theme.unlockKey) -> OwnedRow { TextButton(onClick = { vm.setMenuTheme(theme) }) { Text(stringResource(R.string.shop_use)) } }
            else -> PriceButtons(item, actions)
        }
    }
}

@Composable
private fun BadgeTile(vm: GameViewModel, item: ShopItem, actions: ShopActions) {
    val key = (item.grants.first() as Grant.Unlock).key
    val frame = com.deepline.fishing.core.TitleFrame.entries.first { it.unlockKey == key }
    Tile(item.label(), item.badgeText()?.asString(), preview = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            com.deepline.fishing.ui.TitleBadge(vm.title ?: vm.playerName, frame = frame)
        }
    }) {
        when {
            vm.titleFrame == frame -> OwnedRow(stringResource(R.string.shop_in_use))
            ShopCatalog.owns(vm.progress, key) -> OwnedRow { TextButton(onClick = { vm.setTitleFrame(frame) }) { Text(stringResource(R.string.shop_use)) } }
            else -> PriceButtons(item, actions)
        }
    }
}

@Composable
private fun CardStyleTile(vm: GameViewModel, item: ShopItem, actions: ShopActions) {
    val key = (item.grants.first() as Grant.Unlock).key
    val style = com.deepline.fishing.core.CardStyle.entries.first { it.unlockKey == key }
    Tile(item.label(), item.badgeText()?.asString(), preview = { com.deepline.fishing.ui.dex.CardStyleSample(style, Modifier.fillMaxSize()) }) {
        when {
            vm.cardStyle == style -> OwnedRow(stringResource(R.string.shop_in_use))
            ShopCatalog.owns(vm.progress, key) -> OwnedRow { TextButton(onClick = { vm.setCardStyle(style) }) { Text(stringResource(R.string.shop_use)) } }
            else -> PriceButtons(item, actions)
        }
    }
}

// ---- Aquarium ----------------------------------------------------------------------------------------

private fun LazyGridScope.aquariumPage(vm: GameViewModel, actions: ShopActions) {
    val p = vm.progress
    val items = ShopCatalog.inSection(ShopSection.AQUARIUM)
    header("aquarium_header", R.string.shop_aquarium_title, R.string.shop_aquarium_page_note)
    full("aquarium_tank") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AquariumTank(p, height = 200.dp, modifier = Modifier.tourTarget(TourTarget.SHOP_AQUARIUM))
            TextButton(onClick = vm::openAquarium, modifier = Modifier.testTag("open_aquarium")) { Text(stringResource(R.string.shop_aquarium_open)) }
        }
    }
    for (item in items.filter { (it.grants.single() as? Grant.Unlock)?.key == "aquarium:big" }) full(item.id) { ItemCard(vm, item, actions) }
    items(items.filter { (it.grants.single() as? Grant.Unlock)?.key?.startsWith("decor:") == true }, key = { it.id }) { item ->
        val decor = com.deepline.fishing.core.Decor.entries.first { it.unlockKey == (item.grants.single() as Grant.Unlock).key }
        Tile(item.label(), item.badgeText()?.asString(), preview = { AquariumTank(p, height = 120.dp, extraDecor = decor) }) {
            if (ShopCatalog.owns(p, item.id)) OwnedRow() else PriceButtons(item, actions)
        }
    }
}

// ---- Passes ----------------------------------------------------------------------------------------

private fun LazyGridScope.passesPage(vm: GameViewModel, actions: ShopActions) {
    val p = vm.progress
    val now = ShopClock.now()
    val passes = ShopCatalog.inSection(ShopSection.PASSES)
    fun card(item: ShopItem) {
        // Seasonal items show while they can be bought (once bought, their track says so).
        if (item.kind == ItemKind.SEASONAL && !ShopCatalog.canBuy(item, p, now)) return
        // The starter pack shows during its offer (and once bought), never before.
        if (item.id == "starter_pack" && !vm.starterOfferOpen && !ShopCatalog.owns(p, item.id)) return
        full(item.id) {
            val tagged = if (item.id == "captains_club") Modifier.tourTarget(TourTarget.SHOP_PASSES) else Modifier
            ItemCard(vm, item, actions, tagged)
        }
    }
    header("passes_header", R.string.shop_passes_title, R.string.shop_passes_note)
    // A one-time offer first, then each pass right under what it unlocks.
    passes.filter { it.id == "starter_pack" }.forEach(::card)
    full("season") { SeasonCard(vm, Modifier.tourTarget(TourTarget.SHOP_SEASON)) }
    passes.filter { it.id == "season_pass" || it.id == "collector_edition" }.forEach(::card)
    full("event") { EventCard(vm) }
    passes.filter { it.id == "event_pass" }.forEach(::card)
    passes.filter { it.id !in setOf("starter_pack", "season_pass", "collector_edition", "event_pass") }.forEach(::card)
}

private fun LazyGridScope.genericPage(vm: GameViewModel, section: ShopSection, actions: ShopActions) {
    for (item in ShopCatalog.inSection(section)) full(item.id) { ItemCard(vm, item, actions) }
}

/** A full-width card for any item: name, badge, what it gives, status, and the two prices. */
@Composable
internal fun ItemCard(vm: GameViewModel, item: ShopItem, actions: ShopActions, modifier: Modifier = Modifier) {
    val p = vm.progress
    val now = ShopClock.now()
    val owned = item.kind == ItemKind.PERMANENT && ShopCatalog.owns(p, item.id)
    val discount = ShopCatalog.discountPercent(item, p)
    val badge = discount?.let { discountBadge(it) } ?: item.badgeText()?.asString()
    Card(
        Modifier.fillMaxWidth().then(modifier),
        shape = RoundedCornerShape(14.dp),
        border = if (badge != null && !owned) BorderStroke(1.dp, SparkleGold.copy(alpha = 0.6f)) else null,
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                badge?.let { Badge(it) }
            }
            Text(item.description(), style = MaterialTheme.typography.bodySmall)
            if (discount != null) {
                Text(
                    stringResource(R.string.shop_item_worth, formatEuros(ShopCatalog.separateCents(item, p))),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.id == "starter_pack" && vm.starterOfferOpen) {
                Text(
                    stringResource(R.string.shop_item_offer_ends, ShopText.timeLeftLabel(p.starterOfferEndsMs - now)),
                    style = MaterialTheme.typography.labelMedium,
                    color = com.deepline.fishing.ui.rewardTextColor(),
                )
            }
            if (item.kind == ItemKind.SUBSCRIPTION) {
                val days = GameConfig.CLUB_DAYS
                val status = when {
                    ShopCatalog.owns(p, "badge:CLUB_FOREVER") -> stringResource(R.string.shop_club_member_for_life)
                    p.clubUntilMs > now -> pluralStringResource(
                        R.plurals.shop_club_member_until,
                        days,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.clubUntilMs)),
                        days,
                    )
                    else -> pluralStringResource(R.plurals.shop_club_how, days, days)
                }
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            when {
                owned -> OwnedRow()
                !ShopCatalog.canBuy(item, p, now) -> OwnedRow(stringResource(R.string.shop_not_available))
                else -> PriceButtons(item, actions, wide = true)
            }
        }
    }
}

// ---- Footer --------------------------------------------------------------------------------------

private fun LazyGridScope.footer(vm: GameViewModel) = full("footer") {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                R.string.shop_footer_rates,
                ShopText.fishingTimeLabel(GameConfig.MINUTES_PER_EURO),
                ShopText.fishingTimeLabel(GameConfig.LANTERN_MINUTES_PER_EURO),
                ((GameConfig.tierBonus(499) - 1f) * 100).roundToInt(),
                ((GameConfig.tierBonus(999) - 1f) * 100).roundToInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.shop_footer_payments, vm.billing.name.asString()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::restorePurchases) { Text(stringResource(R.string.shop_footer_restore)) }
        }
        var account by remember { mutableStateOf(false) }
        TextButton(onClick = { account = true }, modifier = Modifier.testTag("open_account")) { Text(stringResource(R.string.shop_footer_account)) }
        if (account) AccountDialog(vm, onClose = { account = false })
    }
}
