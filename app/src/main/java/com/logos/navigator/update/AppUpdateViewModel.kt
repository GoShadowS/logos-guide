package com.logos.navigator.update

import com.logos.navigator.utils.localizedContext

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.logos.navigator.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

internal data class AppUpdateUiState(
    val availableUpdate: AvailableUpdate? = null,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedApk: File? = null,
    val message: String? = null,
    val showPrompt: Boolean = false,
)

internal class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppUpdateRepository(application)
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(AppUpdateUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.deleteStaleDownloads() }
            checkForUpdates(force = false)
        }
    }

    fun checkForUpdates(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (mutableState.value.isChecking || mutableState.value.isDownloading) return
        if (!force && now - preferences.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        viewModelScope.launch {
            mutableState.update { it.copy(isChecking = true, message = null) }
            try {
                val update = repository.findLatestUpdate()
                preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                val dismissedVersion = preferences.getString(KEY_DISMISSED_VERSION, null)
                mutableState.update { current ->
                    val downloadedApk = if (update != null && current.availableUpdate?.versionName == update.versionName) {
                        current.downloadedApk
                    } else {
                        null
                    }
                    current.copy(
                        availableUpdate = update,
                        isChecking = false,
                        downloadedApk = downloadedApk,
                        message = if (update == null) getApplication<Application>().localizedContext().getString(R.string.update_up_to_date) else null,
                        showPrompt = update != null && (update.versionName != dismissedVersion || downloadedApk != null),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                mutableState.update {
                    it.copy(
                        isChecking = false,
                        message = getApplication<Application>().localizedContext().getString(R.string.update_check_failed),
                    )
                }
            }
        }
    }

    fun dismissUpdatePrompt() {
        val versionName = mutableState.value.availableUpdate?.versionName ?: return
        preferences.edit().putString(KEY_DISMISSED_VERSION, versionName).apply()
        mutableState.update { it.copy(showPrompt = false) }
    }

    fun downloadUpdate() {
        val update = mutableState.value.availableUpdate ?: return
        if (mutableState.value.isDownloading) return
        viewModelScope.launch {
            mutableState.update {
                it.copy(isDownloading = true, downloadProgress = 0, message = null)
            }
            try {
                val apkFile = repository.downloadUpdate(update) { progress ->
                    mutableState.update { current -> current.copy(downloadProgress = progress) }
                }
                mutableState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 100,
                        downloadedApk = apkFile,
                        message = getApplication<Application>().localizedContext().getString(R.string.update_downloaded),
                    )
                }
                installUpdate()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isDownloading = false,
                        message = getApplication<Application>().localizedContext().getString(R.string.update_download_failed),
                    )
                }
            }
        }
    }

    fun installUpdate() {
        val apkFile = mutableState.value.downloadedApk ?: return
        try {
            val installerOpened = repository.openInstaller(apkFile)
            mutableState.update {
                it.copy(
                    showPrompt = if (installerOpened) false else it.showPrompt,
                    message = getApplication<Application>().localizedContext().getString(
                        if (installerOpened) R.string.update_installer_opened else R.string.update_install_permission,
                    ),
                )
            }
        } catch (_: Exception) {
            mutableState.update {
                it.copy(
                    message = getApplication<Application>().localizedContext().getString(R.string.update_installer_failed),
                    showPrompt = true,
                )
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "app_update_state"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_DISMISSED_VERSION = "dismissed_version"
        const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
