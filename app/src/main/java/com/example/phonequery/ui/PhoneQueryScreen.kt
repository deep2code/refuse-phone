package com.example.phonequery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.phonequery.R
import com.example.phonequery.model.EnterpriseInfo
import com.example.phonequery.model.LandlineLocation
import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PhoneInfo
import com.example.phonequery.model.ResultSource
import com.example.phonequery.ui.theme.AppCard
import com.example.phonequery.ui.theme.InfoRow
import com.example.phonequery.ui.theme.SectionTitle
import com.example.phonequery.ui.theme.StatusRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneQueryScreen(viewModel: PhoneQueryViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
            SegmentedPhoneInput(
                inputType = uiState.inputType,
                segments = uiState.segments,
                onInputTypeChange = viewModel::onInputTypeChange,
                onSegmentChange = viewModel::onSegmentChange,
                onQuery = viewModel::query,
                enabled = !uiState.isLoading
            )

            Button(
                onClick = viewModel::query,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && uiState.number.isNotBlank()
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_query))
            }

            if (uiState.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.blacklistMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            uiState.result?.let { result ->
                ResultCard(
                    result = result,
                    isInBlacklist = uiState.isInBlacklist,
                    isInWhitelist = uiState.isInWhitelist,
                    isInContacts = uiState.isInContacts,
                    contactsPermissionGranted = uiState.contactsPermissionGranted,
                    userMark = uiState.userMark,
                    onAddToBlacklist = viewModel::addToBlacklist,
                    onMark = viewModel::markNumber,
                    onClearMark = viewModel::clearUserMark
                )
            }

            if (uiState.isEnterpriseLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.enterpriseError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            uiState.landlineLocation?.let { location ->
                LandlineEnterpriseCard(
                    location = location,
                    enterprises = uiState.similarEnterprises
                )
            }
        }
    }
}

