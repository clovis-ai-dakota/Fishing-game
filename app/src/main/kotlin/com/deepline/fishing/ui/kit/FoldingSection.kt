package com.deepline.fishing.ui.kit

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.ui.GameIcons

/**
 * A section that folds away (the Battle setup's choices, the aquarium's lists): closed, one row
 * with the section's name, what's picked or a count (pictogram and text) and a chevron; tapped,
 * its content opens under it. Choices close it again when one is picked.
 */
@Composable
fun FoldingSection(
    title: String,
    icon: ImageVector,
    choice: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    info: String? = null,
    trailing: @Composable () -> Unit = {},
    options: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val calm = LocalCalmMotion.current
    Column(
        modifier.then(if (calm) Modifier else Modifier.animateContentSize(tween(FOLD_MOTION_MS))),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = choice },
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
        ) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                    Text(choice, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                }
                trailing()
                if (info != null) InfoButton(title, info)
                Icon(
                    GameIcons.ChevronRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).rotate(if (expanded) -90f else 90f),
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        if (expanded) options()
    }
}

/** How long a choice takes to open or close (a small change: under NN/g's 300 ms). */
private const val FOLD_MOTION_MS = 200
