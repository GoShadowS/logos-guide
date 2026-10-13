package com.logos.navigator.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.logos.navigator.R

/** Явная фабрика для создания AndroidViewModel в Compose/NavHost при необходимости. */
class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException(application.getString(R.string.unknown_viewmodel_class, modelClass.name))
    }
}
