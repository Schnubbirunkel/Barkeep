@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schnubbirunkel.barkeep.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Settings
import coil3.compose.AsyncImage
import coil3.request.crossfade
import androidx.compose.foundation.border
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.data.Catalog
import de.schnubbirunkel.barkeep.data.Recipe
import de.schnubbirunkel.barkeep.data.RecipeIngredient
import de.schnubbirunkel.barkeep.data.Units

@Composable
fun appLang(): String {
    val l = LocalConfiguration.current.locales[0]?.language ?: "en"
    return if (l == "de") "de" else "en"
}

fun parseColor(hex: String, fallback: Color = Color(0xFFE8C77A)): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) { fallback }

/** Recipe picture: photo when available, otherwise a drawn glass in the drink's colour. */
@Composable
fun DrinkArt(recipe: Recipe, modifier: Modifier = Modifier, corner: Dp = 20.dp, sharedKey: String? = null) {
    val tint = parseColor(recipe.color)
    val bg = Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.06f), Color.White.copy(alpha = 0.05f)))
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier.sharedArt(sharedKey).clip(RoundedCornerShape(corner)).background(bg)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.03f))), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        if (recipe.imageUrl != null) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(ctx).data(recipe.imageUrl).crossfade(250).build(),
                contentDescription = recipe.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            GlassDrawing(recipe.glass, tint, Modifier.fillMaxSize().padding(10.dp))
        }
    }
}

