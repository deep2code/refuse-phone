package com.example.phonequery.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.phonequery.ui.theme.AppCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.phonequery.R
import com.example.phonequery.db.BlocklistEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(viewModel: SettingsViewModel, onBack: () -> Unit = { }) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var areaCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocklist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_home)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 容量卡片：本地无限，明确对比主流 App 的 50 条上限
            CapacityCard(
                blackCount = uiState.blacklist.size,
                whiteCount = uiState.whitelist.size,
                isBlackTab = selectedTab == 0
            )

            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.tab_blacklist))
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(R.string.tab_whitelist))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val list = if (selectedTab == 0) uiState.blacklist else uiState.whitelist

            // 黑名单页：快捷批量屏蔽（应对营销公司换号）
            if (selectedTab == 0) {
                QuickBlockCard(
                    onQuickVirtual = { viewModel.quickBlockVirtualOperators() },
                    areaCode = areaCode,
                    onAreaCodeChange = { areaCode = it },
                    onBlockArea = {
                        if (areaCode.isNotBlank()) {
                            viewModel.addAreaCodeBlock(areaCode)
                            areaCode = ""
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { entity ->
                    BlocklistItem(
                        entity = entity,
                        onDelete = { viewModel.delete(entity) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBlocklistDialog(
            isBlacklist = selectedTab == 0,
            onDismiss = { showAddDialog = false },
            onConfirm = { number, note, isPrefix ->
                if (isPrefix) {
                    val label = if (note.isNotBlank()) note else "号段/区号 $number"
                    if (selectedTab == 0) {
                        viewModel.addBlockPrefix(number, label, isBlock = true)
                    } else {
                        viewModel.addBlockPrefix(number, label, isBlock = false)
                    }
                } else {
                    if (selectedTab == 0) {
                        viewModel.addBlockNumber(number, note)
                    } else {
                        viewModel.addWhitelistNumber(number, note)
                    }
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun CapacityCard(blackCount: Int, whiteCount: Int, isBlackTab: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isBlackTab) {
                    stringResource(R.string.blacklist_capacity, blackCount)
                } else {
                    stringResource(R.string.whitelist_capacity, whiteCount)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.capacity_no_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun QuickBlockCard(
    onQuickVirtual: () -> Unit,
    areaCode: String,
    onAreaCodeChange: (String) -> Unit,
    onBlockArea: () -> Unit
) {
    AppCard {
            Text(
                text = stringResource(R.string.quick_block_title),
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = onQuickVirtual,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_quick_block_virtual))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = areaCode,
                    onValueChange = onAreaCodeChange,
                    label = { Text(stringResource(R.string.hint_area_code)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onBlockArea, enabled = areaCode.isNotBlank()) {
                    Text(stringResource(R.string.btn_block_area))
                }
            }

            Text(
                text = stringResource(R.string.prefix_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}

@Composable
private fun BlocklistItem(
    entity: BlocklistEntity,
    onDelete: () -> Unit
) {
    val isPrefix = entity.type == BlocklistEntity.TYPE_PREFIX
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entity.number,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                if (isPrefix) stringResource(R.string.type_prefix)
                                else stringResource(R.string.type_exact)
                            )
                        }
                    )
                }
                if (entity.label.isNotBlank()) {
                    Text(
                        text = entity.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (entity.note.isNotBlank()) {
                    Text(
                        text = entity.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isPrefix) {
                    Text(
                        text = stringResource(R.string.prefix_match_hint, entity.number),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.btn_delete)
                )
            }
        }
    }
}

@Composable
private fun AddBlocklistDialog(
    isBlacklist: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isPrefix by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isBlacklist) stringResource(R.string.add_blacklist)
                else stringResource(R.string.add_whitelist)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.hint_input_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.hint_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = isPrefix,
                        onCheckedChange = { isPrefix = it }
                    )
                    Text(stringResource(R.string.add_prefix_label))
                }
                if (isPrefix) {
                    Text(
                        text = stringResource(R.string.prefix_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(number, note, isPrefix) },
                enabled = number.isNotBlank()
            ) {
                Text(stringResource(R.string.btn_confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
