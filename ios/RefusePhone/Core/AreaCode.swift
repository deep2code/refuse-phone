import Foundation

/// 固话区号库（area_code.json 移植）
struct AreaCode {
    let code: String
    let city: String
    let pinyin: String
    let province: String?

    /// 解析固话：0 + 区号 + 本地号（移植自 Android AreaCodeHelper.parseLandline）
    static func parseLandline(_ digits: String) -> (areaCode: String, localNumber: String, location: AreaCode?)? {
        guard digits.hasPrefix("0"), digits.count >= 8 else { return nil }
        let db = AreaCodeDatabase.shared
        // 尝试 3 位区号（010/021/022/023）再 4 位区号
        if digits.count >= 10 {
            let c3 = String(digits.prefix(3))
            if let loc = db.area(byCode: c3) {
                return (c3, String(digits.dropFirst(3)), loc)
            }
        }
        if digits.count >= 11 {
            let c4 = String(digits.prefix(4))
            if let loc = db.area(byCode: c4) {
                return (c4, String(digits.dropFirst(4)), loc)
            }
        }
        return nil
    }
}

/// area_code.json 加载器（一次性解析）
final class AreaCodeDatabase {
    static let shared = AreaCodeDatabase()
    private var byCode: [String: AreaCode] = [:]

    private init() {
        guard let url = Bundle.main.url(forResource: "area_code", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return }
        for item in arr {
            guard let code = item["code"] as? String else { continue }
            let a = AreaCode(
                code: code,
                city: item["city"] as? String ?? "",
                pinyin: item["pinyin"] as? String ?? "",
                province: item["province"] as? String
            )
            byCode[code] = a
        }
    }

    func area(byCode code: String) -> AreaCode? { byCode[code] }
}
