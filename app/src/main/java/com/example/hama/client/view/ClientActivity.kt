package com.example.hama.client.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.hama.client.viewmodel.ClientViewModel
import com.example.hama.ui.theme.HamaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ClientActivity : ComponentActivity() {
    private val viewModel: ClientViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HamaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClientScreen(viewModel = viewModel)
                }
            }
        }
    }
}