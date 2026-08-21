import Foundation

/// 工信部码号资源离线表（seed_codenumber.csv 移植：95/96/106/400/800 → 使用单位）
struct CodeNumberEntry {
    let prefix: String
    let type: String
    let owner: String
    let purpose: String?
    let note: String?
}

final class CodeNumberDatabase {
    static let shared = CodeNumberDatabase()
    private var entries: [CodeNumberEntry] = []

    private init() {
        guard let url = Bundle.main.url(forResource: "seed_codenumber", withExtension: "csv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return }
        let lines = text.components(separatedBy: .newlines).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard lines.count > 1 else { return }
        for line in lines.dropFirst() {
            let cols = line.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            guard cols.count >= 3, !cols[0].isEmpty else { continue }
            entries.append(CodeNumberEntry(
                prefix: cols[0],
                type: cols.count > 1 ? cols[1] : "",
                owner: cols.count > 2 ? cols[2] : "",
                purpose: cols.count > 3 && !cols[3].isEmpty ? cols[3] : nil,
                note: cols.count > 5 && !cols[5].isEmpty ? cols[5] : nil
            ))
        }
    }

    /// 按前缀最长匹配：陌生 95/96/106/400/800 号段 → 使用单位
    func lookup(_ digits: String) -> CodeNumberEntry? {
        var best: CodeNumberEntry?
        for e in entries where digits.hasPrefix(e.prefix) {
            if best == nil || e.prefix.count > best!.prefix.count {
                best = e
            }
        }
        return best
    }

    func display(_ e: CodeNumberEntry) -> String {
        var parts = [e.owner]
        if let p = e.purpose, !p.isEmpty { parts.append(p) }
        if let n = e.note, !n.isEmpty { parts.append(n) }
        return parts.joined(separator: "·")
    }
}
