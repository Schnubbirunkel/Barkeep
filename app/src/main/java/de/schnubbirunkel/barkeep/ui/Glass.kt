@file:OptIn(ExperimentalSharedTransitionApi::class)

package de.schnubbirunkel.barkeep.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ shared element plumbing
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
/** Key of the picture that was tapped last, so the detail screen can morph from exactly that tile. */
val LocalHeroKey = staticCompositionLocalOf<MutableState<String?>> { mutableStateOf(null) }

/** Makes the element morph between screens when the same key is used on both. */
@Composable
fun Modifier.sharedArt(key: String?): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimScope.current ?: return this
    if (key == null) return this
    return with(shared) {
        this@sharedArt.sharedElement(
            rememberSharedContentState(key), anim,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.85f, stiffness = 420f) },
        )
    }
}

// ------------------------------------------------------------------ glass
@Composable
fun isDarkUi(): Boolean = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f

/** Frosted glass surface: translucent fill, soft top highlight and a light rim. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp), strength: Float = 1f): Modifier {
    val dark = isDarkUi()
    val fill = if (dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f * strength), Color.White.copy(alpha = 0.04f * strength)))
    else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.52f)))
    val rim = if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.14f)))
    else Brush.linearGradient(listOf(Color.White, Color.Black.copy(alpha = 0.06f), Color.White.copy(alpha = 0.8f)))
    return this.clip(shape).background(fill).border(1.dp, rim, shape)
}

/** Click with a quick spring "press" – no delay, the action fires immediately. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(26.dp), onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(modifier.then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier).glass(shape)) { content() }
}

// ------------------------------------------------------------------ living background
/** Slowly drifting colour blobs – the "liquid" behind all glass. Cheap: three radial gradients. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val base = if (dark) Ink else Color(0xFFF2F3F8)
    val a = if (dark) 0.42f else 0.30f
    val colors = listOf(cs.primary.copy(alpha = a), cs.secondary.copy(alpha = a * 0.9f), cs.tertiary.copy(alpha = a * 0.75f))
    val t = rememberInfiniteTransition(label = "liquid")
    val p by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.fillMaxSize().background(base)) {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.62f
        val centers = listOf(
            Offset(w * (0.15f + 0.12f * cos(p)), h * (0.12f + 0.06f * sin(p * 2))),
            Offset(w * (0.92f + 0.10f * sin(p)), h * (0.42f + 0.10f * cos(p))),
            Offset(w * (0.30f + 0.15f * sin(p + 1.3f)), h * (0.95f + 0.05f * cos(p * 2))),
        )
        centers.forEachIndexed { i, c ->
            drawCircle(Brush.radialGradient(listOf(colors[i], Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
    }
}

// ------------------------------------------------------------------ floating glass navigation
data class NavItem(val label: String, val icon: ImageVector, val badge: Int = 0)

@Composable
fun GlassNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = isDarkUi()
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth().height(68.dp)
            .clip(shape)
            .background(if (dark) Color(0xFF151B2C).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.86f))
            .glass(shape, 1.4f),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
            val itemW = maxWidth / items.size
            // the liquid indicator glides with a soft spring
            val x by animateDpAsState(itemW * selected.coerceAtLeast(0), spring(dampingRatio = 0.72f, stiffness = 520f), label = "navX")
            if (selected >= 0) Box(
                Modifier.offset(x = x).width(itemW).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, item ->
                    val sel = i == selected
                    val tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    val iconScale by animateFloatAsState(if (sel) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "icon")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = true)) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Box(Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) {
                            if (item.badge > 0) BadgedBox(badge = { Badge { Text("${item.badge}") } }) { Icon(item.icon, item.label, tint = tint) }
                            else Icon(item.icon, item.label, tint = tint)
                        }
                        Text(item.label, fontSize = 10.5.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = tint, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Space lists must leave at the bottom so the floating bar never covers content. */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

@Composable
fun ProvideHero(content: @Composable () -> Unit) {
    val state = remember { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalHeroKey provides state, content = content)
}

@Suppress("unused")
private fun Dp.half() = this / 2

/** Translucent fill for Material cards so they sit on the liquid background like glass. */
@Composable
fun glassFill(strong: Boolean = false): Color =
    if (isDarkUi()) Color.White.copy(alpha = if (strong) 0.12f else 0.08f) else Color.White.copy(alpha = if (strong) 0.82f else 0.66f)

@Composable
fun glassBorder() = androidx.compose.foundation.BorderStroke(1.dp, Brush.linearGradient(
    if (isDarkUi()) listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f)) else listOf(Color.White, Color.Black.copy(alpha = 0.06f))))
