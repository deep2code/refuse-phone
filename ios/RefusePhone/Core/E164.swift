import Foundation

/// 号码规范化（移植自 Android 版 E164Normalizer，去除 md5/通讯录部分）
enum E164 {
    /// 清理号码中的空白/括号/连字符，保留 + 号
    static func clean(_ raw: String) -> String {
        raw.replacingOccurrences(of: "＋", with: "+")
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: "(", with: "")
            .replacingOccurrences(of: ")", with: "")
    }

    /// 提取纯数字
    static func digits(_ raw: String) -> String {
        raw.filter { $0.isNumber }
    }

    /// 判断是否为 11 位中国手机号（1 开头）
    static func isChineseMobile(_ digits: String) -> Bool {
        digits.count == 11 && digits.hasPrefix("1")
    }

    /// 判断是否为固话（0 开头）
    static func isLandline(_ digits: String) -> Bool {
        digits.hasPrefix("0")
    }

    /// 转为国际格式数字（Call Directory 用）：本地号补 86 前缀，去 +
    static func internationalDigits(_ raw: String) -> String {
        var d = digits(clean(raw))
        if d.hasPrefix("+") { d.removeFirst() } // clean 已保留 +，digits 会滤掉，这里兜底
        if d.hasPrefix("86") && d.count > 11 { return d }
        if d.count >= 7 && !d.hasPrefix("86") { return "86" + d }
        return d
    }
}
