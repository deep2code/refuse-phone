package com.example.phonequery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phonequery.data.AreaCodeHelper
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
    private val areaCodeHelper = AreaCodeHelper(application.applicationContext)

    private val _uiState = MutableStateFlow(PhoneQueryUiState())
    val uiState: StateFlow<PhoneQueryUiState> = _uiState

    /**
     * 自动识别号码类型（查询自动适应）：
     * - 国内固话以 0 开头，完整格式 = 0 + 长途区号 + 本地 7/8 位座机号
     * - 手机以 1 开头且 11 位
     */
    private fun detectType(raw: String): InputType? {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.startsWith("0") -> InputType.LANDLINE
            digits.startsWith("1") && digits.length == 11 -> InputType.MOBILE
            else -> null
        }
    }

    fun onNumberChange(raw: String) {
        val digits = raw.filter { it.isDigit() || it == '+' }
        // 自动适应：根据输入自动推断固话 / 手机，并实时解析固话编码规律
        val detected = detectType(digits)
        val (breakdown, validation) = if (digits.startsWith("0") && digits.length >= 10) {
            computeLandline(digits)
        } else (null to null)
        _uiState.value = _uiState.value.copy(
            number = digits,
            inputType = detected ?: _uiState.value.inputType,
            landlineBreakdown = breakdown,
            landlineValidation = validation,
            result = null,
            similarEnterprises = emptyList(),
            landlineLocation = null,
            enterpriseError = null
        )
    }

    fun onInputTypeChange(type: InputType) {
        if (type == _uiState.value.inputType) return
        _uiState.value = _uiState.value.copy(inputType = type)
    }

    /**
     * 解析固话编码规律：0 + 长途区号 + 本地 7/8 位座机号
     * @return 解析结果（区号/本地号/归属地）与本地号位数校验提示
     */
    private fun computeLandline(digits: String): Pair<LandlineLocation?, String?> {
        val loc = areaCodeHelper.parseLandline(digits)
        return if (loc != null) {
            val localLen = loc.localNumber.length
            val validation = if (localLen !in 7..8) {
                "本地座机号应为 7 或 8 位（当前 ${localLen} 位）。完整格式：0 + 长途区号(${loc.areaCode}) + 本地 ${localLen} 位"
            } else null
            loc to validation
        } else {
            null to "未能识别长途区号，请确认号码格式：0 + 长途区号 + 本地 7/8 位座机号"
        }
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

            // 固话编码规律适配（与输入实时解析保持一致）
            val (landlineBreakdown, landlineValidation) = if (number.startsWith("0")
                || result.numberType == NumberType.LANDLINE) {
                computeLandline(number)
            } else (null to null)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                result = result,
                isInBlacklist = inBlack,
                isInWhitelist = inWhite,
                isInContacts = inContacts,
                contactsPermissionGranted = contactsGranted,
                userMark = userMark,
                landlineBreakdown = landlineBreakdown,
                landlineValidation = landlineValidation
            )

            // 如果是固话，自动推断相似企业
            if (result.numberType == NumberType.LANDLINE
                || number.startsWith("0")
                || result.numberType == NumberType.UNKNOWN) {
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
    val userMark: String? = null,
    val landlineBreakdown: LandlineLocation? = null,
    val landlineValidation: String? = null
)
