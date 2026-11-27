@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.ui.text.font

import de.schnubbirunkel.barkeep.web.WebEnv

/** Android-style Font(R.font.x, …) backed by the font files preloaded in WebEnv. */
fun Font(
    resId: Int,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Font = androidx.compose.ui.text.platform.Font(
    identity = "barkeep-font-$resId-${weight.weight}-$style",
    data = WebEnv.fonts.getValue(resId),
    weight = weight,
    style = style,
    variationSettings = variationSettings,
)
