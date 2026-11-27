@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import de.schnubbirunkel.barkeep.web.WebLang
import de.schnubbirunkel.barkeep.web.formatAndroid
import de.schnubbirunkel.barkeep.web.webString

/** Android string resources, generated from res/values/strings.xml (+ values-de) (see web/build.gradle.kts). */
@Composable
fun stringResource(id: Int): String = webString(id, WebLang.effective)

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = formatAndroid(webString(id, WebLang.effective), formatArgs)
