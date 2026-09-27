package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.RoutePilotAppRoot
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RoutePilotViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val routePilotViewModel: RoutePilotViewModel = viewModel(
                    factory = RoutePilotViewModel.Factory(applicationContext)
                )
                RoutePilotAppRoot(viewModel = routePilotViewModel)
            }
        }
    }
}
