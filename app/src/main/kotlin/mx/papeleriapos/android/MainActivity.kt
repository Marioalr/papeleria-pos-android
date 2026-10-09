package mx.papeleriapos.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.android.ui.AppEscaner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TemaEscaner { AppEscaner() }
        }
    }
}
