# Refuse Phone

Android 来电识别 / 骚扰拦截 / 号码归属地查询 App。

- 来电时离线识别归属地 / 标记，命中本地黑名单或已开启骚扰自动挂断时直接拦截（基于 `CallScreeningService`，无需常驻前台）；
- 查询页支持离线归属地（Google libphonenumber + 可选的 `assets/phonedata.db` 号段增强）；
- 黑白名单、号码标记库本地存储（Room）。

## 构建

```bash
# 调试包（自动用 Android debug keystore 签名）
./gradlew assembleDebug

# 发布包（需配置签名，详见 docs/BUILD_LOCAL.md）
./gradlew assembleRelease
```

签名与本地环境配置见 [`docs/BUILD_LOCAL.md`](docs/BUILD_LOCAL.md)。

## 自动打包（CI）

推送 `main` 分支、打 `v*` 标签、或手动触发时，GitHub Actions 会自动构建 release APK：

- 推送 `main`：构建并在 **Actions → Artifacts** 中提供 APK；
- 打 `v*` 标签（如 `v1.0.0`）：构建并自动创建 **GitHub Release** 附带 APK；
- 工作流读取名为 `SIGNING_KEY` / `KEY_ALIAS` / `KEYSTORE_PASSWORD` / `KEY_PASSWORD` 的仓库 Secrets 完成签名；未配置时仍会产出未签名 APK 以验证构建。

工作流定义见 [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml)。

## 目录

- `app/` — Android 应用模块
- `docs/BUILD_LOCAL.md` — 本地构建环境配置与踩坑记录
- `scripts/` — 号段数据抓取等辅助脚本
