import Foundation

/// 号码分析器：离线归属地 + 号段提示 + 码号资源 + 本地名单命中
/// 移植自 Android 版 PhoneRepository.query 的离线部分。
final class NumberAnalyzer {
    private let phoneDB = PhoneDataDB.makeShared()

    func analyze(_ raw: String) -> PhoneInfo {
        let cleaned = E164.clean(raw)
        let digits = E164.digits(cleaned)
        var info = PhoneInfo(number: cleaned)

        if digits.isEmpty {
            info.errorMessage = "号码不能为空"
            return info
        }

        // 1. 归属地（号段库，前 7 位）
        if E164.isChineseMobile(digits), let att = phoneDB?.lookupAttribution(digits) {
            info.province = att.province
            info.city = att.city
            info.carrier = att.isp
        } else if E164.isLandline(digits) {
            if let (areaCode, _, location) = AreaCode.parseLandline(digits) {
                info.areaCode = areaCode
                info.city = location.city
                info.province = location.province
            }
        }

        // 2. 号段库提示（虚拟运营商 / 高风险号段，仅提示）
        if let hint = SpamPrefix.match(digits) {
            info.spamHint = hint.label
        }

        // 3. 工信部码号资源（95/96/106/400/800 使用单位）
        if let entry = CodeNumberDatabase.shared.lookup(digits) {
            info.codeNumberInfo = CodeNumberDatabase.shared.display(entry)
        }

        // 4. 本地名单命中（黑名单屏蔽 / 白名单放行）
        let entries = SharedStore.loadEntries()
        let isBlocked = entries.contains { $0.isBlock && digits.hasPrefix($0.digits) && !$0.digits.isEmpty }
        let isWhitelisted = entries.contains { !$0.isBlock && digits.hasPrefix($0.digits) && !$0.digits.isEmpty }
        info.isBlocked = isBlocked
        info.isWhitelisted = isWhitelisted
        if isBlocked { info.ruleHit = "命中黑名单，来电将被屏蔽" }

        return info
    }
}
