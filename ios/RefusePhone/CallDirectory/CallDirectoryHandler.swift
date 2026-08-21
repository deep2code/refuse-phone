import CallKit
import Foundation

/// 来电屏蔽 / 识别扩展（移植自 Android 版系统级拦截思路）
/// - 黑名单号码 → 静默屏蔽（Call Directory 仅支持精确号码匹配）
/// - 已标记骚扰号码 → 来电显示「骚扰电话」
/// - 数据来自 App Group 共享的 UserDefaults，App 内修改后通过
///   CXCallDirectoryManager.reloadExtension 触发本扩展刷新。
///
/// 注意：addBlockingEntry / addIdentificationEntry 的号码必须各自按升序添加。
final class CallDirectoryHandler: CXCallDirectoryProvider {

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        defer { context.completeRequest() }

        // 黑名单号码（去重）
        let blocked: [UInt64] = SharedStore.loadEntries()
            .filter(\.isBlock)
            .flatMap { internationalCandidates($0.number) }
            .reduce(into: [UInt64]()) { acc, n in
                if !acc.contains(n) { acc.append(n) }
            }
            .sorted()

        for n in blocked {
            context.addBlockingEntry(withNextSequentialPhoneNumber: n)
            context.addIdentificationEntry(withNextSequentialPhoneNumber: n, label: "黑名单")
        }

        // 已标记骚扰号码（去重；只标识不屏蔽）
        let spam: [UInt64] = SharedStore.loadSpamNumbers()
            .flatMap { internationalCandidates($0) }
            .reduce(into: [UInt64]()) { acc, n in
                if !acc.contains(n) { acc.append(n) }
            }
            .sorted()

        for n in spam where !blocked.contains(n) {
            context.addIdentificationEntry(withNextSequentialPhoneNumber: n, label: "骚扰电话")
        }
    }

    /// 生成 [86+号码, 本地号码] 候选（Call Directory 按 E.164 无 + 匹配）
    private func internationalCandidates(_ raw: String) -> [UInt64] {
        let digits = E164.digits(E164.clean(raw))
        guard !digits.isEmpty else { return [] }
        var out: [UInt64] = []
        if digits.hasPrefix("86") && digits.count > 11 {
            out.append(UInt64(digits) ?? 0)
            out.append(UInt64(String(digits.dropFirst(2))) ?? 0)
        } else {
            out.append(UInt64("86" + digits) ?? 0)
            out.append(UInt64(digits) ?? 0)
        }
        return out.filter { $0 > 0 }
    }
}
