@file:Suppress("PackageDirectoryMismatch")

package androidx.lifecycle

/** Stand-in for androidx.lifecycle.AndroidViewModel (Android only). */
open class AndroidViewModel(@Suppress("UNUSED_PARAMETER") application: android.app.Application) : ViewModel()
