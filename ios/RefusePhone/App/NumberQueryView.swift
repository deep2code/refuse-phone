import SwiftUI

struct NumberQueryView: View {
    @State private var input = ""
    @State private var info: PhoneInfo?
    @State private var analyzed = false
    @State private var showMarkSheet = false

    private let analyzer = NumberAnalyzer()

    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                HStack {
                    TextField("输入手机号 / 固话", text: $input)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.phonePad)
                        .autocorrectionDisabled()
                    Button("查询") {
                        info = analyzer.analyze(input)
                        analyzed = true
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(.horizontal)

                if analyzed, let info {
                    resultCard(info)
                } else if analyzed {
                    Text("未识别到有效号码")
                        .foregroundColor(.secondary)
                } else {
                    Spacer()
                    Text("离线识别：归属地 · 运营商 · 号段提示 · 码号资源\n数据内置，无需联网")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                    Spacer()
                }
            }
            .padding(.vertical)
            .navigationTitle("号码查询")
        }
    }

    @ViewBuilder
    private func resultCard(_ info: PhoneInfo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(info.number)
                    .font(.title3.bold())
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Spacer()
                if info.isWhitelisted {
                    Label("白名单", systemImage: "checkmark.circle.fill")
                        .foregroundColor(.green)
                } else if info.isBlocked {
                    Label("黑名单", systemImage: "nosign")
                        .foregroundColor(.red)
                }
            }

            if !info.summary.isEmpty {
                Label(info.summary, systemImage: "location.fill")
                    .foregroundColor(.primary)
            }
            if let area = info.areaCode {
                Text("区号 \(area)")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            if let hint = info.spamHint {
                Label(hint, systemImage: "exclamationmark.triangle.fill")
                    .foregroundColor(.orange)
            }
            if let code = info.codeNumberInfo {
                Label(code, systemImage: "building.2.fill")
                    .foregroundColor(.teal)
            }
            if let rule = info.ruleHit {
                Label(rule, systemImage: "shield.slash")
                    .foregroundColor(.red)
            }
            if let err = info.errorMessage {
                Text(err).foregroundColor(.secondary)
            }

            HStack(spacing: 12) {
                Button("加入黑名单") {
                    addEntry(isBlock: true)
                }
                .buttonStyle(.bordered)
                .tint(.red)

                Button("标记为骚扰") {
                    addSpam()
                }
                .buttonStyle(.bordered)
                .tint(.orange)

                if info.isBlocked || info.isWhitelisted {
                    Button("移出名单") {
                        removeEntry()
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding(.top, 4)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
        .padding(.horizontal)
    }

    private func addEntry(isBlock: Bool) {
        let digits = E164.digits(E164.clean(input))
        guard !digits.isEmpty else { return }
        var entries = SharedStore.loadEntries()
        entries.removeAll { $0.digits == digits }
        entries.insert(SharedStore.Entry(number: input, label: isBlock ? "手动添加" : "白名单", isBlock: isBlock), at: 0)
        SharedStore.saveEntries(entries)
        info = analyzer.analyze(input)
    }

    private func addSpam() {
        let digits = E164.digits(E164.clean(input))
        guard !digits.isEmpty else { return }
        var list = SharedStore.loadSpamNumbers()
        if !list.contains(digits) { list.insert(digits, at: 0) }
        SharedStore.saveSpamNumbers(list)
    }

    private func removeEntry() {
        let digits = E164.digits(E164.clean(input))
        var entries = SharedStore.loadEntries()
        entries.removeAll { $0.digits == digits }
        SharedStore.saveEntries(entries)
        info = analyzer.analyze(input)
    }
}
