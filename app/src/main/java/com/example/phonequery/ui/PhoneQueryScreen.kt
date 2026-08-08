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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
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
            OutlinedTextField(
                value = uiState.number,
                onValueChange = viewModel::onNumberChange,
                label = { Text(stringResource(R.string.hint_input_phone)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::query,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
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
                    onAddToBlacklist = viewModel::addToBlacklist
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(
    result: PhoneInfo,
    onAddToBlacklist: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun LandlineEnterpriseCard(
    location: LandlineLocation,
    enterprises: List<EnterpriseInfo>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
