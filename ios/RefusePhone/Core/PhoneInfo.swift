import Foundation

/// 号码分析结果（移植自 Android 版 PhoneInfo / SourceResult）
struct PhoneInfo {
    var number: String = ""
    var province: String?
    var city: String?
    var carrier: String?
    var areaCode: String?
    var zipCode: String?
    /// 骚扰提示（号段库/码号表命中）
    var spamHint: String?
    /// 码号资源信息（95/96/106/400/800 使用单位）
    var codeNumberInfo: String?
    /// 命中拦截规则
    var ruleHit: String?
    var isBlocked: Bool = false
    var isWhitelisted: Bool = false
    /// 最近一次在线查询错误信息（保留字段）
    var errorMessage: String?

    var summary: String {
        var parts: [String] = []
        if let p = province, !p.isEmpty { parts.append(p) }
        if let c = city, !c.isEmpty { parts.append(c) }
        if let ca = carrier, !ca.isEmpty { parts.append(ca) }
        return parts.joined(separator: " ")
    }
}
