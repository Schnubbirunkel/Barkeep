package de.schnubbirunkel.barkeep

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.BarkeepApp
import de.schnubbirunkel.barkeep.ui.BarkeepTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BarkeepTheme {
                val vm: AppViewModel = viewModel()
                BarkeepApp(vm)
            }
        }
    }
}
