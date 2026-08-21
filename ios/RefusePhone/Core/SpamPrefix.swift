import Foundation

/// 内置骚扰号段库（移植自 Android 版 SpamPrefixDatabase）
enum SpamPrefix {
    struct Hint {
        enum Level { case virtualOperator, highRisk }
        let level: Level
        let label: String
    }

    /// 虚拟运营商 / 物联网卡 / 卫星电话号段（提示不硬拦）
    static let virtualOperatorPrefixes = [
        "170", "171", "162", "165", "167", "174", "172",
        "132", "134", "140", "145", "147"
    ]

    /// 公认高频营销/骚扰号段（提示不硬拦）
    static let highRiskPrefixes = ["95", "96", "400", "800", "106"]

    /// 判断号码是否命中已知骚扰号段前缀
    static func match(_ raw: String) -> Hint? {
        let digits = E164.digits(E164.clean(raw))
        for p in virtualOperatorPrefixes where digits.hasPrefix(p) {
            return Hint(level: .virtualOperator, label: "虚拟运营商/高风险号段 \(p)")
        }
        for p in highRiskPrefixes where digits.hasPrefix(p) {
            return Hint(level: .highRisk, label: "高风险营销号段 \(p)")
        }
        return nil
    }
}
