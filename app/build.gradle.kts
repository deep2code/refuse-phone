import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

/** 从项目根目录的 local.properties 读取属性（不存在则返回空字符串）。 */
fun localProperty(key: String): String {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        props.load(FileInputStream(file))
    }
    return props.getProperty(key) ?: ""
}

android {
    namespace = "com.example.phonequery"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.phonequery"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 本项目零 key 运行，无需配置任何 API Key（默认使用 tmini 免费网关 + 内置号段库）
        // 以下为「可选在线源」的 key，留空即不使用；配置后从 local.properties 读取。
        buildConfigField("String", "JUHE_KEY", "\"${localProperty("JUHE_KEY")}\"")
        buildConfigField("String", "BAIDU_PHONE_API_URL", "\"${localProperty("BAIDU_PHONE_API_URL")}\"")
        buildConfigField("String", "BAIDU_PHONE_KEY", "\"${localProperty("BAIDU_PHONE_KEY")}\"")

        // 阿里云市场「多平台号码标记查询 API」（可选在线标记源，需 APPCODE + 调用地址）
        buildConfigField("String", "ALIYUN_MARK_APPCODE", "\"${localProperty("ALIYUN_MARK_APPCODE")}\"")
        buildConfigField("String", "ALIYUN_MARK_URL", "\"${localProperty("ALIYUN_MARK_URL")}\"")
        buildConfigField("String", "ALIYUN_MARK_RESULT_URL", "\"${localProperty("ALIYUN_MARK_RESULT_URL")}\"")

        // 企查查开放平台（可选企业源，按电话反查公司+行业）
        buildConfigField("String", "QCC_KEY", "\"${localProperty("QCC_KEY")}\"")
        buildConfigField("String", "QCC_TOKEN", "\"${localProperty("QCC_TOKEN")}\"")
        // 百度爱企查开放 API（可选企业源）
        buildConfigField("String", "AIQICHA_APIKEY", "\"${localProperty("AIQICHA_APIKEY")}\"")
    }

    // 签名配置：优先读 local.properties，其次读环境变量（CI 注入）。
    // 本地用法：在 local.properties 增加 KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
    // CI 用法：注入环境变量 SIGNING_KEY_BASE64（keystore 的 base64）+ 三个密码变量
    signingConfigs {
        create("release") {
            val keystorePath =
                (project.findProperty("KEYSTORE_PATH") as? String) ?: System.getenv("KEYSTORE_PATH")
            val keystorePassword =
                (project.findProperty("KEYSTORE_PASSWORD") as? String) ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAlias =
                (project.findProperty("KEY_ALIAS") as? String) ?: System.getenv("KEY_ALIAS")
            val keyPassword =
                (project.findProperty("KEY_PASSWORD") as? String) ?: System.getenv("KEY_PASSWORD")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // 开启 R8：混淆 + 裁掉未用代码/资源，包更小、启动更快（已验证编译通过；分发前请在真机测试）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 仅当提供了 KEYSTORE_PATH（来自 local.properties 或 CI 注入的环境变量）时才签名；
            // 否则产出未签名 APK，保证 CI 在未配置密钥时仍可验证构建通过（流水线保持绿色）。
            val ks = (project.findProperty("KEYSTORE_PATH") as? String) ?: System.getenv("KEYSTORE_PATH")
            if (!ks.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 本机走透明代理：lintVitalAnalyzeRelease 会自动联网下载 lint 工具链（dl.google.com），
    // 其 detached 配置解析在代理证书 PKIX 校验时失败，导致 release 构建中断。
    // 个人侧载包无需该 lint 卡口，故关闭（如需恢复，删除此块并预先缓存 lint-gradle 即可）。
    lint {
        checkReleaseBuilds = false
    }

    // 本地单元测试（testReleaseUnitTest）跑在 JVM 上，android.jar 为桩实现；
    // 开启 returnDefaultValues 让未实现的 Android API 返回默认值而非抛异常。
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // 启用 core library desugaring，修复高 JDK 编译依赖（含 Java record）在 D8 dexing 阶段的报错
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Material 图标（Icons.Default.* 需要 material-icons-extended，BOM 管理版本）
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Google libphonenumber：号码解析、格式化、归属地/运营商离线查询
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.29")
    implementation("com.googlecode.libphonenumber:geocoder:3.21")
    implementation("com.googlecode.libphonenumber:carrier:2.29")

    // 注：曾使用 cn.lalaki:phone_location_android 做号段归属地，已移除。原因：
    // 1) 该 AAR 用预览 SDK(CinnamonBun) + Java record 编译，D8 dexing 阶段直接失败；
    // 2) 它体积仅 7KB，号段数据需运行时落盘到 tempDirectory，并非真正内置离线。
    // 替代：libphonenumber(geocoder+carrier) 保底 + assets/phonedata.db 号段级增强
    // （见 PhoneAttributionRepository / scripts/fetch_phonedata.py）。

    // 黑白名单本地存储
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // 偏好设置（开关状态）
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // 权限请求
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
