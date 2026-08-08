package com.example.phonequery.ui.setup

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.phonequery.R

/**
 * 首次使用授权引导页：列出所有运行时权限 / 特殊授权（悬浮窗、默认拨号应用），
 * 每项可一键跳转系统设置或在应用内弹窗授权；授权后返回自动刷新状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    onDone: () -> Unit,
    onMarkSeen: () -> Unit
) {
    val context = LocalContext.current
    val packageName = context.packageName

    // 授权/跳转设置返回后 +1，触发各状态重新读取
    var refresh by remember { mutableStateOf(0) }

    val standardPermissions = remember {
        mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    val dialerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    fun isGranted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun isDefaultDialer(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val tm = context.getSystemService(TelecomManager::class.java)
            return tm != null && context.packageName == tm.defaultDialerPackage
        }
        val rm = context.getSystemService(RoleManager::class.java)
        return rm?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_guide_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.setup_guide_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 常规危险权限
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setup_group_permissions), style = MaterialTheme.typography.titleMedium)
                    standardPermissions.forEach { perm ->
                        key(perm, refresh) {
                            GuideRow(
                                label = permissionLabel(perm),
                                granted = isGranted(perm),
                                onRequest = { permissionLauncher.launch(arrayOf(perm)) }
                            )
                        }
                    }
                }
            }

            // 悬浮窗（特殊权限）
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                key(refresh) {
                    GuideRow(
                        label = stringResource(R.string.permission_overlay),
                        desc = stringResource(R.string.setup_overlay_desc),
                        granted = canDrawOverlays(),
                        onRequest = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            settingsLauncher.launch(intent)
                        }
                    )
                }
            }

            // 默认拨号应用（自动挂断前提）
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                key(refresh) {
                    GuideRow(
                        label = stringResource(R.string.setup_default_dialer),
                        desc = stringResource(R.string.setup_default_dialer_desc),
                        granted = isDefaultDialer(),
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val rm = context.getSystemService(RoleManager::class.java)
                                val intent = rm?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                                if (intent != null) dialerLauncher.launch(intent)
                            } else {
                                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            // 厂商自启动 / 后台限制
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setup_autostart_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.setup_autostart_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                        settingsLauncher.launch(intent)
                    }) {
                        Text(stringResource(R.string.setup_open_app_settings))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onMarkSeen()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.setup_finish))
            }
        }
    }
}

@Composable
private fun GuideRow(
    label: String,
    desc: String? = null,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!desc.isNullOrBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Text(
                stringResource(R.string.status_granted),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            TextButton(onClick = onRequest) {
                Text(stringResource(R.string.action_grant))
            }
        }
    }
}

private fun permissionLabel(perm: String): String = when (perm) {
    Manifest.permission.READ_PHONE_STATE -> "读取电话状态"
    Manifest.permission.READ_CALL_LOG -> "读取通话记录"
    Manifest.permission.CALL_PHONE -> "拨打电话"
    Manifest.permission.ANSWER_PHONE_CALLS -> "接听 / 挂断电话"
    Manifest.permission.POST_NOTIFICATIONS -> "通知"
    else -> perm
}
