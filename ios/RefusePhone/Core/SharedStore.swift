import Foundation

/// App 与 Call Directory 扩展共享的数据（App Group UserDefaults）
enum SharedStore {
    static let appGroupID = "group.refusephone"
    static let entriesKey = "blocklist_entries_v1"
    static let spamNumbersKey = "spam_numbers_v1"

    /// 名单条目：isBlock=true 为黑名单（静默屏蔽），false 为白名单（仅记录）
    struct Entry: Codable, Identifiable, Equatable {
        var id = UUID()
        var number: String      // 用户输入的原始号码（含 + / 空格）
        var label: String = ""
        var isBlock: Bool = true
        var createdAt: Date = Date()

        var digits: String { E164.digits(E164.clean(number)) }

        enum CodingKeys: String, CodingKey {
            case number, label, isBlock, createdAt
        }
    }

    static var defaults: UserDefaults? {
        UserDefaults(suiteName: appGroupID)
    }

    static func loadEntries() -> [Entry] {
        guard let d = defaults?.data(forKey: entriesKey) else { return [] }
        return (try? JSONDecoder().decode([Entry].self, from: d)) ?? []
    }

    static func saveEntries(_ entries: [Entry]) {
        if let data = try? JSONEncoder().encode(entries) {
            defaults?.set(data, forKey: entriesKey)
        }
    }

    /// 被标记为骚扰的号码（供扩展标识"骚扰电话"，不屏蔽）
    static func loadSpamNumbers() -> [String] {
        defaults?.stringArray(forKey: spamNumbersKey) ?? []
    }

    static func saveSpamNumbers(_ numbers: [String]) {
        defaults?.set(numbers, forKey: spamNumbersKey)
    }
}
