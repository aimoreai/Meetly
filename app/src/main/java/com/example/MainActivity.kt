package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.repository.MeetlyRepository
import com.example.ui.MeetlyComposeApp
import com.example.ui.MeetlyViewModel

class MainActivity : ComponentActivity() {
    private val repository by lazy { MeetlyRepository(applicationContext) }
    private val viewModel by lazy { MeetlyViewModel(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeetlyComposeApp(viewModel)
        }
    }
}
