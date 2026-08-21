import Foundation
import SQLite3

/// phonedata.db 离线归属地查询（SQLite，号段前 7 位精确匹配）
/// 移植自 Android 版 PhoneAttributionRepository。
final class PhoneDataDB {
    private var db: OpaquePointer?

    /// 首次使用时把 bundle 里的 phonedata.db 拷贝到 Application Support 再打开（可读可写目录）
    static func makeShared() -> PhoneDataDB? {
        let fm = FileManager.default
        guard let bundled = Bundle.main.path(forResource: "phonedata", ofType: "db") else {
            return nil
        }
        let dir = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        let dest = dir.appendingPathComponent("phonedata.db")
        if !fm.fileExists(atPath: dest.path) {
            try? fm.copyItem(atPath: bundled, toPath: dest.path)
        }
        return PhoneDataDB(path: dest.path)
    }

    init?(path: String) {
        guard sqlite3_open_v2(path, &db, SQLITE_OPEN_READONLY, nil) == SQLITE_OK else {
            db = nil
            return nil
        }
    }

    deinit {
        if let db { sqlite3_close(db) }
    }

    struct Attribution {
        let province: String
        let city: String
        let isp: String
    }

    /// 按手机号前 7 位号段查询（prefix <= 目标的最大记录，与 Android 一致）
    func lookupAttribution(_ digits: String) -> Attribution? {
        guard digits.count >= 7, let prefix = Int(String(digits.prefix(7))) else { return nil }
        guard let db else { return nil }

        var stmt: OpaquePointer?
        let sql = "SELECT province, city, isp FROM segments WHERE prefix <= ? ORDER BY prefix DESC LIMIT 1"
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_int64(stmt, 1, Int64(prefix))
        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }

        func col(_ i: Int32) -> String? {
            guard let c = sqlite3_column_text(stmt, i) else { return nil }
            let s = String(cString: c)
            return s.isEmpty ? nil : s
        }
        guard let province = col(0), let city = col(1) else { return nil }
        return Attribution(province: province, city: city, isp: col(2) ?? "")
    }
}
