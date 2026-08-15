package com.example.phonequery.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.example.phonequery.R
import androidx.compose.material3.Slider
import androidx.compose.runtime.LaunchedEffect
import com.example.phonequery.call.CallScreeningRole
import com.example.phonequery.ui.theme.AppCard
import com.example.phonequery.ui.theme.NavRow
import com.example.phonequery.ui.theme.SettingRow
import com.example.phonequery.call.DefaultDialerRole
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToBlocklist: () -> Unit,
    onNavigateToRecent: () -> Unit,
    onNavigateToSetupGuide: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val cacheCount by viewModel.cacheCount.collectAsState()
    val context = LocalContext.current
    // 用于 UI 回调中启动协程（导出/导入备份），替代 viewModelScope 以避免跨组件作用域解析问题
    val scope = rememberCoroutineScope()

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

    // 读取通讯录权限（「仅放行通讯录」拦截的前置条件）
    val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    // 备份导出：通过系统文件选择器保存 JSON
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = viewModel.exportBackup()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("openOutputStream null")
                }.onSuccess {
                    Toast.makeText(context, R.string.backup_exported_toast, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, R.string.backup_failed_toast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 备份导入：通过系统文件选择器读取 JSON
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText()
                    if (json.isNullOrBlank()) throw IllegalStateException("empty backup")
                    viewModel.importBackup(json)
                }.onSuccess { n ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_imported_toast, n),
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    Toast.makeText(context, R.string.backup_failed_toast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_home)
                        )
                    }
                }
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
            AppCard {
                    Text(
                        text = stringResource(R.string.card_call_features),
                        style = MaterialTheme.typography.titleMedium
                    )

                    SettingRow(
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

                    // 悬浮窗透明度调节（仅开启悬浮窗时显示）
                    if (settings.enableFloatingWindow) {
                        var alpha by remember { mutableStateOf(settings.floatingAlpha) }
                        LaunchedEffect(settings.floatingAlpha) { alpha = settings.floatingAlpha }
                        Text(
                            text = stringResource(
                                R.string.setting_floating_alpha,
                                (alpha * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = alpha,
                            onValueChange = { alpha = it },
                            onValueChangeFinished = { viewModel.setFloatingAlpha(alpha) },
                            valueRange = 0.3f..1.0f,
                            steps = 14
                        )
                    }

                    SettingRow(
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
                        SettingRow(
                            title = stringResource(R.string.setting_blacklist_only),
                            desc = stringResource(R.string.setting_blacklist_only_desc),
                            checked = settings.enableBlacklistOnly,
                            onCheckedChange = { viewModel.setBlacklistOnly(it) }
                        )

                        if (!settings.enableBlacklistOnly) {
                            SettingRow(
                                title = stringResource(R.string.setting_spam_auto_hangup),
                                desc = stringResource(R.string.setting_spam_auto_hangup_desc),
                                checked = settings.enableSpamAutoHangup,
                                onCheckedChange = { viewModel.setSpamAutoHangup(it) }
                            )
                        }
                    }

                    SettingRow(
                        title = stringResource(R.string.setting_boot_start),
                        desc = stringResource(R.string.setting_boot_start_desc),
                        checked = settings.enableBootStart,
                        onCheckedChange = { viewModel.setBootStart(it) }
                    )

                    SettingRow(
                        title = stringResource(R.string.setting_job_hunt_mode),
                        desc = stringResource(R.string.setting_job_hunt_mode_desc),
                        checked = settings.enableJobHuntMode,
                        onCheckedChange = { viewModel.setJobHuntMode(it) }
                    )

                    if (!settings.enableJobHuntMode) {
                        SettingRow(
                            title = stringResource(R.string.setting_silence_unknown),
                            desc = stringResource(R.string.setting_silence_unknown_desc),
                            checked = settings.enableSilenceUnknown,
                            onCheckedChange = { viewModel.setSilenceUnknown(it) }
                        )
                    }

                    SettingRow(
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

                    // 仅放行通讯录（拦截其余所有）
                    // 依赖 READ_CONTACTS 权限；未授权时开启也会被安全忽略（不会误拦通讯录号码）。
                    SettingRow(
                        title = stringResource(R.string.setting_block_non_contacts),
                        desc = stringResource(R.string.setting_block_non_contacts_desc),
                        checked = settings.enableBlockNonContacts,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (contactsPermission.status !is PermissionStatus.Granted) {
                                    contactsPermission.launchPermissionRequest()
                                }
                                viewModel.setBlockNonContacts(true)
                            } else {
                                viewModel.setBlockNonContacts(false)
                            }
                        }
                    )
                    if (settings.enableBlockNonContacts) {
                        val contactsGranted =
                            contactsPermission.status is PermissionStatus.Granted
                        if (!contactsGranted || !CallScreeningRole.isHeld(context)) {
                            Text(
                                text = stringResource(R.string.block_non_contacts_dep_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
            }

            // 在线查询（号码标记）：juhe 标记/归属地 + 阿里云多平台标记，key 由用户填写
            AppCard {
                Text(
                    text = stringResource(R.string.setting_online_lookup),
                    style = MaterialTheme.typography.titleMedium
                )
                SettingRow(
                    title = stringResource(R.string.setting_online_lookup),
                    desc = stringResource(R.string.setting_online_lookup_desc),
                    checked = settings.enableOnlineLookup,
                    onCheckedChange = { viewModel.setOnlineLookup(it) }
                )
                if (settings.enableOnlineLookup) {
                    Text(
                        text = stringResource(R.string.setting_online_sources_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var juheText by remember { mutableStateOf(settings.juheKey) }
                    LaunchedEffect(settings.juheKey) { juheText = settings.juheKey }
                    OutlinedTextField(
                        value = juheText,
                        onValueChange = {
                            juheText = it
                            viewModel.setJuheKey(it)
                        },
                        label = { Text(stringResource(R.string.setting_juhe_key)) },
                        placeholder = { Text(stringResource(R.string.setting_juhe_key_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    var aliyunCodeText by remember { mutableStateOf(settings.aliyunMarkAppcode) }
                    LaunchedEffect(settings.aliyunMarkAppcode) { aliyunCodeText = settings.aliyunMarkAppcode }
                    OutlinedTextField(
                        value = aliyunCodeText,
                        onValueChange = {
                            aliyunCodeText = it
                            viewModel.setAliyunMarkAppcode(it)
                        },
                        label = { Text(stringResource(R.string.setting_aliyun_appcode)) },
                        placeholder = { Text(stringResource(R.string.setting_aliyun_appcode_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    var aliyunUrlText by remember { mutableStateOf(settings.aliyunMarkUrl) }
                    LaunchedEffect(settings.aliyunMarkUrl) { aliyunUrlText = settings.aliyunMarkUrl }
                    OutlinedTextField(
                        value = aliyunUrlText,
                        onValueChange = {
                            aliyunUrlText = it
                            viewModel.setAliyunMarkUrl(it)
                        },
                        label = { Text(stringResource(R.string.setting_aliyun_url)) },
                        placeholder = { Text(stringResource(R.string.setting_aliyun_url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (settings.enableJobHuntMode) {
                AppCard {
                    Text(
                        text = stringResource(R.string.job_hunt_mode_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 黑白名单入口
            NavRow(
                title = stringResource(R.string.blocklist_title),
                subtitle = stringResource(
                    R.string.blocklist_summary,
                    uiState.blacklist.size,
                    uiState.whitelist.size
                ),
                onClick = onNavigateToBlocklist
            )

            // 最近来电入口
            NavRow(
                title = stringResource(R.string.recent_calls_title),
                subtitle = stringResource(R.string.recent_calls_summary),
                onClick = onNavigateToRecent
            )

            // 首次使用授权引导入口
            NavRow(
                title = stringResource(R.string.settings_setup_guide),
                subtitle = stringResource(R.string.settings_setup_guide_desc),
                onClick = onNavigateToSetupGuide
            )

            // 本地标记缓存管理
            NavRow(
                title = stringResource(R.string.clear_cache_title),
                subtitle = stringResource(R.string.clear_cache_desc, cacheCount),
                onClick = { showClearCacheDialog = true }
            )

            // 备份与恢复（黑白名单规则 + 关键设置，JSON 文件）
            AppCard {
                Text(
                    text = stringResource(R.string.backup_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavRow(
                    title = stringResource(R.string.backup_export),
                    subtitle = stringResource(R.string.backup_export_desc),
                    onClick = { exportLauncher.launch("refuse-phone-backup.json") }
                )
                NavRow(
                    title = stringResource(R.string.backup_import),
                    subtitle = stringResource(R.string.backup_import_desc),
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                )
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
    AppCard {
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

