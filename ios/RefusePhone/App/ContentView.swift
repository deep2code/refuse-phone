import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            NumberQueryView()
                .tabItem { Label("查询", systemImage: "magnifyingglass") }
            BlocklistView()
                .tabItem { Label("黑白名单", systemImage: "list.bullet") }
            SettingsView()
                .tabItem { Label("设置", systemImage: "gearshape") }
        }
    }
}
