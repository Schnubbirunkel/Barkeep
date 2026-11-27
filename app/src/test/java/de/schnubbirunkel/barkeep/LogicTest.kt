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

    @Test fun csv() {
        val rows = ArrayList<List<String>>()
        java.io.StringReader("a,b,c\r\n1,\"x, \"\"y\"\"\nz\",3\n").let { r -> de.schnubbirunkel.barkeep.data.Csv.records({ r.read() }) { rows += it } }
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "x, \"y\"\nz", "3")), rows)
    }

    @Test fun resolveIngredients() {
        val file = de.schnubbirunkel.barkeep.data.AppJson.decodeFromString(
            de.schnubbirunkel.barkeep.data.CatalogFile.serializer(), java.io.File("src/main/assets/ingredients.json").readText())
        val c = de.schnubbirunkel.barkeep.data.Catalog(file)
        assertEquals("gin", c.resolve("London Dry Gin"))
        assertEquals("angostura", c.resolve("Angostura aromatic bitters"))
        assertEquals("cinnamon_whisky", c.resolve("Fireball"))
        assertEquals("lime_juice", c.resolve("freshly squeezed lime juice"))
        assertEquals("simple_syrup", c.resolve("Sugar syrup (1:1)"))
        assertEquals(true, "bourbon" in c.search("whisky"))
        assertEquals(true, "cinnamon_whisky" in c.search("fireball"))
        assertEquals(true, "woodruff_syrup" in c.search("Waldmeister"))
    }

    @Test fun pyPairs() {
        val p = DownloadedSources.parsePyPairs("[['4.5 cl', 'gin'], ['2 dash', \"peychaud's\"]]")
        assertEquals(listOf("4.5 cl" to "gin", "2 dash" to "peychaud's"), p)
    }
}
