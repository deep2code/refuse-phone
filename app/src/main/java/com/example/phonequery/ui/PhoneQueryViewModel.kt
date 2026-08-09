package com.example.phonequery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phonequery.data.BlocklistRepository
import com.example.phonequery.data.ContactChecker
import com.example.phonequery.data.EnterpriseRepository
import com.example.phonequery.data.MarkCacheRepository
import com.example.phonequery.data.PhoneRepository
import com.example.phonequery.model.EnterpriseInfo
import com.example.phonequery.model.LandlineLocation
import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PhoneInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class InputType { MOBILE, LANDLINE }

class PhoneQueryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhoneRepository(application.applicationContext)
    private val enterpriseRepository = EnterpriseRepository(application.applicationContext)
    private val blocklistRepository = BlocklistRepository(application.applicationContext)
    private val markCacheRepository = MarkCacheRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(PhoneQueryUiState())
    val uiState: StateFlow<PhoneQueryUiState> = _uiState

    fun onInputTypeChange(type: InputType) {
        if (type == _uiState.value.inputType) return
        val lens = segmentLengths(type)
        _uiState.value = _uiState.value.copy(
            inputType = type,
            segments = List(lens.size) { "" },
            number = ""
        )
    }

    fun onSegmentChange(index: Int, raw: String) {
        val digits = raw.filter { it.isDigit() }
        val lens = segmentLengths(_uiState.value.inputType)
        val segs = _uiState.value.segments.toMutableList()
        // 当前段放置前 lens[index] 位，多余数字向后段顺延（支持整串粘贴）
        segs[index] = digits.take(lens[index])
        var overflow = digits.drop(lens[index])
        var i = index + 1
        while (overflow.isNotEmpty() && i < segs.size) {
            val take = overflow.take(lens[i])
            segs[i] = take
            overflow = overflow.drop(take.length)
            i++
        }
        _uiState.value = _uiState.value.copy(
            segments = segs,
            number = segs.joinToString("")
        )
    }

    private fun segmentLengths(type: InputType): List<Int> = when (type) {
        InputType.MOBILE -> listOf(3, 4, 4)
        InputType.LANDLINE -> listOf(3, 8)
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
            // 计算该号码在黑名单 / 白名单 / 通讯录中的状态，以及用户主动标记
            val digits = result.number.replace(Regex("[^0-9]"), "")
            val inBlack = blocklistRepository.isBlacklisted(digits)
            val inWhite = blocklistRepository.isWhitelisted(digits)
            val contactsGranted = ContactChecker.hasPermission(getApplication())
            val inContacts = if (contactsGranted) {
                ContactChecker.isInContacts(getApplication(), digits)
            } else false
            val userMark = markCacheRepository.getUserMark(digits)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                result = result,
                isInBlacklist = inBlack,
                isInWhitelist = inWhite,
                isInContacts = inContacts,
                contactsPermissionGranted = contactsGranted,
                userMark = userMark
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
                isInBlacklist = true,
                blacklistMessage = "已将 $number 加入黑名单，下次来电将直接挂断"
            )
            kotlinx.coroutines.delay(2000)
            _uiState.value = _uiState.value.copy(blacklistMessage = null)
        }
    }

    /** 主动标记当前号码（我的标记） */
    fun markNumber(spamType: String) {
        val number = _uiState.value.number
        val digits = number.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return
        viewModelScope.launch {
            markCacheRepository.markNumber(digits, spamType)
            _uiState.value = _uiState.value.copy(
                userMark = spamType,
                blacklistMessage = "已标记该号码为：$spamType"
            )
            kotlinx.coroutines.delay(2000)
            _uiState.value = _uiState.value.copy(blacklistMessage = null)
        }
    }

    /** 清除我对当前号码的主动标记 */
    fun clearUserMark() {
        val number = _uiState.value.number
        val digits = number.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return
        viewModelScope.launch {
            markCacheRepository.clearUserMark(digits)
            _uiState.value = _uiState.value.copy(
                userMark = null,
                blacklistMessage = "已清除您的标记"
            )
            kotlinx.coroutines.delay(2000)
            _uiState.value = _uiState.value.copy(blacklistMessage = null)
        }
    }
}

data class PhoneQueryUiState(
    val inputType: InputType = InputType.MOBILE,
    val segments: List<String> = listOf("", "", ""),
    val number: String = "",
    val isLoading: Boolean = false,
    val result: PhoneInfo? = null,
    val isEnterpriseLoading: Boolean = false,
    val landlineLocation: LandlineLocation? = null,
    val similarEnterprises: List<EnterpriseInfo> = emptyList(),
    val enterpriseError: String? = null,
    val blacklistMessage: String? = null,
    val isInBlacklist: Boolean = false,
    val isInWhitelist: Boolean = false,
    val isInContacts: Boolean = false,
    val contactsPermissionGranted: Boolean = false,
    val userMark: String? = null
)
