package de.schnubbirunkel.barkeep

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.BarkeepApp
import de.schnubbirunkel.barkeep.ui.BarkeepTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val user by vm.user.collectAsState()
            BarkeepTheme(user.settings.theme, user.settings.accent) {
                BarkeepApp(vm)
            }
        }
    }
}
