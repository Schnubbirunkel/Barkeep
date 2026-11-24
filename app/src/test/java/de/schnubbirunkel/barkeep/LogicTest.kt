package de.schnubbirunkel.barkeep

import de.schnubbirunkel.barkeep.data.CocktailDb
import de.schnubbirunkel.barkeep.data.DownloadedSources
import de.schnubbirunkel.barkeep.data.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogicTest {
    @Test fun fractions() {
        assertEquals("1 ½", Units.fraction(1.5, 4))
        assertEquals("¾", Units.fraction(0.75, 4))
        assertEquals("2", Units.fraction(2.0, 4))
    }

    @Test fun measures() {
        val a = CocktailDb.parseMeasure("Gin", "1 1/2 oz")
        assertEquals(45.0, a.amount!!, 0.01); assertEquals("ml", a.unit)
        val b = CocktailDb.parseMeasure("Gin", "4.5 cl")
        assertEquals(45.0, b.amount!!, 0.01)
        val c = CocktailDb.parseMeasure("Angostura", "2 dash")
        assertEquals("dash", c.unit)
        val d = CocktailDb.parseMeasure("Lime", "Juice of 1/2")
        assertNull(d.amount)
    }

    @Test fun pyPairs() {
        val p = DownloadedSources.parsePyPairs("[['4.5 cl', 'gin'], ['2 dash', \"peychaud's\"]]")
        assertEquals(listOf("4.5 cl" to "gin", "2 dash" to "peychaud's"), p)
    }
}
