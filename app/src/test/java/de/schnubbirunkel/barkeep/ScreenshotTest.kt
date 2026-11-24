package de.schnubbirunkel.barkeep

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.BarkeepApp
import de.schnubbirunkel.barkeep.ui.BarkeepTheme
import de.schnubbirunkel.barkeep.ui.Routes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the main screens for the README and releases: ./gradlew recordRoborazziDebug */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val lang: String) {
    @get:Rule val rule = createComposeRule()

    private fun shoot(name: String, route: String?, setup: (AppViewModel) -> Unit = {}) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app, "shot-$name-$lang.json", false)
        vm.setOnboarded()
        seed(vm)
        setup(vm)
        lateinit var nav: NavHostController
        rule.setContent {
            nav = rememberNavController()
            BarkeepTheme { BarkeepApp(vm, nav = nav) }
        }
        if (route != null) rule.runOnIdle { nav.navigate(route) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../docs/screenshots/$lang/$name.png")
    }

    private fun seed(vm: AppViewModel) {
        listOf("lime_juice", "lemon_juice", "simple_syrup", "angostura", "soda", "tonic", "mint", "orange", "ginger_beer", "egg_white", "prosecco")
            .forEach { vm.toggleInventory(it) }
        vm.addBottle("London Dry Gin", "Tanqueray", "gin")
        vm.addBottle("Bitter", "Campari", "campari")
        vm.addBottle("Rosso", "Martini", "sweet_vermouth")
        vm.addBottle("Bourbon", "Buffalo Trace", "bourbon")
        vm.addBottle("Carta Blanca", "Bacardí", "white_rum")
        vm.addBottle("Aperitivo", "Aperol", "aperol")
        vm.recipe("negroni")?.let { vm.toggleFavorite(it) }
        vm.recipe("penicillin")?.let { vm.toggleFavorite(it) }
        vm.recipe("hugo")?.let { vm.toggleFavorite(it) }
        vm.addShopping(if (lang == "de") "Limetten" else "Limes")
        vm.addShopping("Cointreau", "cointreau")
    }

    @Test fun discover() = shoot("1_discover", null)
    @Test fun detail() = shoot("2_detail", Routes.recipe("whiskey_sour"))
    @Test fun bar() = shoot("3_bar_bottles", Routes.bar(0))
    @Test fun make() = shoot("4_what_can_i_make", Routes.bar(2))
    @Test fun search() = shoot("5_search", Routes.search(q = "whisky"))
    @Test fun favorites() = shoot("6_favorites", Routes.FAVORITES)
    @Test fun settings() = shoot("7_settings", Routes.SETTINGS)
    @Test fun finder() = shoot("8_finder", Routes.FINDER)
}

@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-xxhdpi")
class ScreenshotDe : ScreenshotBase("de")

@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-xxhdpi")
class ScreenshotEn : ScreenshotBase("en")
