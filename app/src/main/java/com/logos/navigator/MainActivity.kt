package com.logos.navigator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.logos.navigator.ui.MainViewModel
import com.logos.navigator.ui.MainViewModelFactory
import com.logos.navigator.ui.NavigatorApp

/** Единственная Activity приложения; переходы и экраны построены на Compose Navigation. */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NavigatorApp(viewModel = viewModel)
        }
    }
}
