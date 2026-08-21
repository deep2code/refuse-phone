# 本机构建指南（macOS / Apple Silicon）

首次在本机跑通 debug 打包时踩了不少坑，这里记录**最终可用的配置**和**每个坑的原因**，
避免换机器或清缓存后重新踩一遍。

## 一、当前工具链版本

| 组件 | 版本 | 位置 |
| --- | --- | --- |
| JDK | 21 (Homebrew openjdk@21) | `/opt/homebrew/opt/openjdk@21` |
| Gradle | 8.11.1 | wrapper 自动管理 |
| AGP | 8.9.0 | `build.gradle.kts` |
| Kotlin | 1.9.22 | `build.gradle.kts` |
| compileSdk / targetSdk | 34 | `app/build.gradle.kts` |
| minSdk | 24 | `app/build.gradle.kts` |

> AGP 与 Gradle 是强绑定的：AGP 8.9.x 需要 Gradle 8.11.1+，AGP 8.7.x 对应 Gradle 8.9。
> 单独升其中一个会直接构建失败。

## 二、构建命令

```bash
cd <项目根目录>
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

安装到手机（先开启 USB 调试）：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以把 APK 传到手机上直接点击安装（需允许「安装未知来源应用」）。

## 三、本机特有配置：`~/.gradle/gradle.properties`

这台机器的出站 HTTPS 走透明代理（MITM），且 **JDK 21 自带的 `cacerts` 文件缺失**，
Gradle 拉依赖会报：

```
PKIX path building failed: unable to find valid certification path to requested target
```

解决办法是用系统根证书（已包含代理根证书）生成一个 JKS 信任库，并在用户级配置里指定。
该配置放在 `~/.gradle/gradle.properties`（**不进 Git 仓库**，因为它是本机环境特有的）：

```properties
# 关键：显式置空 proxyHost，让 Gradle 绕过 dev-sidecar 系统代理直连。
# 走代理时：大文件下载会卡死（CLOSE_WAIT），且 lint 工具链 detached 配置 PKIX 失败。
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 \
  -Dhttp.proxyHost= -Dhttps.proxyHost= \
  -Djavax.net.ssl.trustStore=/Users/<你>/.gradle/cacerts.jks \
  -Djavax.net.ssl.trustStoreType=JKS \
  -Djavax.net.ssl.trustStorePassword=changeit