@Composable
fun GlassDrawing(glass: String, liquid: Color, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val stroke = Stroke(width = s * 0.03f)
        val bowl = Path()
        var fillTop = 0.3f
        var stem = false
        var stemTop = 0.5f
        var ice = false
        var handle = false
        when (glass) {
            "coupe", "nick_nora" -> {
                val deep = if (glass == "nick_nora") 0.5f else 0.42f
                bowl.moveTo(p(0.14f, 0.2f).x, p(0.14f, 0.2f).y)
                bowl.lineTo(p(0.86f, 0.2f).x, p(0.86f, 0.2f).y)
                bowl.cubicTo(p(0.86f, deep).x, p(0.86f, deep).y, p(0.55f, deep + 0.04f).x, p(0.55f, deep + 0.04f).y, p(0.5f, deep + 0.04f).x, p(0.5f, deep + 0.04f).y)
                bowl.cubicTo(p(0.45f, deep + 0.04f).x, p(0.45f, deep + 0.04f).y, p(0.14f, deep).x, p(0.14f, deep).y, p(0.14f, 0.2f).x, p(0.14f, 0.2f).y)
                fillTop = 0.25f; stem = true; stemTop = deep + 0.04f
            }
            "martini" -> {
                bowl.moveTo(p(0.12f, 0.15f).x, p(0.12f, 0.15f).y)
                bowl.lineTo(p(0.88f, 0.15f).x, p(0.88f, 0.15f).y)
                bowl.lineTo(p(0.5f, 0.52f).x, p(0.5f, 0.52f).y)
                bowl.close()
                fillTop = 0.22f; stem = true; stemTop = 0.52f
            }
            "wine" -> {
                bowl.moveTo(p(0.24f, 0.1f).x, p(0.24f, 0.1f).y)
                bowl.lineTo(p(0.76f, 0.1f).x, p(0.76f, 0.1f).y)
                bowl.cubicTo(p(0.84f, 0.45f).x, p(0.84f, 0.45f).y, p(0.62f, 0.56f).x, p(0.62f, 0.56f).y, p(0.5f, 0.56f).x, p(0.5f, 0.56f).y)
                bowl.cubicTo(p(0.38f, 0.56f).x, p(0.38f, 0.56f).y, p(0.16f, 0.45f).x, p(0.16f, 0.45f).y, p(0.24f, 0.1f).x, p(0.24f, 0.1f).y)
                fillTop = 0.28f; stem = true; stemTop = 0.56f; ice = true
            }
            "flute" -> {
                bowl.moveTo(p(0.38f, 0.06f).x, p(0.38f, 0.06f).y)
                bowl.lineTo(p(0.62f, 0.06f).x, p(0.62f, 0.06f).y)
                bowl.cubicTo(p(0.62f, 0.5f).x, p(0.62f, 0.5f).y, p(0.56f, 0.6f).x, p(0.56f, 0.6f).y, p(0.5f, 0.6f).x, p(0.5f, 0.6f).y)
                bowl.cubicTo(p(0.44f, 0.6f).x, p(0.44f, 0.6f).y, p(0.38f, 0.5f).x, p(0.38f, 0.5f).y, p(0.38f, 0.06f).x, p(0.38f, 0.06f).y)
                fillTop = 0.14f; stem = true; stemTop = 0.6f
            }
            "hurricane" -> {
                bowl.moveTo(p(0.3f, 0.06f).x, p(0.3f, 0.06f).y)
                bowl.lineTo(p(0.7f, 0.06f).x, p(0.7f, 0.06f).y)
                bowl.cubicTo(p(0.8f, 0.3f).x, p(0.8f, 0.3f).y, p(0.58f, 0.4f).x, p(0.58f, 0.4f).y, p(0.66f, 0.64f).x, p(0.66f, 0.64f).y)
                bowl.cubicTo(p(0.7f, 0.76f).x, p(0.7f, 0.76f).y, p(0.6f, 0.8f).x, p(0.6f, 0.8f).y, p(0.5f, 0.8f).x, p(0.5f, 0.8f).y)
                bowl.cubicTo(p(0.4f, 0.8f).x, p(0.4f, 0.8f).y, p(0.3f, 0.76f).x, p(0.3f, 0.76f).y, p(0.34f, 0.64f).x, p(0.34f, 0.64f).y)
                bowl.cubicTo(p(0.42f, 0.4f).x, p(0.42f, 0.4f).y, p(0.2f, 0.3f).x, p(0.2f, 0.3f).y, p(0.3f, 0.06f).x, p(0.3f, 0.06f).y)
                fillTop = 0.16f; stem = true; stemTop = 0.8f
            }
            "highball", "collins", "pint" -> {
                val w = if (glass == "collins") 0.16f else 0.2f
                val wb = if (glass == "pint") w - 0.04f else w
                bowl.moveTo(p(0.5f - w, 0.08f).x, p(0.5f - w, 0.08f).y)
                bowl.lineTo(p(0.5f + w, 0.08f).x, p(0.5f + w, 0.08f).y)
                bowl.lineTo(p(0.5f + wb, 0.9f).x, p(0.5f + wb, 0.9f).y)
                bowl.lineTo(p(0.5f - wb, 0.9f).x, p(0.5f - wb, 0.9f).y)
                bowl.close()
                fillTop = 0.2f; ice = glass != "pint"
            }
            "shot" -> {
                bowl.moveTo(p(0.36f, 0.5f).x, p(0.36f, 0.5f).y)
                bowl.lineTo(p(0.64f, 0.5f).x, p(0.64f, 0.5f).y)
                bowl.lineTo(p(0.61f, 0.88f).x, p(0.61f, 0.88f).y)
                bowl.lineTo(p(0.39f, 0.88f).x, p(0.39f, 0.88f).y)
                bowl.close()
                fillTop = 0.56f
            }
            "mug", "copper_mug", "tiki", "julep" -> {
                bowl.moveTo(p(0.26f, 0.22f).x, p(0.26f, 0.22f).y)
                bowl.lineTo(p(0.66f, 0.22f).x, p(0.66f, 0.22f).y)
                bowl.lineTo(p(0.64f, 0.88f).x, p(0.64f, 0.88f).y)
                bowl.lineTo(p(0.28f, 0.88f).x, p(0.28f, 0.88f).y)
                bowl.close()
                fillTop = 0.3f; handle = glass != "julep" && glass != "tiki"; ice = glass != "mug"
            }
            "punch_bowl" -> {
                bowl.moveTo(p(0.08f, 0.36f).x, p(0.08f, 0.36f).y)
                bowl.lineTo(p(0.92f, 0.36f).x, p(0.92f, 0.36f).y)
                bowl.cubicTo(p(0.92f, 0.78f).x, p(0.92f, 0.78f).y, p(0.08f, 0.78f).x, p(0.08f, 0.78f).y, p(0.08f, 0.36f).x, p(0.08f, 0.36f).y)
                fillTop = 0.42f
            }
            else -> { // rocks
                bowl.moveTo(p(0.24f, 0.4f).x, p(0.24f, 0.4f).y)
                bowl.lineTo(p(0.76f, 0.4f).x, p(0.76f, 0.4f).y)
                bowl.lineTo(p(0.74f, 0.88f).x, p(0.74f, 0.88f).y)
                bowl.lineTo(p(0.26f, 0.88f).x, p(0.26f, 0.88f).y)
                bowl.close()
                fillTop = 0.5f; ice = true
            }
        }
        clipPath(bowl) {
            drawRect(
                Brush.verticalGradient(listOf(liquid.copy(alpha = 0.85f), liquid), startY = p(0f, fillTop).y, endY = oy + s),
                topLeft = Offset(ox, p(0f, fillTop).y), size = Size(s, s),
            )
            if (ice) drawIce(this, p(0.38f, fillTop + 0.02f), s * 0.16f)
            // glass highlight
            drawRect(Color.White.copy(alpha = 0.08f), topLeft = Offset(ox + s * 0.0f, oy), size = Size(s * 0.42f, s))
        }
        drawPath(bowl, outline, style = stroke)
        if (stem) {
            drawLine(outline, p(0.5f, stemTop), p(0.5f, 0.88f), strokeWidth = s * 0.03f)
            drawLine(outline, p(0.32f, 0.9f), p(0.68f, 0.9f), strokeWidth = s * 0.035f)
        }
        if (handle) {
            val h = Path().apply {
                moveTo(p(0.66f, 0.36f).x, p(0.66f, 0.36f).y)
                cubicTo(p(0.86f, 0.36f).x, p(0.86f, 0.36f).y, p(0.86f, 0.7f).x, p(0.86f, 0.7f).y, p(0.65f, 0.7f).x, p(0.65f, 0.7f).y)
            }
            drawPath(h, outline, style = stroke)
        }
    }
}

