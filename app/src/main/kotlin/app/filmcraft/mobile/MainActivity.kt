package app.filmcraft.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import app.filmcraft.mobile.ui.EditorScreen

class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8B5CF6),
                    background = Color(0xFF14121F),
                    surface = Color(0xFF1E1B2E),
                ),
            ) {
                EditorScreen(viewModel)
            }
        }
    }
}
