package com.logos.navigator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.logos.navigator.ui.MainViewModel
import com.logos.navigator.ui.MainViewModelFactory
import com.logos.navigator.ui.NavigatorApp

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NavigatorApp(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForeground()
    }

    override fun onStop() {
        // A rotation or language switch recreates the activity; keep GPS running through it.
        if (!isChangingConfigurations) viewModel.onAppBackground()
        super.onStop()
    }
}
