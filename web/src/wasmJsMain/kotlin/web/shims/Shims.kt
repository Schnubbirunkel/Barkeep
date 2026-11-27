@file:Suppress("PackageDirectoryMismatch", "unused")

package de.schnubbirunkel.barkeep.ui

import androidx.savedstate.SavedState
import androidx.savedstate.read
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/*
 * Web stand-ins for the few JVM/Android APIs the shared code uses.
 */

/** No IO threads in the browser: run on the default (main-thread) dispatcher. */
internal val Dispatchers.IO: CoroutineDispatcher get() = Dispatchers.Default

/** Navigation arguments (Bundle on Android). */
internal fun SavedState.getString(key: String): String? = read { if (contains(key)) getStringOrNull(key) else null }
internal fun SavedState.getInt(key: String): Int? = read { if (contains(key)) getIntOrNull(key) else null }

/** kotlin.jvm.JvmOverloads does not exist outside the JVM; harmless no-op here. */
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
internal annotation class JvmOverloads
