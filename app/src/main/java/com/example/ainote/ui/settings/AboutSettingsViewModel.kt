package com.example.ainote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ainote.data.repository.AppUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AboutSettingsUiState(
    val currentVersion: String,
    val latestVersion: String? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val releaseUrl: String? = null,
    val downloadedApkPath: String? = null,
    val awaitingInstall: Boolean = false,
    val awaitingUnknownSourcesPermission: Boolean = false
)

class AboutSettingsViewModel(
    private val currentVersion: String,
    private val updateRepository: AppUpdateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AboutSettingsUiState(currentVersion = currentVersion))
    val uiState: StateFlow<AboutSettingsUiState> = _uiState.asStateFlow()

    fun runAutoUpdate() {
        val state = _uiState.value
        if (state.checking || state.downloading) return
        if (state.downloadedApkPath != null) {
            _uiState.update {
                it.copy(
                    awaitingInstall = true,
                    awaitingUnknownSourcesPermission = false,
                    errorMessage = null,
                    statusMessage = "准备继续安装 ${it.latestVersion ?: it.currentVersion}"
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    checking = true,
                    downloading = false,
                    progress = null,
                    errorMessage = null,
                    statusMessage = "正在检查最新版本…",
                    awaitingInstall = false,
                    awaitingUnknownSourcesPermission = false
                )
            }
            runCatching { updateRepository.fetchLatestRelease() }
                .onSuccess { release ->
                    if (!updateRepository.isNewerVersion(release.versionName, currentVersion)) {
                        _uiState.update {
                            it.copy(
                                checking = false,
                                latestVersion = release.versionName,
                                releaseUrl = release.releaseUrl,
                                statusMessage = "当前已是最新版本 $currentVersion",
                                errorMessage = null
                            )
                        }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            checking = false,
                            downloading = true,
                            latestVersion = release.versionName,
                            releaseUrl = release.releaseUrl,
                            progress = 0f,
                            statusMessage = "发现新版本 ${release.versionName}，开始下载…",
                            errorMessage = null
                        )
                    }
                    runCatching {
                        updateRepository.downloadReleaseApk(release) { downloadedBytes, totalBytes ->
                            _uiState.update { current ->
                                current.copy(
                                    progress = if (totalBytes > 0) {
                                        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        null
                                    },
                                    statusMessage = if (totalBytes > 0) {
                                        "正在下载 ${release.versionName}：${(downloadedBytes * 100 / totalBytes).coerceIn(0, 100)}%"
                                    } else {
                                        "正在下载 ${release.versionName}…"
                                    }
                                )
                            }
                        }
                    }.onSuccess { apkFile ->
                        _uiState.update {
                            it.copy(
                                downloading = false,
                                progress = 1f,
                                downloadedApkPath = apkFile.absolutePath,
                                awaitingInstall = true,
                                awaitingUnknownSourcesPermission = false,
                                statusMessage = "下载完成，准备安装 ${release.versionName}",
                                errorMessage = null
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(
                                downloading = false,
                                progress = null,
                                statusMessage = null,
                                errorMessage = updateRepository.formatNetworkError(error)
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            checking = false,
                            downloading = false,
                            progress = null,
                            statusMessage = null,
                            errorMessage = updateRepository.formatNetworkError(error)
                        )
                    }
                }
        }
    }

    fun buildInstallRequest(): File? {
        val path = _uiState.value.downloadedApkPath ?: return null
        return File(path).takeIf(File::exists)
    }

    fun markInstallLaunched() {
        _uiState.update {
            it.copy(
                awaitingInstall = false,
                awaitingUnknownSourcesPermission = false,
                statusMessage = "已发起安装，请按系统提示继续。",
                errorMessage = null
            )
        }
    }

    fun markUnknownSourcesPermissionNeeded() {
        _uiState.update {
            it.copy(
                awaitingInstall = false,
                awaitingUnknownSourcesPermission = true,
                statusMessage = "请允许安装未知来源应用后，再点一次“自动更新”继续安装。",
                errorMessage = null
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reportInstallLaunchError(message: String) {
        _uiState.update {
            it.copy(
                awaitingInstall = false,
                awaitingUnknownSourcesPermission = false,
                errorMessage = message
            )
        }
    }

    class Factory(
        private val currentVersion: String,
        private val updateRepository: AppUpdateRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AboutSettingsViewModel(currentVersion, updateRepository) as T
        }
    }
}
