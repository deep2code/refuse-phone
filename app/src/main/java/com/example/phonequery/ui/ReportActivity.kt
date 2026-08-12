package com.example.phonequery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.phonequery.R
import com.example.phonequery.data.BlocklistRepository
import com.example.phonequery.data.MarkCacheRepository
import com.example.phonequery.ui.theme.PhoneQueryTheme
import kotlinx.coroutines.launch

/**
 * 来电举报对话框（独立透明 Activity）。
 * 由悬浮窗「举报」按钮拉起，用户选择骚扰类型并可一键加入黑名单；
 * 标记写入本地 USERMARK 缓存，越用越准，且完全离线。
 */
class ReportActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NUMBER = "extra_number"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明窗口，仅显示对话框
        window.setBackgroundDrawableResource(android.R.color.transparent)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val number = intent.getStringExtra(EXTRA_NUMBER) ?: ""
        val blocklistRepository = BlocklistRepository(this)
        val markCacheRepository = MarkCacheRepository(this)

        setContent {
            PhoneQueryTheme {
                var selected by remember { mutableStateOf(0) }
                var addToBlock by remember { mutableStateOf(true) }

                val labelRes = listOf(
                    R.string.mark_spam,
                    R.string.mark_scam,
                    R.string.mark_ad,
                    R.string.mark_other
                )
                val values = listOf("骚扰", "诈骗", "广告营销", "其他")

                AlertDialog(
                    onDismissRequest = { finish() },
                    properties = DialogProperties(decorFitsSystemWindows = false),
                    title = { Text(stringResource(R.string.report_title)) },
                    text = {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.report_number, number),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            labelRes.forEachIndexed { i, res ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(selected = selected == i, onClick = { selected = i })
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selected == i, onClick = { selected = i })
                                    Text(stringResource(res))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = addToBlock, onCheckedChange = { addToBlock = it })
                                Text(stringResource(R.string.report_add_block))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val spamType = values[selected]
                            lifecycleScope.launch {
                                markCacheRepository.markNumber(number, spamType)
                                if (addToBlock) {
                                    blocklistRepository.add(number, spamType, isBlock = true)
                                }
                                finish()
                            }
                        }) { Text(stringResource(R.string.btn_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}
