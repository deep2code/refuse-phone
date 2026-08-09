package com.example.phonequery.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.phonequery.BuildConfig
import com.example.phonequery.R
import com.example.phonequery.call.CallScreeningRole
import com.example.phonequery.call.DefaultDialerRole
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToBlocklist: () -> Unit,
    onNavigateToSetupGuide: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val cacheCount by viewModel.cacheCount.collectAsState()
    val spamHashCount by viewModel.spamHashCount.collectAsState()
    val codeNumberCount by viewModel.codeNumberCount.collectAsState()
    val context = LocalContext.current

    // 申请「来电筛选」系统角色（CallScreeningService 的前置授权）
    val callScreeningLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val held = CallScreeningRole.isHeld(context)
        viewModel.setCallScreening(held)
        if (!held) {
            Toast.makeText(context, R.string.call_screening_role_missing, Toast.LENGTH_LONG).show()
        }
    }

    var showClearCacheDialog by remember { mutableStateOf(false) }

    val settings = uiState.settings

    val phonePermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_PHONE_STATE)
    } else null

    val callLogPermission = rememberPermissionState(Manifest.permission.READ_CALL_LOG)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限状态卡片
            PermissionStatusCard(
                phonePermissionStatus = phonePermissionState?.status,
                callLogStatus = callLogPermission.status,
                onRequestPhonePermission = { phonePermissionState?.launchPermissionRequest() },
                onRequestCallLogPermission = { callLogPermission.launchPermissionRequest() }
            )

            // 功能开关卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.card_call_features),
                        style = MaterialTheme.typography.titleMedium
                    )

                    SettingSwitchItem(
                        title = stringResource(R.string.setting_floating_window),
                        desc = stringResource(R.string.setting_floating_window_desc),
                        checked = settings.enableFloatingWindow,
                        onCheckedChange = { enabled ->
                            if (enabled && !viewModel.canDrawOverlays(context)) {
                                viewModel.openOverlaySettings(context)
                                Toast.makeText(
                                    context,
                                    R.string.toast_enable_overlay,
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                viewModel.setFloatingWindow(enabled)
                            }
                        }
                    )

                    SettingSwitchItem(
                        title = stringResource(R.string.setting_auto_hangup),
                        desc = stringResource(R.string.setting_auto_hangup_desc),
                        checked = settings.enableAutoHangup,
                        onCheckedChange = { viewModel.setAutoHangup(it) }
                    )

                    // 未持有默认拨号角色时（如 HarmonyOS），主动 endCall 挂断不可用，
                    // 提示用户改用「系统级来电识别」做拦截。
                    if (!DefaultDialerRole.isHeld(context)) {
                        Text(
                            text = stringResource(R.string.auto_hangup_unsupported_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (settings.enableAutoHangup) {
                        SettingSwitchItem(
                            title = stringResource(R.string.setting_blacklist_only),
                            desc = stringResource(R.string.setting_blacklist_only_desc),
                            checked = settings.enableBlacklistOnly,
                            onCheckedChange = { viewModel.setBlacklistOnly(it) }
                        )

                        if (!settings.enableBlacklistOnly) {
                            SettingSwitchItem(
                                title = stringResource(R.string.setting_spam_auto_hangup),
                                desc = stringResource(R.string.setting_spam_auto_hangup_desc),
                                checked = settings.enableSpamAutoHangup,
                                onCheckedChange = { viewModel.setSpamAutoHangup(it) }
                            )
                        }
                    }

                    SettingSwitchItem(
                        title = stringResource(R.string.setting_boot_start),
                        desc = stringResource(R.string.setting_boot_start_desc),
                        checked = settings.enableBootStart,
                        onCheckedChange = { viewModel.setBootStart(it) }
                    )

                    SettingSwitchItem(
                        title = stringResource(R.string.setting_job_hunt_mode),
                        desc = stringResource(R.string.setting_job_hunt_mode_desc),
                        checked = settings.enableJobHuntMode,
                        onCheckedChange = { viewModel.setJobHuntMode(it) }
                    )

                    if (!settings.enableJobHuntMode) {
                        SettingSwitchItem(
                            title = stringResource(R.string.setting_silence_unknown),
                            desc = stringResource(R.string.setting_silence_unknown_desc),
                            checked = settings.enableSilenceUnknown,
                            onCheckedChange = { viewModel.setSilenceUnknown(it) }
                        )
                    }

                    SettingSwitchItem(
                        title = stringResource(R.string.setting_call_screening),
                        desc = stringResource(R.string.setting_call_screening_desc),
                        checked = settings.enableCallScreening,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val intent = CallScreeningRole.createRequestIntent(context)
                                if (intent != null) {
                                    callScreeningLauncher.launch(intent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.call_screening_unsupported,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                viewModel.setCallScreening(false)
                            }
                        }
                    )
                    if (settings.enableCallScreening && !CallScreeningRole.isHeld(context)) {
                        Text(
                            text = stringResource(R.string.call_screening_role_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (settings.enableJobHuntMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.job_hunt_mode_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // 黑白名单入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBlocklist() },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.blocklist_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.blocklist_summary,
                                uiState.blacklist.size,
                                uiState.whitelist.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }

            // 首次使用授权引导入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSetupGuide() },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_setup_guide),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.settings_setup_guide_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }

            // 本地标记缓存管理
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showClearCacheDialog = true },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.clear_cache_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.clear_cache_desc, cacheCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }

            // 数据源状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.data_sources_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.data_source_default),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (BuildConfig.JUHE_KEY.isNotBlank())
                            stringResource(R.string.data_source_juhe_on)
                        else
                            stringResource(R.string.data_source_juhe_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (BuildConfig.BAIDU_PHONE_API_URL.isNotBlank())
                            stringResource(R.string.data_source_baidu_on)
                        else
                            stringResource(R.string.data_source_baidu_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.community_db_desc, spamHashCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (BuildConfig.ALIYUN_MARK_APPCODE.isNotBlank())
                            stringResource(R.string.data_source_aliyun_on)
                        else
                            stringResource(R.string.data_source_aliyun_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (BuildConfig.QCC_KEY.isNotBlank())
                            stringResource(R.string.data_source_qcc_on)
                        else
                            stringResource(R.string.data_source_qcc_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.code_number_db_desc, codeNumberCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.data_source_attribution),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 前台服务说明
            Text(
                text = stringResource(R.string.service_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 清除本地标记缓存确认弹窗
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.clear_cache_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMarkCache()
                        showClearCacheDialog = false
                        Toast.makeText(
                            context,
                            R.string.cache_cleared_toast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) { Text(stringResource(R.string.btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionStatusCard(
    phonePermissionStatus: PermissionStatus?,
    callLogStatus: PermissionStatus,
    onRequestPhonePermission: () -> Unit,
    onRequestCallLogPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.card_permissions),
                style = MaterialTheme.typography.titleMedium
            )

            if (phonePermissionStatus != null) {
                PermissionRow(
                    label = stringResource(R.string.permission_phone_state),
                    status = phonePermissionStatus,
                    onRequest = onRequestPhonePermission
                )
            }

            PermissionRow(
                label = stringResource(R.string.permission_call_log),
                status = callLogStatus,
                onRequest = onRequestCallLogPermission
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionRow(
    label: String,
    status: PermissionStatus,
    onRequest: () -> Unit
) {
    val granted = status is PermissionStatus.Granted
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label)
        Switch(
            checked = granted,
            onCheckedChange = { if (!granted) onRequest() },
            enabled = !granted
        )
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}