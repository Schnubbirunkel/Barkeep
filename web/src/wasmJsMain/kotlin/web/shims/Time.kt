@file:Suppress("PackageDirectoryMismatch")

package java.time

/** Minimal java.time.LocalDate (local calendar date of the browser). */
class LocalDate private constructor(val year: Int, val monthValue: Int, val dayOfMonth: Int) {
    fun toEpochDay(): Long {
        // days-from-civil (Howard Hinnant)
        val y = if (monthValue <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (monthValue + 9) % 12
        val doy = (153 * mp + 2) / 5 + dayOfMonth - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    companion object {
        fun now(): LocalDate = LocalDate(jsYear(), jsMonth(), jsDay())
        fun of(year: Int, month: Int, dayOfMonth: Int): LocalDate = LocalDate(year, month, dayOfMonth)
    }
}

private fun jsYear(): Int = js("new Date().getFullYear()")
private fun jsMonth(): Int = js("new Date().getMonth() + 1")
private fun jsDay(): Int = js("new Date().getDate()")