private fun drawIce(scope: DrawScope, at: Offset, cube: Float) = with(scope) {
    val c = Color.White.copy(alpha = 0.28f)
    drawRoundRect(c, topLeft = at, size = Size(cube, cube), cornerRadius = CornerRadius(cube * 0.2f))
    drawRoundRect(c, topLeft = Offset(at.x + cube * 0.9f, at.y + cube * 0.5f), size = Size(cube, cube), cornerRadius = CornerRadius(cube * 0.2f))
}

/** "30 ml" + "Gin" in the chosen unit system. */
fun ingredientAmount(ing: RecipeIngredient, unitSystem: String, unitLabel: (String) -> String): String {
    val a = ing.amount ?: return ing.measure ?: ""
    val (v, u) = Units.convert(a, ing.unit, unitSystem)
    if (u.isEmpty()) return Units.format(v, "pc")
    return Units.format(v, u) + " " + unitLabel(u)
}

@Composable
fun unitLabeler(): (String) -> String {
    val labels = Labels.unit.mapValues { stringResource(it.value) }
    return { u -> labels[u] ?: u }
}

@Composable
fun typeLabel(type: String): String = stringResource(
    when (type) {
        "mocktail" -> R.string.type_mocktail
        "punch" -> R.string.type_punch
        "smoothie" -> R.string.type_smoothie
        "shot" -> R.string.type_shot
        else -> R.string.type_cocktail
    }
)

@Composable
fun styleLabel(style: String): String = Labels.style[style]?.let { stringResource(it) } ?: style.replace('_', ' ')

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Tall glass tile used in horizontal rows; the picture morphs into the detail screen. */
@Composable
fun RecipeTile(recipe: Recipe, lang: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, section: String = "tile") {
    val hero = LocalHeroKey.current
    val key = "$section/${recipe.id}"
    Column(modifier.padding(horizontal = 5.dp).width(148.dp).bouncyClick { hero.value = key; onClick() }.glass(RoundedCornerShape(24.dp)).padding(6.dp)) {
        DrinkArt(recipe, Modifier.fillMaxWidth().height(148.dp), corner = 19.dp, sharedKey = key)
        Spacer(Modifier.height(8.dp))
        Text(recipe.displayName(lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp))
        Text(subtitle ?: styleLabel(recipe.styles.firstOrNull() ?: recipe.type), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp))
    }
}

/** List row (One UI style card) with ingredients summary and an optional trailing badge. */
@Composable
fun RecipeRow(
    recipe: Recipe, catalog: Catalog, lang: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, section: String = "row", badge: (@Composable () -> Unit)? = null,
) {
    val hero = LocalHeroKey.current
    val key = "$section/${recipe.id}"
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)
            .bouncyClick { hero.value = key; onClick() }.glass(RoundedCornerShape(22.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrinkArt(recipe, Modifier.size(64.dp), corner = 16.dp, sharedKey = key)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(recipe.displayName(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                recipe.ingredients.filter { it.flag != "r" }.joinToString(" · ") { catalog.name(it, lang) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (badge != null) { Spacer(Modifier.width(8.dp)); badge() }
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

val ScreenPadding = Arrangement.spacedBy(12.dp)

@Composable
fun SettingsButton(nav: androidx.navigation.NavController) {
    androidx.compose.material3.IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Settings, stringResource(R.string.settings))
    }
}

/** Simple bottle silhouette showing the fill level. */
@Composable
fun BottleDrawing(liquid: Color, fill: Int, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.42f, h * 0.04f); lineTo(w * 0.58f, h * 0.04f)
            lineTo(w * 0.58f, h * 0.28f)
            cubicTo(w * 0.74f, h * 0.34f, w * 0.76f, h * 0.4f, w * 0.76f, h * 0.48f)
            lineTo(w * 0.76f, h * 0.94f); lineTo(w * 0.24f, h * 0.94f); lineTo(w * 0.24f, h * 0.48f)
            cubicTo(w * 0.24f, h * 0.4f, w * 0.26f, h * 0.34f, w * 0.42f, h * 0.28f)
            close()
        }
        val top = h * 0.94f - (h * 0.6f) * (fill.coerceIn(0, 100) / 100f)
        clipPath(body) { drawRect(liquid, topLeft = Offset(0f, top), size = Size(w, h - top)) }
        drawPath(body, outline, style = Stroke(width = w * 0.05f))
    }
}

/** Human readable text for AI errors (rate limit, missing key, HTTP errors). */
@Composable
fun aiErrorText(message: String?): String {
    val m = message.orEmpty()
    return when {
        m.startsWith("RATE_LIMIT:") -> {
            val secs = m.removePrefix("RATE_LIMIT:").toIntOrNull()
            stringResource(R.string.ai_rate_limit, if (secs != null) stringResource(R.string.ai_wait_seconds, secs) else stringResource(R.string.ai_wait_later))
        }
        m == "NO_KEY" -> stringResource(R.string.scan_no_key)
        else -> m
    }
}
