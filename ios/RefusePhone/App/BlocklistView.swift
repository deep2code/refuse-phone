import SwiftUI

struct BlocklistView: View {
    @State private var entries: [SharedStore.Entry] = []
    @State private var spamNumbers: [String] = []
    @State private var showAdd = false
    @State private var newNumber = ""
    @State private var newIsBlock = true

    var body: some View {
        NavigationView {
            List {
                Section("黑名单（来电静默屏蔽）") {
                    ForEach(entries.filter(\.isBlock)) { e in
                        row(e)
                    }
                    .onDelete { idx in
                        let items = entries.filter(\.isBlock)
                        delete(items, at: idx)
                    }
                }
                Section("白名单（仅记录不屏蔽）") {
                    ForEach(entries.filter { !$0.isBlock }) { e in
                        row(e)
                    }
                }
                Section("已标记骚扰（来电显示标识）") {
                    ForEach(spamNumbers, id: \.self) { n in
                        HStack {
                            Text(n).font(.system(.body, design: .monospaced))
                            Spacer()
                            Text("骚扰电话").foregroundColor(.orange)
                        }
                    }
                    .onDelete { idx in
                        spamNumbers.remove(atOffsets: idx)
                        SharedStore.saveSpamNumbers(spamNumbers)
                    }
                }
            }
            .navigationTitle("黑白名单")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        newNumber = ""
                        showAdd = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showAdd) {
                addSheet
            }
            .onAppear { reload() }
        }
    }

    private func row(_ e: SharedStore.Entry) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(e.number)
                .font(.system(.body, design: .monospaced))
            if !e.label.isEmpty {
                Text(e.label).font(.footnote).foregroundColor(.secondary)
            }
        }
    }

    private var addSheet: some View {
        NavigationView {
            Form {
                Section("号码") {
                    TextField("输入号码（手机号/固话）", text: $newNumber)
                        .keyboardType(.phonePad)
                }
                Section("类型") {
                    Picker("类型", selection: $newIsBlock) {
                        Text("黑名单（屏蔽）").tag(true)
                        Text("白名单（放行）").tag(false)
                    }
                    .pickerStyle(.segmented)
                }
            }
            .navigationTitle("添加号码")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showAdd = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let digits = E164.digits(E164.clean(newNumber))
                        if !digits.isEmpty {
                            var list = SharedStore.loadEntries()
                            list.removeAll { $0.digits == digits }
                            list.insert(SharedStore.Entry(number: newNumber, label: "手动添加", isBlock: newIsBlock), at: 0)
                            SharedStore.saveEntries(list)
                            reload()
                            showAdd = false
                        }
                    }
                }
            }
        }
    }

    private func delete(_ items: [SharedStore.Entry], at offsets: IndexSet) {
        var list = SharedStore.loadEntries()
        for i in offsets {
            let target = items[i]
            list.removeAll { $0.id == target.id }
        }
        SharedStore.saveEntries(list)
        reload()
    }

    private func reload() {
        entries = SharedStore.loadEntries()
        spamNumbers = SharedStore.loadSpamNumbers()
    }
}
