package com.example.ainote.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ainote.data.repository.AppUpdateRepository
import java.io.File

private const val GithubRepoUrl = "https://github.com/WCRfamfih/WCR-Note"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    updateRepository: AppUpdateRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "未知版本" }
    }
    val viewModel: AboutSettingsViewModel = viewModel(
        factory = AboutSettingsViewModel.Factory(versionName, updateRepository)
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.awaitingInstall, state.downloadedApkPath) {
        if (!state.awaitingInstall) return@LaunchedEffect
        val apkFile: File = viewModel.buildInstallRequest() ?: run {
            viewModel.reportInstallLaunchError("安装包不存在，请重新下载。")
            return@LaunchedEffect
        }
        if (updateRepository.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(updateRepository.buildInstallIntent(apkFile))
            }.onSuccess {
                viewModel.markInstallLaunched()
            }.onFailure { error ->
                Toast.makeText(context, error.message ?: "无法启动安装。", Toast.LENGTH_LONG).show()
                viewModel.reportInstallLaunchError(error.message ?: "无法启动安装。")
            }
        } else {
            runCatching {
                context.startActivity(updateRepository.buildUnknownSourcesIntent())
            }
            viewModel.markUnknownSourcesPermissionNeeded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("WCR笔记", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("当前版本：${state.currentVersion}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("作者：WCR with Codex", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("自动更新", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.latestVersion?.let { latest ->
                            "GitHub 最新版本：$latest"
                        } ?: "点击下方按钮后，将从 GitHub 检查并下载最新安装包。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.progress?.let { progress ->
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    state.statusMessage?.let { message ->
                        Spacer(Modifier.height(12.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    state.errorMessage?.let { message ->
                        Spacer(Modifier.height(12.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::runAutoUpdate,
                        enabled = !state.checking && !state.downloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val label = when {
                            state.downloading -> "正在下载…"
                            state.checking -> "正在检查…"
                            state.downloadedApkPath != null -> "继续安装"
                            else -> "自动更新"
                        }
                        Text(label)
                    }
                    if (state.releaseUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.releaseUrl)))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("打开对应 Release 页面")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("开源许可", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本项目采用 MIT License。你可以自由使用、修改、分发和商用，但需保留原版权声明与许可文本。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("GitHub 仓库", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(GithubRepoUrl, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GithubRepoUrl)))
                        }
                    ) {
                        Text("打开仓库地址")
                    }
                }
            }
        }
    }
}
