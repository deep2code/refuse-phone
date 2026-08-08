package com.example.phonequery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phonequery.data.BlocklistRepository
import com.example.phonequery.data.EnterpriseRepository
import com.example.phonequery.data.PhoneRepository
import com.example.phonequery.model.EnterpriseInfo
import com.example.phonequery.model.LandlineLocation
import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PhoneInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PhoneQueryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhoneRepository(application.applicationContext)
    private val enterpriseRepository = EnterpriseRepository(application.applicationContext)
    private val blocklistRepository = BlocklistRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(PhoneQueryUiState())
    val uiState: StateFlow<PhoneQueryUiState> = _uiState

    fun onNumberChange(number: String) {
        _uiState.value = _uiState.value.copy(number = number)
    }

    fun query() {
        val number = _uiState.value.number.trim()
        if (number.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                result = PhoneInfo(errorMessage = "请输入号码"),
                isLoading = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            result = null,
            similarEnterprises = emptyList(),
            landlineLocation = null,
            enterpriseError = null
        )

        viewModelScope.launch {
            val result = repository.query(number)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                result = result
            )

            // 如果是固话，自动推断相似企业
            if (result.numberType == NumberType.LANDLINE || result.numberType == NumberType.UNKNOWN) {
                querySimilarEnterprises(number)
            }
        }
    }

    private fun querySimilarEnterprises(number: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isEnterpriseLoading = true)
            try {
                val (location, enterprises) = enterpriseRepository.querySimilarEnterprises(number)
                _uiState.value = _uiState.value.copy(
                    landlineLocation = location,
                    similarEnterprises = enterprises,
                    isEnterpriseLoading = false,
                    enterpriseError = if (enterprises.isEmpty() && location != null) {
                        "未找到该号码对应的企业信息"
                    } else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isEnterpriseLoading = false,
                    enterpriseError = "企业查询失败：${e.message}"
                )
            }
        }
    }

    fun addToBlacklist(note: String = "") {
        val number = _uiState.value.number
        if (number.isBlank()) return
        viewModelScope.launch {
            blocklistRepository.add(number, note, isBlock = true)
            _uiState.value = _uiState.value.copy(
                blacklistMessage = "已将 $number 加入黑名单，下次来电将直接挂断"
            )
            kotlinx.coroutines.delay(2000)
            _uiState.value = _uiState.value.copy(blacklistMessage = null)
        }
    }
}

data class PhoneQueryUiState(
    val number: String = "",
    val isLoading: Boolean = false,
    val result: PhoneInfo? = null,
    val isEnterpriseLoading: Boolean = false,
    val landlineLocation: LandlineLocation? = null,
    val similarEnterprises: List<EnterpriseInfo> = emptyList(),
    val enterpriseError: String? = null,
    val blacklistMessage: String? = null
)