org.gradle.java.home=/opt/homebrew/opt/openjdk@21
```

> 上面的 `-Dhttp.proxyHost= -Dhttps.proxyHost=` 是**必需**的：本机系统代理
> （dev-sidecar 127.0.0.1:31181, MITM）会导致 release 构建的 lint 工具链下载失败
> （`Could not resolve com.android.tools.lint:lint-gradle` PKIX）以及大文件下载卡死。
> 置空后 Gradle 直连 dl.google.com / mavenCentral / jitpack（均直连可达，且更快更稳）。

### 重新生成 `cacerts.jks`

如果换了代理或证书过期，用下面的脚本重建。
> 注意：/etc/ssl/cert.pem **不包含** dev-sidecar 的根证书（MITM 代理），
> 重建后必须额外导入 `~/.dev-sidecar/dev-sidecar.ca.crt`（或 dev-sidecar 导出的 CA），
> 否则 release 构建的 lint 工具链下载（detached 配置）会报 PKIX 失败。

```bash
#!/bin/bash
# 把系统根证书（/etc/ssl/cert.pem，含代理根）逐个导入成 JKS 信任库
OUT=~/.gradle/cacerts.jks
KEYTOOL=/opt/homebrew/opt/openjdk@21/bin/keytool
rm -f "$OUT"
mkdir -p /tmp/certsplit && rm -f /tmp/certsplit/*.pem
awk 'BEGIN{n=0; f=""}
     /-----BEGIN CERTIFICATE-----/{n++; f=sprintf("/tmp/certsplit/cert_%03d.pem", n)}
     f!=""{print >> f}' /etc/ssl/cert.pem
i=0
for c in /tmp/certsplit/*.pem; do
  i=$((i+1))
  "$KEYTOOL" -importcert -noprompt -trustcacerts \
    -alias "sys$i" -file "$c" -keystore "$OUT" -storepass changeit 2>/dev/null
done
echo "已导入 $i 个证书 → $OUT"
```

> 注意：不要用 `-Djavax.net.ssl.trustStoreType=PEM` 直接指向 `/etc/ssl/cert.pem`。
> JDK 9+ 的 PEM 类型信任库**只支持单个证书**，多证书文件会静默失效。

## 四、Gradle 发行包下载失败的处理

wrapper 默认 `networkTimeout=10000`（10 秒），这台机器拉 136MB 的
`gradle-8.11.1-bin.zip` 根本来不及，会反复留下半截的 `.part` 文件。

已把 `gradle/wrapper/gradle-wrapper.properties` 的超时调到 `120000`。
如果仍然下不动，手动下载后塞进 wrapper 缓存即可：

```bash
# 1. 用 curl 慢慢下（不受 wrapper 超时限制）
curl -L -o /tmp/gradle-8.11.1-bin.zip \
  https://services.gradle.org/distributions/gradle-8.11.1-bin.zip

# 2. 找到 wrapper 为当前 distributionUrl 分配的缓存目录（hash 目录名）
D=$(ls -d ~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/ | head -1)

# 3. 安置并打上完成标记
rm -f "$D"/*.part "$D"/*.lck
cp /tmp/gradle-8.11.1-bin.zip "$D"/gradle-8.11.1-bin.zip
unzip -q /tmp/gradle-8.11.1-bin.zip -d "$D"
touch "$D"/gradle-8.11.1-bin.zip.ok
```

## 五、已知的依赖坑

### `cn.lalaki:phone_location_android` —— 已移除，不要加回来

这个离线号段库看起来很合适，但它有两个致命问题：

1. **无法通过 dexing。** 该 AAR 用预览 SDK（`compileSdkPreview="CinnamonBun"`）+ Java `record`
   编译，D8 在给它单独 dex 时报：
   ```
   Invalid build configuration. Attempt to create a global synthetic for
   'Record desugaring' without a global-synthetics consumer.
   ```
   试过的无效方案：升级 AGP 到 8.9.0 / Gradle 8.11.1、开启 `coreLibraryDesugaring`、
   设置 `android.enableGlobalSyntheticsGeneration=true`、降级到 1.1.0（旧版同样含 record）。

2. **并非真正内置离线数据。** AAR 只有 7KB，却要求调用
   `PhoneLocation.setTempDirectory(cacheDir)`，说明号段数据是运行时落盘的。

**替代方案（当前实现）：**

- 保底：Google libphonenumber 的 `geocoder` + `carrier`，开箱即用，给到省/市 + 运营商；
- 增强：`scripts/fetch_phonedata.py` 生成 `phonedata.db` 放进 `app/src/main/assets/`，
  由 `PhoneAttributionRepository` 按号段前 7 位精确匹配，覆盖保底结果。
  数据来自 xluohome/phonedata，可随时重新生成，不用等第三方库发版。

### `android.telecom.CallIdentification` 不存在

Android 10 短暂提供过这个类，**Android 11 (API 30) 已移除**。
目前没有公开 API 让第三方应用改写系统来电界面的来电人信息。
本项目的识别结果通过 `FloatingWindowManager` 的悬浮窗展示（见 `ScreeningService` 类注释）。

## 六、构建产物校验

```bash
SDK=~/Library/Android/sdk
BT=$SDK/build-tools/35.0.1

# 基本信息（包名/版本/权限）
$BT/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head -20

# 签名（debug 包应显示 CN=Android Debug）
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
  $BT/apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
```

## 七、Release 构建（正式签名 APK）

debug 包用的自动 debug keystore，无法上架；要出"真·release"需要**自己的 keystore**。

### 1. 生成 keystore（只需一次，永久保管）

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p ~/.android
PW=$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24)   # 自行记下这个密码！
keytool -genkeypair -v \
  -keystore ~/.android/release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias refusephone \
  -storepass "$PW" -keypass "$PW" \
  -dname "CN=refuse-phone, OU=Personal, O=refuse-phone, L=Beijing, ST=Beijing, C=CN"
```

> ⚠️ **keystore 丢 = 同 AppId 再也无法覆盖安装 / 上架。** 把 `~/.android/release-key.jks`
> 和上面的密码各备份一份到安全地方。

### 2. 把签名信息写进用户级 gradle.properties（不进仓库）

`app/build.gradle.kts` 的 `signingConfigs.release` 从 `project property` 或环境变量读
`KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD`，已写入
`~/.gradle/gradle.properties`：

```properties
KEYSTORE_PATH=/Users/<你>/.android/release-key.jks
KEYSTORE_PASSWORD=<你的密码>
KEY_ALIAS=refusephone
KEY_PASSWORD=<你的密码>
```

（注：项目注释里写的"在 local.properties 增加"不够准确——`project.findProperty` 读的是
gradle.properties / 环境变量，不读 local.properties。用 ~/.gradle/gradle.properties 更符合本机约定。）

### 3. 两个 release 专属坑

- **`lintVitalAnalyzeRelease` 失败（PKIX）**：该任务会自动联网下载 lint 工具链
  （`dl.google.com` 的 `com.android.tools.lint:lint-gradle`），其 detached 配置解析
  在校验代理证书时失败，导致整个 release 构建中断。个人侧载包无需此卡口，已在
  `app/build.gradle.kts` 关掉：
  ```kotlin
  lint { checkReleaseBuilds = false }
  ```
- **proguard 死规则**：`app/proguard-rules.pro` 里曾为已移除的 `cn.lalaki` 依赖保留规则，
  已删除。当前保留的是 Room / Retrofit / Gson / libphonenumber 的 keep 规则，足够 R8 开启。

### 4. R8 混淆开关与体积对比

`app/build.gradle.kts` 的 `buildTypes.release.isMinifyEnabled`：

| isMinifyEnabled | 产物体积 | 说明 |
| --- | --- | --- |
| `false` | ≈ 12.9 MB | 关 R8，仅 `debuggable=false` + 正式签名 |
| `true` | ≈ 4.8 MB | 开 R8（混淆+裁未用代码/资源），小约 **62%** |

结论：**release 比 debug 快/好，但最大收益来自开 R8**（包更小、启动更快、运行期全程序内联优化）。
当前默认 `isMinifyEnabled = true`。R8 可能暴露 debug 隐藏的反射/混淆问题，**分发前务必在真机跑一遍**。

### 5. 构建与产物落盘（重要：输出目录会被清）

```bash
./gradlew assembleRelease
# 默认产物：app/build/outputs/apk/release/app-release.apk
```

> ⚠️ **`assembleRelease` 重跑打包会清空 `app/build/outputs/apk/release/` 整个目录**，
> 所以想同时留 R8 开/关两版做对比时，**必须把 APK 复制出构建目录**，否则会被下一次构建删掉。
> 已把两份稳定产物放到仓库根的 **`release-artifacts/`**：
> - `release-artifacts/app-release-plain.apk` —— R8 关闭版（≈ 12.9 MB）
> - `release-artifacts/app-release-r8.apk`    —— R8 开启版（≈ 4.8 MB，推荐分发）
>
> 注意：`release-artifacts/` 目前**未**加入 .gitignore，若不想把 APK 提交进 Git 需自行加忽略。