@Composable
private fun SegmentedPhoneInput(
    inputType: InputType,
    segments: List<String>,
    onInputTypeChange: (InputType) -> Unit,
    onSegmentChange: (Int, String) -> Unit,
    onQuery: () -> Unit,
    enabled: Boolean
) {
    val lens = if (inputType == InputType.MOBILE) listOf(3, 4, 4) else listOf(3, 8)
    val focusRequesters = remember(inputType) { List(lens.size) { FocusRequester() } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = inputType == InputType.MOBILE,
                onClick = { onInputTypeChange(InputType.MOBILE) },
                label = { Text(stringResource(R.string.type_mobile)) }
            )
            FilterChip(
                selected = inputType == InputType.LANDLINE,
                onClick = { onInputTypeChange(InputType.LANDLINE) },
                label = { Text(stringResource(R.string.type_landline)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lens.forEachIndexed { index, len ->
                OutlinedTextField(
                    value = segments.getOrElse(index) { "" },
                    onValueChange = { raw ->
                        onSegmentChange(index, raw)
                        val digits = raw.filter { it.isDigit() }
                        if (index < lens.size - 1) {
                            if (digits.length >= len) focusRequesters[index + 1].requestFocus()
                        } else if (digits.length >= len) {
                            onQuery()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequesters[index]),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (index == lens.size - 1) ImeAction.Search else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            if (index < lens.size - 1) focusRequesters[index + 1].requestFocus()
                        },
                        onSearch = { onQuery() },
                        onDone = { onQuery() }
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                    enabled = enabled
                )
            }
        }

        Text(
            text = stringResource(R.string.seg_input_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(
    result: PhoneInfo,
    isInBlacklist: Boolean,
    isInWhitelist: Boolean,
    isInContacts: Boolean,
    contactsPermissionGranted: Boolean,
    userMark: String?,
    onAddToBlacklist: () -> Unit,
    onMark: (String) -> Unit,
    onClearMark: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                    text = stringResource(R.string.result_title),
                    style = MaterialTheme.typography.titleLarge
                )
                SuggestionChip(
                    onClick = { },
                    label = {
                        Text(
                            when (result.source) {
                                ResultSource.OFFLINE -> stringResource(R.string.offline_tag)
                                ResultSource.ONLINE -> stringResource(R.string.online_tag)
                                ResultSource.CACHED -> stringResource(R.string.cached_tag)
                            }
                        )
                    }
                )
            }

            if (result.errorMessage != null) {
                Text(
                    text = result.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            InfoRow(label = stringResource(R.string.label_phone), value = result.number)
            InfoRow(
                label = stringResource(R.string.label_number_type),
                value = result.numberType.displayName
            )
            InfoRow(label = stringResource(R.string.label_province), value = result.province)
            InfoRow(label = stringResource(R.string.label_city), value = result.city)
            InfoRow(label = stringResource(R.string.label_carrier), value = result.carrier)
            InfoRow(label = stringResource(R.string.label_area_code), value = result.areaCode)
            InfoRow(label = stringResource(R.string.label_zip_code), value = result.zipCode)
            if (!result.codeNumberInfo.isNullOrBlank()) {
                InfoRow(label = stringResource(R.string.label_code_number), value = result.codeNumberInfo)
            }

            // —— 号码状态 ——
            SectionTitle(stringResource(R.string.status_title))
            val (blText, blColor) = if (isInBlacklist) {
                stringResource(R.string.status_yes) to MaterialTheme.colorScheme.error
            } else {
                stringResource(R.string.status_no) to MaterialTheme.colorScheme.onSurface
            }
            StatusRow(stringResource(R.string.label_blacklist_status), blText, blColor)

            val (wlText, wlColor) = if (isInWhitelist) {
                stringResource(R.string.status_yes) to MaterialTheme.colorScheme.primary
            } else {
                stringResource(R.string.status_no) to MaterialTheme.colorScheme.onSurface
            }
            StatusRow(stringResource(R.string.label_whitelist_status), wlText, wlColor)

            val (ctText, ctColor) = when {
                !contactsPermissionGranted -> stringResource(R.string.contacts_not_authorized) to MaterialTheme.colorScheme.onSurfaceVariant
                isInContacts -> stringResource(R.string.status_yes) to MaterialTheme.colorScheme.primary
                else -> stringResource(R.string.status_no) to MaterialTheme.colorScheme.onSurface
            }
            StatusRow(stringResource(R.string.label_contacts_status), ctText, ctColor)

            val onlineMark = buildString {
                if (!result.spamType.isNullOrBlank()) append(result.spamType)
                if (result.platformMarks.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(result.platformMarks.joinToString(" / ") { "${it.platform}: ${it.mark}" })
                }
            }
            val (omText, omColor) = if (onlineMark.isBlank()) {
                stringResource(R.string.status_no) to MaterialTheme.colorScheme.onSurface
            } else {
                onlineMark to MaterialTheme.colorScheme.error
            }
            StatusRow(stringResource(R.string.label_online_mark_status), omText, omColor)

            if (!result.spamType.isNullOrBlank()) {
                InfoRow(label = stringResource(R.string.label_spam_type), value = result.spamType)
            }
            if (!result.spamCount.isNullOrBlank()) {
                InfoRow(label = stringResource(R.string.label_spam_count), value = result.spamCount)
            }

            if (result.platformMarks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.label_spam_platforms),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    result.platformMarks.forEach { mark ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text("${mark.platform}: ${mark.mark}") }
                        )
                    }
                }
            }

            // —— 我的标记（主动标记）——
            SectionTitle(stringResource(R.string.my_mark_title))
            Text(
                text = if (userMark.isNullOrBlank()) {
                    stringResource(R.string.my_mark_none)
                } else {
                    "${stringResource(R.string.my_mark_current)}：$userMark"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (userMark.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else MaterialTheme.colorScheme.primary
            )
            MarkChips(current = userMark, onMark = onMark)
            if (!userMark.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onClearMark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_clear_my_mark))
                }
            }

            Button(
                onClick = onAddToBlacklist,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(R.string.btn_add_to_blacklist))
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MarkChips(current: String?, onMark: (String) -> Unit) {
    val options = listOf(
        R.string.mark_spam to "骚扰",
        R.string.mark_scam to "诈骗",
        R.string.mark_ad to "广告营销",
        R.string.mark_normal to "正常",
        R.string.mark_other to "其他"
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (resId, value) ->
            FilterChip(
                selected = current == value,
                onClick = { onMark(value) },
                label = { Text(stringResource(resId)) }
            )
        }
    }
}

@Composable
private fun LandlineEnterpriseCard(
    location: LandlineLocation,
    enterprises: List<EnterpriseInfo>
) {
    AppCard {
            Text(
                text = stringResource(R.string.enterprise_title),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(
                    R.string.enterprise_location_hint,
                    location.areaCode,
                    location.city,
                    location.province ?: ""
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (enterprises.isEmpty()) {
                Text(
                    text = stringResource(R.string.enterprise_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                enterprises.forEach { enterprise ->
                    EnterpriseItem(enterprise)
                    if (enterprise !== enterprises.last()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
    }
}

@Composable
private fun EnterpriseItem(enterprise: EnterpriseInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = enterprise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            EnterpriseInfoRow(stringResource(R.string.label_industry), enterprise.industry)
            EnterpriseInfoRow(stringResource(R.string.label_legal_person), enterprise.legalPerson)
            EnterpriseInfoRow(stringResource(R.string.label_reg_status), enterprise.status)
            EnterpriseInfoRow(stringResource(R.string.label_reg_capital), enterprise.regCapital)
            EnterpriseInfoRow(stringResource(R.string.label_establish_date), enterprise.establishDate)
            EnterpriseInfoRow(stringResource(R.string.label_address), enterprise.address)
            EnterpriseInfoRow(stringResource(R.string.label_enterprise_phone), enterprise.phone)
            Text(
                text = stringResource(R.string.label_data_source, enterprise.source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EnterpriseInfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
