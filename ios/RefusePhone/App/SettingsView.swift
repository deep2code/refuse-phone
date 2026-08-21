import SwiftUI
import CallKit

struct SettingsView: View {
    @State private var reloadState = ""

    var body: some View {
        NavigationView {
            Form {
                Section("来电识别扩展") {
                    HStack {
                        Text("状态")
                        Spacer()
                        Text(reloadState.isEmpty ? "未检查" : reloadState)
                            .foregroundColor(.secondary)
                    }
                    Button("刷新拦截名单") {
                        reloadExtension()
                    }
                    Text("修改黑白名单后，需要点此刷新（或在 系统设置 → 电话 → 来电阻止与身份识别 中启用本应用）")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Section("关于") {
                    HStack {
                        Text("版本")
                        Spacer()
                        Text(appVersion()).foregroundColor(.secondary)
                    }
                    HStack {
                        Text("数据")
                        Spacer()
                        Text("内置 phonedata.db · 区号库 · 码号资源表")
                            .foregroundColor(.secondary)
                    }
                    Text("纯离线识别，号码不上传任何服务器。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("设置")
        }
    }

    private func reloadExtension() {
        let identifier = Bundle.main.bundleIdentifier.map { $0 + ".CallDirectory" }
            ?? "com.example.refusephone.CallDirectory"
        let manager = CXCallDirectoryManager.sharedInstance
        manager.reloadExtension(withIdentifier: identifier) { error in
            DispatchQueue.main.async {
                reloadState = error == nil ? "刷新成功 ✅" : "刷新失败：\(error?.localizedDescription ?? "未知错误")"
            }
        }
    }

    private func appVersion() -> String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }
}
