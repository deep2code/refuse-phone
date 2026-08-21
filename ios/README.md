# iOS 版（纯 Swift 重写 · 自用）

Android 版「反骚扰」的 iOS 移植。受 iOS 平台限制，能力为：

- 号码查询：离线归属地 / 运营商 / 号段提示 / 码号资源（内置 phonedata.db、区号库、工信部码号表，纯离线）
- 来电屏蔽：CallKit Call Directory — 黑名单号码静默屏蔽
- 来电标识：已标记的骚扰号码来电显示「骚扰电话」
- 不支持（iOS 无此能力）：自动挂断、实时拦截、悬浮窗

## 目录

```
ios/
  project.yml                    # XcodeGen 配置（生成 RefusePhone.xcodeproj）
  RefusePhone/
    App/                         # SwiftUI 界面（查询 / 黑白名单 / 设置）
    Core/                        # 共享逻辑（号码规范化、号段库、SQLite 归属地、码号表、名单存储）
    Resources/                   # phonedata.db / area_code.json / seed_codenumber.csv（与 Android 共用同一份数据）
    CallDirectory/               # CallKit Call Directory 扩展（来电屏蔽/标识）
```

## 本地构建（装到自己的 iPhone）

前置：Mac 上安装 Xcode（App Store 免费，约 12GB）+ XcodeGen：

```bash
brew install xcodegen
cd ios && xcodegen generate
open RefusePhone.xcodeproj
```

然后在 Xcode 中：

1. 两个 target（RefusePhone / CallDirectoryExtension）的 Signing & Capabilities 都选你的 Team（免费 Apple ID 即可，App Group 会自动处理）。
2. 连接 iPhone，选真机运行。
3. 首次安装后去 **系统设置 → 电话 → 来电阻止与身份识别**，打开「反骚扰」的开关。
4. 在 App 里添加黑名单后，点「设置 → 刷新拦截名单」（或开关扩展重新加载）。

免费账号签名的包 7 天过期，过期后重新用 Xcode 跑一次即可（自用无碍）。

## 远程构建（GitHub Actions）

每次 push 涉及 `ios/**` 时，`.github/workflows/ios-build.yml` 会在 macOS 云机上自动编译（无需本机 Xcode），产物为未签名 `.app`（zip），可从 Actions 页面下载。未签名包不能直接安装到 iPhone，仅供验证编译/快速预览（模拟器可直接跑）。

## 与 Android 版的数据一致性

- `phonedata.db`、`area_code.json`、`seed_codenumber.csv` 直接拷贝自 `app/src/main/assets/`（同一份数据，重新跑 `scripts/fetch_phonedata.py` 后同步复制即可）。
- 黑白名单数据格式不同（iOS 存 App Group UserDefaults），不可与 Android 直接互通。
